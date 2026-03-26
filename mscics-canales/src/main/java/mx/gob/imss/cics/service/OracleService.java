package mx.gob.imss.cics.service;

import java.util.Map;
 

public interface OracleService {
 
      Map<String, Object> consultarCiz(String nrp, String fechaInicio, String fechaFin, String digVerif);

}
