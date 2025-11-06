package mx.gob.imss.TestCTG.cics.comunicacion;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;


import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.Channel; // Importar Channel
import com.ibm.ctg.client.Container; // Importar Container
import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.Container; // ASEGÚRATE DE QUE ESTÉ IMPORTADO
import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

public class ComunicacionCICSImpl implements ComunicacionCICS {

	public static final String SERVIDOR = "CICSIPIC";

	private String ctgServer = null;
	private int ctgPort = 0;

    public ComunicacionCICSImpl() {
    }

	public JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto) {
		try {
			servidorCTG.setURL(urlCTGServer);
			servidorCTG.setPort(puerto);
			servidorCTG.open();
			this.ctgServer = urlCTGServer;
			this.ctgPort = puerto;
			System.out.println("[0]Se abre comunicacion al CTG Server "+ urlCTGServer + "            puerto: "+ puerto );
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

	public void asignaParametros(ECIRequest solicitud, int callType,
			String server, String user, String password, String programa,
			String transaccion, byte[] commArea, int commAreaLongitud,
			int modoExtendido, int LuwID) {

		solicitud.Call_Type = callType;
		solicitud.Server = server;
		solicitud.Userid = user;
		solicitud.Password = password;
		solicitud.Program = programa;
		solicitud.Transid = transaccion;
		solicitud.Commarea = commArea;
		solicitud.Commarea_Length = commAreaLongitud;
		solicitud.Extend_Mode = modoExtendido;
		solicitud.Luw_Token = LuwID;

	}

	public void asignaParametros(ECIRequest solicitud, ECIRequestBean bean) {
		solicitud.Call_Type = bean.getCallType();
		solicitud.Server = bean.getServer();
		solicitud.Userid = bean.getUser();
		solicitud.Password = bean.getPassword();
		solicitud.Program = bean.getProgram();
		solicitud.Transid = bean.getTransaction();
		solicitud.Commarea = bean.getCommArea();
		solicitud.Commarea_Length = bean.getCommAreaLongitud();
		solicitud.Extend_Mode = bean.getModoExtendido();
		solicitud.Luw_Token = bean.getLuwID();
	}

	// NUEVO MÉTODO PARA CHANNELS Y CONTAINERS
	public void asignaParametrosConContainers(ECIRequest solicitud, ECIRequestBean bean, String channelName) {
		solicitud.Call_Type = bean.getCallType();
		solicitud.Server = bean.getServer();
		solicitud.Userid = bean.getUser();
		solicitud.Password = bean.getPassword();
		solicitud.Program = bean.getProgram();
		solicitud.Transid = bean.getTransaction();
		solicitud.Extend_Mode = bean.getModoExtendido();
		solicitud.Luw_Token = bean.getLuwID();

		// Crear el Channel
		try {
			Channel channel = new Channel(channelName);
			solicitud.setChannel(channel);

			// Añadir los Containers
			if (bean.getContainers() != null) {
				for (Map.Entry<String, byte[]> entry : bean.getContainers().entrySet()) {
					// ¡CORRECCIÓN FINAL! Crear el Container y luego añadirlo al Channel.
                    // El constructor Container(String, byte[]) sí existe y es correcto.
					Container container = new Container(entry.getKey(), entry.getValue()); // ¡CORREGIDO!
					channel.add(container); // ¡CORREGIDO!
					System.out.println("  Añadido Container: " + entry.getKey() + " con " + entry.getValue().length + " bytes.");
				}
			}
			System.out.println("  Asignados parámetros con Channel: " + channelName);
		} catch (IOException e) {
			System.err.println("Error al crear Channel o Container: " + e.getMessage());
			e.printStackTrace();
		}
	}


	public void cambiaPrograma(ECIRequest solicitud, ECIRequestBean bean) {
		solicitud.Program = bean.getProgram();
		solicitud.Transid = bean.getTransaction();
		solicitud.Commarea = bean.getCommArea();
		solicitud.Commarea_Length = bean.getCommAreaLongitud();
	}

	public void cierraComunicacion(JavaGateway servidorCTG) {
		try {
			servidorCTG.close();
			 System.out.println("[4] Se cierra conexion al Servidor CTG");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String traeRespuesta(ECIRequest solicitud) throws IOException {
		String respuesta = new String(solicitud.Commarea, "IBM037");
		  System.out.println("[3] Se trae la respuesta de la peticion a CICS");
		return respuesta;
	}

	// NUEVO MÉTODO PARA TRAER RESPUESTAS DE CONTAINERS
	public Map<String, byte[]> traeRespuestasDeContainers(ECIRequest solicitud) throws IOException {
		Map<String, byte[]> responseContainers = new HashMap<>();
		if (solicitud.getChannel() != null) {
			System.out.println("[3] Trayendo respuestas de Containers del Channel: " + solicitud.getChannel().getName());
            
            Collection<Container> containersCollection = solicitud.getChannel().getContainers();
			Iterator<Container> containerIterator = containersCollection.iterator();
            
			while (containerIterator.hasNext()) {
				Container container = containerIterator.next();
				responseContainers.put(container.getName(), container.get());
				System.out.println("  Container de respuesta: " + container.getName() + " con " + container.get().length + " bytes.");
			}
		} else {
			System.out.println("[3] No se encontró Channel en la solicitud.");
		}
		return responseContainers;
	}


	public int traeCodigoRespuesta(ECIRequest solicitud) {
		int respuesta = 0;
		respuesta = solicitud.Cics_Rc;
		return respuesta;

	}

	public byte[] creaCommArea(String entrada) {
		byte commArea[] = null;

		while (entrada.length() < 6525) { // Tamaño típico de una commarea
			entrada = entrada + " ";
		}

		try {
			commArea = entrada.getBytes("IBM037");
			System.out.println("[1] Se conformo la Link Area");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return commArea;
	}

	// NUEVO MÉTODO PARA CREAR CONTAINER
    // Este método es coherente con el constructor de Container que sí existe.
	public Container creaContainer(String name, byte[] data) throws IOException {
		return new Container(name, data); // ¡CORREGIDO!
	}


	public String enviarMensajeCics(String cadenaEnviada, String usuario,
			String password, String programa, String transaccion) {
		String respuestaCics = "";
		ComunicacionCICS com = new ComunicacionCICSImpl();
		JavaGateway jg = new JavaGateway();
		ECIRequest req = new ECIRequest();
		ECIRequestBean parametros = new ECIRequestBean();
		  System.out.println("SERVIDOR: "+ SERVIDOR);
		try {
			byte commarea[] = com.creaCommArea(cadenaEnviada);
			com.abreComunicacion(jg, ctgServer, ctgPort);
			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(SERVIDOR);
			parametros.setUser(usuario.toUpperCase());
			parametros.setPassword(password.toUpperCase());
			parametros.setProgram(programa);
			parametros.setTransaction(transaccion);
			parametros.setCommArea(commarea);
			parametros.setCommAreaLongitud(commarea.length);
			parametros.setModoExtendido(ECIRequest.ECI_NO_EXTEND);
			parametros.setLuwID(ECIRequest.ECI_LUW_NEW);

			com.asignaParametros(req, parametros);
			com.enviaSolicitud(jg, req);
			respuestaCics = com.traeRespuesta(req).toString();
			com.cierraComunicacion(jg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return respuestaCics;
	}

	// NUEVO MÉTODO PARA ENVIAR MENSAJE CICS USANDO CHANNELS Y CONTAINERS
	public Map<String, byte[]> enviarMensajeCicsConContainers(
			Map<String, byte[]> inputContainers, String servidor, String usuario,
			String password, String programa, String channelName) {

		Map<String, byte[]> responseContainers = new HashMap<>();
		JavaGateway jg = new JavaGateway();
		ECIRequest req = new ECIRequest();
		ECIRequestBean parametros = new ECIRequestBean();

		try {
			// Asignar parámetros básicos para la conexión
			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(servidor);
			parametros.setUser(usuario.toUpperCase());
			parametros.setPassword(password.toUpperCase());
			parametros.setProgram(programa);
			// No se usa commArea ni commAreaLongitud para Channels/Containers
			parametros.setModoExtendido(ECIRequest.ECI_NO_EXTEND);
			parametros.setLuwID(ECIRequest.ECI_LUW_NEW);

			// Añadir los containers de entrada al ECIRequestBean
			parametros.setContainers(inputContainers);

			// Abrir comunicación (ctgServer y ctgPort deben estar inicializados en CicsService o aquí)
			// Para este método, asumimos que 'this.ctgServer' y 'this.ctgPort' ya están configurados
			if (this.ctgServer == null || this.ctgPort == 0) {
				System.err.println("Error: ctgServer o ctgPort no inicializados en ComunicacionCICSImpl.");
				return responseContainers;
			}
			abreComunicacion(jg, this.ctgServer, this.ctgPort);

			// Asignar todos los parámetros, incluyendo el Channel y sus Containers
			asignaParametrosConContainers(req, parametros, channelName);

			// Enviar la solicitud
			enviaSolicitud(jg, req);

			// Traer las respuestas de los Containers
			responseContainers = traeRespuestasDeContainers(req);

			// Cerrar comunicación
			cierraComunicacion(jg);

		} catch (Exception e) {
			e.printStackTrace();
		}
		return responseContainers;
	}

}