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

    // Generamos UUID para trazabilidad
    String uuidTransaccion = java.util.UUID.randomUUID().toString();
    
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
 
                auditoriaService.registrarBitacora(apiUser, programa, transaccion, cadenaEnviar,  rc, elapsedTime, (rc == 0 ? "SUCCESS" : "ERROR"), errorMsg, uuidTransaccion);
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
                String uuidUnico = java.util.UUID.randomUUID().toString();
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
                    auditoriaService.registrarBitacora(apiUser, programa, transaccion, dato, rc, elapsedTime, (rc == 0 ? "SUCCESS" : "ERROR"), errorMessage, uuidUnico);

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
     * PUNTO DE ENTRADA PRINCIPAL: ORQUESTADOR DE CONSULTAS CONCURRENTES CON PARSEO JSON.
     * 
     * Este método centraliza la orquestación masiva de peticiones. Realiza las siguientes acciones:
     * 1. Identifica al usuario del API mediante el contexto de seguridad (JWT).
     * 2. Recupera el mapeo de credenciales de Mainframe y permisos desde la caché/BD.
     * 3. Valida el acceso (ABAC) y recupera el timeout específico por transacción.
     * 4. Distribuye la carga de trabajo en hilos asíncronos paralelos.
     * 5. Consolida los resultados individuales en una respuesta colectiva para el cliente.
     *
     * @param listaDeDatosEntrada       Lista de cadenas (ej. NSS) a procesar.
     * @param usuarioParametroIgnorado  Parametro legacy, se ignora en favor del mapeo dinámico.
     * @param passwordParametroIgnorado Parametro legacy, se ignora en favor del mapeo dinámico.
     * @param nombreProgramaCics        Nombre del programa COBOL en CICS.
     * @param idTransaccionCics         ID de la transacción CICS asociada.
     * @return List de {@link CicsDatosJsonResponse} con el detalle de cada ejecución.
     * @throws InterruptedException Si se interrumpe la espera de los hilos.
     * @throws ExecutionException   Si ocurre un error fatal durante la orquestación.
     */
    @Override
    public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
            List<String> listaDeDatosEntrada, 
            String usuarioParametroIgnorado, 
            String passwordParametroIgnorado, 
            String nombreProgramaCics, 
            String idTransaccionCics) throws InterruptedException, ExecutionException {

        // 1. OBTENCIÓN DE IDENTIDAD: Extraemos el usuario autenticado desde el Token JWT
        String nombreUsuarioApi = SecurityContextHolder.getContext().getAuthentication().getName();

        // 2. RECUPERACIÓN DE CONFIGURACIÓN: Obtenemos credenciales de Mainframe y diccionario de permisos
        UsuarioCicsMapping mapeoIdentidadMainframe = usuarioMappingService.obtenerCredencialesMainframe(nombreUsuarioApi);

        // 3. VALIDACIÓN DE ACCESO Y TIMEOUT: Verificamos privilegios y obtenemos el tiempo límite (SLA) de la transacción
        // Este método es una 'puerta de seguridad'; si falla, lanza RuntimeException inmediatamente.
        int tiempoMaximoEsperaSegundos = validarAccesoYObtenerTimeout(mapeoIdentidadMainframe, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi);

        logger.info("Iniciando orquestación masiva: {} registros | Usuario API: {} | Programa: {} | Timeout: {}s", 
                    listaDeDatosEntrada.size(), nombreUsuarioApi, nombreProgramaCics, tiempoMaximoEsperaSegundos);

        // 4. DISTRIBUCIÓN PARALELA: Creamos una tarea asíncrona por cada elemento de entrada
        List<CompletableFuture<CicsDatosJsonResponse>> listaDeTareasPrometidas = listaDeDatosEntrada.stream()
                .map(datoIndividual -> prepararTareaParaHilo(datoIndividual, mapeoIdentidadMainframe, nombreProgramaCics, idTransaccionCics, nombreUsuarioApi, tiempoMaximoEsperaSegundos))
                .collect(Collectors.toList());

        // 5. CONSOLIDACIÓN: Esperamos a que la totalidad de los hilos finalicen (o den timeout) para retornar la lista
        return CompletableFuture.allOf(listaDeTareasPrometidas.toArray(new CompletableFuture[0]))
                .thenApply(v -> listaDeTareasPrometidas.stream()
                        .map(CompletableFuture::join)
                        .collect(Collectors.toList()))
                .get();
    }

    /**
     * MÉTODO DE GESTIÓN DEL CICLO DE VIDA DEL HILO (LIFECYCLE).
     * 
     * Define la "envoltura" asíncrona de una petición. Gestiona:
     * - El inicio del cronómetro de ejecución.
     * - El disparo del timeout configurado en la base de datos.
     * - El manejo de excepciones tanto técnicas como de tiempo agotado.
     * 
     * @param datoEntrada        Cadena de datos a enviar.
     * @param mapeo              Objeto con credenciales de Mainframe autorizadas.
     * @param programa           Nombre del programa CICS.
     * @param transaccion        ID de la transacción CICS.
     * @param usuarioApi         Nombre del usuario que originó la petición.
     * @param timeoutConfigurado Tiempo límite en segundos para este hilo.
     * @return Un {@link CompletableFuture} que eventualmente contendrá la respuesta procesada.
     */
    private CompletableFuture<CicsDatosJsonResponse> prepararTareaParaHilo(
            String datoEntrada, 
            UsuarioCicsMapping mapeo, 
            String programa, 
            String transaccion, 
            String usuarioApi, 
            int timeoutConfigurado) {

        // Captura del tiempo inicial para cálculo preciso de latencia en milisegundos
        final long momentoInicioMilisegundos = System.currentTimeMillis();

        return CompletableFuture.supplyAsync(() -> {
            // PASO A: Ejecución de la lógica de comunicación y extracción de datos
            return ejecutarLlamadaMainframeYExtraerJson(datoEntrada, mapeo, programa, transaccion);
        }, taskExecutor)
        .orTimeout(timeoutConfigurado, TimeUnit.SECONDS) // PASO B: Control de Timeout dinámico
        .handle((resultadoExitoso, excepcionCapturada) -> {
            // PASO C: Finalización de proceso, registro de auditoría y construcción de DTO final
            return finalizarProcesoYAuditar(datoEntrada, resultadoExitoso, excepcionCapturada, momentoInicioMilisegundos, 
                                        timeoutConfigurado, usuarioApi, programa, transaccion);
        });
    }

    /**
     * MÉTODO DE LÓGICA DE NEGOCIO (EJECUCIÓN TÉCNICA).
     * 
     * Realiza la interacción física con el Mainframe. Sus responsabilidades son:
     * 1. Generar un identificador único (UUID) para asegurar la idempotencia.
     * 2. Llamar al servicio CICS (CicsService) con las credenciales mapeadas.
     * 3. Localizar y extraer estructuras JSON { } o [ ] dentro de la respuesta EBCDIC.
     * 
     * @param dato        Datos de entrada originales.
     * @param mapeo       Contenedor de credenciales de Mainframe.
     * @param prog        Programa CICS a invocar.
     * @param trans       Transacción CICS.
     * @return {@link CicsDatosJsonResponse} parcial con el JSON parseado y el UUID.
     */
    private CicsDatosJsonResponse ejecutarLlamadaMainframeYExtraerJson(String dato, UsuarioCicsMapping mapeo, String prog, String trans) {

        // 1. GENERACIÓN DE TOKEN DE IDEMPOTENCIA: Garantiza trazabilidad única por registro
        String uuidIdentificadorUnico = java.util.UUID.randomUUID().toString();

        // 2. PREPARACIÓN DE CADENA: En este punto se puede anexar el UUID si el COBOL lo requiere
        // String cadenaConIdempotencia = dato + "|" + uuidIdentificadorUnico;
        String cadenaDeEnvioFinal = dato;
        
        // 3. COMUNICACIÓN FÍSICA: Envío al CICS Transaction Gateway (CTG)
        String respuestaCrudaMainframe = cicsService.enviaReciveCadena(cadenaDeEnvioFinal, mapeo.getCveUsuarioMainframe(), mapeo.getDesPasswordMainframe(), prog, trans);
        
        // Inicializamos el constructor de respuesta vinculando el UUID desde este momento
        CicsDatosJsonResponse.CicsDatosJsonResponseBuilder constructorDeRespuesta = 
                CicsDatosJsonResponse.builder()
                .datoEntrada(dato)
                .uuidTransaccion(uuidIdentificadorUnico); 

        if (respuestaCrudaMainframe == null || respuestaCrudaMainframe.trim().isEmpty()) {
            return constructorDeRespuesta.errorMessage("El Mainframe devolvió una respuesta vacía").build();
        }

        // 4. ALGORITMO DE EXTRACCIÓN JSON: Localizamos delimitadores de objetos o arreglos
        int indiceInicioEstructura = Math.max(respuestaCrudaMainframe.indexOf("{"), respuestaCrudaMainframe.indexOf("["));
        int indiceFinEstructura = Math.max(respuestaCrudaMainframe.lastIndexOf("}"), respuestaCrudaMainframe.lastIndexOf("]"));

        if (indiceInicioEstructura >= 0 && indiceFinEstructura >= 0 && indiceFinEstructura > indiceInicioEstructura) {
            String parteTextoInformativo = respuestaCrudaMainframe.substring(0, indiceInicioEstructura).trim();
            String parteJsonPotencial = respuestaCrudaMainframe.substring(indiceInicioEstructura, indiceFinEstructura + 1);
            
            try {
                // Intentamos convertir el segmento de texto en un objeto JSON estructurado
                Object objetoJsonValidado = objectMapper.readTree(parteJsonPotencial);
                return constructorDeRespuesta.headerResponse(parteTextoInformativo).jsonResponse(objetoJsonValidado).build();
            } catch (Exception e) {
                // El JSON está mal formado o incompleto (posible truncamiento en Mainframe)
                return constructorDeRespuesta.headerResponse(respuestaCrudaMainframe).errorMessage("Fallo al parsear estructura JSON: " + e.getMessage()).build();
            }
        } else {
            // Respuesta de texto plano sin estructuras JSON detectadas
            return constructorDeRespuesta.headerResponse(respuestaCrudaMainframe.trim()).errorMessage("No se detectó una estructura JSON válida en la respuesta").build();
        }
    }

    /**
     * MÉTODO DE FINALIZACIÓN Y AUDITORÍA ASÍNCRONA.
     * 
     * Este método garantiza la consistencia entre lo que se le responde al cliente y lo que se persiste.
     * Se encarga de:
     * 1. Determinar el estado final (SUCCESS, TIMEOUT o ERROR).
     * 2. Registrar el evento en la tabla MSCT_AUDITORIA_CICS de forma asíncrona.
     * 3. Asegurar que el UUID de transacción aparezca en la respuesta JSON final.
     * 
     * @param dato                 Dato de entrada procesado.
     * @param resultadoHilo        Resultado exitoso del paso anterior (si existe).
     * @param excepcionControlada  Error o excepción capturada (si existe).
     * @param inicioMilis          Tiempo en que inició la petición.
     * @param timeoutConfigurado   Tiempo máximo que se le otorgó a la petición.
     * @param usuarioApi           Usuario responsable.
     * @param prog                 Programa ejecutado.
     * @param trans                Transacción ejecutada.
     * @return Objeto final {@link CicsDatosJsonResponse} listo para serialización JSON.
     */
    private CicsDatosJsonResponse finalizarProcesoYAuditar(
            String dato, 
            CicsDatosJsonResponse resultadoHilo, 
            Throwable excepcionControlada, 
            long inicioMilis, 
            int timeoutConfigurado, 
            String usuarioApi, 
            String prog, 
            String trans) {

        // Cálculo de latencia total
        long tiempoTranscurridoMilisegundos = System.currentTimeMillis() - inicioMilis;
        
        String mensajeDeErrorFinal = (resultadoHilo != null) ? resultadoHilo.getErrorMessage() : null;
        String estadoParaAuditoria = "SUCCESS";
        int codigoRetornoAuditoria = 0;

        // Recuperación del UUID de trazabilidad (se extrae del resultado previo para mantener consistencia)
        String uuidIdentificadorFinal = (resultadoHilo != null) ? resultadoHilo.getUuidTransaccion() : "N/A";

        // ANÁLISIS DE RESULTADO TÉCNICO
        if (excepcionControlada != null) {
            // Evaluamos si el fallo fue provocado por el disparador de tiempo (orTimeout)
            boolean esFallaPorTimeout = (excepcionControlada instanceof TimeoutException || excepcionControlada.getCause() instanceof TimeoutException);
            
            estadoParaAuditoria = esFallaPorTimeout ? "TIMEOUT" : "ERROR_SIST";
            mensajeDeErrorFinal = esFallaPorTimeout ? "Timeout: El sistema no respondió en el tiempo límite (" + timeoutConfigurado + "s)" : excepcionControlada.getMessage();
            codigoRetornoAuditoria = -1;
        } else if (mensajeDeErrorFinal != null) {
            // El hilo terminó pero se identificó un error lógico o de formato
            estadoParaAuditoria = "ERROR_PROC";
            codigoRetornoAuditoria = -2;
        }

        // PERSISTENCIA EN BITÁCORA INSTITUCIONAL: Se realiza de forma no bloqueante (@Async)
        auditoriaService.registrarBitacora(usuarioApi, prog, trans, dato, 
                                        codigoRetornoAuditoria, tiempoTranscurridoMilisegundos, 
                                        estadoParaAuditoria, mensajeDeErrorFinal, uuidIdentificadorFinal);

        // CONSTRUCCIÓN DEL OBJETO DE RESPUESTA FINAL (Garantizando el UUID en el JSON de salida)
        return CicsDatosJsonResponse.builder()
                .datoEntrada(dato)
                .headerResponse(resultadoHilo != null ? resultadoHilo.getHeaderResponse() : null)
                .jsonResponse(resultadoHilo != null ? resultadoHilo.getJsonResponse() : null)
                .errorMessage(mensajeDeErrorFinal)
                .elapsedTimeMs(tiempoTranscurridoMilisegundos)
                .uuidTransaccion(uuidIdentificadorFinal) // <--- ASIGNACIÓN CRUCIAL PARA VISIBILIDAD EN POSTMAN
                .build();
    }

 
}