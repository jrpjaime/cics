package mx.gob.imss.cics.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CicsTotalConcurrentResponse {
    private List<CicsDatosResponse> individualResponses;
    private long totalElapsedTimeMs;
    private String totalElapsedTimeFormatted;
    private long totalResponseLengthBytes; // Longitud total de todas las respuestas combinadas (opcional)
    private String totalResponseLengthFormatted; // Formato KB/MB
    private int totalErrors;
}