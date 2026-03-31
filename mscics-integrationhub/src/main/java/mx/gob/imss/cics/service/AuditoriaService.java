package mx.gob.imss.cics.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Registra el evento en MSCT_AUDITORIA_CICS de forma asíncrona.
     */
    @Async("cicsTaskExecutor")
    public void registrarBitacora(String apiUser, String programa, String trans, 
                                  String entrada, int rc, long ms, String estado, String error) {
        
        String sql = "INSERT INTO MSCT_AUDITORIA_CICS (" +
                     "ID_AUDITORIA_CICS, CVE_USUARIO_API, NOM_PROGRAMA, NOM_TRANSACCION, " +
                     "REF_DATO_ENTRADA, NUM_CODIGO_RETORNO, NUM_TIEMPO_PROCESO, " +
                     "DES_ESTADO_TRANS, REF_ERROR_LOG, STP_CREACION) " +
                     "VALUES (MSCS_AUDITORIA_CICS.NEXTVAL, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
        
        try {
            jdbcTemplate.update(sql, apiUser, programa, trans, entrada, rc, ms, estado, error);
        } catch (Exception e) {
            logger.error("Error crítico al persistir auditoría: {}", e.getMessage());
        }
    }
}