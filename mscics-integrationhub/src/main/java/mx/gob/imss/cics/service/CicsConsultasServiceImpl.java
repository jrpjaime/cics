package mx.gob.imss.cics.service;
 
 
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.gob.imss.cics.dto.CicsDatosJsonResponse;
import mx.gob.imss.cics.dto.CicsDatosResponse;
import mx.gob.imss.cics.dto.UsuarioCicsMapping;

/**
 * Servicio de Orquestación de Consultas CICS.
 * Implementa seguridad por Token JWT, Mapeo de Identidades con Caché 
 * y Auditoría Transaccional según estándar IMSS CIT-DAT.
 */
@Service("cicsConsultasService")
public class CicsConsultasServiceImpl implements CicsConsultasService {

    private static final Logger logger = LogManager.getLogger(CicsConsultasServiceImpl.class);

    @Value("${app.cics.thread-pool-size}")
    private int threadPoolSize;

  

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
     *  validarAcceso. 
     * 1. Si no tiene permiso -> Lanza Excepción (Seguridad)
     * 2. Si tiene permiso -> Devuelve el timeout de la tabla (Configuración)
     */
    private int validarAccesoYObtenerTimeout(UsuarioCicsMapping mapping, String programa, String transaccion, String apiUser) {
        // Creamos la llave de búsqueda
        String llave = programa.trim() + "-" + transaccion.trim();
        
        // --- VALIDACIÓN DE ACCESO ---
        if (mapping.getPermisosConTimeout() == null || !mapping.getPermisosConTimeout().containsKey(llave)) {
            logger.error("BLOQUEO DE SEGURIDAD: El usuario API [{}] intentó ejecutar el programa-transacción [{}] sin tener permiso en MSCC_PERMISO_CICS.", 
                         apiUser, llave);
            
            // Si llegamos aquí, es porque NO EXISTE el registro en la tabla hija
            throw new RuntimeException("Acceso Denegado: Usted no cuenta con privilegios para ejecutar " + llave);
        }
        
        // Si pasó la validación de arriba, recuperamos su timeout específico
        int timeoutEncontrado = mapping.getPermisosConTimeout().get(llave);
        
        logger.debug("Acceso autorizado para {} con timeout de {}s", llave, timeoutEncontrado);
        
        return timeoutEncontrado;
    }
    
    

    /**
     * Realiza una consulta CICS individual.
     * Identifica al usuario del JWT y aplica el mapeo de credenciales de Mainframe.
     */
 @Override
public String realizarConsultaCics(String cadenaEnviar, String usuarioReq, String passwordReq, String programa, String transaccion) {
    String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
    UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);

    int txTimeout = validarAccesoYObtenerTimeout(mapping, programa, transaccion, apiUser);
    
