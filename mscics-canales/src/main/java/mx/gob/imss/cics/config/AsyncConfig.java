package mx.gob.imss.cics.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig {

    @Value("${app.cics.thread-pool-size:100}")
    private int threadPoolSize;

    @Bean(name = "cicsTaskExecutor")
    public Executor cicsTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Configuramos el tamaño del pool basado en el archivo YML
        executor.setCorePoolSize(threadPoolSize / 2); // Hilos mínimos activos
        executor.setMaxPoolSize(threadPoolSize);     // Hilos máximos
        executor.setQueueCapacity(2000);            // Tareas en espera antes de crear más hilos
        executor.setThreadNamePrefix("CicsAsync-");
        
        
        // CallerRunsPolicy hace que el hilo que hace la petición procese la tarea, 
        // evitando que el sistema colapse o pierda datos bajo estrés extremo.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.initialize();
        return executor;
    }
}