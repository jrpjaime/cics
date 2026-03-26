package mx.gob.imss.cics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CicsDatosJsonResponse {
    private String datoEntrada;
    private String headerResponse; // La parte de texto: "000GH17 15927301331"
    private Object jsonResponse;   // El JSON ya convertido a objeto/mapa
    private String errorMessage;
    private long elapsedTimeMs;
}