package mx.gob.imss.cics.service;
 
 
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger; 
import org.springframework.stereotype.Service;

import mx.gob.imss.cics.dto.CicsNssResponse;

import org.springframework.beans.factory.annotation.Autowired;
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

	@Override
	public String realizarConsultaCics(String cadenaEnviar, String usuario, String password, String programa, String transaccion) {
		logger.info("Realizando consulta CICS con programa: {} y transacción: {}", programa, transaccion);
		return cicsService.enviaReciveCadena(cadenaEnviar, usuario, password, programa, transaccion);
	}



	    @Override
    public List<CicsNssResponse> procesarNssConcurrentemente(
            List<String> nssList,
            String usuario,
            String password,
            String programa,
            String transaccion) throws InterruptedException, ExecutionException {

        logger.info("Iniciando procesamiento concurrente para {} NSS con {} hilos.", nssList.size(), threadPoolSize);

        // Usar los valores por defecto si no se proporcionan en la solicitud
        final String effectiveUser = (usuario != null && !usuario.isEmpty()) ? usuario : defaultUser;
        final String effectivePassword = (password != null && !password.isEmpty()) ? password : defaultPassword;
        final String effectivePrograma = (programa != null && !programa.isEmpty()) ? programa : defaultPrograma;
        final String effectiveTransaccion = (transaccion != null && !transaccion.isEmpty()) ? transaccion : defaultTransaccion;


        // Crear un ExecutorService con un ThreadPool de tamaño fijo
        // Usamos CompletableFuture para manejar las tareas y sus resultados
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);
        List<CompletableFuture<CicsNssResponse>> futures = new ArrayList<>();

        for (String nss : nssList) {
            // Para cada NSS, enviamos una tarea al pool de hilos
            CompletableFuture<CicsNssResponse> future = CompletableFuture.supplyAsync(() -> {
                long startTime = System.currentTimeMillis();
                String cicsResponse = null;
                String errorMessage = null;

                try {
                    // La "cadenaEnviar" para CICS debería formarse con el NSS
                    // Aquí asumo que el NSS se envía directamente como la cadena de entrada
                    // Podrías necesitar formatear esta cadena según lo espere tu programa CICS
                    String cadenaEnviar = nss; // O String.format("FORMATO_CICS%s", nss);

                    logger.debug("Procesando NSS: {}", nss);
                    cicsResponse = cicsService.enviaReciveCadena(
                            cadenaEnviar,
                            effectiveUser,
                            effectivePassword,
                            effectivePrograma,
                            effectiveTransaccion
                    );
                    logger.debug("NSS {} procesado. Respuesta CICS: {}", nss, cicsResponse);

                } catch (Exception e) {
                    logger.error("Error al procesar NSS {}: {}", nss, e.getMessage(), e);
                    errorMessage = "Error al procesar NSS: " + e.getMessage();
                }

                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;

                return CicsNssResponse.builder()
                        .nss(nss)
                        .cicsResponse(cicsResponse)
                        .errorMessage(errorMessage)
                        .elapsedTimeMs(elapsedTime)
                        .build();
            }, executor); // Se ejecuta en el executor definido

            futures.add(future);
        }

        // Esperar a que todas las tareas se completen y recolectar los resultados
        List<CicsNssResponse> allResponses = new ArrayList<>();
        for (CompletableFuture<CicsNssResponse> future : futures) {
            try {
                allResponses.add(future.get()); // get() espera a que la tarea termine
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error al obtener el resultado de una tarea concurrente: {}", e.getMessage(), e);
                // Aquí podrías agregar una respuesta de error para el NSS específico si no se pudo obtener
            }
        }

        executor.shutdown(); // Apagar el pool de hilos una vez que todas las tareas han sido enviadas y sus resultados obtenidos
        logger.info("Procesamiento concurrente de NSS finalizado.");

        return allResponses;
    }
}