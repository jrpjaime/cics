package mx.gob.imss.cics.controller;

 
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity; 
import org.springframework.web.bind.annotation.CrossOrigin; 
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping; 
import org.springframework.web.bind.annotation.RestController;

import mx.gob.imss.cics.dto.CicsConcurrentRequest;
import mx.gob.imss.cics.dto.CicsDatosJsonResponse;
import mx.gob.imss.cics.dto.CicsDatosResponse;
import mx.gob.imss.cics.dto.CicsRequest;
import mx.gob.imss.cics.dto.CicsTotalConcurrentJsonResponse;
import mx.gob.imss.cics.dto.CicsTotalConcurrentResponse;
import mx.gob.imss.cics.service.CicsConsultasService;
 
 
 

@RestController   
@CrossOrigin("*") 
@RequestMapping("/mscics-integrationhub/v1") 
public class ConsultasRestController {
	private final static Logger logger = LoggerFactory.getLogger(ConsultasRestController.class);
 
	@Autowired  
	private CicsConsultasService cicsConsultasService;


 
    @GetMapping("/info")
	public ResponseEntity<List<String>> info() {
		logger.info("........................mscics-integrationhub info..............................");
		List<String> list = new ArrayList<String>();
		list.add("mscics-integrationhub");
		list.add("20251113");
		list.add("CICS Consultas");
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


	@GetMapping("/list")
	public ResponseEntity<List<String>> list() {
		logger.info("........................mscics-integrationhub list..............................");
		List<String> list = new ArrayList<String>();
		list.add("mscics-integrationhub");
		list.add("20251113");
		list.add("CICS Consultas");
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


 
 

    // Método auxiliar para formatear el tiempo
    private String formatElapsedTime(long tiempoTranscurridoMs) {
        long milisegundos = tiempoTranscurridoMs % 1000;
        long segundosTotales = tiempoTranscurridoMs / 1000;
        long segundos = segundosTotales % 60;
        long minutosTotales = segundosTotales / 60;
        long minutos = minutosTotales % 60;
        long horas = minutosTotales / 60;

        return String.format("%02d:%02d:%02d:%03d", horas, minutos, segundos, milisegundos);
    }

    // Método auxiliar para formatear la longitud de la respuesta
    private String formatResponseLength(long longitudEnBytes) {
        if (longitudEnBytes < 1024) {
            return longitudEnBytes + " bytes";
        } else if (longitudEnBytes < 1024 * 1024) {
            return String.format("%.2f KB", (double) longitudEnBytes / 1024);
        } else {
            return String.format("%.2f MB", (double) longitudEnBytes / (1024 * 1024));
        }
    }




@PostMapping("/consultarCicsConcurrentetiempoerroresjson")
public ResponseEntity<CicsTotalConcurrentJsonResponse> consultarCicsConcurrentetiempoerroresjson(@RequestBody CicsConcurrentRequest request) {
    if (request.getDatosEntradaList() == null || request.getDatosEntradaList().isEmpty()) {
        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    long totalTiempoInicio = System.currentTimeMillis();
    List<CicsDatosJsonResponse> responses = new ArrayList<>();
    String errorMessage = null;

    try {
        responses = cicsConsultasService.procesarConcurrentementeJson(
                request.getDatosEntradaList(),
                request.getUsuario(),
                request.getPassword(),
                request.getPrograma(),
                request.getTransaccion()
        );
    } catch (Exception e) {
        logger.error("Error en endpoint JSON: {}", e.getMessage());
        errorMessage = e.getMessage();
    }

    long totalElapsedTimeMs = System.currentTimeMillis() - totalTiempoInicio;
    
    // Cálculos de métricas
    int totalErrors = 0;
    long totalBytes = 0;
    for (CicsDatosJsonResponse res : responses) {
        if (res.getErrorMessage() != null) totalErrors++;
        if (res.getHeaderResponse() != null) totalBytes += res.getHeaderResponse().getBytes(StandardCharsets.UTF_8).length;
        // Nota: No sumamos el JSON aquí para métricas de red cruda, 
        // pero podrías serializarlo si necesitas el tamaño exacto del objeto parseado.
    }

    CicsTotalConcurrentJsonResponse totalResponse = CicsTotalConcurrentJsonResponse.builder()
            .individualResponses(responses)
            .totalElapsedTimeMs(totalElapsedTimeMs)
            .totalElapsedTimeFormatted(formatElapsedTime(totalElapsedTimeMs))
            .totalResponseLengthBytes(totalBytes)
            .totalResponseLengthFormatted(formatResponseLength(totalBytes))
            .totalErrors(totalErrors)
            .build();

    return new ResponseEntity<>(totalResponse, errorMessage != null ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK);
}    

}