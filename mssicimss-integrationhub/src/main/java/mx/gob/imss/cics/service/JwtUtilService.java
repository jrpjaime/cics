package mx.gob.imss.cics.service;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.SignatureException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JwtUtilService {

    private final static Logger logger = LoggerFactory.getLogger(JwtUtilService.class);

    @Value("${jwt.secret}")
    private String JWT_SECRET_KEY;

    // Método para obtener la clave de firma segura
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(JWT_SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Valida el token JWT verificando su firma y si ha expirado.
     * Este método NO requiere UserDetails, haciendo la validación autocontenida.
     *
     * @param token El token JWT a validar.
     * @return true si el token es válido y no ha expirado, false en caso contrario.
     */
    public boolean validateToken(String token) {
        try {
            // Se usa parseSignedClaims(token) en lugar de parseClaimsJws(token)
            Jwts.parser().verifyWith(getSignInKey()).build().parseSignedClaims(token);
            return !isTokenExpired(token);
        } catch (SignatureException e) {
            logger.error("Firma JWT inválida: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Token JWT malformado: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.error("Token JWT expirado: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("Token JWT no soportado: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("Cadena de claims JWT vacía o argumento inválido: {}", e.getMessage());
        }
        return false;
    }

    // Método privado para verificar si el token ha expirado
    private Boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Extrae un claim específico del token.
     *
     * @param token         El token JWT.
     * @param claimsResolver Una función para resolver el claim deseado.
     * @param <T>           El tipo de dato del claim.
     * @return El valor del claim.
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    /**
     * Extrae todas las claims de un token JWT.
     * Este método es crucial para obtener información como roles.
     *
     * @param token El token JWT.
     * @return Un objeto Claims que contiene todas las claims del token.
     */
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)  
                .getPayload(); 
    }

    // Extrae el nombre de usuario (subject) del token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }




    public String generateToken(String username, Collection<? extends GrantedAuthority> authorities) {
        Map<String, Object> claims = new HashMap<>();

                List<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        
        // Los metemos al token con la llave "role"
        claims.put("role", roles); 
        return createToken(claims, username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 18)) // 18 Horas
                .signWith(getSignInKey())
                .compact();
    }

}