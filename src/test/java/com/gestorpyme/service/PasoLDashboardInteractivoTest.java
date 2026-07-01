package com.gestorpyme.service;

import com.gestorpyme.domain.model.DashboardChartSegment;
import com.gestorpyme.domain.model.DashboardDrillDownItem;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso L (consultas de lectura para gráficas clicables y desglose): stock por bodega,
 * ítems por bodega, cartera por estado (medida por delta), ventas por rango, rotación de productos y
 * ventas por ítem, además de robustez con datos vacíos. Año centinela 2099/2098 + marcador "ZPLTEST"
 * para aislar de los datos reales; la cartera se mide por diferencia (antes/después) por ser un
 * agregado global no filtrable por año.
 */
class PasoLDashboardInteractivoTest {

    private static final DashboardService dashboardService = new DashboardService();
    private static int idBodega;
    private static int idCliente;
    private static int idItemA;
    private static int idItemB;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idBodega = idDe("INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", "ZPLTEST Bodega");
        idCliente = idDe("INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'CLIENTE', 'ACTIVO')",
                "ZPLTEST Cliente");
        idItemA = item("ZPLTEST-A");
        idItemB = item("ZPLTEST-B");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    @Test
    void stockPorBodegaIncluyeLaBodegaConStock() throws SQLException {
        stock(idItemA, idBodega, 7);
        DashboardChartSegment seg = buscarSegmento(dashboardService.stockPorBodega(), String.valueOf(idBodega));
        assertNotNull(seg, "La bodega con stock debe aparecer en la gráfica.");
        assertEquals(7d, seg.getValor(), 0.001);
    }

    @Test
    void itemsPorBodegaDevuelveLosItems() throws SQLException {
        stock(idItemB, idBodega, 4);
        List<DashboardDrillDownItem> filas = dashboardService.itemsPorBodega(idBodega, 50);
        boolean halla = filas.stream().anyMatch(f -> "ZPLTEST B".equals(f.getPrincipal()));
        assertTrue(halla, "El desglose por bodega debe listar el ítem con stock.");
    }

    @Test
    void itemsPorBodegaInexistenteListaVaciaSegura() throws SQLException {
        List<DashboardDrillDownItem> filas = dashboardService.itemsPorBodega(-999, 50);
        assertNotNull(filas);
        assertTrue(filas.isEmpty(), "Bodega inexistente debe devolver lista vacía, no excepción.");
    }

    @Test
    void carteraPorEstadoSumaPorDelta() throws SQLException {
        double antes = valorEstado(dashboardService.carteraPorEstado(), "Pendiente");
        int v = venta("V-ZPL-CART", "2099-03-10", "1234");
        cuenta(v, "1234", "PENDIENTE");
        double despues = valorEstado(dashboardService.carteraPorEstado(), "Pendiente");
        assertEquals(1234d, despues - antes, 0.5, "La cartera pendiente debe crecer en el saldo agregado.");
    }

    @Test
    void ventasPorRangoFiltraYDevuelve() throws SQLException {
        venta("V-ZPL-R1", "2099-04-03", "500");
        venta("V-ZPL-R2", "2099-04-20", "300");
        List<DashboardDrillDownItem> abril = dashboardService.ventasPorRango("2099-04-01", "2099-04-30", 50);
        long mias = abril.stream().filter(f -> f.getPrincipal().startsWith("V-ZPL-R")).count();
        assertEquals(2, mias, "Ambas ventas de abril/2099 deben aparecer.");
        List<DashboardDrillDownItem> soloInicio = dashboardService.ventasPorRango("2099-04-01", "2099-04-10", 50);
        long mias2 = soloInicio.stream().filter(f -> f.getPrincipal().startsWith("V-ZPL-R")).count();
        assertEquals(1, mias2, "El rango recortado solo incluye la venta del día 3.");
    }

