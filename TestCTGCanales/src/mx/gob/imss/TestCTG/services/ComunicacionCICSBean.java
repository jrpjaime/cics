package mx.gob.imss.TestCTG.services;

import java.util.Map;

import javax.ejb.Stateless;

import mx.gob.imss.TestCTG.cics.comunicacion.CicsService;
import mx.gob.imss.TestCTG.remote.ComunicacionCICSBeanRemote;


@Stateless(name = "comunicacionCICSService")
public class ComunicacionCICSBean implements ComunicacionCICSBeanRemote {

	@Override
	public String enviaReciveCadena(String cadenaEnviar, String usuario,
			String password, String programa, String transaccion) {

		CicsService service = new CicsService();

		return service.enviaReciveCadena(cadenaEnviar, usuario, password,
				programa, transaccion);

	}


		@Override
	public Map<String, byte[]> enviaReciveConChannelsContainers(
			Map<String, byte[]> inputContainers, String usuario,
			String password, String programa, String channelName) {

		CicsService service = new CicsService();

		return service.enviaReciveConChannelsContainers(inputContainers, usuario, password,
				programa, channelName);
	}

}
