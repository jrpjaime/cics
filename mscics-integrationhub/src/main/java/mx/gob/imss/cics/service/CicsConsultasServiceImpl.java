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
 * Esta clase es el núcleo del sistema y se encarga de la orquestación masiva de peticiones 
 * hacia el Mainframe. Implementa patrones de resiliencia (Timeouts dinámicos), 
 * seguridad granular (ABAC), trazabilidad total (UUID e ISO Timestamps) e 
 * integridad de datos (Idempotencia).
 * 
 * Diseñado para alta disponibilidad en OpenShift atendiendo millones de peticiones diarias.
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
     * PROCESAMIENTO CONCURRENTE CON PARSEO JSON (MÉTODO PRINCIPAL).
     * 
     * Orquesta la ejecución paralela de una lista de datos, validando permisos y 
     * tiempos de respuesta específicos para cada binomio Programa-Transacción.
     */
    @Override
    public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
            List<String> listaDeDatosEntrada, 
            String userLegacy, 
            String passLegacy, 
            String nombreProgramaCics, 
            String idTransaccionCics) throws InterruptedException, ExecutionException {

        // 1. OBTENCIÓN DE IDENTIDAD Y REGLAS DE NEGOCIO
        String nombreUsuarioApi = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapeoConfiguracionMainframe = usuarioMappingService.obtenerCredencialesMainframe(nombreUsuarioApi);

        // 2. SEGURIDAD PERIMETRAL Y SLA (Service Level Agreement)
        // Valida si el usuario tiene permiso y recupera el tiempo máximo de espera (Timeout) de la tabla.
        int tiempoMaximoEsperaSegundos = validarAccesoYObtenerTimeout(mapeoConfiguracionMainframe, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi);

        logger.info("Iniciando orquestación masiva: {} registros | Usuario API: {} | Programa: {} | Timeout: {}s", 
                    listaDeDatosEntrada.size(), nombreUsuarioApi, nombreProgramaCics, tiempoMaximoEsperaSegundos);

        // 3. GENERACIÓN DE TAREAS ASÍNCRONAS
        // Utilizamos Java Streams para mapear cada dato de entrada a un hilo de ejecución independiente.
        List<CompletableFuture<CicsDatosJsonResponse>> listaDeTareasPrometidas = listaDeDatosEntrada.stream()
                .map(datoIndividual -> prepararTareaParaHilo(datoIndividual, mapeoConfiguracionMainframe, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi, tiempoMaximoEsperaSegundos))
                .collect(Collectors.toList());

        // 4. CONSOLIDACIÓN DE RESULTADOS
        // Esperamos a que la totalidad de los hilos finalicen (o den timeout) y recolectamos sus respuestas.
        return CompletableFuture.allOf(listaDeTareasPrometidas.toArray(new CompletableFuture[0]))
                .thenApply(v -> listaDeTareasPrometidas.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .get();
    }

    /**
     * CONFIGURACIÓN DEL CICLO DE VIDA DEL HILO (LIFECYCLE MANAGEMENT).
     * 
     * Prepara el entorno de ejecución para una sola petición:
     * - Genera el UUID y la Fecha de inicio ANTES de disparar el hilo para evitar NULLs en caso de error.
     * - Aplica el orTimeout dinámico.
     * - Dispara la auditoría al finalizar sin importar el resultado (éxito/error/timeout).
     */
    private CompletableFuture<CicsDatosJsonResponse> prepararTareaParaHilo(
            String datoEntrada, 
            UsuarioCicsMapping mapeo, 
            String programa, 
            String transaccion, 
            String usuarioApi, 
            int timeoutConfigurado) {

        // Estos valores se capturan aquí para estar disponibles en el bloque .handle aunque el Mainframe falle.
        final String uuidIdentificadorUnico = java.util.UUID.randomUUID().toString();
        final String fechaPeticionIso = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"));
        final long momentoInicioMilisegundos = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            // EJECUCIÓN TÉCNICA: Llamada al Mainframe y limpieza de datos
            return ejecutarLlamadaMainframeYExtraerJson(datoEntrada, mapeo, programa, transaccion, uuidIdentificadorUnico, fechaPeticionIso);
        }, taskExecutor)
        .orTimeout(timeoutConfigurado, TimeUnit.SECONDS) // APLICACIÓN DE SLA (Corta la conexión si el host no responde)
        .handle((resultadoExitoso, excepcionCapturada) -> {
            // CIERRE Y AUDITORÍA: Sincroniza la respuesta al cliente con la base de datos
            return finalizarProcesoYAuditar(datoEntrada, resultadoExitoso, excepcionCapturada, momentoInicioMilisegundos, 
                                        timeoutConfigurado, usuarioApi, programa, transaccion, uuidIdentificadorUnico, fechaPeticionIso);
        });
    }

    /**
     * LÓGICA DE NEGOCIO E INTERACCIÓN CON EL HOST.
     * 
     * Este método contiene la lógica "sucia" de comunicación:
     * - Envío de datos al CICS.
     * - Algoritmo de localización de estructuras JSON ({ } o [ ]) en la respuesta cruda.
     */
    private CicsDatosJsonResponse ejecutarLlamadaMainframeYExtraerJson(
            String dato, 
            UsuarioCicsMapping mapeo, 
            String prog, 
            String trans, 
            String uuidAsignado, 
            String fechaAsignada) {

        // COMUNICACIÓN FÍSICA: Usamos el servicio CICS central
        String respuestaCrudaMainframe = cicsService.enviaReciveCadena(dato, mapeo.getCveUsuarioMainframe(), mapeo.getDesPasswordMainframe(), prog, trans);
        
        // Preparación del constructor con los metadatos de trazabilidad ya asignados
        CicsDatosJsonResponse.CicsDatosJsonResponseBuilder constructorRespuesta = 
                CicsDatosJsonResponse.builder()
                .datoEntrada(dato)
                .uuidTransaccion(uuidAsignado)
                .fechaPeticionIso(fechaAsignada); 

        if (respuestaCrudaMainframe == null || respuestaCrudaMainframe.trim().isEmpty()) {
            return constructorRespuesta.errorMessage("Mainframe devolvió respuesta nula o vacía").build();
        }

        // EXTRACCIÓN DE JSON: Localizamos el primer y último delimitador de estructura
        int indiceInicioJson = Math.max(respuestaCrudaMainframe.indexOf("{"), respuestaCrudaMainframe.indexOf("["));
        int indiceFinJson = Math.max(respuestaCrudaMainframe.lastIndexOf("}"), respuestaCrudaMainframe.lastIndexOf("]"));

        if (indiceInicioJson >= 0 && indiceFinJson >= 0 && indiceFinJson > indiceInicioJson) {
            String parteHeaderTexto = respuestaCrudaMainframe.substring(0, indiceInicioJson).trim();
            String parteJsonPuro = respuestaCrudaMainframe.substring(indiceInicioJson, indiceFinJson + 1);
            
            try {
                // Intentamos convertir el texto a un objeto JSON (Map/List)
                Object objetoJsonValidado = objectMapper.readTree(parteJsonPuro);
                return constructorRespuesta.headerResponse(parteHeaderTexto).jsonResponse(objetoJsonValidado).build();
            } catch (Exception e) {
                // El JSON está incompleto o mal formado (Error de comunicación o lógica COBOL)
                return constructorRespuesta.headerResponse(respuestaCrudaMainframe).errorMessage("Error de formato JSON: " + e.getMessage()).build();
            }
        } else {
            // Respuesta puramente textual sin estructura JSON
            return constructorRespuesta.headerResponse(respuestaCrudaMainframe.trim()).errorMessage("No se detectó estructura JSON").build();
        }
    }

    /**
     * CIERRE, AUDITORÍA ASÍNCRONA Y CONSTRUCCIÓN DE RESPUESTA FINAL.
     * 
     * Sincroniza el resultado técnico (éxito o error) con la bitácora institucional.
     * Garantiza que el UUID y la Fecha aparezcan siempre en la respuesta final.
     */
    private CicsDatosJsonResponse finalizarProcesoYAuditar(
            String dato, 
            CicsDatosJsonResponse resultadoHilo, 
            Throwable excepcionCapturada, 
            long inicioMilis, 
            int timeoutSec, 
            String usuarioApi, 
            String prog, 
            String trans, 
            String uuidFijo, 
            String fechaFija) {

        long tiempoTranscurridoMilis = System.currentTimeMillis() - inicioMilis;
        String mensajeErrorFinal = (resultadoHilo != null) ? resultadoHilo.getErrorMessage() : null;
        String estadoAuditoria = "SUCCESS";
        int rcAuditoria = 0;

        // EVALUACIÓN TÉCNICA DEL RESULTADO
        if (excepcionCapturada != null) {
            // El hilo terminó por una excepción (posiblemente Timeout)
            boolean esTimeout = (excepcionCapturada instanceof TimeoutException || excepcionCapturada.getCause() instanceof TimeoutException);
            estadoAuditoria = esTimeout ? "TIMEOUT" : "ERROR_SIST";
            mensajeErrorFinal = esTimeout ? "Timeout: El sistema no respondió en el tiempo límite (" + timeoutSec + "s)" : excepcionCapturada.getMessage();
            rcAuditoria = -1;
            logger.warn("Transacción fallida [{}]: {} en {}ms", uuidFijo, estadoAuditoria, tiempoTranscurridoMilis);
        } else if (mensajeErrorFinal != null) {
            // El hilo terminó pero con un error de proceso (parseo o respuesta vacía)
            estadoAuditoria = "ERROR_PROC";
            rcAuditoria = -2;
        }

        // AUDITORÍA INSTITUCIONAL: Se lanza de forma asíncrona para no afectar el tiempo de respuesta
        auditoriaService.registrarBitacora(usuarioApi, prog, trans, dato, 
                                           rcAuditoria, tiempoTranscurridoMilis, 
                                           estadoAuditoria, mensajeErrorFinal, uuidFijo);

        // CONSTRUCCIÓN DEL OBJETO DE RESPUESTA FINAL PARA EL CLIENTE
        return CicsDatosJsonResponse.builder()
                .datoEntrada(dato)
                .headerResponse(resultadoHilo != null ? resultadoHilo.getHeaderResponse() : null)
                .jsonResponse(resultadoHilo != null ? resultadoHilo.getJsonResponse() : null)
                .errorMessage(mensajeErrorFinal)
                .elapsedTimeMs(tiempoTranscurridoMilis)
                .uuidTransaccion(uuidFijo) 
                .fechaPeticionIso(fechaFija)
                .build();
    }

    /**
     * SEGURIDAD ABAC (ATTRIBUTE-BASED ACCESS CONTROL).
     * 
     * Valida si el usuario tiene privilegios para el binomio Programa-Transacción
     * y recupera el SLA (Timeout) correspondiente.
     */
    private int validarAccesoYObtenerTimeout(UsuarioCicsMapping mapping, String programa, String transaccion, String apiUser) {
        String llaveAcceso = programa.trim() + "-" + transaccion.trim();
        
        if (mapping.getPermisosConTimeout() == null || !mapping.getPermisosConTimeout().containsKey(llaveAcceso)) {
            logger.error("BLOQUEO DE SEGURIDAD: Usuario [{}] intentó ejecutar [{}] sin autorización.", apiUser, llaveAcceso);
            throw new RuntimeException("Acceso Denegado: No tiene permisos para ejecutar " + llaveAcceso);
        }
        
        return mapping.getPermisosConTimeout().get(llaveAcceso);
    }

    
}