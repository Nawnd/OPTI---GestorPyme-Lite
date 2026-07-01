package com.gestorpyme.infrastructure.database;

import com.gestorpyme.util.PasswordHasher;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Inicializa la base de datos:
 *  1) Crea el esquema ejecutando el Script SQLite v0.2 (classpath: /database/...),
 *     solo si aun no existe (idempotente).
 *  2) Repara la contrasena del usuario administrador: si conserva el valor
 *     provisional de la semilla, le asigna un hash BCrypt real con una clave por
 *     defecto, para que el modulo Login pueda autenticarlo.
 *
 * Asume el script canonico v0.2 (sin triggers; sin '--' dentro de cadenas).
 * Capa: infrastructure.
 */
public final class DatabaseInitializer {

    /** Ruta del script SQL dentro del classpath (copiado a target/classes/database). */
    private static final String SCRIPT_PATH =
            "/database/05_OPTI_GestorPyme_Lite_Script_SQLite_Inicial_v0.2.sql";

    /** Ruta del script de migracion de compras (Paso B) en el classpath. */
    private static final String MIGRACION_COMPRAS_PATH =
            "/database/06_OPTI_GestorPyme_Lite_Migracion_Compras_v1.0.sql";

    /** Ruta del script de migracion de subtipos (v0.7.2) en el classpath. */
    private static final String MIGRACION_SUBTIPOS_PATH =
            "/database/07_OPTI_GestorPyme_Lite_Migracion_Subtipos_v1.0.sql";

    /** Marca que separa la Parte 1 (tablas) de la Parte 2 (recrear movimientos). */
    private static final String MARCA_PARTE2 = "@@PARTE2_RECREAR_MOVIMIENTOS@@";

    /** Marca que separa la estructura de las semillas en la migracion de subtipos. */
    private static final String MARCA_SEMILLAS = "@@PARTE2_SEMILLAS@@";

    /** Valor provisional que la semilla deja en usuarios.password_hash. */
    private static final String HASH_PROVISIONAL = "PENDIENTE_GENERAR_HASH_DESDE_JAVA";

    /** Clave por defecto asignada al administrador en el primer arranque. CAMBIAR cuanto antes. */
    private static final String PASSWORD_ADMIN_POR_DEFECTO = "admin123";

