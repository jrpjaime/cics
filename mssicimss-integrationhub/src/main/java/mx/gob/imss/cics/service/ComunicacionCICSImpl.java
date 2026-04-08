package mx.gob.imss.cics.service;

import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.Container;
import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.exceptions.ChannelException;
import com.ibm.ctg.client.exceptions.ContainerException;
import com.ibm.ctg.client.exceptions.ContainerNotFoundException;
import mx.gob.imss.cics.beans.ECIRequestBean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
public class ComunicacionCICSImpl implements ComunicacionCICS {

    private static final Logger logger = LogManager.getLogger(ComunicacionCICSImpl.class);

    private static final String CONTAINER_INPUT_NAME = "INPUT";
    private static final String CONTAINER_OUTPUT_NAME = "OUTPUT";
    private static final String CICS_ENCODING = "IBM037";

    @Override
    public void enviaSolicitud(JavaGateway servidorCTG, ECIRequest solicitud) throws IOException {
        servidorCTG.flow(solicitud);
    }

    @Override
    public void asignaParametros(ECIRequest solicitud, ECIRequestBean bean) {
        solicitud.Call_Type = bean.getCallType();
        solicitud.Server = bean.getServer();
        solicitud.Userid = bean.getUser();
        solicitud.Password = bean.getPassword();
        solicitud.Program = bean.getProgram();
        solicitud.Transid = bean.getTransaction();
        solicitud.channel = bean.getChannel();
        solicitud.Extend_Mode = bean.getModoExtendido();
        solicitud.Luw_Token = bean.getLuwID();
    }

    @Override
    public void cambiaPrograma(ECIRequest solicitud, ECIRequestBean bean) {
        solicitud.Program = bean.getProgram();
        solicitud.Transid = bean.getTransaction();
        solicitud.channel = bean.getChannel();
    }

    @Override
    public int traeCodigoRespuesta(ECIRequest solicitud) {
        return solicitud.Cics_Rc;
    }

    @Override
    public Channel creaCanal(String entrada, String nombreCanal)  {
        String textoSeguro = (entrada == null) ? "" : entrada;
        Channel channel = null;
        try {
             channel = new Channel(nombreCanal);
            // Validación Senior: Check de nulo antes de getBytes
            byte[] inputBytes = textoSeguro.getBytes(CICS_ENCODING);
            channel.createContainer(CONTAINER_INPUT_NAME, inputBytes);
        
        } catch (UnsupportedEncodingException e) {
            // Error crítico: El JVM no soporta IBM037  
            logger.error("Error crítico de configuración: El encoding {} no es soportado por la JVM", CICS_ENCODING); 
            throw new RuntimeException("Fallo en la codificación EBCDIC", e);
            
        } catch (ChannelException | ContainerException e) {
            logger.error("Error de infraestructura CICS al crear Canal/Contenedor: {}", e.getMessage(), e);
            // Aquí podrías decidir si devolver null o propagar la excepción
            throw new RuntimeException("Error de infraestructura CICS al crear Canal/Contenedor");
        }
        return channel;
    }

@Override
public String traeRespuestaCanal(ECIRequest solicitud) throws IOException, ContainerNotFoundException {
    Channel responseChannel = solicitud.channel;
    if (responseChannel == null) {
        throw new IOException("El canal de respuesta es nulo.");
    }

    Container outputContainer = responseChannel.getContainer(CONTAINER_OUTPUT_NAME);
    if (outputContainer == null) {
        return "";  
    }

    try {
        byte[] responseBytes = outputContainer.getBITData();
        if (responseBytes == null || responseBytes.length == 0) {
            return "";
        }
        return new String(responseBytes, CICS_ENCODING);
    } catch (UnsupportedEncodingException e) {
        throw new IOException("Error de decodificación EBCDIC", e);
    } catch (ContainerException e) {
        throw new IOException("Error al extraer datos del contenedor CICS", e);
    }
}
}