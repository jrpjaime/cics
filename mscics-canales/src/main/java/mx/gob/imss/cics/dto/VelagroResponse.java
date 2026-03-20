package mx.gob.imss.cics.dto;
import lombok.Data;

@Data
public class VelagroResponse {
    private Object jsonResponse; 
    private String errorMessage;
    private Integer codigo_error;
 
}
