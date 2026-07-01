package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.model.ComprasPeriodoResumen;
import com.gestorpyme.domain.model.DashboardResumen;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.util.MoneyFormatter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso G (Dashboard de compras por periodo): agregados de compras ordenadas/recibidas,
 * cantidades (totales, pendientes, recibidas), promedio por orden, filtros año/mes, mes sin datos
 * y que no se rompan los KPIs existentes. Solo lectura/calculo.
 *
 * AISLAMIENTO (importante): {@code comprasPeriodo} agrega TODAS las ordenes del periodo, por lo que
 * los totales no pueden filtrarse por un marcador de datos. Para que las aserciones exactas sean
 * independientes de los datos reales de la base del usuario, los datos de prueba se siembran en un
 * AÑO CENTINELA (2099) que la operacion real nunca usara. Asi, consultar 2099 devuelve unicamente
 * los datos sembrados aqui. Ademas se marcan con prefijo "ZPGTEST" para una limpieza segura.
 */
class PasoGDashboardComprasTest {

    /** Año centinela: ningun dato real de negocio existe en este año, por lo que aisla la prueba. */
    private static final int ANIO = 2099;

    private static final DashboardService dashboard = new DashboardService();
    private static final ItemService itemService = new ItemService();

    private static int idItem;
    private static int idBodega;
    private static int idProv1;
    private static int idProv2;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        // Guarda de aislamiento: el año centinela debe estar vacio antes de sembrar. Si no lo esta,
        // la prueba se detiene con un mensaje claro (en vez de fallar luego por totales inesperados).
        assertEquals(0, dashboard.comprasPeriodo(ANIO, null).getCantidadOrdenes(),
                "El año centinela " + ANIO + " debe estar libre de compras para aislar la prueba.");
        idProv1 = insertarProveedor("ZPGTEST Prov Uno");
        idProv2 = insertarProveedor("ZPGTEST Prov Dos");
        idBodega = insertarBodega("ZPGTEST Bodega");
        Item it = inventariable("ZPGTEST-ITM");
        itemService.guardar(it);
        idItem = it.getIdItem();

        // Ordenes de Junio 2099 (año CENTINELA: aísla la prueba de los datos reales de la base) (las que cuentan como "emitidas": EMITIDA/PARC/RECIBIDA).
        int oc1 = insertarOrden("ZPGTEST-OC1", idProv1, "2099-06-05", "EMITIDA", "100000");
        int oc2 = insertarOrden("ZPGTEST-OC2", idProv1, "2099-06-10", "RECIBIDA", "300000");
        insertarOrden("ZPGTEST-OC3", idProv2, "2099-06-15", "PARCIALMENTE_RECIBIDA", "200000");
        // Excluidas: BORRADOR y CANCELADA.
        insertarOrden("ZPGTEST-OC4", idProv2, "2099-06-20", "BORRADOR", "999000");
        insertarOrden("ZPGTEST-OC5", idProv1, "2099-06-25", "CANCELADA", "888000");
        // Otro mes (Mayo 2099): cuenta para el año centinela, no para Junio.
        insertarOrden("ZPGTEST-OC6", idProv1, "2099-05-05", "EMITIDA", "500000");

        // Recepcion en Junio para OC2: 10 unidades x 30000 = 300000 recibido.
        int detOc2 = insertarDetalleOC(oc2, idItem, "10", "30000");
        int rec = insertarRecepcion("ZPGTEST-RC1", oc2, "2099-06-12");
        insertarDetalleRecepcion(rec, detOc2, idItem, idBodega, "10");
        // (oc1 se referencia para mantener trazabilidad de la prueba)
        assertTrue(oc1 > 0);
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Agregados de Junio 2099 (año centinela) ----------------

    @Test
    void totalOrdenadoYCantidadDeJunio() throws SQLException {
        ComprasPeriodoResumen c = dashboard.comprasPeriodo(ANIO, 6);
        // 100000 + 300000 + 200000 (BORRADOR y CANCELADA excluidas).
        assertEquals(0, new BigDecimal("600000").compareTo(c.getTotalOrdenado()));
        assertEquals(3, c.getCantidadOrdenes());
    }

    @Test
    void ordenesPendientesYRecibidasDeJunio() throws SQLException {
        ComprasPeriodoResumen c = dashboard.comprasPeriodo(ANIO, 6);
        assertEquals(2, c.getOrdenesPendientes(), "EMITIDA + PARCIALMENTE_RECIBIDA");
        assertEquals(1, c.getOrdenesRecibidas(), "RECIBIDA");
    }

    @Test
    void valorRecibidoDeJunio() throws SQLException {
        ComprasPeriodoResumen c = dashboard.comprasPeriodo(ANIO, 6);
        // 10 unidades x 30000 (precio unitario de la linea de OC).
        assertEquals(0, new BigDecimal("300000").compareTo(c.getTotalRecibido()));
    }

    @Test
    void promedioPorOrdenDeJunio() throws SQLException {
        ComprasPeriodoResumen c = dashboard.comprasPeriodo(ANIO, 6);
        // 600000 / 3 = 200000.
        assertEquals(0, new BigDecimal("200000.00").compareTo(c.getPromedioPorOrden()));
    }

    @Test
    void proveedorTopDeJunio() throws SQLException {
        ComprasPeriodoResumen c = dashboard.comprasPeriodo(ANIO, 6);
        // Prov1 = 100000 + 300000 = 400000 ; Prov2 = 200000 -> top Prov1.
        assertEquals("ZPGTEST Prov Uno", c.getProveedorTopNombre());
        assertEquals(0, new BigDecimal("400000").compareTo(c.getProveedorTopValor()));
    }

    // ---------------- Filtros año / mes ----------------

