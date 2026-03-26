package mx.gob.imss.cics.comunicacion;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

 

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

import mx.gob.imss.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.Channel; 

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

	private String enviarMensajeCics(String cadenaEnviada, String servidor,	String usuario, String password, String programa, String transaccion) {


		System.out.println("enviarMensajeCics");
		System.out.println("usuario: " + usuario);
		 

		String respuestaCics = "";
		int codRet = 0;
		ComunicacionCICS com = new ComunicacionCICSImpl();
		ECIRequest req = new ECIRequest();
		JavaGateway jg = new JavaGateway();
		ECIRequestBean parametros = new ECIRequestBean();
		
		try {
			
			// Crear el Channel y el Container de Entrada
			Channel requestChannel = com.creaCanal(cadenaEnviada); 
			
			com.abreComunicacion(jg, ctgServer, ctgPort);
			
			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(servidor);
			parametros.setUser(usuario.toUpperCase());
			parametros.setPassword(password.toUpperCase());
			parametros.setProgram(programa);
			parametros.setTransaction(transaccion);
			parametros.setChannel(requestChannel);
			
			parametros.setModoExtendido(ECIRequest.ECI_NO_EXTEND);
			parametros.setLuwID(ECIRequest.ECI_LUW_NEW);
 
			com.enviaSolicitud(jg, req);
			
			// Obtener la respuesta del Container de Salida 
			respuestaCics = com.traeRespuestaCanal(req).toString();
			
			codRet = com.traeCodigoRespuesta(req);
			
			imprimirSeparador(codRet);
			com.cierraComunicacion(jg);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return respuestaCics;
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

			//System.out.println(version);
			//System.out.println(ctgServer);
			//System.out.println(ctgPortAux);
			//System.out.println(ctgPort);
			//System.out.println(serverName);

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

	/*
	 * Metodo para enviar una cadena al CICS y atrapar la respuesta del mismo,
	 * con los parametros se especifica el usuario, password, programa y tipo de
	 * transaccin. del CICS.
	 * 
	 * @params La cadena con la transaccin a realizar, el usuario, el password,
	 * el programa y la transaccion.
	 * 
	 * @return La cadena de respuesta del CICS
	 */
	public String enviaReciveCadena(String cadenaEnviar, String usuario,
			String password, String programa, String transaccion) {

		String respuestaCICS;
		DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
		Date date = new Date();
		String fechaActual = dateFormat.format(date);


		System.out.println("[" + fechaActual + "]: IP-" + ctgServer + " | SERV-" + serverName + " | PROG:" + programa + "/TRANS:" + transaccion);
		System.out.println(">>SEND:" + cadenaEnviar);



		respuestaCICS = limpiarAsteriscos(enviarMensajeCics(cadenaEnviar, serverName, usuario, password, programa, transaccion));

		
		// NOTA: Con canales y contenedores, CICS no necesariamente devuelve la cadena de entrada
		// Por simplicidad, se mantiene el logeo de la respuesta completa.
		//System.out.println("<<RCVD:" + respuestaCICS);
	
		
		return respuestaCICS;
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
		String responseHeader = ">>> [CICS]: " + decodificarCodigoRespuesta(codRet);
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