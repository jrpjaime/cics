package mx.gob.imss.cics.dto;

import lombok.Data;  

@Data  
public class CicsRequest {
    private String cadenaEnviar;
    private String usuario;
    private String password;
    private String programa;
    private String transaccion;

}