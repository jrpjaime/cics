package mx.gob.imss.TestCTG.cics.comunicacion;

import java.io.IOException;
import java.util.Map;

import mx.gob.imss.TestCTG.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;

 
import com.ibm.ctg.client.Channel; // Importar Channel
import com.ibm.ctg.client.Container; // Importar Container

public interface ComunicacionCICS {

	public abstract void asignaParametros(ECIRequest solicitud, int callType,
			String server, String user, String password, String programa,
			String transaccion, byte[] commArea, int commAreaLongitud,
			int modoExtendido, int LuwID);

	public abstract void asignaParametros(ECIRequest solicitud,
			ECIRequestBean bean);

	// Nuevo método para asignar parámetros usando Channels y Containers
	public abstract void asignaParametrosConContainers(ECIRequest solicitud,
			ECIRequestBean bean, String channelName); // Agrega el nombre del canal

	public abstract JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto);

	public abstract void enviaSolicitud(JavaGateway servidorCTG,
			ECIRequest solicitud);

	public abstract void cierraComunicacion(JavaGateway servidorCTG);

	public abstract String traeRespuesta(ECIRequest solicitud)
			throws IOException;

	// Nuevo método para traer respuestas de containers
	public abstract Map<String, byte[]> traeRespuestasDeContainers(ECIRequest solicitud)
			throws IOException;

	public abstract int traeCodigoRespuesta(ECIRequest solicitud);

	public abstract byte[] creaCommArea(String entrada);

	// Nuevo método para crear un Container
	public abstract Container creaContainer(String name, byte[] data) throws IOException;

	public abstract void cambiaPrograma(ECIRequest solicitud,
			ECIRequestBean bean);

	public abstract String enviarMensajeCics(String cadenaEnviada,
			String usuario, String password, String programa, String transaccion);

	// Nuevo método para enviar mensaje CICS usando Channels y Containers
	public abstract Map<String, byte[]> enviarMensajeCicsConContainers(
			Map<String, byte[]> inputContainers, String servidor, String usuario,
			String password, String programa, String channelName);
}