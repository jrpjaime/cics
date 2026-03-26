package mx.gob.imss.cics.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class CicsTotalConcurrentJsonResponse {
    private List<CicsDatosJsonResponse> individualResponses;
    private long totalElapsedTimeMs;
    private String totalElapsedTimeFormatted;
    private long totalResponseLengthBytes;
    private String totalResponseLengthFormatted;
    private int totalErrors;
}