package mx.gob.imss.cics.service;
 
 
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.concurrent.Executor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger; 
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import mx.gob.imss.cics.dto.CicsDatosJsonResponse;
import mx.gob.imss.cics.dto.CicsDatosResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value; 

@Service("cicsConsultasService")
public class CicsConsultasServiceImpl implements CicsConsultasService {

	private static final Logger logger = LogManager.getLogger(CicsConsultasServiceImpl.class);

	@Value("${app.cics.thread-pool-size}")
    private int threadPoolSize;


	@Value("${app.cics.default-cics-user}")
    private String defaultUser;
    @Value("${app.cics.default-cics-password}")
    private String defaultPassword;
    @Value("${app.cics.default-cics-programa}")
    private String defaultPrograma;
    @Value("${app.cics.default-cics-transaccion}")
    private String defaultTransaccion;


	@Autowired
	private CicsService cicsService;  

    @Autowired
    @Qualifier("cicsTaskExecutor")
    private Executor taskExecutor;

    @Autowired
    private ObjectMapper objectMapper; 


    @Override
    public String realizarConsultaCics(String cadenaEnviar, String usuario, String password, String programa, String transaccion) {
        logger.info("Realizando consulta CICS con programa: {} y transacción: {}", programa, transaccion);
        return cicsService.enviaReciveCadena(cadenaEnviar, usuario, password, programa, transaccion);
    }

    @Override
    public List<CicsDatosResponse> procesarConcurrentemente(
            List<String> datosEntradaList,
            String usuario,
            String password,
            String programa,
            String transaccion) throws InterruptedException, ExecutionException {

        logger.info("Iniciando procesamiento concurrente para {} registros.", datosEntradaList.size());

        // 1. Determinar parámetros efectivos
        final String effUser = (usuario != null && !usuario.isEmpty()) ? usuario : defaultUser;
        final String effPass = (password != null && !password.isEmpty()) ? password : defaultPassword;
        final String effProg = (programa != null && !programa.isEmpty()) ? programa : defaultPrograma;
        final String effTrans = (transaccion != null && !transaccion.isEmpty()) ? transaccion : defaultTransaccion;

        try {
            List<CompletableFuture<CicsDatosResponse>> futures = new ArrayList<>();

            for (String dato : datosEntradaList) {
                // USAMOS EL taskExecutor INYECTADO (Pool global reutilizable)
                CompletableFuture<CicsDatosResponse> future = CompletableFuture.supplyAsync(() -> {
                    long startTime = System.currentTimeMillis();
                    String cicsResponse = null;
                    String errorMessage = null;

                    try {
                        // Llamada al servicio CICS
                        cicsResponse = cicsService.enviaReciveCadena(dato, effUser, effPass, effProg, effTrans);
                    } catch (Exception e) {
                        logger.error("Error procesando dato [{}]: {}", dato, e.getMessage());
                        errorMessage = e.getMessage();
                    }

                    long elapsedTime = System.currentTimeMillis() - startTime;

                    return CicsDatosResponse.builder()
                            .datoEntrada(dato)
                            .cicsResponse(cicsResponse)
                            .errorMessage(errorMessage)
                            .elapsedTimeMs(elapsedTime)
                            .build();
                }, taskExecutor); // <--- Referencia al pool inyectado

                futures.add(future);
            }

            // 2. Esperar a que TODAS las tareas terminen usando el pool inyectado
            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            
            // 3. Unir resultados 
            return allOf.thenApply(v -> futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList())
            ).get(); 

        } catch (Exception e) {
            logger.error("Error crítico en el orquestador concurrente: {}", e.getMessage(), e);
            throw e;
        } 
        //  EL BLOQUE FINALLY CON SHUTDOWN: El ciclo de vida del executor ahora lo maneja Spring.
    }

