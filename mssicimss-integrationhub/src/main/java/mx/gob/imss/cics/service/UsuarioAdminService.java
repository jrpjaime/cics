package mx.gob.imss.cics.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Service
public class UsuarioAdminService {

 

    private static final Logger logger = LogManager.getLogger(UsuarioAdminService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Lista usuarios aplicando filtros dinámicos y paginación para Oracle 11g.
     */
    public Map<String, Object> listarUsuariosPaginados(int page, int size, String userApi, String userMain, String rol) {
        StringBuilder sqlWhere = new StringBuilder(" WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (userApi != null && !userApi.trim().isEmpty()) {
            sqlWhere.append(" AND UPPER(CVE_USUARIO_API) LIKE ? ");
            params.add("%" + userApi.trim().toUpperCase() + "%");
        }
        if (userMain != null && !userMain.trim().isEmpty()) {
            sqlWhere.append(" AND UPPER(CVE_USUARIO_MAINFRAME) LIKE ? ");
            params.add("%" + userMain.trim().toUpperCase() + "%");
        }
        if (rol != null && !rol.trim().isEmpty()) {
            sqlWhere.append(" AND CVE_ROL = ? ");
            params.add(rol.trim());
        }

        String sqlCount = "SELECT COUNT(*) FROM MSCC_USUARIO_CICS " + sqlWhere;
        //Integer totalElements = jdbcTemplate.queryForObject(sqlCount, Integer.class, params.toArray());
        Long totalElements = jdbcTemplate.queryForObject(sqlCount, Long.class, params.toArray());

        int startRow = (page * size) + 1;
        int endRow = (page + 1) * size;
        params.add(endRow);
        params.add(startRow);

        String sqlPaged = "SELECT * FROM ( SELECT a.*, ROWNUM rnum FROM ( " +
                          "SELECT ID_USUARIO_CICS, CVE_USUARIO_API, CVE_USUARIO_MAINFRAME, CVE_ROL, IND_ACTIVO " +
                          "FROM MSCC_USUARIO_CICS " + sqlWhere + " ORDER BY ID_USUARIO_CICS DESC " +
                          ") a WHERE ROWNUM <= ? ) WHERE rnum >= ?";

        List<Map<String, Object>> content = jdbcTemplate.queryForList(sqlPaged, params.toArray());

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("totalElements", totalElements);
        response.put("totalPages", (int) Math.ceil((double) (totalElements != null ? totalElements : 0) / size));
        response.put("number", page);
        response.put("size", size);

        return response;
    }

    /**
     * Obtiene la información de un usuario y todos sus programas autorizados.
     */
    public Map<String, Object> obtenerDetalleUsuario(Long id) {
        String sqlUser = "SELECT ID_USUARIO_CICS, CVE_USUARIO_API, CVE_USUARIO_MAINFRAME, CVE_ROL, IND_ACTIVO " +
                         "FROM MSCC_USUARIO_CICS WHERE ID_USUARIO_CICS = ?";
        Map<String, Object> user = jdbcTemplate.queryForMap(sqlUser, id);

        // Solo traemos permisos que no han sido dados de baja (STP_BAJA is null)
        String sqlPermisos = "SELECT ID_PERMISO_CICS, NOM_PROGRAMA, NOM_TRANSACCION, NUM_TIMEOUT_SEC, IND_ACTIVO " +
                             "FROM MSCC_PERMISO_CICS WHERE ID_USUARIO_CICS = ? AND STP_BAJA IS NULL";
        List<Map<String, Object>> permisos = jdbcTemplate.queryForList(sqlPermisos, id);

        user.put("permisos", permisos);
        return user;
    }

    /**
     * Registra un nuevo mapeo de identidad. Si es CLIENTE, guarda sus programas.
     */
     @Transactional
    public void registrarUsuario(Map<String, Object> datos) {
        logger.info("Iniciando registro de usuario con datos: {}", datos);
        
        try {
            String userApi = Optional.ofNullable(datos.get("cveUsuarioApi")).map(Object::toString).orElse("").trim();
            String passRaw = Optional.ofNullable(datos.get("desPasswordApi")).map(Object::toString).orElse("");
            String rol = Optional.ofNullable(datos.get("cveRol")).map(Object::toString).orElse("CLIENTE");

            if (userApi.isEmpty() || passRaw.isEmpty()) {
                throw new RuntimeException("Campos obligatorios faltantes (Usuario/Password API)");
            }

            // 1. Validar duplicados
            Long existe = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ?", 
                Long.class, userApi);
            if (existe != null && existe > 0) {
                throw new RuntimeException("El usuario '" + userApi + "' ya está registrado.");
            }

            // 2. Obtener ID único para Oracle
            Long idUsuario = jdbcTemplate.queryForObject("SELECT MSCS_USUARIO_CICS.NEXTVAL FROM DUAL", Long.class);
            logger.info("Generado ID de Oracle: {}", idUsuario);

            // 3. Encriptar Password (BCrypt genera 60 caracteres, asegura que tu columna sea VARCHAR2(100))
            String passHash = passwordEncoder.encode(passRaw);

            String userMain = "CLIENTE".equals(rol) ? Optional.ofNullable(datos.get("cveUsuarioMainframe")).map(o -> o.toString().toUpperCase()).orElse(null) : null;
            String passMain = "CLIENTE".equals(rol) ? Optional.ofNullable(datos.get("desPasswordMainframe")).map(Object::toString).orElse(null) : null;

            // 4. Insertar Maestro
            String sqlUser = "INSERT INTO MSCC_USUARIO_CICS (ID_USUARIO_CICS, CVE_USUARIO_API, DES_PASSWORD_API, " +
                            "CVE_USUARIO_MAINFRAME, DES_PASSWORD_MAINFRAME, CVE_ROL, IND_ACTIVO) " +
                            "VALUES (?, ?, ?, ?, ?, ?, 1)";
            
            jdbcTemplate.update(sqlUser, idUsuario, userApi, passHash, userMain, passMain, rol);
            logger.info("Insertado registro maestro para ID: {}", idUsuario);

            // 5. Procesar Permisos
            if ("CLIENTE".equals(rol) && datos.get("permisos") != null) {
                List<Map<String, Object>> permisos = (List<Map<String, Object>>) datos.get("permisos");
                for (Map<String, Object> p : permisos) {
                    // Solo insertar si tiene datos de programa
                    if (p.get("nomPrograma") != null && !p.get("nomPrograma").toString().isEmpty()) {
                        p.put("idUsuarioCics", idUsuario);
                        insertarPermiso(p);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("ERROR CRÍTICO en registrarUsuario: {}", e.getMessage(), e);
            throw e; // Lanza para que el Controller lo cachee y haga Rollback
        }
    }

    private void insertarPermiso(Map<String, Object> p) {
        String sql = "INSERT INTO MSCC_PERMISO_CICS (ID_PERMISO_CICS, ID_USUARIO_CICS, NOM_PROGRAMA, " + 
                    "NOM_TRANSACCION, NUM_TIMEOUT_SEC, IND_ACTIVO) " +
                    "VALUES (MSCS_PERMISO_CICS.NEXTVAL, ?, ?, ?, ?, 1)";
        
        // Manejo seguro del timeout (Angular envía números, pero Jackson puede verlos como Integer o Double)
        Object timeoutObj = p.get("numTimeoutSec");
        int timeout = 30; // default
        if (timeoutObj instanceof Number) {
            timeout = ((Number) timeoutObj).intValue();
        }

        String prog = p.get("nomPrograma").toString().trim().toUpperCase();
        String trans = p.get("nomTransaccion") != null ? p.get("nomTransaccion").toString().trim().toUpperCase() : "";

        jdbcTemplate.update(sql, p.get("idUsuarioCics"), prog, trans, timeout);
        logger.debug("Insertado permiso: {}/{} para usuario: {}", prog, trans, p.get("idUsuarioCics"));
    }

    /**
     * Actualiza la identidad y gestiona la tabla de permisos (Altas y Bajas con STP_BAJA).
     */
    @Transactional
    public void actualizarUsuario(Map<String, Object> datos) {
        Long idUser = Long.valueOf(datos.get("idUsuarioCics").toString());
        String rol = datos.get("cveRol").toString();
        Integer indActivo = Integer.valueOf(datos.get("indActivo").toString());

        // Extraemos campos de Mainframe
        String userMain = datos.get("cveUsuarioMainframe") != null ? datos.get("cveUsuarioMainframe").toString().toUpperCase() : null;
        String passMain = datos.get("desPasswordMainframe") != null ? datos.get("desPasswordMainframe").toString() : null;

        // Lógica para el Password del API (solo se actualiza si enviaron uno nuevo)
        String passApiRaw = datos.get("desPasswordApi") != null ? datos.get("desPasswordApi").toString() : "";
        
        if (!passApiRaw.trim().isEmpty()) {
            // El usuario quiere cambiar su contraseña del portal
            String passApiHash = passwordEncoder.encode(passApiRaw);
            String sqlUpdPass = "UPDATE MSCC_USUARIO_CICS SET DES_PASSWORD_API = ? WHERE ID_USUARIO_CICS = ?";
            jdbcTemplate.update(sqlUpdPass, passApiHash, idUser);
        }

        // Actualización de datos generales
        String sqlUpd = "UPDATE MSCC_USUARIO_CICS SET " +
                        "CVE_USUARIO_MAINFRAME = ?, " +
                        "DES_PASSWORD_MAINFRAME = ?, " +
                        "CVE_ROL = ?, " +
                        "IND_ACTIVO = ?, " +
                        "STP_ACTUALIZACION = CURRENT_TIMESTAMP " +
                        "WHERE ID_USUARIO_CICS = ?";
        
        jdbcTemplate.update(sqlUpd, userMain, passMain, rol, indActivo, idUser);

        // Gestión de Permisos (Programas)
        if (datos.containsKey("permisos")) {
            List<Map<String, Object>> permisos = (List<Map<String, Object>>) datos.get("permisos");
            for (Map<String, Object> p : permisos) {
                if (p.get("idPermisoCics") == null) {
                    // Es un programa nuevo agregado en la pantalla de edición
                    p.put("idUsuarioCics", idUser);
                    insertarPermiso(p);
                } else {
                    // Es un permiso existente, actualizamos su estado o datos
                    int statusPermiso = Integer.parseInt(p.get("indActivo").toString());
                    if (statusPermiso == 0) {
                        // Si se marcó para borrar
                        jdbcTemplate.update("UPDATE MSCC_PERMISO_CICS SET IND_ACTIVO = 0, STP_BAJA = CURRENT_TIMESTAMP WHERE ID_PERMISO_CICS = ?", 
                            p.get("idPermisoCics"));
                    } else {
                        // Actualización de timeout o nombres por si cambiaron
                        String sqlUpdPerm = "UPDATE MSCC_PERMISO_CICS SET NOM_PROGRAMA = ?, NOM_TRANSACCION = ?, NUM_TIMEOUT_SEC = ? WHERE ID_PERMISO_CICS = ?";
                        jdbcTemplate.update(sqlUpdPerm, 
                            p.get("nomPrograma").toString().toUpperCase(), 
                            p.get("nomTransaccion").toString().toUpperCase(), 
                            p.get("numTimeoutSec"),
                            p.get("idPermisoCics"));
                    }
                }
            }
        }
    }
    


    public Map<String, Object> obtenerUsuarioDetalle(Long id) { // <--- Nombre corregido
    String sqlUser = "SELECT ID_USUARIO_CICS, CVE_USUARIO_API, CVE_USUARIO_MAINFRAME, CVE_ROL, IND_ACTIVO " +
                     "FROM MSCC_USUARIO_CICS WHERE ID_USUARIO_CICS = ?";
    Map<String, Object> user = jdbcTemplate.queryForMap(sqlUser, id);

    // Solo traemos permisos que no han sido dados de baja (STP_BAJA is null)
    String sqlPermisos = "SELECT ID_PERMISO_CICS, NOM_PROGRAMA, NOM_TRANSACCION, NUM_TIMEOUT_SEC, IND_ACTIVO " +
                         "FROM MSCC_PERMISO_CICS WHERE ID_USUARIO_CICS = ? AND STP_BAJA IS NULL";
    List<Map<String, Object>> permisos = jdbcTemplate.queryForList(sqlPermisos, id);

    user.put("permisos", permisos);
    return user;
}
}