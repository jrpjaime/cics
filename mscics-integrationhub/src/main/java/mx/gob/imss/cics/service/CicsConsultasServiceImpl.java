package mx.gob.imss.cics.service;
 
 
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
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
     * Valida si el usuario tiene permiso explícito para el binomio Programa-Transacción
     */
    private void validarAcceso(UsuarioCicsMapping mapping, String programa, String transaccion, String apiUser) {
        String llave = programa.trim() + "-" + transaccion.trim();
        if (mapping.getPermisosAutorizados() == null || !mapping.getPermisosAutorizados().contains(llave)) {
            logger.error("ACCESO DENEGADO: El usuario {} intentó ejecutar {}-{} sin autorización.", apiUser, programa, transaccion);
            throw new RuntimeException("No tiene permisos para ejecutar el programa/transacción solicitado.");
        }
    }
    
    

    /**
     * Realiza una consulta CICS individual.
     * Identifica al usuario del JWT y aplica el mapeo de credenciales de Mainframe.
     */
 @Override
public String realizarConsultaCics(String cadenaEnviar, String usuarioReq, String passwordReq, String programa, String transaccion) {
    String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
    UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);

    validarAcceso(mapping, programa, transaccion, apiUser);
    
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
        .get(60, TimeUnit.SECONDS); // Timeout forzado para peticiones individuales
    } catch (Exception e) {
        logger.error("Error o Timeout en consulta individual: {}", e.getMessage());
        throw new RuntimeException("La consulta al Mainframe excedió el tiempo límite.");
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


        validarAcceso(mapping, programa, transaccion, apiUser);
        
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
                    .orTimeout(10, TimeUnit.SECONDS) // Si en 10 seg no responde el CTG/Mainframe, cancela.
                    .exceptionally(ex -> {
                        // Si ocurre un timeout o cualquier error no capturado arriba
                        logger.error("Timeout o error crítico en petición para [{}]: {}", dato, ex.getMessage());
                        return CicsDatosResponse.builder()
                                .datoEntrada(dato)
                                .errorMessage("Timeout: El Mainframe no respondió en el tiempo límite (10s)")
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
     * Mantiene la lógica original de extracción de estructuras { } [ ].
     */
    @Override
    public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
            List<String> datosEntradaList, String usuario, String password, 
            String programa, String transaccion) throws InterruptedException, ExecutionException {

        String apiUser = SecurityContextHolder.getContext().getAuthentication().getName();
        UsuarioCicsMapping mapping = usuarioMappingService.obtenerCredencialesMainframe(apiUser);

        validarAcceso(mapping, programa, transaccion, apiUser);
        
        final String mfUser = mapping.getCveUsuarioMainframe();
        final String mfPass = mapping.getDesPasswordMainframe();

        logger.info("Iniciando procesamiento concurrente JSON para {} registros. Usuario API: {}", datosEntradaList.size(), apiUser);

        try {
            List<CompletableFuture<CicsDatosJsonResponse>> futures = new ArrayList<>();

            for (String dato : datosEntradaList) {
                CompletableFuture<CicsDatosJsonResponse> future = CompletableFuture.supplyAsync(() -> {
                    long startTime = System.currentTimeMillis();
                    String rawResponse = null;
                    String header = null;
                    Object jsonParsed = null;
                    String errorMessage = null;
                    int rc = 0;

                    try {
                        rawResponse = cicsService.enviaReciveCadena(dato, mfUser, mfPass, programa, transaccion);

                        if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                            // LÓGICA ORIGINAL DE LOCALIZACIÓN DE JSON
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

                                // LÓGICA ORIGINAL DE LIMPIEZA DE SUFIJO
                                int lastJsonIndex = jsonPart.lastIndexOf("}");
                                int lastArrayIndex = jsonPart.lastIndexOf("]");
                                int finalCharIndex = Math.max(lastJsonIndex, lastArrayIndex);

                                if (finalCharIndex >= 0) {
                                    jsonPart = jsonPart.substring(0, finalCharIndex + 1);
                                }

                                try {
                                    jsonParsed = objectMapper.readTree(jsonPart);
                                } catch (Exception jsonEx) {
                                    logger.error("Error parseando JSON para dato [{}]: {}", dato, jsonEx.getMessage());
                                    errorMessage = "Error de formato JSON: " + jsonEx.getMessage();
                                    header = rawResponse; 
                                    rc = -2; // Código interno para error de parseo
                                }
                            } else {
                                header = rawResponse.trim();
                                errorMessage = "La respuesta de CICS no contiene una estructura JSON válida.";
                                rc = -3;
                            }
                        } else {
                            header = "";
                            errorMessage = (rawResponse == null) ? "Respuesta nula de CICS" : "Respuesta vacía de CICS";
                            rc = -4;
                        }

                    } catch (Exception e) {
                        logger.error("Error crítico procesando dato [{}]: {}", dato, e.getMessage());
                        errorMessage = e.getMessage();
                        rc = -1;
                    }

                    long elapsedTime = System.currentTimeMillis() - startTime;

                    // Registro en Bitácora Institucional
                    auditoriaService.registrarBitacora(apiUser, programa, transaccion, dato, 
                                                       rc, elapsedTime, (rc == 0 ? "SUCCESS" : "ERROR"), errorMessage);

                    return CicsDatosJsonResponse.builder()
                            .datoEntrada(dato)
                            .headerResponse(header)
                            .jsonResponse(jsonParsed)
                            .errorMessage(errorMessage)
                            .elapsedTimeMs(elapsedTime)
                            .build();
                }, taskExecutor)  // ---  TIMEOUT A NIVEL ORQUESTADOR ---
                    .orTimeout(60, TimeUnit.SECONDS) // Si en 10 seg no responde el CTG/Mainframe, cancela.
                    .exceptionally(ex -> {
                        // Si ocurre un timeout o cualquier error no capturado arriba
                        logger.error("Timeout o error crítico en petición para [{}]: {}", dato, ex.getMessage());
                        return CicsDatosJsonResponse.builder()
                                .datoEntrada(dato)
                                .errorMessage("Timeout: El Mainframe no respondió en el tiempo límite (60s)")
                                .build();
                    });

                futures.add(future);
            }

            return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream().map(CompletableFuture::join).collect(Collectors.toList()))
                    .get();

        } catch (Exception e) {
            logger.error("Error crítico en el orquestador concurrente JSON: {}", e.getMessage(), e);
            throw e;
        }
    }
}