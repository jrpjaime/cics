package mx.gob.imss.cics.cics;

 
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

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

import jakarta.servlet.http.HttpServletRequest;
import mx.gob.imss.cics.dto.CicsDatosJsonResponse; 
import mx.gob.imss.cics.dto.VelagroConcurrentRequest;
import mx.gob.imss.cics.dto.VelagroConcurrentResponse;
import mx.gob.imss.cics.dto.VelagroRequest;
import mx.gob.imss.cics.dto.VelagroResponse;
import mx.gob.imss.cics.service.CicsConsultasService;
import mx.gob.imss.cics.service.OracleService;

import org.springframework.beans.factory.annotation.Value; 
 

@RestController   
@CrossOrigin("*") 
@RequestMapping("/mscics-velagro/v1") 
public class CicsConnection {
	private final static Logger logger = LoggerFactory.getLogger(CicsConnection.class);
 
	@Autowired  
	private OracleService oracleService;

    @Autowired  
	private CicsConsultasService cicsConsultasService;

    @Autowired
    private HttpServletRequest request;  


    @Value("${app.cics.default-cics-transaccion}")
    private String defaultTransaccion;     

    @Value("${app.cics.default-rest-user}")
    private String defaultRestUser;

    @Value("${app.cics.default-rest-password}")
    private String defaultRestPassword;
 
    @GetMapping("/info")
	public ResponseEntity<List<String>> info() {
		logger.info("......................VELAGRO......................");
		List<String> list = new ArrayList<String>();
         String digVerif = getDigVerif("nrp");
		list.add("mscics-velagro");
		list.add("20260319");
		list.add("CICS Consultas");
        list.add("digVerif "+ digVerif);
		return new ResponseEntity<List<String>>(list, HttpStatus.OK);
	}


