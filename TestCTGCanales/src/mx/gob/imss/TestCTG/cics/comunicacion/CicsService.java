package mx.gob.imss.TestCTG.cics.comunicacion;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.Channel; // Importar Channel
import com.ibm.ctg.client.Container; // Importar Container


import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Properties;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.Container;
import java.io.UnsupportedEncodingException;


public class CicsService implements Serializable {

	private String version = null;
	private String ctgServer = null;
	private String ctgPortAux = null;
	private String serverName = null;
	private int ctgPort = 0;
	private static final long serialVersionUID = 1L;

	public CicsService() {
		super();
		leerArchivoDeConfiguracion();
	}

	private String enviarMensajeCics(String cadenaEnviada, String servidor,
			String usuario, String password, String programa, String transaccion) {
		System.out.println("cadenaEnviada: " + cadenaEnviada);
		String respuestaCics = "";
		int codRet = 0;
		// Asegúrate de que ComunicacionCICSImpl tenga acceso a ctgServer y ctgPort
		ComunicacionCICSImpl com = new ComunicacionCICSImpl();
		JavaGateway jg = new JavaGateway();
		ECIRequest req = new ECIRequest();
		ECIRequestBean parametros = new ECIRequestBean();
		System.out.println("ctgServer: " + ctgServer);
		System.out.println("ctgPort: " + ctgPort);
		try {
			byte commarea[] = com.creaCommArea(cadenaEnviada);
			com.abreComunicacion(jg, ctgServer, ctgPort); // Pasa ctgServer y ctgPort
			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(servidor);
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
			codRet = com.traeCodigoRespuesta(req);

			imprimirSeparador(codRet);
			com.cierraComunicacion(jg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return respuestaCics;
	}

	// NUEVO MÉTODO para enviar y recibir datos usando Channels y Containers
	private Map<String, byte[]> enviarMensajeCicsConChannelsContainers(
			Map<String, byte[]> inputContainers, String servidor,
			String usuario, String password, String programa, String channelName) {

		Map<String, byte[]> responseContainers = null;
		int codRet = 0;
		ComunicacionCICSImpl com = new ComunicacionCICSImpl(); // Se crea una nueva instancia aquí

		// *** CORRECCIÓN AQUÍ: Abrimos y cerramos la conexión una vez para inicializar
		//     ctgServer y ctgPort en la instancia de ComunicacionCICSImpl,
		//     ya que el método enviarMensajeCicsConContainers en ComunicacionCICSImpl
		//     requiere que esos campos estén seteados internamente.
		//     Una alternativa sería pasar ctgServer y ctgPort directamente a ese método.
		try {
			JavaGateway tempJg = new JavaGateway();
			com.abreComunicacion(tempJg, ctgServer, ctgPort); // Esto inicializa ctgServer y ctgPort en 'com'
			tempJg.close(); // Cerramos la conexión temporal
		} catch (IOException e) {
			System.err.println("Error al inicializar ComunicacionCICSImpl con ctgServer/ctgPort: " + e.getMessage());
			e.printStackTrace();
			return null;
		}

		System.out.println("Enviando a CICS con Channels y Containers...");
		System.out.println("ctgServer: " + ctgServer);
		System.out.println("ctgPort: " + ctgPort);
		System.out.println("Programa CICS: " + programa);
		System.out.println("Channel Name: " + channelName);
		System.out.println("Input Containers:");
		for (Map.Entry<String, byte[]> entry : inputContainers.entrySet()) {
			try {
				System.out.println("  - " + entry.getKey() + ": " + new String(entry.getValue(), "IBM037"));
			} catch (UnsupportedEncodingException e) {
				System.out.println("  - " + entry.getKey() + ": [Error al decodificar]");
			}
		}

		responseContainers = com.enviarMensajeCicsConContainers(
				inputContainers, servidor, usuario, password, programa, channelName);

		if (responseContainers != null && !responseContainers.isEmpty()) {
			System.out.println("Respuesta CICS recibida con Channels y Containers.");
			System.out.println("Output Containers:");
			for (Map.Entry<String, byte[]> entry : responseContainers.entrySet()) {
				try {
					System.out.println("  - " + entry.getKey() + ": " + new String(entry.getValue(), "IBM037"));
				} catch (UnsupportedEncodingException e) {
					System.out.println("  - " + entry.getKey() + ": [Error al decodificar]");
				}
			}
		} else {
			System.out.println("No se recibieron Containers de respuesta.");
		}

		return responseContainers;
	}


	private void leerArchivoDeConfiguracion() {
		InputStream inputFile = null;

		try {

			inputFile = ResourceLoader.load("configuration.properties");
			Properties configurationProps = new Properties();
			configurationProps.load(inputFile);

			version = configurationProps.getProperty("EA01.version");
			ctgServer = configurationProps.getProperty("ctg.ipaddress");
			ctgPortAux = configurationProps.getProperty("ctg.port");
			ctgPort = Integer.valueOf(ctgPortAux);
			serverName = configurationProps.getProperty("ctg.servername");

		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (inputFile != null) {
				try {
					inputFile.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public String enviaReciveCadena(String cadenaEnviar, String usuario,
			String password, String programa, String transaccion) {

		String respuestaCICS;
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String fechaActual = dateFormat.format(date);

		respuestaCICS = limpiarAsteriscos(enviarMensajeCics(cadenaEnviar, serverName, usuario, password, programa, transaccion));
		System.out
				.println("["
						+ fechaActual
						+ "]: IP-"
						+ ctgServer
						+ " | SERV-"
						+ serverName
						+ " | PROG:"
						+ programa
						+ "/TRANS:"
						+ transaccion);
		System.out.println(">>SEND:" + cadenaEnviar);
		System.out.println("<<RCVD:" + respuestaCICS);
		respuestaCICS = respuestaCICS.substring(cadenaEnviar.length());
		return respuestaCICS;
	}

	public Map<String, byte[]> enviaReciveConChannelsContainers(
			Map<String, byte[]> inputContainers, String usuario,
			String password, String programa, String channelName) {

		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String fechaActual = dateFormat.format(date);

		System.out
				.println("["
						+ fechaActual
						+ "]: IP-"
						+ ctgServer
						+ " | SERV-"
						+ serverName
						+ " | PROG:"
						+ programa
						+ "/CHANNEL:"
						+ channelName);

		return enviarMensajeCicsConChannelsContainers(inputContainers, serverName, usuario, password, programa, channelName);
	}


	private String decodificarCodigoRespuesta (int codigo){
		switch (codigo){
			case -15:
				return String.valueOf(codigo) + " " + "ECI_ERR_ALREADY_ACTIVE";
			case -23:
				return String.valueOf(codigo) + " " + "ECI_ERR_CALL_FROM_CALLBACK";
			case -4:
				return String.valueOf(codigo) + " " + "ECI_ERR_CICS_DIED";
			case -8:
				return String.valueOf(codigo) + " " + "ECI_ERR_EXEC_NOT_RESIDENT";
			case -14:
				return String.valueOf(codigo) + " " + "ECI_ERR_INVALID_CALL_TYPE";
			case -19:
				return String.valueOf(codigo) + " " + "ECI_ERR_INVALID_DATA_AREA";
			case -1:
				return String.valueOf(codigo) + " " + "ECI_ERR_INVALID_DATA_LENGTH";
			case -2:
				return String.valueOf(codigo) + " " + "ECI_ERR_INVALID_EXTEND_MODE";
			case -21:
				return String.valueOf(codigo) + " " + "ECI_ERR_INVALID_VERSION";
			case -29:
				return String.valueOf(codigo) + " " + "ECI_ERR_MAX_SESSIONS";
			case -28:
				return String.valueOf(codigo) + " " + "ECI_ERR_MAX_SYSTEMS";
			case -25:
				return String.valueOf(codigo) + " " + "ECI_ERR_MORE_SYSTEMS";
			case -1001:
				return String.valueOf(codigo) + " " + "ECI_ERR_MSG_QUAL_IN_USE";
			case -3:
				return String.valueOf(codigo) + " " + "ECI_ERR_NO_CICS";
			case -1000:
				return String.valueOf(codigo) + " " + "ECI_ERR_NO_MSG_QUALS";
			case -5:
				return String.valueOf(codigo) + " " + "ECI_ERR_NO_REPLY";
			case -17:
				return String.valueOf(codigo) + " " + "ECI_ERR_NO_SESSIONS";
			case -26:
				return String.valueOf(codigo) + " " + "ECI_ERR_NO_SYSTEMS";
			case -12:
				return String.valueOf(codigo) + " " + "ECI_ERR_NULL_MESSAGE_ID";
			case -18:
				return String.valueOf(codigo) + " " + "ECI_ERR_NULL_SEM_HANDLE";
			case -10:
				return String.valueOf(codigo) + " " + "ECI_ERR_NULL_WIN_HANDLE";
			case -16:
				return String.valueOf(codigo) + " " + "ECI_ERR_RESOURCE_SHORTAGE";
			case -6:
				return String.valueOf(codigo) + " " + "ECI_ERR_RESPONSE_TIMEOUT";
			case -30:
				return String.valueOf(codigo) + " " + "ECI_ERR_ROLLEDBACK";
			case -27:
				return String.valueOf(codigo) + " " + "ECI_ERR_SECURITY_ERROR";
			case -9:
				return String.valueOf(codigo) + " " + "ECI_ERR_SYSTEM_ERROR";
			case -13:
				return String.valueOf(codigo) + " " + "ECI_ERR_THREAD_CREATE_ERROR";
			case -7:
				return String.valueOf(codigo) + " " + "ECI_ERR_TRANSACTION_ABEND";
			case -22:
				return String.valueOf(codigo) + " " + "ECI_ERR_UNKNOWN_SERVER";
			case -31:
				return String.valueOf(codigo) + " " + "ECI_ERR_XID_INVALID";
			case 0:
				return String.valueOf(codigo) + " " + "ECI_NO_ERROR";
			default:
				return "";
		}
	}

	private void imprimirSeparador (int codRet){
		String responseHeader = "[CR]: " + decodificarCodigoRespuesta(codRet);
		StringBuffer separator = new StringBuffer();

		for (int i = responseHeader.length() ; i<200 ; i++)
			separator.append("-");

		separator.append("]");

		System.out.println(responseHeader + " " + separator.toString());
	}

	public static String limpiarAsteriscos(String respuestaCics) {
		return respuestaCics.replace('*', ' ');
	}

	public String getVersion() {
		return version;
	}

	public String getCtgServer() {
		return ctgServer;
	}

	public String getCtgPortAux() {
		return ctgPortAux;
	}

	public int getCtgPort() {
		return ctgPort;
	}

	public static long getSerialVersionUID() {
		return serialVersionUID;
	}
}