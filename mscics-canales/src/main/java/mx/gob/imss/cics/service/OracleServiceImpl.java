package mx.gob.imss.cics.service;

import org.springframework.stereotype.Service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall; 

import java.sql.Types;
import java.util.Map;

@Service("oracleService")
public class OracleServiceImpl implements  OracleService {

    private final JdbcTemplate jdbcTemplate;
    private final SimpleJdbcCall procedureCall;


    public OracleServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate; // Aquí se usa el field
        
        this.procedureCall = new SimpleJdbcCall(jdbcTemplate)
               // .withSchemaName("MGPBDTU9X")
                .withProcedureName("MGSP_INFOPAT_AGROEXP")
                .withoutProcedureColumnMetaDataAccess() 
                .declareParameters(
                        new SqlParameter("PREGISTROPATRONAL", Types.VARCHAR),
                        new SqlParameter("PFECINICIAL", Types.VARCHAR), 
                        new SqlParameter("PFECFINAL", Types.VARCHAR),  
                        new SqlParameter("PDIGVERIF", Types.VARCHAR),    
                        new SqlOutParameter("OCIZ", Types.NUMERIC),
                        new SqlOutParameter("OCODPROC", Types.NUMERIC),
                        new SqlOutParameter("ODESPROC", Types.VARCHAR)
                );
    }

    // 3. EL MÉTODO: Debe tener un tipo de retorno (Map<String, Object>)
    public Map<String, Object> consultarCiz(String nrp, String fechaInicio, String fechaFin, String digVerif) {
        SqlParameterSource in = new MapSqlParameterSource()
                .addValue("PREGISTROPATRONAL", nrp)
                .addValue("PFECINICIAL", fechaInicio)  
                .addValue("PFECFINAL", fechaFin)
                .addValue("PDIGVERIF", digVerif);  
                
        return procedureCall.execute(in);
    }
}