	@GetMapping("/list")
	public ResponseEntity<List<String>> list() {
		logger.info("......................VELAGRO......................");
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

    logger.info("......................VELAGRO......................");

    // --- 1. VALIDACIÓN DE SEGURIDAD (HTTP 401) ---
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

    // --- 2. VALIDACIONES GLOBALES DE LA LISTA (Códigos 1 y 2) ---
    
    // Código 1: Lista nula o vacía
    if (velagroRequest.getDatosEntradaList() == null || velagroRequest.getDatosEntradaList().isEmpty()) {
        VelagroResponse errorGlobal = new VelagroResponse();
        errorGlobal.setCodigo_error(1);
        errorGlobal.setErrorMessage("Error de Estructura - La lista de datos de entrada esta vacia.");
        concurrentResponse.setVelagroResponses(java.util.Collections.singletonList(errorGlobal));
        return new ResponseEntity<>(concurrentResponse, HttpStatus.BAD_REQUEST);
    }

    // Código 2: Límite de 10 registros
    if (velagroRequest.getDatosEntradaList().size() > 10) {
        VelagroResponse errorGlobal = new VelagroResponse();
        errorGlobal.setCodigo_error(2);
        errorGlobal.setErrorMessage("Error de Estructura - Se ha superado el limite maximo de 10 registros por peticion.");
        concurrentResponse.setVelagroResponses(java.util.Collections.singletonList(errorGlobal));
        return new ResponseEntity<>(concurrentResponse, HttpStatus.BAD_REQUEST);
    }

    // Preparación de formatos de fecha
    DateTimeFormatter dtfEntrada = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    DateTimeFormatter dtfSalida = DateTimeFormatter.ofPattern("uuuu-MM-dd");

    // --- 3. PROCESAMIENTO INDIVIDUAL POR REGISTRO ---
    for (VelagroRequest item : velagroRequest.getDatosEntradaList()) {
        VelagroResponse vRes = new VelagroResponse();
        boolean isValid = true;

        // Código 3: Validación de NRP (10 posiciones)
        if (isValid && (item.getNrp() == null || item.getNrp().length() != 11)) {
            vRes.setCodigo_error(3);
            vRes.setErrorMessage("NRP Invalido - El formato debe ser de 11 posiciones.");
            isValid = false;
        }

        // Código 4: Validación de Formato de Fecha y Existencia
        LocalDate fechaInicio = null;
        LocalDate fechaFin = null;
        if (isValid) {
            try {
                fechaInicio = LocalDate.parse(item.getF_inicio(), dtfEntrada);
                fechaFin = LocalDate.parse(item.getF_fin(), dtfEntrada);
            } catch (DateTimeParseException | NullPointerException e) {
                vRes.setCodigo_error(4);
                vRes.setErrorMessage("Fecha Invalida - El formato debe ser AAAAMMDD y ser una fecha calendario valida.");
                isValid = false;
            }
        }

        // Código 5: Validación Lógica (Inicio > Fin)
        if (isValid) {
            if (fechaInicio.isAfter(fechaFin)) {
                vRes.setCodigo_error(5);
                vRes.setErrorMessage("Periodo Invalido - La fecha inicial no puede ser mayor a la final.");
                isValid = false;
            }
        }

        // Código 6: Validación de Negocio (Máximo 8 meses)
        if (isValid) {
            if (ChronoUnit.MONTHS.between(fechaInicio, fechaFin.plusDays(1)) > 8) {
                vRes.setCodigo_error(6);
                vRes.setErrorMessage("Periodo Excedido - El rango de consulta no debe superar los 8 meses.");
                isValid = false;
            }
        }


logger.info(">>> [CICS] Open connection...");
    Integer cizObtenido = null;
    String digVerif = getDigVerif(item.getNrp());

    logger.info(">>> [CICS] Connection received");
    // ---  CONSULTA ORACLE PARA OBTENER CIZ ---
    if (isValid) {
        try {
            logger.info(">>> [CICS] Iniciando ejecucion para NRP: {}, F_INI: {}, F_FIN: {}", item.getNrp(), item.getF_inicio(), item.getF_fin());
            
            // Llamada al servicio actualizada con los 3 parámetros
            Map<String, Object> out = oracleService.consultarCiz(
                    item.getNrp(), 
                    item.getF_inicio(), 
                    item.getF_fin(),
                    digVerif
            );
            
            // Log de la respuesta cruda (Útil para depuración)
            logger.info(">>> [CICS] Received response: {}", out);

            // Mapeo de variables (Asegúrate que los nombres coincidan con los SqlOutParameter del OracleService)
            BigDecimal codProc = (BigDecimal) out.get("OCODPROC");
            String desProc = (String) out.get("ODESPROC");
            BigDecimal ciz = (BigDecimal) out.get("OCIZ");

            if (codProc != null && codProc.intValue() == 1) {
                cizObtenido = (ciz != null) ? ciz.intValue() : 0;
                logger.info(">>> [CICS] NRP {} CIZ: {}", item.getNrp(), cizObtenido);
            } else if (codProc != null && codProc.intValue() == 3) {
                logger.warn(">>> [CICS] AVISO: El NRP {} no existe en Padron (OCODPROC=3)", item.getNrp());
                vRes.setCodigo_error(10);
                vRes.setErrorMessage("Registro Patronal Inexistente en Padron Agro.");
                isValid = false;
            } else {
                logger.error(">>> [CICS] ERROR DE NEGOCIO: Codigo={}, Descripcion={}", codProc, desProc);
                vRes.setCodigo_error(9);
                vRes.setErrorMessage("Error Oracle: " + (desProc != null ? desProc : "Fallo desconocido"));
                isValid = false;
            }
        } catch (Exception e) {
            logger.error(">>> [ORACLE] ERROR CRITICO DE CONEXION/EJECUCION para NRP {}: {}", item.getNrp(), e.getMessage());
            vRes.setCodigo_error(9);
            vRes.setErrorMessage("Error de conexion con Base de Datos de Padron.");
            isValid = false;
        }
    }






        // --- 4. COMUNICACIÓN CON CICS (Códigos 7 y 8) ---
        if (isValid) {
            try {
                // Formateo de cadena para CICS
                String prefijo = "000" + defaultTransaccion + "       ";
                String nrpParaCics = item.getNrp().substring(0, 10);
                String cadenaCics = String.format("%s%s%s%s%d", 
                                    prefijo,
                                    nrpParaCics, 
                                    fechaInicio.format(dtfSalida), 
                                    fechaFin.format(dtfSalida),
                                    cizObtenido);

                // Llamada asíncrona (internamente usa el pool de hilos y conexiones)
                List<CicsDatosJsonResponse> cicsResList = cicsConsultasService.procesarConcurrentementeJson(
                        java.util.Collections.singletonList(cadenaCics), null, null, null, null);

                if (cicsResList != null && !cicsResList.isEmpty()) {
                    CicsDatosJsonResponse cicsRes = cicsResList.get(0);
                    
                    if (cicsRes.getErrorMessage() != null) {
                        // Código 7: Error de Comunicación o de CICS (ECI Errors)
                        vRes.setCodigo_error(7);
                        vRes.setErrorMessage("Error Backend Transaction" );
                        logger.error("Error Backend CICS - " + cicsRes.getErrorMessage());
                    } else if (cicsRes.getJsonResponse() == null) {
                        // Código 8: Error de Formato en la Respuesta (No es JSON válido)
                        logger.error("Error Backend JSON - La respuesta del Mainframe no tiene un formato JSON valido.");
                        vRes.setCodigo_error(8);
                        vRes.setErrorMessage("Error Backend JSON - La respuesta no tiene un formato JSON valido.");
                        vRes.setJsonResponse(cicsRes.getHeaderResponse()); // Se envía el texto crudo para análisis
                    } else {
                        // ÉXITO: Código 0
                        vRes.setJsonResponse(cicsRes.getJsonResponse());
                        vRes.setCodigo_error(0);
                        vRes.setErrorMessage("Respuesta Exitosa");
                    }
                }
            } catch (Exception e) {
                logger.error("Fallo critico en procesamiento de NRP {}: {}", item.getNrp(), e.getMessage());
                vRes.setCodigo_error(7);
                vRes.setErrorMessage("Error Backend - Excepcion inesperada en la llamada al servicio.");
            }
        }
        listaRespuestasFinal.add(vRes);
    }

    concurrentResponse.setVelagroResponses(listaRespuestasFinal);
    logger.info(">>> [CICS] Query completed - Records processed: {}", listaRespuestasFinal.size());
    return new ResponseEntity<>(concurrentResponse, HttpStatus.OK);
}




 
    private String getDigVerif(HttpServletRequest request , String nrp) {
    String[] headers = {
        "X-Forwarded-For",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "HTTP_X_FORWARDED_FOR",
        "HTTP_X_FORWARDED",
        "HTTP_X_CLUSTER_CLIENT_IP",
        "HTTP_CLIENT_IP",
        "HTTP_FORWARDED_FOR",
        "HTTP_FORWARDED",
        "X-Real-IP"
    };

    for (String header : headers) {
        String ip = request.getHeader(header);
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // Si hay varias IPs (separadas por coma), tomar la primera
            return ip.split(",")[0].trim();
        }
    }

    // Si no hay headers de proxy, obtener la IP de la conexión directa
    String remoteIp = request.getRemoteAddr();
    
    // Opcional: Convertir el loopback IPv6 a IPv4 para que sea más legible
    if ("0:0:0:0:0:0:0:1".equals(remoteIp)) {
        return "127.0.0.1";
    }
    
    return remoteIp;
}


private String getDigVerif(String nrp) {
    try {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface iface = interfaces.nextElement();
            // Filtramos interfaces inactivas o de loopback (127.0.0.1)
            if (iface.isLoopback() || !iface.isUp()) continue;

            Enumeration<InetAddress> addresses = iface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                // Buscamos solo direcciones IPv4 (para que no te de la IPv6 larga)
                if (addr instanceof java.net.Inet4Address) {
                    return addr.getHostAddress();
                }
            }
        }
    } catch (Exception e) {
        logger.error("Error obteniendo IP del servidor", e);
    }
    return "127.0.0.1";
}






}