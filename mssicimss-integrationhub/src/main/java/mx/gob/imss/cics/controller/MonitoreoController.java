package mx.gob.imss.cics.controller;

import mx.gob.imss.cics.service.MonitoreoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mssicimss-integrationhub/v1/admin/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class MonitoreoController {

    @Autowired
    private MonitoreoService monitoreoService;

    @GetMapping("/metricas")
    public ResponseEntity<?> getDashboardData(
            @RequestParam(required = false) String fechaInicio,
            @RequestParam(required = false) String fechaFin) {
        return ResponseEntity.ok(monitoreoService.obtenerEstadisticasUso(fechaInicio, fechaFin));
    }
}