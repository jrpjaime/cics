package mx.gob.imss.cics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO para la respuesta con el token generado.
 */
@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String type = "Bearer";
}