package com.gestorpyme.service;

import com.gestorpyme.domain.model.DashboardComparativoMeta;
import com.gestorpyme.domain.model.DashboardEjecutivoResumen;
import com.gestorpyme.domain.model.DashboardFinancieroResumen;
import com.gestorpyme.domain.model.DashboardMeta;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.util.PeriodoDashboard;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso K (Dashboard Ejecutivo 360 + metas): cálculo de períodos y semanas operativas, KPIs
 * financieros por período, utilidad/margen estimados con exclusión de líneas sin costo, metas y real vs
 * meta, ceros seguros y migración idempotente. Año centinela 2099 + marcador "ZPKTEST" (aislamiento).
 */
class PasoKDashboardEjecutivoTest {

    private static final DashboardService dashboardService = new DashboardService();
    private static int idCliente;
    private static int idBodega;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idCliente = idDe("INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'CLIENTE', 'ACTIVO')", "ZPKTEST Cliente");
        idBodega = idDe("INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", "ZPKTEST Bodega");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Períodos (cálculo puro) ----------------

    @Test
    void periodoMensual() {
        PeriodoDashboard p = PeriodoDashboard.de(2026, 6, null);
        assertEquals("2026-06-01", p.getFechaInicio());
        assertEquals("2026-06-30", p.getFechaFin());
        assertEquals("06/2026", p.getEtiqueta());
    }

    @Test
    void semana1() {
        PeriodoDashboard p = PeriodoDashboard.de(2026, 6, 1);
        assertEquals("2026-06-01", p.getFechaInicio());
        assertEquals("2026-06-07", p.getFechaFin());
    }

    @Test
    void semana2() {
        PeriodoDashboard p = PeriodoDashboard.de(2026, 6, 2);
        assertEquals("2026-06-08", p.getFechaInicio());
        assertEquals("2026-06-14", p.getFechaFin());
    }

    @Test
    void semana3() {
        PeriodoDashboard p = PeriodoDashboard.de(2026, 6, 3);
        assertEquals("2026-06-15", p.getFechaInicio());
        assertEquals("2026-06-21", p.getFechaFin());
    }

    @Test
    void semana4HastaFinDeMes() {
        assertEquals("2026-06-30", PeriodoDashboard.de(2026, 6, 4).getFechaFin());
        // Febrero 2026 (28 días): la semana 4 llega al día 28.
        assertEquals("2026-02-22", PeriodoDashboard.de(2026, 2, 4).getFechaInicio());
        assertEquals("2026-02-28", PeriodoDashboard.de(2026, 2, 4).getFechaFin());
    }

    // ---------------- KPIs financieros por período ----------------

    @Test
    void ventasDelPeriodo() throws SQLException {
        insertarVenta("V-ZPK-V1", "2099-06-03", "1000", "0", false);
        insertarVenta("V-ZPK-V2", "2099-06-20", "500", "0", false);
        DashboardFinancieroResumen f = dashboardService.resumenEjecutivo(2099, 6, null).getFinanciero();
        assertEquals(0, new BigDecimal("1500").compareTo(f.getVentas()), "Ventas del mes = 1500.");
        assertEquals(2, f.getCantidadVentas());
    }

    @Test
    void filtroPorSemanaAislaVentas() throws SQLException {
        insertarVenta("V-ZPK-S1", "2099-07-03", "300", "0", false); // semana 1
        insertarVenta("V-ZPK-S3", "2099-07-17", "700", "0", false); // semana 3
        DashboardFinancieroResumen sem1 = dashboardService.resumenEjecutivo(2099, 7, 1).getFinanciero();
        assertEquals(0, new BigDecimal("300").compareTo(sem1.getVentas()), "La semana 1 solo incluye su venta.");
        assertEquals(1, sem1.getCantidadVentas());
    }

    @Test
    void ticketPromedio() {
        assertEquals(0, new BigDecimal("250.00").compareTo(
                dashboardService.calcularTicket(new BigDecimal("1000"), 4)));
    }

    @Test
    void descuentosDelPeriodo() throws SQLException {
        insertarVenta("V-ZPK-D1", "2099-08-05", "900", "100", false);
        DashboardFinancieroResumen f = dashboardService.resumenEjecutivo(2099, 8, null).getFinanciero();
        assertEquals(0, new BigDecimal("100").compareTo(f.getDescuentos()));
    }

