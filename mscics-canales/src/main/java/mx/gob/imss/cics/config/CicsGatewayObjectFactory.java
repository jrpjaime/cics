package mx.gob.imss.cics.config;

import com.ibm.ctg.client.JavaGateway;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CicsGatewayObjectFactory extends BasePooledObjectFactory<JavaGateway> {

    private static final Logger logger = LoggerFactory.getLogger(CicsGatewayObjectFactory.class);

    @Value("${ctg.ipaddress}")
    private String ctgServer;

    @Value("${ctg.port}")
    private int ctgPort;

    /**
     * Crea una nueva instancia de JavaGateway y abre la conexión físicamente.
     * La lógica de apertura ahora reside aquí, eliminando la dependencia de ComunicacionCICS.
     */
    @Override
    public JavaGateway create() throws Exception {
        JavaGateway jg = new JavaGateway();
        try {
            jg.setURL(ctgServer);
            jg.setPort(ctgPort);
            jg.open(); 
            return jg;
        } catch (IOException e) {
            logger.error( e.getMessage());
            throw e; // El pool manejará la excepción según su configuración
        }
    }

    @Override
    public PooledObject<JavaGateway> wrap(JavaGateway javaGateway) {
        return new DefaultPooledObject<>(javaGateway);
    }

    /**
     * Cierra la conexión física cuando el pool decide destruir el objeto.
     */
    @Override
    public void destroyObject(PooledObject<JavaGateway> p) throws Exception {
        JavaGateway jg = p.getObject();
        if (jg != null) {
            try {
                if (jg.isOpen()) {
                    jg.close(); 
                }
            } catch (IOException e) {
                logger.error(  e.getMessage());
            }
        }
    }

    @Override
    public boolean validateObject(PooledObject<JavaGateway> p) {
        JavaGateway jg = p.getObject();
        // Un objeto es válido solo si no es nulo y la conexión permanece abierta
        return jg != null && jg.isOpen();
    }
}