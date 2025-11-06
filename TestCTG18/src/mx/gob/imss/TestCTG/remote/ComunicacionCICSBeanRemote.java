package mx.gob.imss.TestCTG.remote;

import javax.ejb.Remote;

@Remote
public interface ComunicacionCICSBeanRemote {
	
	public String enviaReciveCadena (String cadenaEnviar, String usuario, String password, String programa, String transaccion);

}