    @Test
    void rotacionProductosOrdenaPorCantidad() throws SQLException {
        int v = venta("V-ZPL-ROT", "2099-05-05", "0");
        detalle(v, idItemA, "3", "100");   // A: 3
        detalle(v, idItemB, "9", "100");   // B: 9 (mayor)
        List<DashboardChartSegment> top = dashboardService.rotacionProductos("2099-05-01", "2099-05-31", 5);
        List<DashboardChartSegment> mios = top.stream()
                .filter(s -> s.getReferencia().equals(String.valueOf(idItemA))
                        || s.getReferencia().equals(String.valueOf(idItemB)))
                .toList();
        assertEquals(2, mios.size(), "Ambos productos deben estar en el Top.");
        // El de mayor cantidad (B) debe ir antes que A en el orden global.
        int posA = indiceRef(top, String.valueOf(idItemA));
        int posB = indiceRef(top, String.valueOf(idItemB));
        assertTrue(posB < posA, "El producto más vendido (B) debe ir primero.");
    }

    @Test
    void ventasPorItemDevuelveSusVentas() throws SQLException {
        int v = venta("V-ZPL-ITM", "2099-06-06", "0");
        detalle(v, idItemA, "2", "150");
        List<DashboardDrillDownItem> filas = dashboardService.ventasPorItem(idItemA, "2099-06-01", "2099-06-30", 50);
        boolean halla = filas.stream().anyMatch(f -> "V-ZPL-ITM".equals(f.getPrincipal()));
        assertTrue(halla, "El desglose del ítem debe incluir su venta.");
    }

    @Test
    void dashboardSinDatosNoRompe() throws SQLException {
        // Año 2098 sin datos: las consultas de rango devuelven listas vacías y no lanzan excepción.
        assertTrue(dashboardService.ventasPorRango("2098-01-01", "2098-12-31", 50).isEmpty());
        assertTrue(dashboardService.rotacionProductos("2098-01-01", "2098-12-31", 5).isEmpty());
        assertNotNull(dashboardService.stockPorBodega());
        assertNotNull(dashboardService.carteraPorEstado());
    }

    @Test
    void pagosDelDiaSumaPagosYAbonosDeHoy() throws SQLException {
        double antes = dashboardService.pagosDelDia().doubleValue();
        int v = venta("V-ZPL-PAGO", "2099-07-07", "800");
        pago(v, "800");                       // pago de contado con fecha de hoy
        int v2 = venta("V-ZPL-ABO", "2099-07-07", "500");
        cuenta(v2, "500", "PENDIENTE");
        abono(v2, "200");                     // abono con fecha de hoy
        double despues = dashboardService.pagosDelDia().doubleValue();
        assertEquals(1000d, despues - antes, 0.5, "Pagos del día = pagos + abonos de hoy (por delta).");
    }

    @Test
    void cuentasPorEstadoDevuelveLasDelEstado() throws SQLException {
        int v = venta("V-ZPL-VENC", "2099-08-08", "900");
        cuenta(v, "900", "VENCIDA");
        List<DashboardDrillDownItem> filas = dashboardService.cuentasPorEstado("VENCIDA", 50);
        boolean halla = filas.stream().anyMatch(f -> "V-ZPL-VENC".equals(f.getPrincipal()));
        assertTrue(halla, "El desglose de cartera por estado debe incluir la cuenta sembrada.");
    }

    // ---------------- auxiliares ----------------

    private static DashboardChartSegment buscarSegmento(List<DashboardChartSegment> segs, String ref) {
        return segs.stream().filter(s -> ref.equals(s.getReferencia())).findFirst().orElse(null);
    }

    private static double valorEstado(List<DashboardChartSegment> segs, String etiqueta) {
        return segs.stream().filter(s -> etiqueta.equals(s.getEtiqueta()))
                .mapToDouble(DashboardChartSegment::getValor).sum();
    }

