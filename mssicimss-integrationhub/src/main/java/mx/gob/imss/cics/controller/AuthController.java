package mx.gob.imss.cics.controller;

import mx.gob.imss.cics.dto.LoginRequest;
import mx.gob.imss.cics.dto.JwtResponse;
import mx.gob.imss.cics.service.JwtUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mssicimss-integrationhub/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtilService jwtUtil;

@PostMapping("/login")
public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest authRequest) {
    
    // 1. Validar credenciales
    Authentication auth = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            authRequest.getUsername(), 
            authRequest.getPassword()
        )
    );

    // 2. Si es exitoso, obtenemos los detalles del usuario (que traen el ROL de la DB)
    UserDetails userDetails = (UserDetails) auth.getPrincipal();

    // 3. Generar el Token PASANDO LOS ROLES
    final String token = jwtUtil.generateToken(userDetails.getUsername(), userDetails.getAuthorities());
    
    return ResponseEntity.ok(new JwtResponse(token, "Bearer"));
}
}