package mx.gob.imss.cics.service;
 
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.gob.imss.cics.dto.CicsDatosJsonResponse;
import mx.gob.imss.cics.dto.CicsDatosResponse;
import mx.gob.imss.cics.dto.UsuarioCicsMapping;

/**
 * SERVICIO ORQUESTADOR DE INTEGRACIÓN CICS (HUB TRANSACCIONAL).
 * 
 * Esta clase es el núcleo de comunicación del sistema. Sus responsabilidades incluyen:
 * 1. SEGURIDAD PERIMETRAL: Validación de privilegios por binomio Programa-Transacción (ABAC).
 * 2. RESILIENCIA: Gestión de Timeouts dinámicos configurados desde base de datos.
 * 3. CONCURRENCIA: Procesamiento paralelo masivo utilizando un pool de hilos optimizado.
 * 4. TRAZABILIDAD: Generación de UUIDs e ISO Timestamps para auditoría forense.
 * 5. LIMPIEZA DE DATOS: Extracción automática de estructuras JSON en respuestas EBCDIC.
 * 
 * @author Arquitectura de Integración Senior
 */
@Service("cicsConsultasService")
public class CicsConsultasServiceImpl implements CicsConsultasService {

    private static final Logger logger = LogManager.getLogger(CicsConsultasServiceImpl.class);

    @Autowired
    private CicsService cicsService;

    @Autowired
    private UsuarioMappingService usuarioMappingService;

    @Autowired
    private AuditoriaService auditoriaService;

    @Autowired
    @Qualifier("cicsTaskExecutor")
    private Executor taskExecutor;

    @Autowired
    private ObjectMapper objectMapper;

  

    /**
     * MÉTODO 1: CONSULTA INDIVIDUAL (Síncrona con protección de Timeout).
     */
    @Override
    public String realizarConsultaCics(String cadenaEnviar, String userLegacy, String passLegacy, String programa, String transaccion) {
        String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);
        