    @Test
    void filtroPorAnioIncluyeTodosLosMeses() throws SQLException {
        ComprasPeriodoResumen c = dashboard.comprasPeriodo(ANIO, null);
        // Junio (600000) + Mayo (500000) = 1.100.000 ; cantidad = 4 emitidas.
        assertEquals(0, new BigDecimal("1100000").compareTo(c.getTotalOrdenado()));
        assertEquals(4, c.getCantidadOrdenes());
    }

    @Test
    void filtroPorMesAislaElPeriodo() throws SQLException {
        ComprasPeriodoResumen mayo = dashboard.comprasPeriodo(ANIO, 5);
        assertEquals(1, mayo.getCantidadOrdenes());
        assertEquals(0, new BigDecimal("500000").compareTo(mayo.getTotalOrdenado()));
    }

    @Test
    void mesSinComprasDevuelveCero() throws SQLException {
        ComprasPeriodoResumen enero = dashboard.comprasPeriodo(ANIO, 1);
        assertEquals(0, enero.getCantidadOrdenes());
        assertEquals(0, BigDecimal.ZERO.compareTo(enero.getTotalOrdenado()));
        assertEquals(0, BigDecimal.ZERO.compareTo(enero.getTotalRecibido()));
        assertEquals(0, BigDecimal.ZERO.compareTo(enero.getPromedioPorOrden()));
        assertEquals(0, enero.getOrdenesPendientes());
    }

    // ---------------- Promedio (metodo puro) y formato ----------------

    @Test
    void promedioPorOrdenMetodoPuro() {
        assertEquals(0, new BigDecimal("200000.00")
                .compareTo(dashboard.promedioPorOrden(new BigDecimal("600000"), 3)));
        assertEquals(0, BigDecimal.ZERO
                .compareTo(dashboard.promedioPorOrden(new BigDecimal("600000"), 0)), "cantidad 0 -> 0");
    }

    @Test
    void noRompeKpisExistentesYFormatoCop() throws SQLException {
        DashboardResumen r = dashboard.obtenerResumen();
        assertNotNull(r, "El resumen del Dashboard debe seguir disponible.");
        assertNotNull(r.getTotalVentasMes());
        // MoneyFormatter se usa para mostrar los valores COP en la UI.
        String cop = MoneyFormatter.cop(new BigDecimal("600000"));
        assertTrue(cop != null && cop.matches(".*\\d.*"), "El formato COP debe contener dígitos.");
    }

    // ---------------- auxiliares ----------------

    private static Item inventariable(String codigo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPGTEST " + codigo);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal("5"));
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static int insertarProveedor(String nombre) throws SQLException {
        return insertarConId("INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'PROVEEDOR', 'ACTIVO')", nombre);
    }

    private static int insertarBodega(String nombre) throws SQLException {
        return insertarConId("INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", nombre);
    }

    private static int insertarConId(String sql, String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int insertarOrden(String numero, int idProveedor, String fechaOrden,
                                     String estado, String total) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ordenes_compra (numero_orden, id_proveedor, fecha_orden, estado, subtotal, total, fecha_creacion) "
                     + "VALUES (?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, idProveedor);
            ps.setString(3, fechaOrden);
            ps.setString(4, estado);
            ps.setDouble(5, new BigDecimal(total).doubleValue());
            ps.setDouble(6, new BigDecimal(total).doubleValue());
            ps.setString(7, fechaOrden);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int insertarDetalleOC(int idOrden, int idItem, String cantidad, String precioUnit) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ordenes_compra_detalles (id_orden, id_item, cantidad_solicitada, cantidad_recibida, precio_unitario, subtotal) "
                     + "VALUES (?, ?, ?, 0, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idOrden);
            ps.setInt(2, idItem);
            ps.setDouble(3, new BigDecimal(cantidad).doubleValue());
            ps.setDouble(4, new BigDecimal(precioUnit).doubleValue());
            ps.setDouble(5, new BigDecimal(cantidad).multiply(new BigDecimal(precioUnit)).doubleValue());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int insertarRecepcion(String numero, int idOrden, String fecha) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO recepciones_mercancia (numero_recepcion, id_orden, fecha) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, idOrden);
            ps.setString(3, fecha);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void insertarDetalleRecepcion(int idRecepcion, int idDetalleOc, int idItem,
                                                 int idBodega, String cantidadRecibida) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO recepciones_detalles (id_recepcion, id_detalle_oc, id_item, id_bodega, cantidad_recibida) "
                     + "VALUES (?, ?, ?, ?, ?)")) {
            ps.setInt(1, idRecepcion);
            ps.setInt(2, idDetalleOc);
            ps.setInt(3, idItem);
            ps.setInt(4, idBodega);
            ps.setDouble(5, new BigDecimal(cantidadRecibida).doubleValue());
            ps.executeUpdate();
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ocZ = "SELECT id_orden FROM ordenes_compra WHERE numero_orden LIKE 'ZPGTEST%'";
            String recZ = "SELECT id_recepcion FROM recepciones_mercancia WHERE numero_recepcion LIKE 'ZPGTEST%'";
            st.executeUpdate("DELETE FROM recepciones_detalles WHERE id_recepcion IN (" + recZ + ")");
            st.executeUpdate("DELETE FROM recepciones_mercancia WHERE numero_recepcion LIKE 'ZPGTEST%'");
            st.executeUpdate("DELETE FROM ordenes_compra_detalles WHERE id_orden IN (" + ocZ + ")");
            st.executeUpdate("DELETE FROM ordenes_compra WHERE numero_orden LIKE 'ZPGTEST%'");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (SELECT id_item FROM items WHERE codigo LIKE 'ZPGTEST%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPGTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPGTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPGTEST%'");
        }
    }
}
