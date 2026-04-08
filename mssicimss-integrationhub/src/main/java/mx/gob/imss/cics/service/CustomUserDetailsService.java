package mx.gob.imss.cics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Servicio que conecta Spring Security con la tabla MSCC_USUARIO_CICS.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        String sql = "SELECT CVE_USUARIO_API, DES_PASSWORD_API, IND_ACTIVO, CVE_ROL " +
                    "FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ? AND IND_ACTIVO = 1";

        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                List<GrantedAuthority> authorities = new ArrayList<>();
                // Mapeamos el rol de la base de datos a la autoridad de Spring
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rs.getString("CVE_ROL").trim()));
                
                return new User(
                    rs.getString("CVE_USUARIO_API").trim(),
                    rs.getString("DES_PASSWORD_API").trim(),
                    authorities
                );
            }, username);
        } catch (Exception e) {
            throw new UsernameNotFoundException("Usuario no encontrado: " + username);
        }
    }
}
