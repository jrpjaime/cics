package mx.gob.imss.cics.service;

 
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date; 
 

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

import mx.gob.imss.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.Channel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service; // Importa Service

@Service // Marca esta clase como un servicio de Spring
public class CicsService implements Serializable {

	private static final long serialVersionUID = 1L;

    private static final Logger logger = LogManager.getLogger(CicsService.class);

	@Value("${ea01.version}")
	private String version;

	@Value("${ctg.ipaddress}")
	private String ctgServer;

	@Value("${ctg.port}")
	private int ctgPort;

	@Value("${ctg.servername}")
	private String serverName;

	// Inyectamos la implementación de ComunicacionCICS
	@Autowired
	private ComunicacionCICS comunicacionCICS;


	// Constructor vacío (no necesitamos leer archivo de configuracion aqui, Spring lo hace)
	public CicsService() {
		super();
	}

	// Método refactorizado para usar la inyección de dependencia
	private String enviarMensajeCics(String cadenaEnviada, String servidor,	String usuario, String password, String programa, String transaccion) {

		logger.info("enviarMensajeCics");
		logger.info("usuario: " + usuario);

		String respuestaCics = "";
		int codRet = 0;
		// Ya no creamos new ComunicacionCICSImpl(), Spring la inyecta
		ECIRequest req = new ECIRequest();
		JavaGateway jg = new JavaGateway();
		ECIRequestBean parametros = new ECIRequestBean();

		try {
			// Crear el Channel y el Container de Entrada usando la instancia inyectada
			Channel requestChannel = comunicacionCICS.creaCanal(cadenaEnviada);

			comunicacionCICS.abreComunicacion(jg, ctgServer, ctgPort);

			parametros.setCallType(ECIRequest.ECI_SYNC);
			parametros.setServer(servidor); // Este 'servidor' viene del parámetro, que será el 'serverName' inyectado
			parametros.setUser(usuario.toUpperCase());
			parametros.setPassword(password.toUpperCase());
			parametros.setProgram(programa);
			parametros.setTransaction(transaccion);
			parametros.setChannel(requestChannel);

			parametros.setModoExtendido(ECIRequest.ECI_NO_EXTEND);
			parametros.setLuwID(ECIRequest.ECI_LUW_NEW);

			comunicacionCICS.asignaParametros(req, parametros);
			comunicacionCICS.enviaSolicitud(jg, req);

			// Obtener la respuesta del Container de Salida
			respuestaCics = comunicacionCICS.traeRespuestaCanal(req).toString();

			codRet = comunicacionCICS.traeCodigoRespuesta(req);

			imprimirSeparador(codRet);
			//comunicacionCICS.cierraComunicacion(jg);
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
            if (jg != null) {
                comunicacionCICS.cierraComunicacion(jg); // Asegurarse de cerrar
            }
        }
		return respuestaCics;
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


		logger.info("[" + fechaActual + "]: IP-" + ctgServer + " | SERV-" + serverName + " | PROG:" + programa + "/TRANS:" + transaccion);
		logger.info(">>SEND:" + cadenaEnviar);



		respuestaCICS = limpiarAsteriscos(enviarMensajeCics(cadenaEnviar, serverName, usuario, password, programa, transaccion));


		// NOTA: Con canales y contenedores, CICS no necesariamente devuelve la cadena de entrada
		// Por simplicidad, se mantiene el logeo de la respuesta completa.
		//logger.info("<<RCVD:" + respuestaCICS);


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
		String responseHeader = "[CR]: " + decodificarCodigoRespuesta(codRet);
		StringBuffer separator = new StringBuffer();

		for (int i = responseHeader.length() ; i<200 ; i++)
			separator.append("-");

		separator.append("]");

		logger.info(responseHeader + " " + separator.toString());
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

	public int getCtgPort() {
		return ctgPort;
	}

	public String getServerName() {
		return serverName;
	}
}