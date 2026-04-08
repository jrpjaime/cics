package mx.gob.imss.cics.service;

import mx.gob.imss.cics.dto.UsuarioCicsMapping;

  
import java.util.Map; 

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class UsuarioMappingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Cacheable(value = "mainframeCredentials", key = "#apiUser")
    public UsuarioCicsMapping obtenerCredencialesMainframe(String apiUser) {
        String sqlUser = "SELECT ID_USUARIO_CICS, CVE_USUARIO_MAINFRAME, DES_PASSWORD_MAINFRAME " +
                         "FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ? AND IND_ACTIVO = 1";

        return jdbcTemplate.queryForObject(sqlUser, (rs, rowNum) -> {
            Long idUsuario = rs.getLong("ID_USUARIO_CICS");
            
            // Traemos el nombre y el timeout
            String sqlPermisos = "SELECT NOM_PROGRAMA, NOM_TRANSACCION, NUM_TIMEOUT_SEC " + 
                                 "FROM MSCC_PERMISO_CICS WHERE ID_USUARIO_CICS = ? AND IND_ACTIVO = 1";
            
            // Mapeamos a un Diccionario (Map)
            Map<String, Integer> mapaPermisos = new java.util.HashMap<>();
            jdbcTemplate.query(sqlPermisos, (pRs) -> {
                String llave = pRs.getString("NOM_PROGRAMA").trim() + "-" + pRs.getString("NOM_TRANSACCION").trim();
                mapaPermisos.put(llave, pRs.getInt("NUM_TIMEOUT_SEC"));
            }, idUsuario);

            return UsuarioCicsMapping.builder()
                .cveUsuarioMainframe(rs.getString("CVE_USUARIO_MAINFRAME"))
                .desPasswordMainframe(rs.getString("DES_PASSWORD_MAINFRAME"))
                .permisosConTimeout(mapaPermisos)
                .build();
        }, apiUser);
    }

}