    // Usar CompletableFuture también aquí para aplicar el Timeout de 10s
    try {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String respuesta = null;
            String errorMsg = null;
            int rc = 0;
            try {
                respuesta = cicsService.enviaReciveCadena(cadenaEnviar, mapping.getCveUsuarioMainframe(), 
                                                         mapping.getDesPasswordMainframe(), programa, transaccion);
                return respuesta;
            } catch (Exception e) {
                errorMsg = e.getMessage();
                rc = -1;
                throw new RuntimeException(e);
            } finally {
                long elapsedTime = System.currentTimeMillis() - startTime;
                auditoriaService.registrarBitacora(apiUser, programa, transaccion, cadenaEnviar, 
                                                   rc, elapsedTime, (rc == 0 ? "SUCCESS" : "ERROR"), errorMsg);
            }
        }, taskExecutor)
        .get(txTimeout, TimeUnit.SECONDS); // Timeout forzado para peticiones individuales
    } catch (Exception e) {
        logger.error("Error o Timeout en consulta individual: {}", e.getMessage());
        logger.error("Error o Timeout ({}s) en consulta ", txTimeout);
        throw new RuntimeException("La consulta al Mainframe excedió el tiempo límite Timeout "+ txTimeout + "s" );
    }
}

    /**
     * Procesamiento concurrente de datos (NSS) con Auditoría individual por registro.
     */
    @Override
    public List<CicsDatosResponse> procesarConcurrentemente(
            List<String> datosEntradaList, String usuario, String password, 
            String programa, String transaccion) throws InterruptedException, ExecutionException {

        // Identidad desde JWT y Mapeo
        String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);


        int txTimeout = validarAccesoYObtenerTimeout(mapping, programa, transaccion, apiUser);
        
        final String mfUser = mapping.getCveUsuarioMainframe();
        final String mfPass = mapping.getDesPasswordMainframe();

        logger.info("Iniciando procesamiento concurrente para {} registros. Usuario API: {}", datosEntradaList.size(), apiUser);

        try {
            List<CompletableFuture<CicsDatosResponse>> futures = new ArrayList<>();

            for (String dato : datosEntradaList) {
                CompletableFuture<CicsDatosResponse> future = CompletableFuture.supplyAsync(() -> {
                    long startTime = System.currentTimeMillis();
                    String cicsResponse = null;
                    String errorMessage = null;
                    int rc = 0;

                    try {
                        cicsResponse = cicsService.enviaReciveCadena(dato, mfUser, mfPass, programa, transaccion);
                    } catch (Exception e) {
                        logger.error("Error procesando dato [{}]: {}", dato, e.getMessage());
                        errorMessage = e.getMessage();
                        rc = -1;
                    }

                    long elapsedTime = System.currentTimeMillis() - startTime;

                    // Registro en Bitácora Institucional (Asíncrono)
                    auditoriaService.registrarBitacora(apiUser, programa, transaccion, dato, 
                                                       rc, elapsedTime, (rc == 0 ? "SUCCESS" : "ERROR"), errorMessage);

                    return CicsDatosResponse.builder()
                            .datoEntrada(dato)
                            .cicsResponse(cicsResponse)
                            .errorMessage(errorMessage)
                            .elapsedTimeMs(elapsedTime)
                            .build();
                }, taskExecutor)    // ---  TIMEOUT A NIVEL ORQUESTADOR ---
                    .orTimeout(txTimeout, TimeUnit.SECONDS) // Si en 10 seg no responde el CTG/Mainframe, cancela.
                    .exceptionally(ex -> {
                        // Si ocurre un timeout o cualquier error no capturado arriba
                        logger.error("Timeout o error crítico en petición para [{}]: {}", dato, ex.getMessage());
                        logger.error("Error o Timeout ({}s) en consulta ", txTimeout);
                        return CicsDatosResponse.builder()
                                .datoEntrada(dato)
                                .errorMessage("Timeout: El sistema no respondió en el tiempo límite ("+ txTimeout+ ")s")
                                .build();
                    });

                futures.add(future);
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                    .get();

        } catch (Exception e) {
            logger.error("Error crítico en el orquestador concurrente: {}", e.getMessage(), e);
            throw e;
        }
    }

   
