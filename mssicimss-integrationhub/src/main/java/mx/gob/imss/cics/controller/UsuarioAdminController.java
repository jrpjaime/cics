package mx.gob.imss.cics.controller;


import mx.gob.imss.cics.service.UsuarioAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/mssicimss-integrationhub/v1/admin")
@PreAuthorize("hasRole('ADMIN')")

public class UsuarioAdminController {

    @Autowired
    private UsuarioAdminService adminService;

    @GetMapping("/usuarios")
    public ResponseEntity<?> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(required = false) String userApi,
            @RequestParam(required = false) String userMain,
            @RequestParam(required = false) String rol) {
        return ResponseEntity.ok(adminService.listarUsuariosPaginados(page, size, userApi, userMain, rol));
    }

    // RUTA PARA DETALLE POR ID (POST para ofuscar el ID en la URL)
    @PostMapping("/usuarios/detalle")  
    public ResponseEntity<?> obtenerDetalle(@RequestBody Map<String, Long> payload) {
        try {
            Long id = payload.get("idUsuarioCics");
            if (id == null) return ResponseEntity.badRequest().body(Map.of("message", "ID requerido"));
            
            return ResponseEntity.ok(adminService.obtenerUsuarioDetalle(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "No encontrado"));
        }
    }

    @PostMapping("/usuarios")
    public ResponseEntity<?> crear(@RequestBody Map<String, Object> datos) {
        try {
            adminService.registrarUsuario(datos);
            return ResponseEntity.ok(Map.of("message", "Usuario registrado exitosamente."));
        } catch (Exception e) {
            e.printStackTrace(); // Ver el error real en la consola de tu IDE (Eclipse/IntelliJ)
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("message", "Error: " + e.getMessage())); // Enviar el mensaje real al front
        }
    }


    @PutMapping("/usuarios")
    public ResponseEntity<?> actualizar(@RequestBody Map<String, Object> datos) {
        try {
            adminService.actualizarUsuario(datos);
            return ResponseEntity.ok(Map.of("message", "Usuario actualizado correctamente."));
        } catch (Exception e) {
            e.printStackTrace(); // Ver en consola de Java
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("message", "Error al actualizar: " + e.getMessage()));
        }
    }
}