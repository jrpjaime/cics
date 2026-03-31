package mx.gob.imss.cics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioCicsMapping {
    private String cveUsuarioMainframe;
    private String desPasswordMainframe;
}