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
import mx.gob.imss.cics.dto.CicsNssResponse;
import mx.gob.imss.cics.dto.CicsRequest;
import mx.gob.imss.cics.dto.CicsTotalConcurrentResponse;
import mx.gob.imss.cics.service.CicsConsultasService;
 
 
 

@RestController   
@CrossOrigin("*") 
@RequestMapping("/mscics-consultas/v1") 
public class ConsultasRestController {
	private final static Logger logger = LoggerFactory.getLogger(ConsultasRestController.class);
 
	@Autowired  
	private CicsConsultasService cicsConsultasService;


 
    @GetMapping("/info")
	public ResponseEntity<List<String>> info() {
		logger.info("........................mscics-consultas info..............................");
		List<String> list = new ArrayList<String>();
		list.add("mscics-consultas");
		list.add("20251113");
		list.add("CICS Consultas");
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


	@GetMapping("/list")
	public ResponseEntity<List<String>> list() {
		logger.info("........................mscics-consultas list..............................");
		List<String> list = new ArrayList<String>();
		list.add("mscics-consultas");
		list.add("20251113");
		list.add("CICS Consultas");
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


 
	// endpoint para realizar la consulta CICS
	@PostMapping("/consultarCics")
	public ResponseEntity<String> consultarCics(@RequestBody CicsRequest cicsRequest) {
		logger.info("Recibida solicitud para consultar CICS.");
		try {
			String respuestaCics = cicsConsultasService.realizarConsultaCics(
					cicsRequest.getCadenaEnviar(),
					cicsRequest.getUsuario(),
					cicsRequest.getPassword(),
					cicsRequest.getPrograma(),
					cicsRequest.getTransaccion()
			);
			return new ResponseEntity<>(respuestaCics, HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error al realizar la consulta CICS: {}", e.getMessage(), e);
			return new ResponseEntity<>("Error al procesar la solicitud CICS: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
 



	    // Nuevo endpoint para procesamiento concurrente de NSS
    @PostMapping("/consultarCicsConcurrente")
    public ResponseEntity<List<CicsNssResponse>> consultarCicsConcurrente(@RequestBody CicsConcurrentRequest request) {
        logger.info("Recibida solicitud para consultar CICS concurrentemente para {} NSS.", request.getNssList().size());
        if (request.getNssList() == null || request.getNssList().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        try {
            List<CicsNssResponse> responses = cicsConsultasService.procesarNssConcurrentemente(
                    request.getNssList(),
                    request.getUsuario(),
                    request.getPassword(),
                    request.getPrograma(),
                    request.getTransaccion()
            );
            return new ResponseEntity<>(responses, HttpStatus.OK);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error durante el procesamiento concurrente de NSS: {}", e.getMessage(), e);
            // Podrías devolver un mensaje de error más específico si quieres
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.error("Error inesperado durante el procesamiento concurrente de NSS: {}", e.getMessage(), e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }





	    // Nuevo endpoint para procesamiento concurrente de NSS
    @PostMapping("/consultarCicsConcurrentetiempo")
    public ResponseEntity<CicsTotalConcurrentResponse> consultarCicsConcurrentetiempo(@RequestBody CicsConcurrentRequest request) {
        logger.info("Recibida solicitud para consultar CICS concurrentemente para {} NSS.", request.getNssList().size());
        if (request.getNssList() == null || request.getNssList().isEmpty()) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST); // Retornamos BAD_REQUEST con el tipo de respuesta adecuado
        }

        long totalTiempoInicio = System.currentTimeMillis(); // Iniciar contador de tiempo total
        List<CicsNssResponse> responses = new ArrayList<>();
        String errorMessage = null;

        try {
            responses = cicsConsultasService.procesarNssConcurrentemente(
                    request.getNssList(),
                    request.getUsuario(),
                    request.getPassword(),
                    request.getPrograma(),
                    request.getTransaccion()
            );
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error durante el procesamiento concurrente de NSS: {}", e.getMessage(), e);
            errorMessage = "Error durante el procesamiento concurrente: " + e.getMessage();
        } catch (Exception e) {
            logger.error("Error inesperado durante el procesamiento concurrente de NSS: {}", e.getMessage(), e);
            errorMessage = "Error inesperado: " + e.getMessage();
        }

        long totalTiempoFin = System.currentTimeMillis(); // Finalizar contador de tiempo total
        long totalElapsedTimeMs = totalTiempoFin - totalTiempoInicio;

        // Formatear el tiempo total
        String totalElapsedTimeFormatted = formatElapsedTime(totalElapsedTimeMs);
        logger.info("Tiempo total de procesamiento concurrente para {} NSS: {}", responses.size(), totalElapsedTimeFormatted);

        // Calcular la longitud total de las respuestas (opcional)
        long totalResponseLengthBytes = 0;
        for (CicsNssResponse res : responses) {
            if (res.getCicsResponse() != null) {
                totalResponseLengthBytes += res.getCicsResponse().getBytes(StandardCharsets.UTF_8).length;
            }
        }
        String totalResponseLengthFormatted = formatResponseLength(totalResponseLengthBytes);
        logger.info("Longitud total de las respuestas (UTF-8): {} ({})", totalResponseLengthBytes, totalResponseLengthFormatted);


        // Construir la respuesta final
        CicsTotalConcurrentResponse totalResponse = CicsTotalConcurrentResponse.builder()
                .individualResponses(responses)
                .totalElapsedTimeMs(totalElapsedTimeMs)
                .totalElapsedTimeFormatted(totalElapsedTimeFormatted)
                .totalResponseLengthBytes(totalResponseLengthBytes)
                .totalResponseLengthFormatted(totalResponseLengthFormatted)
                .build();

        if (errorMessage != null) {
    
            return new ResponseEntity<>(totalResponse, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new ResponseEntity<>(totalResponse, HttpStatus.OK);
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

}