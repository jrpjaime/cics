package mx.gob.imss.cics.service;


import java.io.IOException;
 
import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.exceptions.ContainerNotFoundException;

import mx.gob.imss.cics.beans.ECIRequestBean;

import com.ibm.ctg.client.Channel;

public interface ComunicacionCICS {

	public abstract void asignaParametros(ECIRequest solicitud,
			ECIRequestBean bean);

	public abstract JavaGateway abreComunicacion(JavaGateway servidorCTG,
			String urlCTGServer, int puerto);

	public abstract void enviaSolicitud(JavaGateway servidorCTG,
			ECIRequest solicitud);

	public abstract void cierraComunicacion(JavaGateway servidorCTG);

	/**
	 * Crea un Channel con un Container llamado "INPUT" conteniendo la entrada.
	 * @param entrada La cadena a enviar.
	 * @return El objeto Channel creado.
	 */
	public abstract Channel creaCanal(String entrada);

	/**
	 * Trae la respuesta del Container llamado "OUTPUT" del Channel devuelto.
	 * @param solicitud La ECIRequest completada.
	 * @return La cadena de respuesta del Container "OUTPUT".
	 * @throws IOException
	 * @throws ContainerNotFoundException // <--- Esta excepción es requerida
	 */
	public abstract String traeRespuestaCanal(ECIRequest solicitud) throws IOException, ContainerNotFoundException;

	public abstract int traeCodigoRespuesta(ECIRequest solicitud);

	public abstract void cambiaPrograma(ECIRequest solicitud,
			ECIRequestBean bean);

	public abstract String enviarMensajeCics(String cadenaEnviada,
			String usuario, String password, String programa, String transaccion);

}