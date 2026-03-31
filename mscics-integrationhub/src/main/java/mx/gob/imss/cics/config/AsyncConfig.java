package mx.gob.imss.cics.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync // Habilita el soporte para @Async en toda la aplicación
public class AsyncConfig {

    private static final Logger logger = LoggerFactory.getLogger(AsyncConfig.class);

    @Value("${app.cics.thread-pool-size:25}")
    private int maxPoolSize;

    @Value("${app.cics.pool.max-total:10}")
    private int ctgConnectionLimit;

    /**
     * Configuración del Executor para tareas asíncronas y concurrentes (CICS y Auditoría).
     * Diseñado para alta disponibilidad en OpenShift.
     */
    @Bean(name = "cicsTaskExecutor")
    public Executor cicsTaskExecutor() {
        logger.info("Configurando CicsAsyncExecutor: MaxPoolSize={}, CTG_Limit={}", maxPoolSize, ctgConnectionLimit);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 1. CORE POOL: Hilos mínimos activos (50% del máximo para eficiencia en reposo)
        executor.setCorePoolSize(Math.max(2, maxPoolSize / 2)); 
        
        // 2. MAX POOL: Límite máximo de hilos (Alineado con el YAML)
        executor.setMaxPoolSize(maxPoolSize);     
        
        // 3. QUEUE CAPACITY: Tareas en espera antes de crear nuevos hilos.
        // Un valor de 500 es ideal para absorber ráfagas sin saturar la RAM del Pod.
        executor.setQueueCapacity(500);            
        
        // 4. THREAD PREFIX: Para identificar los hilos en los logs de OpenShift/Splunk
        executor.setThreadNamePrefix("CicsAsync-");
        
        // 5. BACKPRESSURE (CallerRunsPolicy): 
        // Si el pool y la cola están llenos (estrés extremo), el hilo que hace la petición 
        // ejecutará la tarea él mismo. Esto ralentiza al productor y evita que el Pod colapse.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 6. SHUTDOWN GRACEFUL: Asegura que las transacciones en curso terminen antes de matar el Pod.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }
}