package mx.gob.imss.cics.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class MonitoreoService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Resumen General de las últimas 24 horas.
     */
public Map<String, Object> obtenerEstadisticasUso(String fechaInicio, String fechaFin) {
    Map<String, Object> stats = new HashMap<>();
    
    // 1. Definir el filtro base
    // Definición del filtro con precisión de MINUTOS
    String sqlWhere = " WHERE STP_REGISTRO >= (CURRENT_TIMESTAMP - 1) ";
    List<Object> params = new ArrayList<>();

    if (fechaInicio != null && !fechaInicio.isEmpty() && fechaFin != null && !fechaFin.isEmpty()) {
        /* 
           Explicación: 
           El input datetime-local envía: '2026-04-10T12:00'
           Usamos TO_TIMESTAMP con el formato exacto de ISO para no perder los minutos.
        */
        sqlWhere = " WHERE STP_REGISTRO BETWEEN TO_TIMESTAMP(?, 'YYYY-MM-DD\"T\"HH24:MI') " +
                   " AND TO_TIMESTAMP(?, 'YYYY-MM-DD\"T\"HH24:MI') ";
        params.add(fechaInicio);
        params.add(fechaFin);
    }

    Object[] queryParams = params.toArray();

    // 2. Resumen
    String sqlKpis = "SELECT COUNT(*) as TOTAL, " +
                     "SUM(CASE WHEN DES_ESTADO_TRANS = 'SUCCESS' THEN 1 ELSE 0 END) as EXITOS, " +
                     "SUM(CASE WHEN DES_ESTADO_TRANS = 'TIMEOUT' THEN 1 ELSE 0 END) as TIMEOUTS, " +
                     "SUM(CASE WHEN DES_ESTADO_TRANS = 'ERROR' THEN 1 ELSE 0 END) as ERRORES, " +
                     "ROUND(AVG(NUM_TIEMPO_PROCESO), 0) as LATENCIA_MEDIA " +
                     "FROM MSCT_AUDITORIA_CICS " + sqlWhere;
    stats.put("resumen", jdbcTemplate.queryForMap(sqlKpis, queryParams));

    // 3. Desglose Errores
    String sqlErr = "SELECT DES_ESTADO_TRANS, COUNT(*) as CANTIDAD FROM MSCT_AUDITORIA_CICS " + 
                    sqlWhere + " AND DES_ESTADO_TRANS != 'SUCCESS' GROUP BY DES_ESTADO_TRANS";
    stats.put("desgloseErrores", jdbcTemplate.queryForList(sqlErr, queryParams));

    // 4. Top Programas
    String sqlTopP = "SELECT * FROM (SELECT NOM_PROGRAMA, COUNT(*) as EJECUCIONES FROM MSCT_AUDITORIA_CICS " + 
                     sqlWhere + " GROUP BY NOM_PROGRAMA ORDER BY EJECUCIONES DESC) WHERE ROWNUM <= 5";
    stats.put("topProgramas", jdbcTemplate.queryForList(sqlTopP, queryParams));

    // 5. Peores Tiempos
    String sqlSlow = "SELECT * FROM (SELECT NOM_PROGRAMA, ROUND(AVG(NUM_TIEMPO_PROCESO), 0) as PROMEDIO_MS FROM MSCT_AUDITORIA_CICS " + 
                     sqlWhere + " AND DES_ESTADO_TRANS = 'SUCCESS' GROUP BY NOM_PROGRAMA ORDER BY PROMEDIO_MS DESC) WHERE ROWNUM <= 5";
    stats.put("peoresTiempos", jdbcTemplate.queryForList(sqlSlow, queryParams));

    // 6. CORRECCIÓN AQUÍ: Usuarios Activos (Debe llevar sqlWhere)
    String sqlUsers = "SELECT * FROM (SELECT CVE_USUARIO_API, COUNT(*) as PETICIONES FROM MSCT_AUDITORIA_CICS " + 
                      sqlWhere + " GROUP BY CVE_USUARIO_API ORDER BY PETICIONES DESC) WHERE ROWNUM <= 5";
    stats.put("usuariosActivos", jdbcTemplate.queryForList(sqlUsers, queryParams));

    String sqlFallasProg = "SELECT * FROM ( " +
                           "  SELECT NOM_PROGRAMA, COUNT(*) as FALLAS " +
                           "  FROM MSCT_AUDITORIA_CICS " + sqlWhere + 
                           "  AND DES_ESTADO_TRANS != 'SUCCESS' " +
                           "  GROUP BY NOM_PROGRAMA ORDER BY FALLAS DESC " +
                           ") WHERE ROWNUM <= 5";
    stats.put("topFallasProgramas", jdbcTemplate.queryForList(sqlFallasProg, queryParams));

    // 2. NUEVO: Últimos 5 errores detallados (Para ver el REF_ERROR_LOG)
    // Usamos CAST para el CLOB en Oracle para que el Driver lo trate como String simple
    String sqlLogs = "SELECT * FROM ( " +
                     "  SELECT NOM_PROGRAMA, CVE_USUARIO_API, DES_ESTADO_TRANS, " +
                     "  DBMS_LOB.SUBSTR(REF_ERROR_LOG, 200, 1) as ERROR_MSG, STP_REGISTRO " +
                     "  FROM MSCT_AUDITORIA_CICS " + sqlWhere +
                     "  AND DES_ESTADO_TRANS != 'SUCCESS' " +
                     "  ORDER BY STP_REGISTRO DESC " +
                     ") WHERE ROWNUM <= 5";
    stats.put("ultimosErrores", jdbcTemplate.queryForList(sqlLogs, queryParams));

    return stats;
}
}