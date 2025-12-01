package mx.gob.imss.cics.config;

import com.ibm.ctg.client.JavaGateway;
import mx.gob.imss.cics.service.ComunicacionCICS;
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

    // Aunque no se usa directamente aquí, es bueno tener una referencia al servicio de comunicación
    // para las operaciones de apertura/cierre de la conexión
    private final ComunicacionCICS comunicacionCICS;

    // Inyectamos ComunicacionCICS para usar sus métodos de apertura y cierre
    public CicsGatewayObjectFactory(ComunicacionCICS comunicacionCICS) {
        this.comunicacionCICS = comunicacionCICS;
    }

    /**
     * Crea una nueva instancia de JavaGateway y abre la conexión.
     */
    @Override
    public JavaGateway create() throws Exception {
        JavaGateway jg = new JavaGateway();
        
            // Usamos el método abreComunicacion de nuestro servicio para abrir la conexión
            comunicacionCICS.abreComunicacion(jg, ctgServer, ctgPort);
            logger.info("CicsGatewayObjectFactory: Nueva instancia de JavaGateway creada y conexión abierta a {}:{}", ctgServer, ctgPort);
      
        return jg;
    }

    /**
     * Envuelve el objeto JavaGateway en un PooledObject.
     */
    @Override
    public PooledObject<JavaGateway> wrap(JavaGateway javaGateway) {
        return new DefaultPooledObject<>(javaGateway);
    }

    /**
     * Destruye una instancia de JavaGateway, cerrando la conexión.
     */
    @Override
    public void destroyObject(PooledObject<JavaGateway> p) throws Exception {
        JavaGateway jg = p.getObject();
        if (jg != null) {
            
                comunicacionCICS.cierraComunicacion(jg);
               // logger.info("CicsGatewayObjectFactory: Instancia de JavaGateway destruida y conexión cerrada.");
          
        }
    }

    /**
     * Valida un objeto antes de que sea prestado del pool.
     * Podrías implementar una lógica para verificar si la conexión sigue activa.
     */
    @Override
    public boolean validateObject(PooledObject<JavaGateway> p) {
        JavaGateway jg = p.getObject();
        // Una forma simple de validar si la conexión está abierta
        // CTG Client no expone un método isConnected() fácilmente,
        // pero podemos intentar un ping o verificar algún estado interno si estuviera disponible.
        // Por ahora, asumimos que si se creó, es válido hasta que falle una operación.
        // Si necesitas una validación más robusta, tendrías que ver la API de CTG.
        // Ejemplo simplificado:
        return jg != null;
    }

    /**
     * Activa un objeto antes de que sea devuelto del pool al cliente.
     * No se requiere ninguna operación especial para JavaGateway aquí, pero se puede añadir lógica si fuera necesario.
     */
    @Override
    public void activateObject(PooledObject<JavaGateway> p) throws Exception {
        super.activateObject(p);
    }

    /**
     * Desactiva un objeto cuando se devuelve al pool.
     * No se requiere ninguna operación especial para JavaGateway aquí.
     */
    @Override
    public void passivateObject(PooledObject<JavaGateway> p) throws Exception {
        super.passivateObject(p);
    }
}