package mx.gob.imss.cics.service;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
 

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.exceptions.ChannelException;
import com.ibm.ctg.client.exceptions.ContainerException;
import com.ibm.ctg.client.exceptions.ContainerNotFoundException;

import mx.gob.imss.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.Container;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component; // Importa Component

@Component // Marca esta clase como un componente de Spring
public class ComunicacionCICSImpl implements ComunicacionCICS {

     private static final Logger logger = LogManager.getLogger(CicsService.class);

	// Inyectamos las propiedades de configuracion desde application.properties
	@Value("${ctg.ipaddress}")
	private String ctgServer;

	@Value("${ctg.port}")
	private int ctgPort;

	@Value("${ctg.servername}")
	private String cicsServerName; // Renombrado para evitar confusion con el parametro 'servidor'

	public static final String SERVIDOR = "CICSIPIC"; // Considera si esto debe ser una propiedad tambien

	// Nombres de Contenedores estandarizados
	private static final String CONTAINER_INPUT_NAME = "INPUT";
	private static final String CONTAINER_OUTPUT_NAME = "OUTPUT";

	public JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto) {
		try {
			servidorCTG.setURL(urlCTGServer);
			servidorCTG.setPort(puerto);
			servidorCTG.open();
			logger.info("[0]Se abre comunicacion al CTG Server " + urlCTGServer + " puerto:"+ puerto );
		} catch (IOException e) {
			e.printStackTrace();
		}
		return servidorCTG;
	}

	public void enviaSolicitud(JavaGateway servidorCTG, ECIRequest solicitud) {
		try {
			servidorCTG.flow(solicitud);
			logger.info("[2] Se envia peticion al CTG Server");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

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

	public void cambiaPrograma(ECIRequest solicitud, ECIRequestBean bean) {
		solicitud.Program = bean.getProgram();
		solicitud.Transid = bean.getTransaction();
		solicitud.channel = bean.getChannel();
	}

	public void cierraComunicacion(JavaGateway servidorCTG) {
		try {
			servidorCTG.close();
			 logger.info("[4] Se cierra conexion al Servidor CTG");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int traeCodigoRespuesta(ECIRequest solicitud) {
		int respuesta = 0;
		respuesta = solicitud.Cics_Rc;
		return respuesta;
	}

	/**
	 * Crea un Channel con un Container llamado "INPUT" conteniendo la entrada,
	 * usando el patrón de fábrica createContainer().
	 */
	@Override
	public Channel creaCanal(String entrada) {

	  Channel channel = null;

	  try {
	    //Crear el Channel
	    channel = new Channel("MYCHANNEL");

	    // Los datos deben estar en bytes, usando la codificación CICS (EBCDIC/IBM037)
	    byte[] inputBytes = entrada.getBytes("IBM037");

	    // Crear el contenedor y añadirlo al canal en un solo paso
	    channel.createContainer(CONTAINER_INPUT_NAME, inputBytes);

	  } catch (ChannelException e) {
	    System.err.println("Error de ChannelException al crear el canal: " + e.getMessage());
	    channel = null;
	  } catch (ContainerException e) {
	    System.err.println("Error de ContainerException al crear el contenedor: " + e.getMessage());
	    channel = null;
	  } catch (UnsupportedEncodingException e) {
	    System.err.println("Error de codificación: " + e.getMessage());
	    channel = null;
	  }

	  return channel;
	}

	/**
	 * Trae la respuesta del Container llamado "OUTPUT" del Channel devuelto,
	 * utilizando getBITData().
	 * Lanza ContainerNotFoundException si el CICS no devuelve el contenedor.
	 */
	@Override
	public String traeRespuestaCanal(ECIRequest solicitud) throws IOException, ContainerNotFoundException {

	  Channel responseChannel = solicitud.channel;

	  if (responseChannel == null) {
	    return "ERROR: No se recibió Channel de respuesta.";
	  }

	  // OBTENER EL CONTENEDOR: Si no existe, lanza ContainerNotFoundException, según la firma.
	  Container outputContainer = responseChannel.getContainer(CONTAINER_OUTPUT_NAME);


	  try {
	    // Obtener los bytes del contenedor usando el método de la API
	    byte[] responseBytes = outputContainer.getBITData();

	    if (responseBytes == null) {
	       return "ERROR: El contenedor de respuesta está vacío.";
	    }

	    // Convertir los bytes a String usando la codificación CICS (IBM037/EBCDIC)
	    String respuesta = new String(responseBytes, "IBM037");

	    return respuesta;

	  } catch (UnsupportedEncodingException e) {
	    throw new IOException("Error al decodificar la respuesta: Codificación IBM037 no soportada.", e);
	  } catch (ContainerException e) {
	    throw new IOException("Error de ContainerException al obtener el registro: " + e.getMessage(), e);
	  } catch (Exception e) {
	    throw new IOException("Error desconocido al procesar la respuesta del contenedor: " + e.getMessage(), e);
	  }
	}

	@Override
	public String enviarMensajeCics(String cadenaEnviada, String usuario,
			String password, String programa, String transaccion) {
		String respuestaCics = "";
		// ComunicacionCICS com = new ComunicacionCICSImpl(); // Ya no creamos una nueva instancia, Spring la inyecta
		JavaGateway jg = new JavaGateway();
		ECIRequest req = new ECIRequest();
		ECIRequestBean parametros = new ECIRequestBean();
		try {
			Channel requestChannel = creaCanal(cadenaEnviada); // Usamos el método de esta misma instancia

			abreComunicacion(jg, ctgServer, ctgPort); // Usamos las propiedades inyectadas
			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(cicsServerName); // Usamos la propiedad inyectada
			parametros.setUser(usuario.toUpperCase());
			parametros.setPassword(password.toUpperCase());
			parametros.setProgram(programa);
			parametros.setTransaction(transaccion);
			parametros.setChannel(requestChannel);
			parametros.setModoExtendido(ECIRequest.ECI_NO_EXTEND);
			parametros.setLuwID(ECIRequest.ECI_LUW_NEW);

			asignaParametros(req, parametros);
			enviaSolicitud(jg, req);

			respuestaCics = traeRespuestaCanal(req).toString();

			cierraComunicacion(jg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return respuestaCics;
	}
}