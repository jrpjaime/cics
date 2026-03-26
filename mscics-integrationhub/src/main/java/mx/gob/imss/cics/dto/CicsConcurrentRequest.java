package mx.gob.imss.cics.dto;

import lombok.Data;
import java.util.List;

@Data
public class CicsConcurrentRequest {
    private List<String> datosEntradaList; // Lista de Números de Seguridad Social
    private String usuario;       // Opcional, si no se usan los default
    private String password;      // Opcional
    private String programa;      // Opcional
    private String transaccion;   // Opcional
}