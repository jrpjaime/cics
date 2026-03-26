package mx.gob.imss.cics.comunicacion;
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

public class ComunicacionCICSImpl implements ComunicacionCICS {

	public static final String SERVIDOR = "CICSIPIC";
	// public static final String SERVIDOR = "IMSSREG1";
	
	// Nombres de Contenedores estandarizados
	private static final String CONTAINER_INPUT_NAME = "INPUT"; 
	private static final String CONTAINER_OUTPUT_NAME = "OUTPUT";

	private String ctgServer = null;
	private int ctgPort = 0;

	public JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto) {
		try {
			servidorCTG.setURL(urlCTGServer);
			servidorCTG.setPort(puerto);
			servidorCTG.open();
			System.out.println("[0]Se abre comunicacion al CTG Server " + urlCTGServer + " puerto:"+ puerto );
		} catch (IOException e) {
			e.printStackTrace();
		}
		return servidorCTG;
	}

	public void enviaSolicitud(JavaGateway servidorCTG, ECIRequest solicitud) {
		try {
			servidorCTG.flow(solicitud);
			System.out.println("[2] Se envia peticion al CTG Server");
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
			 System.out.println("[4] Se cierra conexion al Servidor CTG");
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
 


	public String enviarMensajeCics(String cadenaEnviada, String usuario,
			String password, String programa, String transaccion) {
		String respuestaCics = "";
		ComunicacionCICS com = new ComunicacionCICSImpl();
		JavaGateway jg = new JavaGateway();
		ECIRequest req = new ECIRequest();
		ECIRequestBean parametros = new ECIRequestBean();
		try {
			Channel requestChannel = com.creaCanal(cadenaEnviada);
			
			com.abreComunicacion(jg, ctgServer, ctgPort);
			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(SERVIDOR);
			parametros.setUser(usuario.toUpperCase());
			parametros.setPassword(password.toUpperCase());
			parametros.setProgram(programa);
			parametros.setTransaction(transaccion);
			parametros.setChannel(requestChannel);
			parametros.setModoExtendido(ECIRequest.ECI_NO_EXTEND);
			parametros.setLuwID(ECIRequest.ECI_LUW_NEW);

			com.asignaParametros(req, parametros);
			com.enviaSolicitud(jg, req);
			
			respuestaCics = com.traeRespuestaCanal(req).toString();
			
			com.cierraComunicacion(jg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return respuestaCics;
	}

}