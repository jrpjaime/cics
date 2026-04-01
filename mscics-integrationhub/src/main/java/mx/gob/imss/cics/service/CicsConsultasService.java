package mx.gob.imss.cics.service;

import java.util.List;
import java.util.concurrent.ExecutionException;

import mx.gob.imss.cics.dto.CicsDatosJsonResponse;
import mx.gob.imss.cics.dto.CicsDatosResponse;

public interface  CicsConsultasService {

 
 
    List<CicsDatosJsonResponse> procesarConcurrentementeJson(List<String> datosEntradaList, String usuario, String password, String programa, String transaccion) throws InterruptedException, ExecutionException;
}