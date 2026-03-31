package mx.gob.imss.cics.service;

import mx.gob.imss.cics.dto.UsuarioCicsMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UsuarioMappingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Recupera las credenciales de Mainframe asociadas al usuario del API.
     * Implementa cache para alto rendimiento.
     */
    @Cacheable(value = "mainframeCredentials", key = "#apiUser")
    public UsuarioCicsMapping obtenerCredencialesMainframe(String apiUser) {
        String sql = "SELECT CVE_USUARIO_MAINFRAME, DES_PASSWORD_MAINFRAME " +
                     "FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ? AND IND_ACTIVO = 1";
        
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> 
            UsuarioCicsMapping.builder()
                .cveUsuarioMainframe(rs.getString("CVE_USUARIO_MAINFRAME"))
                .desPasswordMainframe(rs.getString("DES_PASSWORD_MAINFRAME"))
                .build()
            , apiUser);
    }
}