/**
     * Procesamiento concurrente con limpieza y parseo de JSON.
     * Implementa orquestación resiliente: si el Mainframe excede el timeout 
     * configurado en la tabla de permisos, se corta la petición y se audita como TIMEOUT.
     * 
     * @author Arquitectura Senior MSC
     * @param datosEntradaList Lista de cadenas a enviar al Mainframe.
     * @param usuario Credencial de entrada (ignorada, se usa mapeo de BD).
     * @param password Credencial de entrada (ignorada, se usa mapeo de BD).
     * @param programa Nombre del programa CICS.
     * @param transaccion ID de la transacción CICS.
     * @return Lista de respuestas estructuradas con Header, JSON y Métricas.
     */
    /*
    @Override
    public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
            List<String> datosEntradaList, String usuario, String password, 
            String programa, String transaccion) throws InterruptedException, ExecutionException {

        // 1. Identificación del Usuario del API (Seguridad Perimetral)
        String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);

        // 2. Validación de Privilegios y Recuperación de Timeout Granular (ABAC)
        // Si no tiene permiso, este método lanza RuntimeException y corta el flujo aquí.
        int txTimeout = validarAccesoYObtenerTimeout(mapping, programa, transaccion, apiUser);
        
        final String mfUser = mapping.getCveUsuarioMainframe();
        final String mfPass = mapping.getDesPasswordMainframe();

        logger.info("Orquestando ejecución concurrente JSON: {} registros | Usuario: {} | Timeout: {}s", 
                    datosEntradaList.size(), apiUser, txTimeout);

        try {
            List<CompletableFuture<CicsDatosJsonResponse>> futures = new ArrayList<>();

            for (String dato : datosEntradaList) {
                // Captura del tiempo de inicio antes de entrar al hilo asíncrono
                final long startTime = System.currentTimeMillis();

                CompletableFuture<CicsDatosJsonResponse> future = CompletableFuture.supplyAsync(() -> {
                    // --- BLOQUE DE EJECUCIÓN EN MAINFRAME ---
                    String rawResponse = null;
                    String header = null;
                    Object jsonParsed = null;
                    String errorInternal = null;
                    int rc = 0;

                    try {
                        rawResponse = cicsService.enviaReciveCadena(dato, mfUser, mfPass, programa, transaccion);

                        if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                            // LÓGICA DE LOCALIZACIÓN DE ESTRUCTURAS JSON { } o [ ]
                            int jsonStartIndex = rawResponse.indexOf("{");
                            int arrayStartIndex = rawResponse.indexOf("[");
                            int firstCharIndex = -1;

                            if (jsonStartIndex >= 0 && arrayStartIndex >= 0) {
                                firstCharIndex = Math.min(jsonStartIndex, arrayStartIndex);
                            } else if (jsonStartIndex >= 0) {
                                firstCharIndex = jsonStartIndex;
                            } else if (arrayStartIndex >= 0) {
                                firstCharIndex = arrayStartIndex;
                            }

                            if (firstCharIndex >= 0) {
                                header = rawResponse.substring(0, firstCharIndex).trim();
                                String jsonPart = rawResponse.substring(firstCharIndex).trim();

                                // LIMPIEZA DE SUFIJOS (Basura de comunicación)
                                int lastJsonIndex = jsonPart.lastIndexOf("}");
                                int lastArrayIndex = jsonPart.lastIndexOf("]");
                                int finalCharIndex = Math.max(lastJsonIndex, lastArrayIndex);

                                if (finalCharIndex >= 0) {
                                    jsonPart = jsonPart.substring(0, finalCharIndex + 1);
                                }

                                try {
                                    jsonParsed = objectMapper.readTree(jsonPart);
                                } catch (Exception jsonEx) {
                                    logger.error("Error en formato JSON para [{}]: {}", dato, jsonEx.getMessage());
                                    errorInternal = "Error de parseo: " + jsonEx.getMessage();
                                    header = rawResponse; 
                                    rc = -2;
                                }
                            } else {
                                header = rawResponse.trim();
                                errorInternal = "La respuesta no contiene JSON válido.";
                                rc = -3;
                            }
                        } else {
                            header = "";
                            errorInternal = (rawResponse == null) ? "Respuesta Nula" : "Respuesta Vacía";
                            rc = -4;
                        }
                    } catch (Exception e) {
                        logger.error("Fallo técnico en comunicación CICS para [{}]: {}", dato, e.getMessage());
                        errorInternal = e.getMessage();
                        rc = -1;
                    }

                    // Devolvemos un objeto temporal con los datos obtenidos del host
                    return CicsDatosJsonResponse.builder()
                            .datoEntrada(dato)
                            .headerResponse(header)
                            .jsonResponse(jsonParsed)
                            .errorMessage(errorInternal)
                            .build();

                }, taskExecutor)
                .orTimeout(txTimeout, TimeUnit.SECONDS) // Aplicación de Timeout Dinámico
                .handle((res, ex) -> {
                    // --- BLOQUE DE FINALIZACIÓN (Handle se ejecuta SIEMPRE: éxito o fallo) ---
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    String finalError = null;
                    String estadoAuditoria;
                    int rcAuditoria;
                    Object finalJson = null;
                    String finalHeader = null;

                    if (ex != null) {
                        // Caso de FALLO o TIMEOUT
                        // Verificamos si la causa fue el tiempo excedido
                        boolean isTimeout = (ex instanceof java.util.concurrent.TimeoutException || 
                                             ex.getCause() instanceof java.util.concurrent.TimeoutException);
                        
                        estadoAuditoria = isTimeout ? "TIMEOUT" : "ERROR";
                        rcAuditoria = -1;
                        finalError = isTimeout ? 
                                     "Timeout: El sistema no respondió en el tiempo límite de " + txTimeout + "s" : 
                                     ex.getMessage();
                        
                        logger.warn("Petición fallida para [{}]: {} ({} ms)", dato, estadoAuditoria, elapsedTime);
                    } else {
                        // Caso de ÉXITO en la ejecución del hilo
                        finalHeader = res.getHeaderResponse();
                        finalJson = res.getJsonResponse();
                        finalError = res.getErrorMessage();
                        
                        // Si el supplyAsync terminó pero traía un error de lógica (RC != 0)
                        if (finalError != null) {
                            estadoAuditoria = "ERROR_PROC"; // Error de parseo o respuesta vacía
                            rcAuditoria = -2;
                        } else {
                            estadoAuditoria = "SUCCESS";
                            rcAuditoria = 0;
                        }
                    }

                    // 3. Registro en Bitácora (Sincronizado con la respuesta real del microservicio)
                    auditoriaService.registrarBitacora(apiUser, programa, transaccion, dato, 
                                                       rcAuditoria, elapsedTime, estadoAuditoria, finalError);

                    return CicsDatosJsonResponse.builder()
                            .datoEntrada(dato)
                            .headerResponse(finalHeader)
                            .jsonResponse(finalJson)
                            .errorMessage(finalError)
                            .elapsedTimeMs(elapsedTime)
                            .build();
                });

                futures.add(future);
            }

            // Esperar a que todos los hilos terminen (o fallen)
            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                    .get();

        } catch (Exception e) {
            logger.error("Error crítico en orquestador concurrente JSON: {}", e.getMessage(), e);
            throw e;
        }
    }    
*/

    /**
     * PUNTO DE ENTRADA PRINCIPAL: ORQUESTADOR DE CONSULTAS CONCURRENTES
     * 
     * Este método recibe una lista de peticiones y las procesa en paralelo
     * utilizando los hilos configurados y validando la seguridad por cada transacción.
     */
    @Override
    public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
            List<String> listaDeDatosEntrada, 
            String usuarioParametroIgnorado, 
            String passwordParametroIgnorado, 
            String nombreProgramaCics, 
            String idTransaccionCics) throws InterruptedException, ExecutionException {

        // 1. OBTENER IDENTIDAD: Identificamos quién llama desde el Token JWT
        String nombreUsuarioApi = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. RECUPERAR MAPEO: Buscamos en base de datos (o caché) sus credenciales de Mainframe
        UsuarioCicsMapping mapeoIdentidadMainframe = usuarioMappingService.obtenerCredencialesMainframe(nombreUsuarioApi);

        // 3. VALIDAR PERMISOS Y TIEMPO: Verificamos si puede ejecutar este programa y cuánto tiempo le damos
        // Este método lanza una excepción si no tiene permiso, bloqueando la ejecución.
        int tiempoLimiteSegundos = validarAccesoYObtenerTimeout(mapeoIdentidadMainframe, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi);

        logger.info("Iniciando orquestación masiva: {} registros | Usuario API: {} | Programa: {} | Timeout: {}s",  listaDeDatosEntrada.size(), nombreUsuarioApi, nombreProgramaCics, tiempoLimiteSegundos);

        // 4. LANZAR HILOS: Por cada dato en la lista, creamos una tarea asíncrona independiente
        List<CompletableFuture<CicsDatosJsonResponse>> listaDeTareasPrometidas = listaDeDatosEntrada.stream()
                .map(datoIndividual -> prepararTareaParaHilo(datoIndividual, mapeoIdentidadMainframe, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi, tiempoLimiteSegundos))
                .collect(Collectors.toList());

        // 5. CONSOLIDAR RESULTADOS: Esperamos a que todos los hilos terminen (o den timeout) y juntamos las respuestas
        return CompletableFuture.allOf(listaDeTareasPrometidas.toArray(new CompletableFuture[0]))
                .thenApply(v -> listaDeTareasPrometidas.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .get();
    }

    /**
     * MÉTODO DE GESTIÓN DE HILOS (LIFECYCLE)
     * 
     * Configura el comportamiento de un solo hilo: qué código ejecutar, cuánto tiempo
     * esperar y qué hacer cuando termine (ya sea con éxito o con error).
     */
    private CompletableFuture<CicsDatosJsonResponse> prepararTareaParaHilo(
            String datoEntrada, 
            UsuarioCicsMapping mapeo, 
            String programa, 
            String transaccion, 
            String usuarioApi, 
            int timeoutConfigurado) {

        // Capturamos el momento exacto en que inicia este hilo específico
        final long momentoInicioMilisegundos = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            // PASO A: Llamada real al Mainframe y extracción del JSON
            return ejecutarLlamadaMainframeYExtraerJson(datoEntrada, mapeo, programa, transaccion);
        }, taskExecutor)
        .orTimeout(timeoutConfigurado, TimeUnit.SECONDS) // PASO B: Aplicar la "guillotina" de tiempo de la tabla
        .handle((resultadoExitoso, excepcionCapturada) -> {
            // PASO C: Finalizar el proceso, registrar bitácora y construir respuesta para el cliente
            return finalizarProcesoYAuditar(datoEntrada, resultadoExitoso, excepcionCapturada, momentoInicioMilisegundos, 
                                        timeoutConfigurado, usuarioApi, programa, transaccion);
        });
    }

    /**
     * MÉTODO DE LÓGICA DE NEGOCIO (EL TRABAJO REAL)
     * 
     * Este método contiene la lógica pura de comunicación y limpieza de datos.
     * Es el que se ejecuta dentro de cada hilo.
     */
    private CicsDatosJsonResponse ejecutarLlamadaMainframeYExtraerJson(String dato, UsuarioCicsMapping mapeo, String prog, String trans) {

        // 1. GENERAR IDENTIFICADOR ÚNICO DE TRANSACCIÓN (Idempotencia)
        // Este código garantiza que cada registro de la lista tenga su propio ADN único.
        String uuidUnico = java.util.UUID.randomUUID().toString();


        // 2. PREPARAR CADENA PARA EL MAINFRAME
        // Concatenamos el dato original con el UUID usando un separador (ej. pipe '|')
        // IMPORTANTE: El equipo de Mainframe debe estar avisado para leer el UUID después del '|'
       // String cadenaConIdempotencia = dato + "|" + uuidUnico;
         String cadenaConIdempotencia = dato  ;
        
        // 1. Enviar los datos al Mainframe usando las credenciales mapeadas de la BD
        String respuestaCrudaMainframe = cicsService.enviaReciveCadena(cadenaConIdempotencia, mapeo.getCveUsuarioMainframe(), mapeo.getDesPasswordMainframe(), prog, trans);
        
        // Preparamos el constructor de la respuesta
        CicsDatosJsonResponse.CicsDatosJsonResponseBuilder constructorRespuesta = CicsDatosJsonResponse.builder().datoEntrada(dato).uuidTransaccion(uuidUnico); ;

        if (respuestaCrudaMainframe == null || respuestaCrudaMainframe.trim().isEmpty()) {
            return constructorRespuesta.errorMessage("El Mainframe devolvió una respuesta vacía").build();
        }

        // 2. BUSCAR ESTRUCTURA JSON: Localizamos el inicio '{' o '[' y el final '}' o ']'
        int indiceInicioJson = Math.max(respuestaCrudaMainframe.indexOf("{"), respuestaCrudaMainframe.indexOf("["));
        int indiceFinJson = Math.max(respuestaCrudaMainframe.lastIndexOf("}"), respuestaCrudaMainframe.lastIndexOf("]"));

        // 3. PARSEAR SI EXISTE: Si encontramos las llaves, separamos el texto del JSON
        if (indiceInicioJson >= 0 && indiceFinJson >= 0 && indiceFinJson > indiceInicioJson) {
            String parteTextoHeader = respuestaCrudaMainframe.substring(0, indiceInicioJson).trim();
            String parteJsonPuro = respuestaCrudaMainframe.substring(indiceInicioJson, indiceFinJson + 1);
            
            try {
                // Intentamos convertir la cadena de texto en un objeto JSON real
                Object objetoJsonConvertido = objectMapper.readTree(parteJsonPuro);
                return constructorRespuesta.headerResponse(parteTextoHeader).jsonResponse(objetoJsonConvertido).build();
            } catch (Exception e) {
                // Si el JSON viene mal formado (ej. truncado por el Mainframe)
                return constructorRespuesta.headerResponse(respuestaCrudaMainframe).errorMessage("Error de formato JSON: " + e.getMessage()).build();
            }
        } else {
            // No se detectó ninguna estructura JSON, devolvemos todo como texto plano en el header
            return constructorRespuesta.headerResponse(respuestaCrudaMainframe.trim()).errorMessage("No se detectó estructura JSON en la respuesta").build();
        }
    }

    /**
     * MÉTODO DE CIERRE Y AUDITORÍA
     * 
     * Este método sincroniza lo que el usuario recibe con lo que se guarda en la bitácora.
     * Es crucial para que la base de datos refleje si hubo un TIMEOUT real.
     */
    private CicsDatosJsonResponse finalizarProcesoYAuditar(
            String dato, 
            CicsDatosJsonResponse resultadoHilo, 
            Throwable excepcionControlada, 
            long inicioMilis, 
            int timeout, 
            String usuarioApi, 
            String prog, 
            String trans) {

        // Calculamos el tiempo real que tardó (o el tiempo que pasó hasta que dio timeout)
        long tiempoTranscurridoMilisegundos = System.currentTimeMillis() - inicioMilis;
        
        String mensajeErrorFinal = (resultadoHilo != null) ? resultadoHilo.getErrorMessage() : null;
        String estadoParaAuditoria = "SUCCESS";
        int codigoRetornoAuditoria = 0;

        // EVALUACIÓN DE RESULTADO: ¿Terminó bien o hubo un error/timeout?
        if (excepcionControlada != null) {
            // Revisamos si la causa del fallo fue que se acabó el tiempo (Timeout)
            boolean esFallaPorTiempo = (excepcionControlada instanceof TimeoutException || excepcionControlada.getCause() instanceof TimeoutException);
            
            estadoParaAuditoria = esFallaPorTiempo ? "TIMEOUT" : "ERROR_SIST";
            mensajeErrorFinal = esFallaPorTiempo ? "Timeout: El sistema no respondió en " + timeout + "s" : excepcionControlada.getMessage();
            codigoRetornoAuditoria = -1;
        } else if (mensajeErrorFinal != null) {
            // El hilo terminó pero traía un error de negocio o de parseo
            estadoParaAuditoria = "ERROR_PROC";
            codigoRetornoAuditoria = -2;
        }

            // Extraemos el UUID que generamos en el paso anterior
    String uuidParaBd = (resultadoHilo != null) ? resultadoHilo.getUuidTransaccion() : "N/A";

        // REGISTRO ASÍNCRONO EN LA TABLA MSCT_AUDITORIA_CICS
        auditoriaService.registrarBitacora(usuarioApi, prog, trans, dato, 
                                        codigoRetornoAuditoria, tiempoTranscurridoMilisegundos, 
                                        estadoParaAuditoria, mensajeErrorFinal, uuidParaBd);

        // CONSTRUIR OBJETO FINAL PARA EL JSON DE POSTMAN
        return CicsDatosJsonResponse.builder()
                .datoEntrada(dato)
                .headerResponse(resultadoHilo != null ? resultadoHilo.getHeaderResponse() : null)
                .jsonResponse(resultadoHilo != null ? resultadoHilo.getJsonResponse() : null)
                .errorMessage(mensajeErrorFinal)
                .elapsedTimeMs(tiempoTranscurridoMilisegundos) // Tiempo real medido con precisión
                .build();
    }

}