    private static int indiceRef(List<DashboardChartSegment> segs, String ref) {
        for (int i = 0; i < segs.size(); i++) {
            if (ref.equals(segs.get(i).getReferencia())) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int item(String codigo) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO items (codigo, nombre, tipo_item, id_categoria, precio_compra, precio_venta, "
                     + "controla_inventario, stock_minimo, estado, fecha_creacion, modo_calculo_servicio, unidad_medida) "
                     + "VALUES (?, ?, 'REPUESTO', 1, 100, 200, 1, 0, 'ACTIVO', date('now'), 'FIJO', 'Unidad')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, "ZPLTEST " + codigo.substring(codigo.length() - 1));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void stock(int idItem, int idBod, double cant) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBod);
            ps.setDouble(3, cant);
            ps.executeUpdate();
        }
    }

    private static int venta(String numero, String fecha, String total) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ventas (numero_venta, id_tercero, fecha, subtotal, descuento, total, estado) "
                     + "VALUES (?, ?, ?, ?, 0, ?, 'PAGADA')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, idCliente);
            ps.setString(3, fecha);
            ps.setDouble(4, Double.parseDouble(total));
            ps.setDouble(5, Double.parseDouble(total));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void detalle(int idVenta, int idItem, String cantidad, String precio) throws SQLException {
        double sub = Double.parseDouble(precio) * Double.parseDouble(cantidad);
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO venta_detalles (id_venta, id_item, cantidad, precio_unitario, descuento_linea, subtotal_linea) "
                     + "VALUES (?, ?, ?, ?, 0, ?)")) {
            ps.setInt(1, idVenta);
            ps.setInt(2, idItem);
            ps.setDouble(3, Double.parseDouble(cantidad));
            ps.setDouble(4, Double.parseDouble(precio));
            ps.setDouble(5, sub);
            ps.executeUpdate();
        }
    }

    private static void cuenta(int idVenta, String saldo, String estado) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO cuentas_por_cobrar (id_venta, id_tercero, valor_total, valor_pagado, "
                     + "saldo_pendiente, estado) VALUES (?, ?, ?, 0, ?, ?)")) {
            ps.setInt(1, idVenta);
            ps.setInt(2, idCliente);
            ps.setDouble(3, Double.parseDouble(saldo));
            ps.setDouble(4, Double.parseDouble(saldo));
            ps.setString(5, estado);
            ps.executeUpdate();
        }
    }

    private static void pago(int idVenta, String valor) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pagos (id_venta, medio_pago, valor, fecha) "
                     + "VALUES (?, 'EFECTIVO', ?, date('now','localtime'))")) {
            ps.setInt(1, idVenta);
            ps.setDouble(2, Double.parseDouble(valor));
            ps.executeUpdate();
        }
    }

    private static void abono(int idVenta, String valor) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO abonos_cuenta (id_cuenta, valor, fecha, medio_pago) "
                     + "VALUES ((SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta=?), ?, "
                     + "date('now','localtime'), 'EFECTIVO')")) {
            ps.setInt(1, idVenta);
            ps.setDouble(2, Double.parseDouble(valor));
            ps.executeUpdate();
        }
    }

    private static int idDe(String sql, String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ventasZ = "SELECT id_venta FROM ventas WHERE numero_venta LIKE 'V-ZPL-%'";
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPLTEST%'";
            st.executeUpdate("DELETE FROM venta_detalle_lotes WHERE id_detalle IN "
                    + "(SELECT id_detalle FROM venta_detalles WHERE id_venta IN (" + ventasZ + "))");
            st.executeUpdate("DELETE FROM abonos_cuenta WHERE id_cuenta IN "
                    + "(SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + "))");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE numero_venta LIKE 'V-ZPL-%'");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM lotes WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPLTEST%'");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_bodega IN "
                    + "(SELECT id_bodega FROM bodegas WHERE nombre LIKE 'ZPLTEST%')");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPLTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPLTEST%'");
        }
    }
}
