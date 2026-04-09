package mx.gob.imss.cics.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

@Service
public class UsuarioAdminService {

    private static final Logger logger = LogManager.getLogger(UsuarioAdminService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Lista usuarios aplicando filtros dinámicos y paginación para Oracle 11g.
     * Incluye los nuevos campos de nombre en el resultado de la tabla.
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
        Long totalElements = jdbcTemplate.queryForObject(sqlCount, Long.class, params.toArray());

        int startRow = (page * size) + 1;
        int endRow = (page + 1) * size;
        params.add(endRow);
        params.add(startRow);

        // Agregamos NOM_NOMBRE y NOM_APELLIDO_1 para que se vean en la lista principal
        String sqlPaged = "SELECT * FROM ( SELECT a.*, ROWNUM rnum FROM ( " +
                          "SELECT ID_USUARIO_CICS, CVE_USUARIO_API, CVE_USUARIO_MAINFRAME, CVE_ROL, IND_ACTIVO, NOM_NOMBRE, NOM_APELLIDO_1 " +
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
     * Obtiene la información completa de una identidad y sus programas activos.
     */
    public Map<String, Object> obtenerUsuarioDetalle(Long id) {
        // SELECT * asegura traer todos los campos nuevos (contacto, propóstio, etc)
        String sqlUser = "SELECT * FROM MSCC_USUARIO_CICS WHERE ID_USUARIO_CICS = ?";
        Map<String, Object> user = jdbcTemplate.queryForMap(sqlUser, id);

        // Traemos todos los campos de permisos, incluyendo las nuevas fechas de auditoría
        String sqlPermisos = "SELECT * FROM MSCC_PERMISO_CICS WHERE ID_USUARIO_CICS = ? AND STP_BAJA IS NULL";
        List<Map<String, Object>> permisos = jdbcTemplate.queryForList(sqlPermisos, id);

        user.put("permisos", permisos);
        return user;
    }

    /**
     * Registra un nuevo mapeo de identidad con todos los campos de contacto y auditoría.
     */
    @Transactional
    public void registrarUsuario(Map<String, Object> datos) {
        logger.info("Iniciando registro de usuario con datos completos: {}", datos);
        
        try {
            // Extracción segura de datos
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

            // 2. Obtener ID de la secuencia Oracle
            Long idUsuario = jdbcTemplate.queryForObject("SELECT MSCS_USUARIO_CICS.NEXTVAL FROM DUAL", Long.class);
            logger.info("ID Generado para nueva identidad: {}", idUsuario);

            // 3. Preparar datos de Mainframe si aplica
            String userMain = "CLIENTE".equals(rol) ? Optional.ofNullable(datos.get("cveUsuarioMainframe")).map(o -> o.toString().toUpperCase()).orElse(null) : null;
            String passMain = "CLIENTE".equals(rol) ? Optional.ofNullable(datos.get("desPasswordMainframe")).map(Object::toString).orElse(null) : null;

            // 4. Insertar Registro Maestro con todos los nuevos campos
            String sqlUser = "INSERT INTO MSCC_USUARIO_CICS (ID_USUARIO_CICS, CVE_USUARIO_API, DES_PASSWORD_API, " +
                            "NOM_NOMBRE, NOM_APELLIDO_1, NOM_APELLIDO_2, DES_USO_CUENTA, DES_CORREO, NUM_TELEFONO, " +
                            "NUM_EXTENSION, CVE_ROL, CVE_USUARIO_MAINFRAME, DES_PASSWORD_MAINFRAME, IND_ACTIVO, " +
                            "STP_REGISTRO, STP_ACTUALIZACION) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
            
            jdbcTemplate.update(sqlUser, 
                idUsuario, 
                userApi, 
                passwordEncoder.encode(passRaw),
                datos.get("nomNombre"), 
                datos.get("nomApellido1"), 
                datos.get("nomApellido2"),
                datos.get("desUsoCuenta"), 
                datos.get("desCorreo"), 
                datos.get("numTelefono"), 
                datos.get("numExtension"),
                rol, 
                userMain, 
                passMain);

            // 5. Procesar Permisos vinculados
            if (datos.get("permisos") != null) {
                List<Map<String, Object>> permisos = (List<Map<String, Object>>) datos.get("permisos");
                for (Map<String, Object> p : permisos) {
                    if (p.get("nomPrograma") != null && !p.get("nomPrograma").toString().isEmpty()) {
                        p.put("idUsuarioCics", idUsuario);
                        insertarPermiso(p);
                    }
                }
            }
            logger.info("Identidad {} registrada exitosamente.", userApi);
        } catch (Exception e) {
            logger.error("Fallo al registrar usuario: {}", e.getMessage(), e);
            throw e; 
        }
    }

    /**
     * Actualiza los datos de la identidad, manejando cambios en password y auditoría.
     */
    @Transactional
    public void actualizarUsuario(Map<String, Object> datos) {
        logger.info("Actualizando identidad ID: {}", datos.get("idUsuarioCics"));
        
        Long idUser = Long.valueOf(datos.get("idUsuarioCics").toString());
        String rol = datos.get("cveRol").toString();
        Integer indActivo = Integer.valueOf(datos.get("indActivo").toString());

        // Lógica de Password del Portal (solo si se tecleó uno nuevo)
        String passApiRaw = datos.get("desPasswordApi") != null ? datos.get("desPasswordApi").toString() : "";
        if (!passApiRaw.trim().isEmpty()) {
            String passApiHash = passwordEncoder.encode(passApiRaw);
            jdbcTemplate.update("UPDATE MSCC_USUARIO_CICS SET DES_PASSWORD_API = ? WHERE ID_USUARIO_CICS = ?", 
                passApiHash, idUser);
        }

        // Actualización de Campos Maestros con STP_ACTUALIZACION
        String sqlUpd = "UPDATE MSCC_USUARIO_CICS SET " +
                        "NOM_NOMBRE = ?, NOM_APELLIDO_1 = ?, NOM_APELLIDO_2 = ?, " +
                        "DES_USO_CUENTA = ?, DES_CORREO = ?, NUM_TELEFONO = ?, NUM_EXTENSION = ?, " +
                        "CVE_USUARIO_MAINFRAME = ?, DES_PASSWORD_MAINFRAME = ?, " +
                        "CVE_ROL = ?, IND_ACTIVO = ?, STP_ACTUALIZACION = CURRENT_TIMESTAMP " +
                        "WHERE ID_USUARIO_CICS = ?";
        
        jdbcTemplate.update(sqlUpd, 
            datos.get("nomNombre"), 
            datos.get("nomApellido1"), 
            datos.get("nomApellido2"),
            datos.get("desUsoCuenta"), 
            datos.get("desCorreo"), 
            datos.get("numTelefono"), 
            datos.get("numExtension"),
            datos.get("cveUsuarioMainframe") != null ? datos.get("cveUsuarioMainframe").toString().toUpperCase() : null,
            datos.get("desPasswordMainframe"), 
            rol, 
            indActivo, 
            idUser);

        // Gestión de la tabla de Permisos (Altas y Bajas lógicas)
        if (datos.containsKey("permisos")) {
            List<Map<String, Object>> permisos = (List<Map<String, Object>>) datos.get("permisos");
            for (Map<String, Object> p : permisos) {
                if (p.get("idPermisoCics") == null) {
                    // Es un programa nuevo añadido en edición
                    p.put("idUsuarioCics", idUser);
                    insertarPermiso(p);
                } else {
                    int statusPermiso = Integer.parseInt(p.get("indActivo").toString());
                    if (statusPermiso == 0) {
                        // Se marcó para eliminar: Actualizamos estado y ponemos fecha de BAJA
                        jdbcTemplate.update("UPDATE MSCC_PERMISO_CICS SET IND_ACTIVO = 0, STP_BAJA = CURRENT_TIMESTAMP, STP_ACTUALIZACION = CURRENT_TIMESTAMP WHERE ID_PERMISO_CICS = ?", 
                            p.get("idPermisoCics"));
                    } else {
                        // Se modificó (ej: cambio de timeout): Actualizamos y refrescamos STP_ACTUALIZACION
                        String sqlUpdPerm = "UPDATE MSCC_PERMISO_CICS SET NOM_PROGRAMA = ?, NOM_TRANSACCION = ?, " +
                                            "NUM_TIMEOUT_SEC = ?, STP_ACTUALIZACION = CURRENT_TIMESTAMP WHERE ID_PERMISO_CICS = ?";
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

    /**
     * Inserta un registro de permiso con marcas de tiempo iniciales.
     */
    private void insertarPermiso(Map<String, Object> p) {
        String sql = "INSERT INTO MSCC_PERMISO_CICS (ID_PERMISO_CICS, ID_USUARIO_CICS, NOM_PROGRAMA, " + 
                    "NOM_TRANSACCION, NUM_TIMEOUT_SEC, IND_ACTIVO, STP_REGISTRO, STP_ACTUALIZACION) " +
                    "VALUES (MSCS_PERMISO_CICS.NEXTVAL, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        
        Object timeoutObj = p.get("numTimeoutSec");
        int timeout = (timeoutObj instanceof Number) ? ((Number) timeoutObj).intValue() : 30;

        jdbcTemplate.update(sql, 
            p.get("idUsuarioCics"), 
            p.get("nomPrograma").toString().trim().toUpperCase(), 
            p.get("nomTransaccion").toString().trim().toUpperCase(), 
            timeout);
    }
}