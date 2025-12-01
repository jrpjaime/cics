package mx.gob.imss.cics.service;


import com.ibm.ctg.client.Channel;
import com.ibm.ctg.client.ECIRequest;
import com.ibm.ctg.client.JavaGateway;
import com.ibm.ctg.client.exceptions.ContainerNotFoundException;
import mx.gob.imss.cics.beans.ECIRequestBean;

import java.io.IOException;

public interface ComunicacionCICS {

	public abstract void asignaParametros(ECIRequest solicitud, ECIRequestBean bean);

	public abstract JavaGateway abreComunicacion(JavaGateway servidorCTG, String urlCTGServer, int puerto) throws IOException;

	public abstract void enviaSolicitud(JavaGateway servidorCTG, ECIRequest solicitud) throws IOException;

	public abstract void cierraComunicacion(JavaGateway servidorCTG) throws IOException;

	public abstract Channel creaCanal(String entrada);

	public abstract String traeRespuestaCanal(ECIRequest solicitud) throws IOException, ContainerNotFoundException;

	public abstract int traeCodigoRespuesta(ECIRequest solicitud);

	public abstract void cambiaPrograma(ECIRequest solicitud, ECIRequestBean bean);

    // Este método ya no es usado directamente por el CicsService,
    // pero si lo mantienes, debería lanzar UnsupportedOperationException en la implementación.
    // Opcional: Podrías considerar eliminarlo de la interfaz si ya no tiene un propósito claro.
	public abstract String enviarMensajeCics(String cadenaEnviada, String usuario, String password, String programa, String transaccion);
}