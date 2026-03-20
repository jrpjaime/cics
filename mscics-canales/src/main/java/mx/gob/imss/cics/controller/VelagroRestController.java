package mx.gob.imss.cics.controller;

 
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List; 

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity; 
import org.springframework.web.bind.annotation.CrossOrigin; 
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RestController;
 
import mx.gob.imss.cics.dto.CicsDatosJsonResponse; 
import mx.gob.imss.cics.dto.VelagroConcurrentRequest;
import mx.gob.imss.cics.dto.VelagroConcurrentResponse;
import mx.gob.imss.cics.dto.VelagroRequest;
import mx.gob.imss.cics.dto.VelagroResponse;
import mx.gob.imss.cics.service.CicsConsultasService;
 
 import org.springframework.beans.factory.annotation.Value; 
 

@RestController   
@CrossOrigin("*") 
@RequestMapping("/mscics-velagro/v1") 
public class VelagroRestController {
	private final static Logger logger = LoggerFactory.getLogger(VelagroRestController.class);
 
	@Autowired  
	private CicsConsultasService cicsConsultasService;


    @Value("${app.cics.default-cics-transaccion}")
    private String defaultTransaccion;     

    @Value("${app.cics.default-rest-user}")
    private String defaultRestUser;

    @Value("${app.cics.default-rest-password}")
    private String defaultRestPassword;
 
    @GetMapping("/info")
	public ResponseEntity<List<String>> info() {
		logger.info("........................mscics-velagro info..............................");
		List<String> list = new ArrayList<String>();
		list.add("mscics-velagro");
		list.add("20260319");
		list.add("CICS Consultas");
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


	@GetMapping("/list")
	public ResponseEntity<List<String>> list() {
		logger.info("........................mscics-velagro list..............................");
		List<String> list = new ArrayList<String>();
		list.add("mscics-velagro");
		list.add("20260319");
		list.add("CICS Consultas");
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


  @PostMapping("/consultarVelagro")
public ResponseEntity<VelagroConcurrentResponse> consultarVelagro(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody VelagroConcurrentRequest velagroRequest) {

    // 1. VALIDACIÓN DE SEGURIDAD (Se mantiene igual)
    if (authHeader == null || !authHeader.startsWith("Basic ")) {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }
    try {
        String base64Credentials = authHeader.substring("Basic ".length()).trim();
        byte[] credDecoded = java.util.Base64.getDecoder().decode(base64Credentials);
        String credentials = new String(credDecoded, StandardCharsets.UTF_8);
        String[] values = credentials.split(":", 2);
        if (values.length != 2 || !defaultRestUser.equals(values[0]) || !defaultRestPassword.equals(values[1])) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    } catch (Exception e) {
        return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
    }

    VelagroConcurrentResponse concurrentResponse = new VelagroConcurrentResponse();
    List<VelagroResponse> listaRespuestasFinal = new ArrayList<>();

    if (velagroRequest.getDatosEntradaList() == null || velagroRequest.getDatosEntradaList().isEmpty()) {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    DateTimeFormatter dtfEntrada = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    DateTimeFormatter dtfSalida = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    // 2. PROCESAR CADA REGISTRO
    for (VelagroRequest item : velagroRequest.getDatosEntradaList()) {
        VelagroResponse vRes = new VelagroResponse();
        boolean isValid = true;

        // VALIDACIÓN 3: NRP de 10 posiciones
        if (item.getNrp() == null || item.getNrp().length() != 10) {
            vRes.setCodigo_error(3);
            vRes.setErrorMessage("Numero de Registro Patronal no Valido - El formato no es valido (10 posiciones).");
            isValid = false;
        }

        // VALIDACIÓN 2: Periodo y Fechas
        LocalDate fechaInicio = null;
        LocalDate fechaFin = null;
        if (isValid) {
            try {
                fechaInicio = LocalDate.parse(item.getF_inicio(), dtfEntrada);
                fechaFin = LocalDate.parse(item.getF_fin(), dtfEntrada);

                if (fechaInicio.isAfter(fechaFin)) {
                    vRes.setCodigo_error(2);
                    vRes.setErrorMessage("Periodo No valido - La fecha inicial es mayor a la final.");
                    isValid = false;
                } else if (ChronoUnit.MONTHS.between(fechaInicio, fechaFin.plusDays(1)) > 8) {
                    vRes.setCodigo_error(2);
                    vRes.setErrorMessage("Periodo No valido - El periodo supera los 8 meses.");
                    isValid = false;
                }
            } catch (DateTimeParseException e) {
                vRes.setCodigo_error(2);
                vRes.setErrorMessage("Periodo No valido - Formato de fecha incorrecto o fecha inexistente.");
                isValid = false;
            }
        }

        // 3. LLAMADA AL SERVICIO CICS
        if (isValid) {
            try {
                // Cadena formateada: 000 + Transacción + 7 espacios + NRP + FechaIni + FechaFin
                String prefijo = "000" + defaultTransaccion + "       ";
                String cadenaCics = String.format("%s%s%s%s", 
                                    prefijo,
                                    item.getNrp(), 
                                    fechaInicio.format(dtfSalida), 
                                    fechaFin.format(dtfSalida));

                List<CicsDatosJsonResponse> cicsResList = cicsConsultasService.procesarConcurrentementeJson(
                        java.util.Collections.singletonList(cadenaCics), null, null, null, null);

                if (cicsResList != null && !cicsResList.isEmpty()) {
                    CicsDatosJsonResponse cicsRes = cicsResList.get(0);
                    
                    if (cicsRes.getErrorMessage() != null) {
                        vRes.setCodigo_error(1);
                        vRes.setErrorMessage("Error BackEnd - " + cicsRes.getErrorMessage());
                    } else if (cicsRes.getJsonResponse() == null) {
                        // Si no hay JSON pero tampoco error explícito, devolvemos lo que llegó en el header
                        vRes.setCodigo_error(1);
                        vRes.setErrorMessage("Error BackEnd - Respuesta CICS no es un JSON válido.");
                        vRes.setJsonResponse(cicsRes.getHeaderResponse()); 
                    } else {
                        vRes.setJsonResponse(cicsRes.getJsonResponse());
                        vRes.setCodigo_error(0);
                        vRes.setErrorMessage("Respuesta Exitosa");
                    }
                }
            } catch (Exception e) {
                logger.error("Error crítico en NRP {}: {}", item.getNrp(), e.getMessage());
                vRes.setCodigo_error(1);
                vRes.setErrorMessage("Error BackEnd - Error interno.");
            }
        }
        listaRespuestasFinal.add(vRes);
    }

    concurrentResponse.setVelagroResponses(listaRespuestasFinal);
    logger.info("........................VELAGRO FINISH (Transacción: {}) ..............................", defaultTransaccion);
    return new ResponseEntity<>(concurrentResponse, HttpStatus.OK);
}
    


}