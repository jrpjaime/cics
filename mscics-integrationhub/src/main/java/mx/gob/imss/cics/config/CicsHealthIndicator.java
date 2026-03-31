package mx.gob.imss.cics.config;

import com.ibm.ctg.client.JavaGateway;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class CicsHealthIndicator implements HealthIndicator {

    private final GenericObjectPool<JavaGateway> pool;

    public CicsHealthIndicator(GenericObjectPool<JavaGateway> pool) {
        this.pool = pool;
    }

    @Override
    public Health health() {
        JavaGateway jg = null;
        try {
            jg = pool.borrowObject();
            if (jg.isOpen()) {
                return Health.up()
                        .withDetail("ctg_status", "Connected")
                        .withDetail("active_connections", pool.getNumActive())
                        .withDetail("idle_connections", pool.getNumIdle())
                        .build();
            }
            return Health.down().withDetail("reason", "Gateway closed").build();
        } catch (Exception e) {
            return Health.down(e).build();
        } finally {
            if (jg != null) try { pool.returnObject(jg); } catch (Exception ignore) {}
        }
    }
}