@Override
public List<CicsDatosJsonResponse> procesarConcurrentementeJson(
        List<String> datosEntradaList,
        String usuario,
        String password,
        String programa,
        String transaccion) throws InterruptedException, ExecutionException {

    logger.info("Iniciando procesamiento concurrente JSON para {} registros.", datosEntradaList.size());

    // 1. Determinar parámetros efectivos (Usa los del YML si vienen null)
    final String effUser = (usuario != null && !usuario.isEmpty()) ? usuario : defaultUser;
    final String effPass = (password != null && !password.isEmpty()) ? password : defaultPassword;
    final String effProg = (programa != null && !programa.isEmpty()) ? programa : defaultPrograma;
     final String effTrans = (transaccion != null && !transaccion.isEmpty()) ? transaccion : defaultTransaccion;

    try {
        List<CompletableFuture<CicsDatosJsonResponse>> futures = new ArrayList<>();

        for (String dato : datosEntradaList) {
            CompletableFuture<CicsDatosJsonResponse> future = CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                String rawResponse = null;
                String header = null;
                Object jsonParsed = null;
                String errorMessage = null;

                try {
                    // Llamada al servicio CICS
                    rawResponse = cicsService.enviaReciveCadena(dato, effUser, effPass, effProg, effTrans);

                    if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                        // A. Encontrar dónde empieza el JSON realmente
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
                            // Separar el encabezado de texto (Ej: "WM-RET-JSON (")
                            header = rawResponse.substring(0, firstCharIndex).trim();
                            String jsonPart = rawResponse.substring(firstCharIndex).trim();

                            // B. LIMPIEZA DEL SUFIJO: Encontrar dónde termina el JSON realmente
                            // Buscamos la última llave de cierre o corchete de cierre
                            int lastJsonIndex = jsonPart.lastIndexOf("}");
                            int lastArrayIndex = jsonPart.lastIndexOf("]");
                            int finalCharIndex = Math.max(lastJsonIndex, lastArrayIndex);

                            if (finalCharIndex >= 0) {
                                // Cortamos la cadena para eliminar el ")" y espacios sobrantes
                                jsonPart = jsonPart.substring(0, finalCharIndex + 1);
                            }

                            try {
                                // Intentar parsear la cadena limpia
                                jsonParsed = objectMapper.readTree(jsonPart);
                            } catch (Exception jsonEx) {
                                logger.error("Error parseando JSON para dato [{}]. Contenido intentado: >>>{}<<<", dato, jsonPart);
                                logger.error("Detalle error Jackson: {}", jsonEx.getMessage());
                                errorMessage = "Error de formato JSON: " + jsonEx.getMessage();
                                // Si falla, guardamos la respuesta cruda en el header para diagnóstico
                                header = rawResponse; 
                            }
                        } else {
                            // No se encontró ninguna estructura JSON { o [
                            header = rawResponse.trim();
                            errorMessage = "La respuesta de CICS no contiene una estructura JSON válida.";
                        }
                    } else {
                        header = "";
                        errorMessage = (rawResponse == null) ? "Respuesta nula de CICS" : "Respuesta vacía de CICS";
                    }

                } catch (Exception e) {
                    logger.error("Error crítico procesando dato [{}]: {}", dato, e.getMessage());
                    errorMessage = e.getMessage();
                }

                long elapsedTime = System.currentTimeMillis() - startTime;

                return CicsDatosJsonResponse.builder()
                        .datoEntrada(dato)
                        .headerResponse(header)
                        .jsonResponse(jsonParsed)
                        .errorMessage(errorMessage)
                        .elapsedTimeMs(elapsedTime)
                        .build();
            }, taskExecutor);

            futures.add(future);
        }

        // 2. Esperar a que todas las tareas terminen
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));

        // 3. Unir resultados
        return allOf.thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList())
        ).get();

    } catch (Exception e) {
        logger.error("Error crítico en el orquestador concurrente JSON: {}", e.getMessage(), e);
        throw e;
    }
}

}