package mx.gob.imss.cics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Servicio que conecta Spring Security con la tabla MSCC_USUARIO_CICS.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Consulta siguiendo el estándar IMSS CIT-DAT
        String sql = "SELECT CVE_USUARIO_API, DES_PASSWORD_API, IND_ACTIVO " +
                     "FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ? AND IND_ACTIVO = 1";

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                return new User(
                    rs.getString("CVE_USUARIO_API").trim(),
                    rs.getString("DES_PASSWORD_API").trim(), // Debe estar en BCrypt en la DB
                    new ArrayList<>() // Aquí podrías cargar Roles si los añades a la tabla
                );
            }, username);
        } catch (Exception e) {
            throw new UsernameNotFoundException("Usuario API no encontrado en MSCC_USUARIO_CICS: " + username);
        }
    }
}