    @Test
    void utilidadEstimadaConCostoValido() throws SQLException {
        int item = item("ZPKTEST-UTIL", "600", true);          // costo 600
        int v = insertarVenta("V-ZPK-U1", "2099-09-10", "2000", "0", false);
        insertarDetalle(v, item, "2", "1000", "0");            // (1000-600)*2 = 800; base = 2000
        DashboardFinancieroResumen f = dashboardService.resumenEjecutivo(2099, 9, null).getFinanciero();
        assertEquals(0, new BigDecimal("800").compareTo(f.getUtilidadEstimada()), "Utilidad estimada = 800.");
        assertEquals(0, new BigDecimal("2000").compareTo(f.getBaseVentasConCosto()), "Base con costo = 2000.");
        assertEquals(1, f.getLineasConCosto());
        assertEquals(0, f.getLineasSinCosto());
    }

    @Test
    void lineasSinCostoSeExcluyen() throws SQLException {
        int item = item("ZPKTEST-NOCOST", "0", true);          // costo 0 -> excluida
        int v = insertarVenta("V-ZPK-NC", "2099-10-10", "1000", "0", false);
        insertarDetalle(v, item, "1", "1000", "0");
        DashboardFinancieroResumen f = dashboardService.resumenEjecutivo(2099, 10, null).getFinanciero();
        assertEquals(0, BigDecimal.ZERO.compareTo(f.getUtilidadEstimada()), "Sin costo válido, utilidad = 0.");
        assertEquals(1, f.getLineasSinCosto(), "La línea sin costo se cuenta como excluida.");
        assertFalse(f.isMargenDisponible(), "Sin base con costo, el margen no está disponible.");
    }

