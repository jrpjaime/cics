package mx.gob.imss.TestCTG.cics.comunicacion;

import java.io.IOException;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

public interface ComunicacionCICS {

	public abstract void asignaParametros(ECIRequest solicitud, int callType,
			String server, String user, String password, String programa,
			String transaccion, byte[] commArea, int commAreaLongitud,
			int modoExtendido, int LuwID);

	public abstract void asignaParametros(ECIRequest solicitud,
			ECIRequestBean bean);

	public abstract JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto);

	public abstract void enviaSolicitud(JavaGateway servidorCTG,
			ECIRequest solicitud);

	public abstract void cierraComunicacion(JavaGateway servidorCTG);

	public abstract String traeRespuesta(ECIRequest solicitud)
			throws IOException;

	public abstract int traeCodigoRespuesta(ECIRequest solicitud);

	public abstract byte[] creaCommArea(String entrada);

	public abstract void cambiaPrograma(ECIRequest solicitud,
			ECIRequestBean bean);

	public abstract String enviarMensajeCics(String cadenaEnviada,
			String usuario, String password, String programa, String transaccion);

}