        int txTimeout = validarAccesoYObtenerTimeout(mapping, programa, transaccion, apiUser);
        String uuid = java.util.UUID.randomUUID().toString();
        String fechaIso = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));

        try {
            return CompletableFuture.supplyAsync(() -> {
                long inicio = System.currentTimeMillis();
                String respuesta = null;
                String error = null;
                int rc = 0;
                try {
                    respuesta = cicsService.enviaReciveCadena(cadenaEnviar, mapping.getCveUsuarioMainframe(), mapping.getDesPasswordMainframe(), programa, transaccion);
                    return respuesta;
                } catch (Exception e) {
                    error = e.getMessage(); rc = -3; throw new RuntimeException(e);
                } finally {
                    long milis = System.currentTimeMillis() - inicio;
                    auditoriaService.registrarBitacora(apiUser, programa, transaccion, cadenaEnviar, rc, milis, (rc == 0 ? "SUCCESS" : "ERROR"), error, uuid);
                }
            }, taskExecutor).get(txTimeout, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Fallo en consulta individual (" + txTimeout + "s): " + e.getMessage());
        }
    }

    /**
     * MÉTODO 2: PROCESAMIENTO CONCURRENTE (LISTA SIMPLE).
     */
    @Override
    public List<CicsDatosResponse> procesarConcurrentemente(List<String> datosEntradaList, String u, String p, String prog, String trans) throws InterruptedException, ExecutionException {
        String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);
        int txTimeout = validarAccesoYObtenerTimeout(mapping, prog, trans, apiUser);

        List<CompletableFuture<CicsDatosResponse>> futures = datosEntradaList.stream().map(dato -> {
            String uuid = java.util.UUID.randomUUID().toString();
            long inicio = System.currentTimeMillis();
            return CompletableFuture.supplyAsync(() -> 
                cicsService.enviaReciveCadena(dato, mapping.getCveUsuarioMainframe(), mapping.getDesPasswordMainframe(), prog, trans), taskExecutor)
                .orTimeout(txTimeout, TimeUnit.SECONDS)
                .handle((res, ex) -> {
                    long milis = System.currentTimeMillis() - inicio;
                    String err = (ex != null) ? ex.getMessage() : null;
                    String est = (ex == null) ? "SUCCESS" : (ex instanceof TimeoutException ? "TIMEOUT" : "ERROR");
                    auditoriaService.registrarBitacora(apiUser, prog, trans, dato, (ex == null ? 0 : -1), milis, est, err, uuid);
                    return CicsDatosResponse.builder().datoEntrada(dato).cicsResponse(res).errorMessage(err).elapsedTimeMs(milis).build();
                });
        }).collect(Collectors.toList());

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList())).get();
    }

    /**
     * MÉTODO 3: PROCESAMIENTO CONCURRENTE CON PARSEO JSON (ORQUESTADOR MASIVO).
     */
    @Override
    public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
            List<String> listaDeDatosEntrada, String userLeg, String passLeg, 
            String nombreProgramaCics, String idTransaccionCics) throws InterruptedException, ExecutionException {

        String nombreUsuarioApi = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapeoConfiguracion = usuarioMappingService.obtenerCredencialesMainframe(nombreUsuarioApi);

        // Seguridad ABAC e identificación de SLA (Timeout)
        int tiempoMaximoEspera = validarAccesoYObtenerTimeout(mapeoConfiguracion, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi);

        logger.info("Orquestando ejecución masiva: {} registros | Usuario: {} | Programa: {} | SLA: {}s", 
                    listaDeDatosEntrada.size(), nombreUsuarioApi, nombreProgramaCics, tiempoMaximoEspera);

        // Reparto de carga asíncrona
        List<CompletableFuture<CicsDatosJsonResponse>> listaTareas = listaDeDatosEntrada.stream()
                .map(dato -> prepararTareaParaHilo(dato, mapeoConfiguracion, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi, tiempoMaximoEspera))
                .collect(Collectors.toList());

        return CompletableFuture.allOf(listaTareas.toArray(new CompletableFuture[0]))
                .thenApply(v -> listaTareas.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                .get();
    }

    // --- MÉTODOS PRIVADOS DE GESTIÓN (ENCAPSULAMIENTO) ---

    /**
     * GESTIÓN DEL CICLO DE VIDA DEL HILO (LIFECYCLE).
     */
    private CompletableFuture<CicsDatosJsonResponse> prepararTareaParaHilo(
            String dato, UsuarioCicsMapping mapeo, String prog, String trans, String userApi, int timeout) {

        final String uuidFijo = java.util.UUID.randomUUID().toString();
        final String fechaIsoFija = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        final long momentoInicio = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            return ejecutarLlamadaMainframeYExtraerJson(dato, mapeo, prog, trans, uuidFijo, fechaIsoFija);
        }, taskExecutor)
        .orTimeout(timeout, TimeUnit.SECONDS)
        .handle((resultado, error) -> {
            return finalizarProcesoYAuditar(dato, resultado, error, momentoInicio, timeout, userApi, prog, trans, uuidFijo, fechaIsoFija);
        });
    }

    /**
     * EJECUCIÓN TÉCNICA Y PARSEO: Interactúa con el Host y limpia la basura textual del JSON.
     */
    private CicsDatosJsonResponse ejecutarLlamadaMainframeYExtraerJson(String dato, UsuarioCicsMapping mapeo, String prog, String trans, String uuid, String fecha) {
        
        String respuestaHost = cicsService.enviaReciveCadena(dato, mapeo.getCveUsuarioMainframe(), mapeo.getDesPasswordMainframe(), prog, trans);
        
        CicsDatosJsonResponse.CicsDatosJsonResponseBuilder builder = CicsDatosJsonResponse.builder()
                .datoEntrada(dato).uuidTransaccion(uuid).fechaPeticionIso(fecha);

        if (respuestaHost == null || respuestaHost.trim().isEmpty()) {
            return builder.errorCode(-4).errorMessage("Mainframe devolvió respuesta vacía").build();
        }

        // Algoritmo de localización de estructuras JSON
        int inicio = Math.max(respuestaHost.indexOf("{"), respuestaHost.indexOf("["));
        int fin = Math.max(respuestaHost.lastIndexOf("}"), respuestaHost.lastIndexOf("]"));

        if (inicio >= 0 && fin >= 0 && fin > inicio) {
            String header = respuestaHost.substring(0, inicio).trim();
            String jsonPart = respuestaHost.substring(inicio, fin + 1);
            try {
                return builder.errorCode(0).headerResponse(header).jsonResponse(objectMapper.readTree(jsonPart)).build();
            } catch (Exception e) {
                return builder.errorCode(-2).headerResponse(respuestaHost).errorMessage("Error Formato JSON: " + e.getMessage()).build();
            }
        }
        return builder.errorCode(-2).headerResponse(respuestaHost.trim()).errorMessage("No se detectó estructura JSON").build();
    }

    /**
     * CIERRE Y AUDITORÍA: Sincroniza la respuesta final con la bitácora institucional.
     */
    private CicsDatosJsonResponse finalizarProcesoYAuditar(
            String dato, CicsDatosJsonResponse res, Throwable ex, long inicio, int timeout, 
            String user, String prog, String trans, String uuid, String fecha) {

        long totalMilis = System.currentTimeMillis() - inicio;
        int codigoFinal = (res != null && res.getErrorCode() != null) ? res.getErrorCode() : 0;
        String msgError = (res != null) ? res.getErrorMessage() : null;
        String estadoAudit = "SUCCESS";

        if (ex != null) {
            boolean esTimeout = (ex instanceof TimeoutException || ex.getCause() instanceof TimeoutException);
            codigoFinal = esTimeout ? -1 : -3;
            estadoAudit = esTimeout ? "TIMEOUT" : "ERROR_SIST";
            msgError = esTimeout ? "Timeout: Sin respuesta en " + timeout + "s" : ex.getMessage();
        } else if (codigoFinal < 0) {
            estadoAudit = "ERROR_PROC";
        }

        // Persistencia asíncrona (9 parámetros requeridos por AuditoriaService)
        auditoriaService.registrarBitacora(user, prog, trans, dato, codigoFinal, totalMilis, estadoAudit, msgError, uuid);

        return CicsDatosJsonResponse.builder()
                .datoEntrada(dato)
                .errorCode(codigoFinal)
                .errorMessage(msgError)
                .headerResponse(res != null ? res.getHeaderResponse() : null)
                .jsonResponse(res != null ? res.getJsonResponse() : null)
                .elapsedTimeMs(totalMilis)
                .uuidTransaccion(uuid)
                .fechaPeticionIso(fecha)
                .build();
    }

    /**
     * SEGURIDAD ABAC: Valida privilegios y recupera el SLA (Timeout) de la transacción.
     */
    private int validarAccesoYObtenerTimeout(UsuarioCicsMapping mapping, String programa, String transaccion, String apiUser) {
        String llave = programa.trim() + "-" + transaccion.trim();
        if (mapping.getPermisosConTimeout() == null || !mapping.getPermisosConTimeout().containsKey(llave)) {
            logger.error("ACCESO DENEGADO: Usuario [{}] intentó ejecutar [{}] sin permiso.", apiUser, llave);
            throw new RuntimeException("Acceso Denegado: No tiene permisos para ejecutar " + llave);
        }
        return mapping.getPermisosConTimeout().get(llave);
    }
}