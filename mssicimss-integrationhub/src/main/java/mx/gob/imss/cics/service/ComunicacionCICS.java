package mx.gob.imss.cics.service;


import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.exceptions.ContainerNotFoundException;
import mx.gob.imss.cics.beans.ECIRequestBean;
import java.io.IOException;

public interface ComunicacionCICS {

    // Métodos de configuración de la solicitud
    void asignaParametros(ECIRequest solicitud, ECIRequestBean bean);
    void cambiaPrograma(ECIRequest solicitud, ECIRequestBean bean);

    // Métodos de ejecución
    void enviaSolicitud(JavaGateway servidorCTG, ECIRequest solicitud) throws IOException;

    // Métodos de manejo de datos (Canales y Contenedores)
    Channel creaCanal(String entrada, String nombreCanal);
    String traeRespuestaCanal(ECIRequest solicitud) throws IOException, ContainerNotFoundException;
    int traeCodigoRespuesta(ECIRequest solicitud);

    // NOTA: Se eliminan abreComunicacion() y cierraComunicacion() de aquí 
    // porque ahora son responsabilidad exclusiva de la Factory.
}