    private DatabaseInitializer() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Crea el esquema si no existe y repara la contrasena provisional del admin.
     *
     * @return true si el esquema se creo en esta ejecucion; false si ya estaba.
     */
    public static boolean initialize() throws SQLException {
        boolean creada;
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (isInitialized(conn)) {
                creada = false;
            } else {
                executeScript(conn, loadScript());
                creada = true;
            }
            // Se ejecuta siempre: cubre tambien bases creadas antes de existir esta logica.
            repararPasswordAdmin(conn);
            // Migracion del Paso B (compras / recepcion). Idempotente y con respaldo.
            migrarCompras(conn);
            // Migracion v0.7.2 (clasificacion: subtipos por categoria). Idempotente y segura.
            migrarSubtipos(conn);
            // Migracion v0.7.3 (piloto Mano de Obra: columnas de servicio en items). Idempotente.
            migrarManoObra(conn);
            // Paso A v0.8 (estandarizacion operativa: unidad de medida + ubicacion interna). Idempotente.
            migrarUnidadYUbicacion(conn);
            // Paso F: stock maximo y proveedor preferido por item (aditivo, nullable).
            migrarStockMaximoYProveedorPreferido(conn);
            // Paso I: bodega de salida por linea de venta (aditiva, nullable).
            migrarBodegaSalidaVentaDetalles(conn);
            // Paso J: cantidad disponible por lote + tabla puente venta_detalle_lotes (FEFO).
            migrarLotesFefo(conn);
            // Paso K: tabla de metas gerenciales (separada de los datos reales).
            migrarDashboardMetas(conn);
            // Paso U.1: tabla de vehiculos por cliente (base del modulo Taller). Aditiva e idempotente.
            migrarVehiculos(conn);
            // Paso U.2: tablas de ordenes de trabajo (cabecera + servicios + repuestos). Aditivas.
            migrarOrdenesTrabajo(conn);
            // Coherencia financiera: repara ventas a credito ya saldadas que quedaron
            // como PENDIENTE_PAGO (su cuenta por cobrar tiene saldo 0 o estado PAGADA).
            reconciliarEstadosVenta(conn);
        }
        return creada;
    }

    private static boolean isInitialized(Connection conn) throws SQLException {
        String query = "SELECT 1 FROM sqlite_master WHERE type='table' AND name='usuarios'";
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(query)) {
            return rs.next();
        }
    }

    /**
     * Reemplaza el hash provisional por un hash BCrypt real (clave por defecto)
     * en todo usuario que aun lo conserve. No hace nada si no hay ninguno.
     */
    private static void repararPasswordAdmin(Connection conn) throws SQLException {
        String select = "SELECT id_usuario FROM usuarios WHERE password_hash = ?";
        List<Integer> pendientes = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, HASH_PROVISIONAL);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    pendientes.add(rs.getInt("id_usuario"));
                }
            }
        }

        if (pendientes.isEmpty()) {
            return;
        }

        String update = "UPDATE usuarios SET password_hash = ? WHERE id_usuario = ?";
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            for (int id : pendientes) {
                ps.setString(1, PasswordHasher.hash(PASSWORD_ADMIN_POR_DEFECTO));
                ps.setInt(2, id);
                ps.executeUpdate();
            }
        }

        System.out.println("AVISO: se asigno la contrasena por defecto '"
                + PASSWORD_ADMIN_POR_DEFECTO + "' al usuario administrador. Cambiela cuanto antes.");
    }

    private static String loadScript() throws SQLException {
        return loadResource(SCRIPT_PATH);
    }

    private static String loadResource(String path) throws SQLException {
        try (InputStream in = DatabaseInitializer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new SQLException("No se encontro el recurso en el classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Error leyendo el recurso SQL: " + path, e);
        }
    }

    // ------------------------------------------------------------------------
    // Migracion del Paso B (compras / recepcion). Idempotente:
    //   - Parte 1 (tablas/indices/vista) solo si 'ordenes_compra' no existe.
    //   - Parte 2 (recrear inventario_movimientos) solo si aun no admite ENTRADA_COMPRA.
    // ------------------------------------------------------------------------
    private static void migrarCompras(Connection conn) throws SQLException {
        String mig = loadResource(MIGRACION_COMPRAS_PATH);
        int idx = mig.indexOf(MARCA_PARTE2);
        String parte1 = (idx >= 0) ? mig.substring(0, idx) : mig;
        // La Parte 2 arranca tras la marca; se elimina el token para no enviarlo como SQL.
        String parte2 = (idx >= 0) ? mig.substring(idx).replace(MARCA_PARTE2, "") : "";

        if (!existeTabla(conn, "ordenes_compra")) {
            ejecutarSentencias(conn, splitStatements(parte1));
            System.out.println("AVISO: migracion Paso B - tablas de compras creadas.");
        }
        if (!parte2.isEmpty() && !movimientosAdmiteEntradaCompra(conn)) {
            recrearMovimientos(conn, splitStatements(parte2));
        }
    }

    /** Ejecuta una lista de sentencias en una transaccion (rollback si falla). */
    private static void ejecutarSentencias(Connection conn, List<String> sentencias) throws SQLException {
        boolean previo = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            for (String sql : sentencias) {
                st.execute(sql);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previo);
        }
    }

    /**
     * Recrea inventario_movimientos (Parte 2) con respaldo seguro: cuenta las filas antes,
     * ejecuta la recreacion dentro de una transaccion y verifica que el conteo coincida;
     * si difiere, hace rollback y aborta sin tocar la tabla original.
     */
    private static void recrearMovimientos(Connection conn, List<String> sentencias) throws SQLException {
        int antes = contarFilas(conn, "inventario_movimientos");
        boolean previo = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            for (String sql : sentencias) {
                st.execute(sql);
            }
            int despues = contarFilas(conn, "inventario_movimientos"); // ya renombrada
            if (antes != despues) {
                conn.rollback();
                throw new SQLException("Migracion abortada: el respaldo de inventario_movimientos "
                        + "no coincide (" + antes + " -> " + despues + ").");
            }
            conn.commit();
            System.out.println("AVISO: migracion Paso B - inventario_movimientos admite ENTRADA_COMPRA ("
                    + despues + " filas respaldadas).");
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previo);
        }
    }

    // ------------------------------------------------------------------------
    // Migracion v0.7.2 (clasificacion: subtipos por categoria). Idempotente:
    //   - Parte 1 (tabla subtipos + indice) solo si 'subtipos' no existe.
    //   - Columna items.id_subtipo solo si aun no existe (ADD COLUMN no admite IF NOT EXISTS).
    //   - Parte 2 (semillas) con INSERT OR IGNORE: segura de ejecutar siempre.
    // No borra ni recrea tablas con datos.
    // ------------------------------------------------------------------------
    private static void migrarSubtipos(Connection conn) throws SQLException {
        String mig = loadResource(MIGRACION_SUBTIPOS_PATH);
        int idx = mig.indexOf(MARCA_SEMILLAS);
        String estructura = (idx >= 0) ? mig.substring(0, idx) : mig;
        String semillas = (idx >= 0) ? mig.substring(idx).replace(MARCA_SEMILLAS, "") : "";

        boolean creoTabla = false;
        if (!existeTabla(conn, "subtipos")) {
            ejecutarSentencias(conn, splitStatements(estructura));
            creoTabla = true;
        }
        if (!columnaExiste(conn, "items", "id_subtipo")) {
            // La columna es nullable: preserva todos los items existentes (quedan con subtipo null).
            ejecutarSentencias(conn, List.of("ALTER TABLE items ADD COLUMN id_subtipo INTEGER"));
            System.out.println("AVISO: migracion v0.7.2 - columna items.id_subtipo agregada (nullable).");
        }
        // Semillas idempotentes (INSERT OR IGNORE + UNIQUE): no duplican en re-ejecuciones.
        if (!semillas.trim().isEmpty()) {
            ejecutarSentencias(conn, splitStatements(semillas));
        }
        if (creoTabla) {
            System.out.println("AVISO: migracion v0.7.2 - tabla subtipos creada y sembrada.");
        }
    }

    // ------------------------------------------------------------------------
    // Migracion v0.7.3 (piloto Mano de Obra). Idempotente y sin pérdida:
    //   - Agrega items.modo_calculo_servicio (TEXT NOT NULL DEFAULT 'FIJO') si no existe.
    //   - Agrega items.porcentaje_servicio (REAL, nullable) si no existe.
    // Solo anade columnas (no recrea tablas). Los servicios existentes quedan en 'FIJO'.
    // No requiere script .sql porque solo son ALTER TABLE ADD COLUMN condicionales.
    // ------------------------------------------------------------------------
    private static void migrarManoObra(Connection conn) throws SQLException {
        boolean cambio = false;
        if (!columnaExiste(conn, "items", "modo_calculo_servicio")) {
            // DEFAULT 'FIJO' deja a todos los items existentes con modo fijo (servicios incluidos).
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE items ADD COLUMN modo_calculo_servicio TEXT NOT NULL DEFAULT 'FIJO'"));
            cambio = true;
        }
        if (!columnaExiste(conn, "items", "porcentaje_servicio")) {
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE items ADD COLUMN porcentaje_servicio REAL"));
            cambio = true;
        }
        if (cambio) {
            System.out.println("AVISO: migracion v0.7.3 - columnas de servicio agregadas a items "
                    + "(modo_calculo_servicio DEFAULT 'FIJO', porcentaje_servicio nullable).");
        }
    }

    // ------------------------------------------------------------------------
    // Paso A v0.8 (estandarizacion operativa). Idempotente y sin pérdida:
    //   - items.unidad_medida (TEXT NOT NULL DEFAULT 'Unidad') si no existe.
    //   - inventario_stock.ubicacion_interna (TEXT nullable) si no existe.
    // Solo anade columnas (no recrea tablas, no toca datos ni cantidades). Los items
    // existentes quedan con unidad 'Unidad'; las existencias, con ubicacion null.
    // ------------------------------------------------------------------------
    /**
     * Migracion Paso F (idempotente): agrega a items el stock maximo y el proveedor preferido.
     * Verifica con PRAGMA table_info antes de cada ALTER; columnas nullable, sin defaults que
     * alteren datos; no recrea tablas ni borra registros. Ejecutarla dos veces no repite el cambio.
     */
    /**
     * Migracion Paso I (idempotente): agrega venta_detalles.id_bodega_salida (INTEGER nullable),
     * la bodega real desde la que sale cada linea inventariable. Verifica con PRAGMA table_info antes
     * del ALTER; nullable (compatible con ventas historicas y servicios); no recrea tablas ni borra.
     */
    /**
     * Migraciones Paso J (idempotentes y aditivas) para trazabilidad FEFO por lote en ventas:
     * <ol>
     *   <li>Agrega lotes.cantidad_disponible (REAL nullable). En lotes historicos null se interpreta
     *       como cantidad_inicial al leer (IFNULL); no se hace actualizacion masiva.</li>
     *   <li>Crea la tabla puente venta_detalle_lotes (consumo de lote por linea de venta) si no existe.</li>
     *   <li>inventario_movimientos.id_lote ya existe en el esquema base; se reutiliza para registrar el
     *       lote en la salida por venta. Se agrega de forma defensiva solo si faltara.</li>
     * </ol>
     * Verifica con PRAGMA table_info / sqlite_master antes de crear; no recrea tablas ni borra datos.
     */
    /**
     * Migración Paso K (idempotente y aditiva): crea la tabla dashboard_metas (metas gerenciales por
     * período) si no existe. Las metas son OBJETIVOS de referencia, separados de los datos reales: no
     * tocan ventas, utilidad ni contabilidad. Verifica con sqlite_master antes de crear; no borra datos.
     */
    private static void migrarDashboardMetas(Connection conn) throws SQLException {
        if (!existeTabla(conn, "dashboard_metas")) {
            ejecutarSentencias(conn, List.of(
                    "CREATE TABLE dashboard_metas ("
                  + "id_meta INTEGER PRIMARY KEY AUTOINCREMENT, "
                  + "anio INTEGER NOT NULL, "
                  + "mes INTEGER, "
                  + "semana INTEGER, "
                  + "meta_ventas REAL, "
                  + "meta_utilidad REAL, "
                  + "meta_margen REAL, "
                  + "comentario TEXT, "
                  + "fecha_actualizacion TEXT DEFAULT (datetime('now','localtime')))"));
            System.out.println("AVISO: migracion Paso K - tabla creada (dashboard_metas).");
        }
    }

    /**
     * Paso U.1: crea la tabla {@code vehiculos} (vehiculos por cliente, base del modulo Taller).
     * Aditiva e idempotente: solo se crea si no existe; no toca tablas existentes ni borra datos.
     * Placa UNIQUE global (un taller no repite placas); FK a terceros (cliente).
     */
    private static void migrarVehiculos(Connection conn) throws SQLException {
        if (!existeTabla(conn, "vehiculos")) {
            ejecutarSentencias(conn, List.of(
                    "CREATE TABLE vehiculos ("
                  + "id_vehiculo INTEGER PRIMARY KEY AUTOINCREMENT, "
                  + "id_tercero INTEGER NOT NULL, "
                  + "placa TEXT NOT NULL, "
                  + "marca TEXT, "
                  + "linea TEXT, "
                  + "anio INTEGER, "
                  + "color TEXT, "
                  + "kilometraje REAL DEFAULT 0, "
                  + "observaciones TEXT, "
                  + "estado TEXT NOT NULL DEFAULT 'ACTIVO', "
                  + "fecha_creacion TEXT DEFAULT (datetime('now','localtime')), "
                  + "fecha_actualizacion TEXT, "
                  + "FOREIGN KEY (id_tercero) REFERENCES terceros(id_tercero), "
                  + "CONSTRAINT uq_vehiculos_placa UNIQUE (placa))"));
            System.out.println("AVISO: migracion Paso U.1 - tabla creada (vehiculos).");
        }
    }

    /**
     * Paso U.2: crea las tablas de ordenes de trabajo (cabecera + servicios + repuestos) como
     * documento de trabajo. Aditivas e idempotentes: solo se crean si no existen; no tocan tablas
     * existentes ni borran datos. En U.2 la OT NO descuenta inventario ni genera venta (eso es U.3);
     * por eso {@code id_venta} queda nullable. FKs a terceros, vehiculos, items, bodegas, ventas, usuarios.
     */
    private static void migrarOrdenesTrabajo(Connection conn) throws SQLException {
        if (!existeTabla(conn, "ordenes_trabajo")) {
            ejecutarSentencias(conn, List.of(
                    "CREATE TABLE ordenes_trabajo ("
                  + "id_orden_trabajo INTEGER PRIMARY KEY AUTOINCREMENT, "
                  + "numero_ot TEXT NOT NULL UNIQUE, "
                  + "id_tercero INTEGER NOT NULL, "
                  + "id_vehiculo INTEGER NOT NULL, "
                  + "fecha_ingreso TEXT DEFAULT (datetime('now','localtime')), "
                  + "fecha_entrega_estimada TEXT, "
                  + "kilometraje_ingreso REAL DEFAULT 0, "
                  + "motivo_ingreso TEXT, "
                  + "diagnostico TEXT, "
                  + "estado TEXT NOT NULL DEFAULT 'ABIERTA', "
                  + "observaciones TEXT, "
                  + "id_usuario INTEGER, "
                  + "id_venta INTEGER, "
                  + "subtotal_servicios REAL DEFAULT 0, "
                  + "subtotal_repuestos REAL DEFAULT 0, "
                  + "total REAL DEFAULT 0, "
                  + "fecha_creacion TEXT DEFAULT (datetime('now','localtime')), "
                  + "fecha_actualizacion TEXT, "
                  + "FOREIGN KEY (id_tercero) REFERENCES terceros(id_tercero), "
                  + "FOREIGN KEY (id_vehiculo) REFERENCES vehiculos(id_vehiculo), "
                  + "FOREIGN KEY (id_venta) REFERENCES ventas(id_venta), "
                  + "FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario))"));
            System.out.println("AVISO: migracion Paso U.2 - tabla creada (ordenes_trabajo).");
        }
        if (!existeTabla(conn, "orden_trabajo_servicios")) {
            ejecutarSentencias(conn, List.of(
                    "CREATE TABLE orden_trabajo_servicios ("
                  + "id_ot_servicio INTEGER PRIMARY KEY AUTOINCREMENT, "
                  + "id_orden_trabajo INTEGER NOT NULL, "
                  + "id_item INTEGER NOT NULL, "
                  + "cantidad REAL NOT NULL, "
                  + "precio_unitario REAL NOT NULL, "
                  + "subtotal REAL NOT NULL, "
                  + "FOREIGN KEY (id_orden_trabajo) REFERENCES ordenes_trabajo(id_orden_trabajo), "
                  + "FOREIGN KEY (id_item) REFERENCES items(id_item))"));
            System.out.println("AVISO: migracion Paso U.2 - tabla creada (orden_trabajo_servicios).");
        }
        if (!existeTabla(conn, "orden_trabajo_repuestos")) {
            ejecutarSentencias(conn, List.of(
                    "CREATE TABLE orden_trabajo_repuestos ("
                  + "id_ot_repuesto INTEGER PRIMARY KEY AUTOINCREMENT, "
                  + "id_orden_trabajo INTEGER NOT NULL, "
                  + "id_item INTEGER NOT NULL, "
                  + "id_bodega_salida INTEGER, "
                  + "cantidad REAL NOT NULL, "
                  + "precio_unitario REAL NOT NULL, "
                  + "subtotal REAL NOT NULL, "
                  + "FOREIGN KEY (id_orden_trabajo) REFERENCES ordenes_trabajo(id_orden_trabajo), "
                  + "FOREIGN KEY (id_item) REFERENCES items(id_item), "
                  + "FOREIGN KEY (id_bodega_salida) REFERENCES bodegas(id_bodega))"));
            System.out.println("AVISO: migracion Paso U.2 - tabla creada (orden_trabajo_repuestos).");
        }
    }

    private static void migrarLotesFefo(Connection conn) throws SQLException {
        // 1) cantidad disponible por lote (trazabilidad secundaria; el stock por bodega sigue siendo autoritativo).
        if (!columnaExiste(conn, "lotes", "cantidad_disponible")) {
            ejecutarSentencias(conn, List.of("ALTER TABLE lotes ADD COLUMN cantidad_disponible REAL"));
            System.out.println("AVISO: migracion Paso J - columna agregada (lotes.cantidad_disponible nullable).");
        }
        // 2) tabla puente venta_detalle_lotes: permite que una linea consuma uno o varios lotes (FEFO).
        if (!existeTabla(conn, "venta_detalle_lotes")) {
            ejecutarSentencias(conn, List.of(
                    "CREATE TABLE venta_detalle_lotes ("
                  + "id_venta_detalle_lote INTEGER PRIMARY KEY AUTOINCREMENT, "
                  + "id_detalle INTEGER NOT NULL, "
                  + "id_lote INTEGER NOT NULL, "
                  + "cantidad REAL NOT NULL, "
                  + "fecha_registro TEXT DEFAULT (datetime('now','localtime')), "
                  + "FOREIGN KEY (id_detalle) REFERENCES venta_detalles(id_detalle), "
                  + "FOREIGN KEY (id_lote) REFERENCES lotes(id_lote))"));
            System.out.println("AVISO: migracion Paso J - tabla creada (venta_detalle_lotes).");
        }
        // 3) id_lote en el Kardex (defensivo; normalmente ya existe en el esquema base).
        if (!columnaExiste(conn, "inventario_movimientos", "id_lote")) {
            ejecutarSentencias(conn, List.of("ALTER TABLE inventario_movimientos ADD COLUMN id_lote INTEGER"));
            System.out.println("AVISO: migracion Paso J - columna agregada (inventario_movimientos.id_lote nullable).");
        }
    }

    private static void migrarBodegaSalidaVentaDetalles(Connection conn) throws SQLException {
        if (!columnaExiste(conn, "venta_detalles", "id_bodega_salida")) {
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE venta_detalles ADD COLUMN id_bodega_salida INTEGER"));
            System.out.println("AVISO: migracion Paso I - columna agregada (venta_detalles.id_bodega_salida nullable).");
        }
    }

    private static void migrarStockMaximoYProveedorPreferido(Connection conn) throws SQLException {
        boolean cambio = false;
        if (!columnaExiste(conn, "items", "stock_maximo")) {
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE items ADD COLUMN stock_maximo REAL"));
            cambio = true;
        }
        if (!columnaExiste(conn, "items", "id_proveedor_preferido")) {
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE items ADD COLUMN id_proveedor_preferido INTEGER"));
            cambio = true;
        }
        if (cambio) {
            System.out.println("AVISO: migracion Paso F - columnas agregadas "
                    + "(items.stock_maximo nullable, items.id_proveedor_preferido nullable).");
        }
    }

    private static void migrarUnidadYUbicacion(Connection conn) throws SQLException {
        boolean cambio = false;
        if (!columnaExiste(conn, "items", "unidad_medida")) {
            // DEFAULT 'Unidad' deja a todos los items existentes con una unidad segura.
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE items ADD COLUMN unidad_medida TEXT NOT NULL DEFAULT 'Unidad'"));
            cambio = true;
        }
        if (!columnaExiste(conn, "inventario_stock", "ubicacion_interna")) {
            ejecutarSentencias(conn, List.of(
                    "ALTER TABLE inventario_stock ADD COLUMN ubicacion_interna TEXT"));
            cambio = true;
        }
        if (cambio) {
            System.out.println("AVISO: migracion Paso A - columnas agregadas "
                    + "(items.unidad_medida DEFAULT 'Unidad', inventario_stock.ubicacion_interna nullable).");
        }
    }

    /** Indica si una tabla tiene una columna con el nombre dado (via PRAGMA table_info). */
    private static boolean columnaExiste(Connection conn, String tabla, String columna) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (rs.next()) {
                if (columna.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean existeTabla(Connection conn, String nombre) throws SQLException {
        String q = "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?";
        try (PreparedStatement ps = conn.prepareStatement(q)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean movimientosAdmiteEntradaCompra(Connection conn) throws SQLException {
        String q = "SELECT sql FROM sqlite_master WHERE type='table' AND name='inventario_movimientos'";
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(q)) {
            if (rs.next()) {
                String sql = rs.getString("sql");
                return sql != null && sql.contains("ENTRADA_COMPRA");
            }
        }
        return false;
    }

    private static int contarFilas(Connection conn, String tabla) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Marca como PAGADA toda venta a credito cuya cuenta por cobrar ya esta saldada
     * (saldo 0 o estado PAGADA) pero que quedo en PENDIENTE_PAGO. Idempotente: no afecta
     * ventas anuladas ni las que ya estan PAGADA.
     */
    private static void reconciliarEstadosVenta(Connection conn) throws SQLException {
        String sql = "UPDATE ventas SET estado = 'PAGADA' "
                   + "WHERE estado = 'PENDIENTE_PAGO' AND id_venta IN "
                   + "(SELECT id_venta FROM cuentas_por_cobrar "
                   + " WHERE saldo_pendiente <= 0 OR estado = 'PAGADA')";
        try (Statement st = conn.createStatement()) {
            int filas = st.executeUpdate(sql);
            if (filas > 0) {
                System.out.println("AVISO: coherencia financiera - " + filas
                        + " venta(s) marcadas PAGADA al estar saldada su cuenta.");
            }
        }
    }

    private static void executeScript(Connection conn, String script) throws SQLException {
        List<String> statements = splitStatements(script);
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (Statement st = conn.createStatement()) {
            for (String sql : statements) {
                st.execute(sql);
            }
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    /**
     * Separa el script en sentencias por ';', ignorando comentarios de linea '--'
     * y lineas en blanco. Valido para el script canonico v0.2 (sin triggers).
     */
    private static List<String> splitStatements(String script) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : script.split("\\r?\\n")) {
            String line = rawLine;
            int commentIdx = line.indexOf("--");
            if (commentIdx >= 0) {
                line = line.substring(0, commentIdx);
            }
            if (line.trim().isEmpty()) {
                continue;
            }
            current.append(line).append('\n');
            if (line.trim().endsWith(";")) {
                statements.add(current.toString().trim());
                current.setLength(0);
            }
        }

        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            statements.add(tail);
        }
        return statements;
    }
}
