package mx.gob.imss.cics.dto;

import java.util.Set;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UsuarioCicsMapping {
    private String cveUsuarioMainframe;
    private String desPasswordMainframe;
    private Set<String> permisosAutorizados;
}