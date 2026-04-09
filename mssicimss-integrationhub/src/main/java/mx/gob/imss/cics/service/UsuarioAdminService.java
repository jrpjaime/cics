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
import java.util.Date;

@Service
public class UsuarioAdminService {

    private static final Logger logger = LogManager.getLogger(UsuarioAdminService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Lista usuarios aplicando filtros dinámicos y paginación para Oracle 11g.
     * Recupera nombres y apellidos para la tabla principal.
     */
    public Map<String, Object> listarUsuariosPaginados(int page, int size, String userApi, String userMain, String rol) {
        logger.info("Ejecutando consulta paginada de usuarios. Pagina: {}, Tamaño: {}", page, size);
        
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

        // Consulta detallada para Oracle 11g usando ROWNUM
        String sqlPaged = "SELECT * FROM ( " +
                          "  SELECT a.*, ROWNUM rnum FROM ( " +
                          "    SELECT " +
                          "      ID_USUARIO_CICS, " +
                          "      CVE_USUARIO_API, " +
                          "      CVE_USUARIO_MAINFRAME, " +
                          "      CVE_ROL, " +
                          "      IND_ACTIVO, " +
                          "      NOM_NOMBRE, " +
                          "      NOM_APELLIDO_1 " +
                          "    FROM MSCC_USUARIO_CICS " + sqlWhere + 
                          "    ORDER BY ID_USUARIO_CICS DESC " +
                          "  ) a WHERE ROWNUM <= ? " +
                          ") WHERE rnum >= ?";

        List<Map<String, Object>> content = jdbcTemplate.queryForList(sqlPaged, params.toArray());

        Map<String, Object> response = new HashMap<>();
        response.put("content", content);
        response.put("totalElements", totalElements);
        response.put("totalPages", (int) Math.ceil((double) (totalElements != null ? totalElements : 0) / size));
        response.put("number", page);
        response.put("size", size);

        logger.info("Consulta finalizada. Total de registros encontrados: {}", totalElements);
        return response;
    }

    /**
     * Obtiene la información completa de una identidad y sus programas autorizados.
     */
    public Map<String, Object> obtenerUsuarioDetalle(Long id) {
        logger.info("Iniciando busqueda de detalle para ID_USUARIO_CICS: {}", id);
        
        String sqlUser = "SELECT * FROM MSCC_USUARIO_CICS WHERE ID_USUARIO_CICS = ?";
        Map<String, Object> user = jdbcTemplate.queryForMap(sqlUser, id);

        String sqlPermisos = "SELECT " +
                             "  ID_PERMISO_CICS, " +
                             "  NOM_PROGRAMA, " +
                             "  NOM_TRANSACCION, " +
                             "  NUM_TIMEOUT_SEC, " +
                             "  IND_ACTIVO, " +
                             "  STP_REGISTRO, " +
                             "  STP_ACTUALIZACION " +
                             "FROM MSCC_PERMISO_CICS " +
                             "WHERE ID_USUARIO_CICS = ? AND STP_BAJA IS NULL " +
                             "ORDER BY NOM_PROGRAMA ASC";
        
        List<Map<String, Object>> permisos = jdbcTemplate.queryForList(sqlPermisos, id);

        user.put("permisos", permisos);
        logger.info("Detalle recuperado exitosamente para el usuario: {}", user.get("CVE_USUARIO_API"));
        return user;
    }

    /**
     * Registra un nuevo mapeo de identidad con todos los campos de contacto y auditoría.
     */
    @Transactional
    public void registrarUsuario(Map<String, Object> datos) {
        logger.info("RECIBIENDO SOLICITUD DE REGISTRO: {}", datos);
        
        // Sincronizacion con hora de CDMX (Java)
        Date ahora = new Date();
        
        try {
            // Extraccion de datos obligatorios
            Object cveApiObj = datos.get("cveUsuarioApi");
            String userApi = (cveApiObj != null) ? cveApiObj.toString().trim() : "";
            
            Object passApiObj = datos.get("desPasswordApi");
            String passRaw = (passApiObj != null) ? passApiObj.toString() : "";
            
            Object rolObj = datos.get("cveRol");
            String rol = (rolObj != null) ? rolObj.toString() : "CLIENTE";

            if (userApi.isEmpty() || passRaw.isEmpty()) {
                logger.error("Error de validacion: Usuario o Password API vacios.");
                throw new RuntimeException("Campos de credenciales obligatorios faltantes.");
            }

            // 1. Verificacion de duplicidad
            String sqlValida = "SELECT COUNT(*) FROM MSCC_USUARIO_CICS WHERE CVE_USUARIO_API = ?";
            Long conteo = jdbcTemplate.queryForObject(sqlValida, Long.class, userApi);
            if (conteo != null && conteo > 0) {
                throw new RuntimeException("No se puede registrar. El usuario '" + userApi + "' ya existe.");
            }

            // 2. Obtencion de ID de secuencia
            Long idUsuario = jdbcTemplate.queryForObject("SELECT MSCS_USUARIO_CICS.NEXTVAL FROM DUAL", Long.class);
            logger.info("Asignando nuevo ID de secuencia: {}", idUsuario);

            // 3. Logica de Mainframe
            String userMain = null;
            String passMain = null;
            if ("CLIENTE".equals(rol)) {
                Object uM = datos.get("cveUsuarioMainframe");
                userMain = (uM != null) ? uM.toString().toUpperCase() : null;
                
                Object pM = datos.get("desPasswordMainframe");
                passMain = (pM != null) ? pM.toString() : null;
            }

            // 4. Insercion del Maestro (Campos de Identidad, Contacto y Auditoria sincronizados con Java)
            String sqlInsertMaster = "INSERT INTO MSCC_USUARIO_CICS (" +
                                    "  ID_USUARIO_CICS, CVE_USUARIO_API, DES_PASSWORD_API, " +
                                    "  NOM_NOMBRE, NOM_APELLIDO_1, NOM_APELLIDO_2, " +
                                    "  DES_USO_CUENTA, DES_CORREO, NUM_TELEFONO, NUM_EXTENSION, " +
                                    "  CVE_ROL, CVE_USUARIO_MAINFRAME, DES_PASSWORD_MAINFRAME, " +
                                    "  IND_ACTIVO, STP_REGISTRO, STP_ACTUALIZACION" +
                                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)";
            
            jdbcTemplate.update(sqlInsertMaster, 
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
                passMain,
                ahora, // STP_REGISTRO (Hora Java)
                ahora  // STP_ACTUALIZACION (Hora Java)
            );

            // 5. Registro de lista de programas autorizados
            if (datos.get("permisos") != null) {
                List<Map<String, Object>> listaPermisos = (List<Map<String, Object>>) datos.get("permisos");
                logger.info("Insertando {} programas autorizados para el usuario.", listaPermisos.size());
                
                for (Map<String, Object> p : listaPermisos) {
                    Object nomP = p.get("nomPrograma");
                    String strProg = (nomP != null) ? nomP.toString().trim() : "";
                    
                    // VALIDACIÓN ORA-01400: Evita insertar filas vacías
                    if (!strProg.isEmpty()) {
                        p.put("idUsuarioCics", idUsuario);
                        this.insertarPermisoIndividual(p, ahora);
                    }
                }
            }
            logger.info("OPERACION EXITOSA: Usuario {} registrado con ID {}", userApi, idUsuario);

        } catch (Exception e) {
            logger.error("FALLO CRITICO EN registrarUsuario: " + e.getMessage(), e);
            throw e; 
        }
    }

