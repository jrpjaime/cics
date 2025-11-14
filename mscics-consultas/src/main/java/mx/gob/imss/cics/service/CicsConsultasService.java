package mx.gob.imss.cics.service;

import java.util.List;
import java.util.concurrent.ExecutionException;

import mx.gob.imss.cics.dto.CicsNssResponse;

public interface  CicsConsultasService {

 
	String realizarConsultaCics(String cadenaEnviar, String usuario, String password, String programa, String transaccion);
    List<CicsNssResponse> procesarNssConcurrentemente(List<String> nssList, String usuario, String password, String programa, String transaccion) throws InterruptedException, ExecutionException;
}