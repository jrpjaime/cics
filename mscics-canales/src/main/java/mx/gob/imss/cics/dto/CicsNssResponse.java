package mx.gob.imss.cics.dto;

import lombok.Builder;  
import lombok.Data;

@Data
@Builder  
public class CicsNssResponse {
    private String nss;
    private String cicsResponse;
    private String errorMessage;
    private long elapsedTimeMs;
}