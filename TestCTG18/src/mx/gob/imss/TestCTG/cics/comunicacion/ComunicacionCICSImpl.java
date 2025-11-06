package mx.gob.imss.TestCTG.cics.comunicacion;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;


public class ComunicacionCICSImpl implements ComunicacionCICS {

	public static final String SERVIDOR = "CICSIPIC";
	// public static final String SERVIDOR = "IMSSREG1";

	private String ctgServer = null;
	private int ctgPort = 0;

	public JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto) {
		try {
			servidorCTG.setURL(urlCTGServer);
			servidorCTG.setPort(puerto);
			servidorCTG.open();
			System.out.println("[0]Se abre comunicacion al CTG Server"+ urlCTGServer + "            puerto: "+ puerto );
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

	public int traeCodigoRespuesta(ECIRequest solicitud) {
		int respuesta = 0;
		respuesta = solicitud.Cics_Rc;
		return respuesta;

	}

	public byte[] creaCommArea(String entrada) {
		byte commArea[] = null;

		while (entrada.length() < 6525) {
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

}
