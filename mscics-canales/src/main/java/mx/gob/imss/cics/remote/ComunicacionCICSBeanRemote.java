package mx.gob.imss.cics.remote;
 
public interface ComunicacionCICSBeanRemote {
	
	public String enviaReciveCadena (String cadenaEnviar, String usuario, String password, String programa, String transaccion);

}
