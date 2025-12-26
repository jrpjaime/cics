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

}