    /**
     * Actualiza los datos de la identidad realizando una comparacion inteligente
     * para no mover las fechas de auditoria de los permisos si no hay cambios reales.
     */
    @Transactional
    public void actualizarUsuario(Map<String, Object> datos) {
        logger.info("INICIANDO ACTUALIZACION PARCIAL/TOTAL. ID_USUARIO: {}", datos.get("idUsuarioCics"));
        
        Date ahora = new Date(); // Sincronizado con CDMX
        Long idUser = Long.valueOf(datos.get("idUsuarioCics").toString());
        String rolActual = datos.get("cveRol").toString();
        Integer estadoActual = Integer.valueOf(datos.get("indActivo").toString());

        // 1. Password del Portal: Solo si el usuario escribio algo nuevo
        Object passObj = datos.get("desPasswordApi");
        String passRaw = (passObj != null) ? passObj.toString() : "";
        
        if (!passRaw.trim().isEmpty()) {
            logger.info("Cambiando password del API para el usuario ID: {}", idUser);
            String sqlP = "UPDATE MSCC_USUARIO_CICS SET DES_PASSWORD_API = ? WHERE ID_USUARIO_CICS = ?";
            jdbcTemplate.update(sqlP, passwordEncoder.encode(passRaw), idUser);
        }

        // 2. Actualizacion de Datos Maestros e Identidad
        String sqlUpdMaster = "UPDATE MSCC_USUARIO_CICS SET " +
                              "  NOM_NOMBRE = ?, NOM_APELLIDO_1 = ?, NOM_APELLIDO_2 = ?, " +
                              "  DES_USO_CUENTA = ?, DES_CORREO = ?, NUM_TELEFONO = ?, NUM_EXTENSION = ?, " +
                              "  CVE_USUARIO_MAINFRAME = ?, DES_PASSWORD_MAINFRAME = ?, " +
                              "  CVE_ROL = ?, IND_ACTIVO = ?, STP_ACTUALIZACION = ? " +
                              "WHERE ID_USUARIO_CICS = ?";
        
        jdbcTemplate.update(sqlUpdMaster, 
            datos.get("nomNombre"), 
            datos.get("nomApellido1"), 
            datos.get("nomApellido2"),
            datos.get("desUsoCuenta"), 
            datos.get("desCorreo"), 
            datos.get("numTelefono"), 
            datos.get("numExtension"),
            datos.get("cveUsuarioMainframe"), 
            datos.get("desPasswordMainframe"), 
            rolActual, 
            estadoActual, 
            ahora, 
            idUser);

        // 3. Gestion de Auditoria Independiente por Programa
        if (datos.containsKey("permisos")) {
            List<Map<String, Object>> listadoFront = (List<Map<String, Object>>) datos.get("permisos");
            
            // Traemos el estado actual de la base de datos para comparar fila por fila
            String sqlCheck = "SELECT * FROM MSCC_PERMISO_CICS WHERE ID_USUARIO_CICS = ? AND STP_BAJA IS NULL";
            List<Map<String, Object>> listadoDB = jdbcTemplate.queryForList(sqlCheck, idUser);

            for (Map<String, Object> itemFront : listadoFront) {
                Object nomPFrontObj = itemFront.get("nomPrograma");
                String nomPFront = (nomPFrontObj != null) ? nomPFrontObj.toString().trim().toUpperCase() : "";
                
                if (itemFront.get("idPermisoCics") == null) {
                    // Es un programa nuevo agregado en la pantalla de edicion
                    if (!nomPFront.isEmpty()) {
                        logger.info("Detectado nuevo programa para insertar en edicion: {}", nomPFront);
                        itemFront.put("idUsuarioCics", idUser);
                        this.insertarPermisoIndividual(itemFront, ahora);
                    }
                } else {
                    // El programa ya existia en la base de datos: Comparar para no "pisar" auditoria
                    Long idPermiso = Long.valueOf(itemFront.get("idPermisoCics").toString());
                    
                    Map<String, Object> registroOriginal = null;
                    for(Map<String, Object> dbRow : listadoDB) {
                        if (idPermiso.equals(Long.valueOf(dbRow.get("ID_PERMISO_CICS").toString()))) {
                            registroOriginal = dbRow;
                            break;
                        }
                    }

                    if (registroOriginal != null) {
                        boolean esBaja = "0".equals(itemFront.get("indActivo").toString());
                        
                        // Comparar campos para detectar cambios reales y solo entonces actualizar STP_ACTUALIZACION
                        boolean cambioEnPrograma = !nomPFront.equals(registroOriginal.get("NOM_PROGRAMA"));
                        boolean cambioEnTrans = !itemFront.get("nomTransaccion").toString().toUpperCase().equals(registroOriginal.get("NOM_TRANSACCION"));
                        boolean cambioEnTimeout = !itemFront.get("numTimeoutSec").toString().equals(registroOriginal.get("NUM_TIMEOUT_SEC").toString());

                        if (esBaja) {
                            logger.info("Marcando BAJA LOGICA para permiso ID: {}", idPermiso);
                            String sqlBaja = "UPDATE MSCC_PERMISO_CICS SET IND_ACTIVO = 0, STP_BAJA = ?, STP_ACTUALIZACION = ? WHERE ID_PERMISO_CICS = ?";
                            jdbcTemplate.update(sqlBaja, ahora, ahora, idPermiso);
                        } 
                        else if (cambioEnPrograma || cambioEnTrans || cambioEnTimeout) {
                            logger.info("Cambio detectado en permiso ID {}. Actualizando auditoria.", idPermiso);
                            String sqlUpdateRow = "UPDATE MSCC_PERMISO_CICS SET " +
                                                 "  NOM_PROGRAMA = ?, NOM_TRANSACCION = ?, " +
                                                 "  NUM_TIMEOUT_SEC = ?, STP_ACTUALIZACION = ? " +
                                                 "WHERE ID_PERMISO_CICS = ?";
                            jdbcTemplate.update(sqlUpdateRow, 
                                nomPFront, 
                                itemFront.get("nomTransaccion").toString().toUpperCase(), 
                                itemFront.get("numTimeoutSec"), 
                                ahora, 
                                idPermiso);
                        } else {
                            logger.debug("Permiso {} sin cambios. Se preserva fecha original.", idPermiso);
                        }
                    }
                }
            }
        }
        logger.info("PROCESO DE ACTUALIZACION CONCLUIDO PARA ID {}", idUser);
    }

    /**
     * Inserta una nueva fila de permiso con su fecha de registro inicial (Hora Java).
     */
    private void insertarPermisoIndividual(Map<String, Object> p, Date fecha) {
        String sql = "INSERT INTO MSCC_PERMISO_CICS (" +
                    "  ID_PERMISO_CICS, ID_USUARIO_CICS, NOM_PROGRAMA, " + 
                    "  NOM_TRANSACCION, NUM_TIMEOUT_SEC, IND_ACTIVO, " +
                    "  STP_REGISTRO, STP_ACTUALIZACION" +
                    ") VALUES (MSCS_PERMISO_CICS.NEXTVAL, ?, ?, ?, ?, 1, ?, ?)";
        
        Object timeoutObj = p.get("numTimeoutSec");
        int timeoutVal = (timeoutObj instanceof Number) ? ((Number) timeoutObj).intValue() : 30;

        jdbcTemplate.update(sql, 
            p.get("idUsuarioCics"), 
            p.get("nomPrograma").toString().trim().toUpperCase(), 
            p.get("nomTransaccion").toString().trim().toUpperCase(), 
            timeoutVal, 
            fecha, 
            fecha);
        
        logger.info("Permiso individual registrado: {}", p.get("nomPrograma"));
    }
}