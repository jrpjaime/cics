package mx.gob.imss.cics.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse; 

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException; 
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final static Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class); 

    @Autowired
    private mx.gob.imss.cics.service.JwtUtilService jwtUtilService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        
        final String authorizationHeader = request.getHeader("Authorization");

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);  

            try {
                // 1. Validar el token (firma y expiración)
                if (jwtUtilService.validateToken(jwt)) {
                    username = jwtUtilService.extractUsername(jwt);
                    Claims claims = jwtUtilService.extractAllClaims(jwt);
                    
                    // 2. Extraer los roles del token
                    Object roleClaim = claims.get("role");
                    List<String> rolesStrings = new ArrayList<>();
                    
                    if (roleClaim instanceof String) { 
                        rolesStrings.add((String) roleClaim);
                    } else if (roleClaim instanceof List<?>) { 
                        for (Object item : (List<?>) roleClaim) {
                            if (item instanceof String) {
                                rolesStrings.add((String) item);
                            }
                        }
                    }

                  
                    // No agregamos "ROLE_" manualmente porque el JwtUtilService ya lo incluyó
                    // al extraerlo de UserDetails (que viene de la base de datos).
                    List<SimpleGrantedAuthority> authorities = rolesStrings.stream()
                            .map(SimpleGrantedAuthority::new) 
                            .collect(Collectors.toList());  

                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                                username, null, authorities);  
 
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        
                        logger.info("Autenticación establecida: {} con roles: {}", username, authorities);
                    }
                } else {
                    logger.warn("Token JWT inválido o expirado.");
                }
            } catch (Exception e) {
                logger.error("Error al procesar el token JWT: " + e.getMessage());
                // En un filtro, es mejor no lanzar excepciones hacia arriba sin capturarlas,
                // pero si quieres bloquear la petición aquí está bien.
                SecurityContextHolder.clearContext();
            }
        }

        // Continúa la cadena de filtros
        filterChain.doFilter(request, response);
    }
}