    @Test
    void margenEvitaDivisionPorCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(
                dashboardService.calcularMargen(new BigDecimal("500"), BigDecimal.ZERO)),
                "Margen con base 0 debe ser 0, sin excepción.");
    }

    // ---------------- Metas y real vs meta ----------------

    @Test
    void metasMensualesSeGuardanYConsultan() throws SQLException {
        DashboardMeta m = new DashboardMeta();
        m.setAnio(2099); m.setMes(11);
        m.setMetaVentas(new BigDecimal("5000"));
        m.setComentario("Meta noviembre");
        dashboardService.guardarMeta(m);
        DashboardMeta leida = dashboardService.buscarMeta(2099, 11, null);
        assertNotNull(leida);
        assertEquals(0, new BigDecimal("5000").compareTo(leida.getMetaVentas()));
    }

    @Test
    void metasAnualesSeGuardanYConsultan() throws SQLException {
        DashboardMeta m = new DashboardMeta();
        m.setAnio(2099); m.setMes(null); // año completo
        m.setMetaVentas(new BigDecimal("60000"));
        dashboardService.guardarMeta(m);
        DashboardMeta leida = dashboardService.buscarMeta(2099, null, null);
        assertNotNull(leida, "La meta anual (mes null) debe consultarse.");
        assertEquals(0, new BigDecimal("60000").compareTo(leida.getMetaVentas()));
    }

    @Test
    void realVsMetaNoModificaDatosReales() throws SQLException {
        insertarVenta("V-ZPK-RM", "2099-12-05", "1000", "0", false);
        DashboardMeta m = new DashboardMeta();
        m.setAnio(2099); m.setMes(12);
        m.setMetaVentas(new BigDecimal("2000"));
        dashboardService.guardarMeta(m);
        DashboardComparativoMeta c = dashboardService.resumenEjecutivo(2099, 12, null).getComparativoMeta();
        assertTrue(c.isHayMeta());
        assertEquals(0.5, c.getCumplimientoVentas(), 0.0001, "1000/2000 = 0.5.");
        // Los datos reales no cambian por comparar.
        assertEquals(0, new BigDecimal("1000").compareTo(totalVenta("V-ZPK-RM")), "La venta real no se modifica.");
    }

    // ---------------- Robustez ----------------

    @Test
    void dashboardSinDatosDevuelveCerosSeguros() throws SQLException {
        DashboardEjecutivoResumen r = dashboardService.resumenEjecutivo(2098, 1, null); // año vacío
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getFinanciero().getVentas()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getFinanciero().getTicketPromedio()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getFinanciero().getMargenEstimado()));
        assertEquals(0, r.getFinanciero().getCantidadVentas());
    }

    @Test
    void kpisInventarioNoRompen() throws SQLException {
        assertNotNull(dashboardService.resumenEjecutivo(2098, 1, null).getInventario());
    }

    @Test
    void kpisComprasNoRompen() throws SQLException {
        assertNotNull(dashboardService.resumenEjecutivo(2098, 1, null).getCompras());
    }

    @Test
    void kpisLotesNoRompen() throws SQLException {
        assertNotNull(dashboardService.resumenEjecutivo(2098, 1, null).getLotes());
    }

    @Test
    void migracionDashboardMetasIdempotente() throws SQLException {
        DatabaseInitializer.initialize(); // segunda corrida no debe fallar
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='dashboard_metas'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "La tabla dashboard_metas debe existir.");
        }
    }

    // ---------------- auxiliares ----------------

    private static int item(String codigo, String precioCompra, boolean inv) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO items (codigo, nombre, tipo_item, id_categoria, precio_compra, precio_venta, "
                     + "controla_inventario, stock_minimo, estado, fecha_creacion, modo_calculo_servicio) "
                     + "VALUES (?, ?, 'REPUESTO', 1, ?, 1000, ?, 0, 'ACTIVO', date('now'), 'FIJO')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, "ZPKTEST " + codigo);
            ps.setDouble(3, new BigDecimal(precioCompra).doubleValue());
            ps.setInt(4, inv ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int insertarVenta(String numero, String fecha, String total, String descuento, boolean conCuenta)
            throws SQLException {
        int idVenta;
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ventas (numero_venta, id_tercero, fecha, subtotal, descuento, total, estado) "
                     + "VALUES (?, ?, ?, ?, ?, ?, 'PAGADA')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, idCliente);
            ps.setString(3, fecha);
            ps.setDouble(4, new BigDecimal(total).doubleValue());
            ps.setDouble(5, new BigDecimal(descuento).doubleValue());
            ps.setDouble(6, new BigDecimal(total).doubleValue());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                idVenta = rs.next() ? rs.getInt(1) : -1;
            }
        }
        if (conCuenta) {
            try (Connection c = DatabaseConnection.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO cuentas_por_cobrar (id_venta, id_tercero, valor_total, valor_pagado, "
                         + "saldo_pendiente, estado) VALUES (?, ?, ?, 0, ?, 'PENDIENTE')")) {
                ps.setInt(1, idVenta);
                ps.setInt(2, idCliente);
                ps.setDouble(3, new BigDecimal(total).doubleValue());
                ps.setDouble(4, new BigDecimal(total).doubleValue());
                ps.executeUpdate();
            }
        }
        return idVenta;
    }

    private static void insertarDetalle(int idVenta, int idItem, String cantidad, String precio, String descuento)
            throws SQLException {
        BigDecimal sub = new BigDecimal(precio).multiply(new BigDecimal(cantidad)).subtract(new BigDecimal(descuento));
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO venta_detalles (id_venta, id_item, cantidad, precio_unitario, descuento_linea, subtotal_linea) "
                     + "VALUES (?, ?, ?, ?, ?, ?)")) {
            ps.setInt(1, idVenta);
            ps.setInt(2, idItem);
            ps.setDouble(3, new BigDecimal(cantidad).doubleValue());
            ps.setDouble(4, new BigDecimal(precio).doubleValue());
            ps.setDouble(5, new BigDecimal(descuento).doubleValue());
            ps.setDouble(6, sub.doubleValue());
            ps.executeUpdate();
        }
    }

    private static BigDecimal totalVenta(String numero) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT total FROM ventas WHERE numero_venta=?")) {
            ps.setString(1, numero);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
            }
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
            String ventasZ = "SELECT id_venta FROM ventas WHERE numero_venta LIKE 'V-ZPK-%'";
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPKTEST%'";
            st.executeUpdate("DELETE FROM venta_detalle_lotes WHERE id_detalle IN "
                    + "(SELECT id_detalle FROM venta_detalles WHERE id_venta IN (" + ventasZ + "))");
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE numero_venta LIKE 'V-ZPK-%'");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM lotes WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPKTEST%'");
            st.executeUpdate("DELETE FROM dashboard_metas WHERE anio IN (2098, 2099)");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPKTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPKTEST%'");
        }
    }
}
