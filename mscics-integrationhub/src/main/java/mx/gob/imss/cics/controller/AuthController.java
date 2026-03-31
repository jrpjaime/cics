package mx.gob.imss.cics.controller;

import mx.gob.imss.cics.dto.LoginRequest;
import mx.gob.imss.cics.dto.JwtResponse;
import mx.gob.imss.cics.service.JwtUtilService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/mscics-integrationhub/v1/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtUtilService jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@RequestBody LoginRequest authRequest) {
        
        // 1. Validar credenciales contra la base de datos (MSCC_USUARIO_CICS)
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                authRequest.getUsername(), 
                authRequest.getPassword()
            )
        );

        // 2. Si la autenticación es exitosa, generar el Token
        final String token = jwtUtil.generateToken(authRequest.getUsername());
        
        // 3. Devolver objeto estructurado
        return ResponseEntity.ok(new JwtResponse(token, "Bearer"));
    }
}