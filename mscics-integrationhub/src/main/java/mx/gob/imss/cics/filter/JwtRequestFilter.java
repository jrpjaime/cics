package mx.gob.imss.cics.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse; 

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority; // Para manejar los roles
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException; 
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final static Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class); 

    @Autowired
    private mx.gob.imss.cics.service.JwtUtilService jwtUtilService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        logger.info(":::::::::SEGURIDAD :::::::::");
     
        final String authorizationHeader = request.getHeader("Authorization");
 

        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);  
            logger.info("JWT: "+ jwt);

            try {
                // 1. Validar el token (firma y expiración)
            if (jwtUtilService.validateToken(jwt)) {
                username = jwtUtilService.extractUsername(jwt);
                logger.info("username extraído del token: " + username);

                Claims claims = jwtUtilService.extractAllClaims(jwt);
                
              
                Object roleClaim = claims.get("role");
                List<String> roles = new java.util.ArrayList<>();
                
                if (roleClaim instanceof String) { 
                    roles.add((String) roleClaim);
                } else if (roleClaim instanceof List<?>) { 
                    for (Object item : (List<?>) roleClaim) {
                        if (item instanceof String) {
                            roles.add((String) item);
                        }
                    }
                }

                List<SimpleGrantedAuthority> authorities = roles.stream()
                        .map(role -> "ROLE_" + role.toUpperCase())
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());  
                    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
             
                        UsernamePasswordAuthenticationToken authenticationToken =   new UsernamePasswordAuthenticationToken(username, null, authorities);  
 
                        authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                        logger.info("Autenticación establecida para el usuario: " + username + " con roles: " + authorities);
                    }
                } else {
                    logger.warn("Token JWT inválido o expirado. No se establecerá la autenticación.");
                }
            } catch (Exception e) {
                logger.error("Error al procesar el token JWT: " + e.getMessage(), e);
            
                throw new BadCredentialsException("Token inválido o expirado", e);
            }
        } else {
          //  logger.info("No se encontró encabezado de autorización Bearer o el formato es incorrecto.");
        }

        // Continúa la cadena de filtros
        filterChain.doFilter(request, response);
    }
}