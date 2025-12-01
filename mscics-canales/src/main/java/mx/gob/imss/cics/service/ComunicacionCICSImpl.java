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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

@Component
public class ComunicacionCICSImpl implements ComunicacionCICS {

    private static final Logger logger = LogManager.getLogger(ComunicacionCICSImpl.class); // Cambiado a ComunicacionCICSImpl.class

    @Value("${ctg.ipaddress}")
    private String ctgServer;

    @Value("${ctg.port}")
    private int ctgPort;

    @Value("${ctg.servername}")
    private String cicsServerName;

    public static final String SERVIDOR = "CICSIPIC";

    private static final String CONTAINER_INPUT_NAME = "INPUT";
    private static final String CONTAINER_OUTPUT_NAME = "OUTPUT";

    @Override
    public JavaGateway abreComunicacion(JavaGateway servidorCTG, String urlCTGServer, int puerto) throws IOException {
        servidorCTG.setURL(urlCTGServer);
        servidorCTG.setPort(puerto);
        servidorCTG.open();
        logger.info("[0]Se abre comunicacion al CTG Server {} puerto:{}", urlCTGServer, puerto);
        return servidorCTG;
    }

    @Override
    public void enviaSolicitud(JavaGateway servidorCTG, ECIRequest solicitud) throws IOException {
        servidorCTG.flow(solicitud);
        //logger.info("[2] Se envia peticion al CTG Server");
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
    public void cierraComunicacion(JavaGateway servidorCTG) throws IOException {
        servidorCTG.close();
       // logger.info("[4] Se cierra conexion al Servidor CTG");
    }

    @Override
    public int traeCodigoRespuesta(ECIRequest solicitud) {
        return solicitud.Cics_Rc;
    }

    @Override
    public Channel creaCanal(String entrada) {
        Channel channel = null;
        try {
            channel = new Channel("MYCHANNEL");
            byte[] inputBytes = entrada.getBytes("IBM037");
            channel.createContainer(CONTAINER_INPUT_NAME, inputBytes);
        } catch (ChannelException e) {
            logger.error("Error de ChannelException al crear el canal: {}", e.getMessage(), e);
        } catch (ContainerException e) {
            logger.error("Error de ContainerException al crear el contenedor: {}", e.getMessage(), e);
        } catch (UnsupportedEncodingException e) {
            logger.error("Error de codificación: {}", e.getMessage(), e);
        }
        return channel;
    }

    @Override
    public String traeRespuestaCanal(ECIRequest solicitud) throws IOException, ContainerNotFoundException {
        Channel responseChannel = solicitud.channel;

        if (responseChannel == null) {
            logger.error("ERROR: No se recibió Channel de respuesta.");
            return "ERROR: No se recibió Channel de respuesta.";
        }

        Container outputContainer = responseChannel.getContainer(CONTAINER_OUTPUT_NAME);

        try {
            byte[] responseBytes = outputContainer.getBITData();
            if (responseBytes == null) {
                return "ERROR: El contenedor de respuesta está vacío.";
            }
            return new String(responseBytes, "IBM037");
        } catch (UnsupportedEncodingException e) {
            throw new IOException("Error al decodificar la respuesta: Codificación IBM037 no soportada.", e);
        } catch (ContainerException e) {
            throw new IOException("Error de ContainerException al obtener el registro: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IOException("Error desconocido al procesar la respuesta del contenedor: " + e.getMessage(), e);
        }
    }

    // Este método ya no es necesario, CicsService lo orquesta
    @Override
    public String enviarMensajeCics(String cadenaEnviada, String usuario, String password, String programa, String transaccion) {
        throw new UnsupportedOperationException("Este método ya no debe ser llamado directamente. Utiliza CicsService.enviaReciveCadena().");
    }
}