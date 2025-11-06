package mx.gob.imss.TestCTG.remote;

import java.util.Map;

import javax.ejb.Remote;

@Remote
public interface ComunicacionCICSBeanRemote {
	
	public String enviaReciveCadena (String cadenaEnviar, String usuario, String password, String programa, String transaccion);
	public Map<String, byte[]> enviaReciveConChannelsContainers(
			Map<String, byte[]> inputContainers, String usuario,
			String password, String programa, String channelName);
}
