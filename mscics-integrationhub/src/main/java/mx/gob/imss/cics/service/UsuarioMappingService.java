package mx.gob.imss.cics.service;

import mx.gob.imss.cics.dto.UsuarioCicsMapping;

import java.util.HashSet;
import java.util.List;

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
        // 1. Obtener credenciales básicas
        String sqlUser = "SELECT ID_USUARIO_CICS, CVE_USUARIO_MAINFRAME, DES_PASSWORD_MAINFRAME " +
                         "FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ? AND IND_ACTIVO = 1";

        return jdbcTemplate.queryForObject(sqlUser, (rs, rowNum) -> {
            Long idUsuario = rs.getLong("ID_USUARIO_CICS");
            
            // 2. Obtener lista de programas/transacciones autorizados (Sub-consulta o Join)
            String sqlPermisos = "SELECT NOM_PROGRAMA, NOM_TRANSACCION FROM MSCC_PERMISO_CICS " +
                                 "WHERE ID_USUARIO_CICS = ? AND IND_ACTIVO = 1";
            
            List<String> listaPermisos = jdbcTemplate.query(sqlPermisos, (pRs, pRowNum) -> 
                pRs.getString("NOM_PROGRAMA").trim() + "-" + pRs.getString("NOM_TRANSACCION").trim()
            , idUsuario);

            return UsuarioCicsMapping.builder()
                .cveUsuarioMainframe(rs.getString("CVE_USUARIO_MAINFRAME"))
                .desPasswordMainframe(rs.getString("DES_PASSWORD_MAINFRAME"))
                .permisosAutorizados(new HashSet<>(listaPermisos))
                .build();
        }, apiUser);
    }
}