package mx.gob.imss.cics.config;


import com.ibm.ctg.client.JavaGateway;
// Importar GenericObjectPool directamente aquí
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CicsGatewayPoolConfig {

    @Value("${app.cics.pool.max-total:10}")
    private int maxTotal;

    @Value("${app.cics.pool.max-idle:5}")
    private int maxIdle;

    @Value("${app.cics.pool.min-idle:2}")
    private int minIdle;

    @Value("${app.cics.pool.max-wait-millis:5000}")
    private long maxWaitMillis;

    private final CicsGatewayObjectFactory cicsGatewayObjectFactory;

    public CicsGatewayPoolConfig(CicsGatewayObjectFactory cicsGatewayObjectFactory) {
        this.cicsGatewayObjectFactory = cicsGatewayObjectFactory;
    }

    @Bean
    // Cambiar el tipo de retorno a GenericObjectPool
    public GenericObjectPool<JavaGateway> cicsGatewayPool() {
        GenericObjectPoolConfig<JavaGateway> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        config.setTestOnBorrow(true); 
        config.setTestWhileIdle(true);
        config.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        config.setMinEvictableIdleTime(Duration.ofMinutes(5));
        config.setTestOnReturn(true); // Validar la conexión antes de regresarla al pool
        config.setBlockWhenExhausted(true); 
        config.setJmxEnabled(false);
        GenericObjectPool<JavaGateway> pool = new GenericObjectPool<>(cicsGatewayObjectFactory, config);
        return pool;
    }
}