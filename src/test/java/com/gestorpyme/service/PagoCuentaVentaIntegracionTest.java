package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Pago;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.repository.TerceroRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba de integracion del flujo real del modulo Pagos sobre ventas a credito
 * (v0.7.1). A diferencia de {@link CoherenciaFinancieraTest} (que ejercita la ruta
 * de abonos via CuentaService), aqui se invoca {@link PagoService#registrarPago},
 * que es el punto de entrada que usa la interfaz de Pagos.
 *
 * <p>Verifica que cuando la venta tiene cuenta por cobrar, el cobro desde Pagos:
 * reduce el saldo de la cuenta, queda registrado como abono (no como pago), no se
 * duplica en la tabla pagos, y sincroniza el estado de la venta al saldarse. Tambien
 * verifica que un cobro mayor al saldo se rechaza sin alterar nada.</p>
 *
 * <p>Datos marcados con el prefijo "ZBPAGOTEST"; se eliminan al finalizar.</p>
 */
class PagoCuentaVentaIntegracionTest {

    private static final VentaService ventaService = new VentaService();
    private static final CuentaService cuentaService = new CuentaService();
    private static final PagoService pagoService = new PagoService(); // cablea CuentaService internamente
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();

    private static int idCliente;
    private static int idItem;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();

        Tercero cli = new Tercero();
        cli.setNombre("ZBPAGOTEST Cliente Pagos");
        cli.setTipoTercero(TipoTercero.CLIENTE);
        cli.setEstado(EstadoRegistro.ACTIVO);
        idCliente = terceroRepo.insertar(cli);

        // Item de servicio (no controla inventario): la venta no requiere stock.
        Item it = new Item();
        it.setCodigo("ZBPAGOTEST-SRV");
        it.setNombre("ZBPAGOTEST Servicio");
        it.setTipoItem(TipoItem.SERVICIO);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(false);
        it.setStockMinimo(BigDecimal.ZERO);
        it.setEstado(EstadoRegistro.ACTIVO);
        idItem = itemRepo.insertar(it);
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    /** Crea una venta a credito por el total indicado y devuelve la venta (con id). */
    private Venta crearVentaCredito(String total) throws SQLException {
        Venta v = new Venta();
        v.setIdTercero(idCliente);
        v.setIdUsuario(1);
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setNombreItem("ZBPAGOTEST Servicio");
        d.setControlaInventario(false);
        d.setCantidad(BigDecimal.ONE);
        d.setPrecioUnitario(new BigDecimal(total));
        d.setDescuentoLinea(BigDecimal.ZERO);
        d.setSubtotalLinea(new BigDecimal(total));
        List<VentaDetalle> det = new ArrayList<>();
        det.add(d);
        ventaService.registrarVenta(v, det, 0, false, null, null);
        return v;
    }

    /** Simula el cobro desde el modulo Pagos (mismo punto de entrada que la UI). */
    private void cobrarDesdePagos(int idVenta, String valor, BigDecimal saldoMostrado) throws SQLException {
        Pago p = new Pago();
        p.setIdVenta(idVenta);
        p.setValor(new BigDecimal(valor));
        p.setMedioPago(MedioPago.EFECTIVO);
        p.setObservaciones("ZBPAGOTEST cobro desde Pagos");
        pagoService.registrarPago(p, saldoMostrado);
    }

    @Test
    void pagoParcialDesdePagosRegistraAbonoYNoDuplica() throws SQLException {
        Venta v = crearVentaCredito("1000");
        int idCuenta = idCuentaDe(v.getIdVenta());

        // Cobro parcial desde Pagos (saldo mostrado = 1000).
        cobrarDesdePagos(v.getIdVenta(), "400", new BigDecimal("1000"));

        // La cuenta baja su saldo; ese es el saldo que Pagos muestra (misma fuente).
        assertEquals(600.0, saldoCuenta(idCuenta), 0.0001);
        assertEquals("ABONADA", estadoCuenta(idCuenta));
        // La venta sigue pendiente.
        assertEquals("PENDIENTE_PAGO", estadoVenta(v.getIdVenta()));
        // Se registro como abono, no como pago (sin doble conteo en la tabla pagos).
        assertEquals(1, cuentaService.listarAbonos(idCuenta).size());
        assertEquals(0.0, pagoService.totalPagado(v.getIdVenta()).doubleValue(), 0.0001);
    }

    @Test
    void pagoTotalDesdePagosDejaCuentaYVentaPagadas() throws SQLException {
        Venta v = crearVentaCredito("1000");
        int idCuenta = idCuentaDe(v.getIdVenta());

        cobrarDesdePagos(v.getIdVenta(), "400", new BigDecimal("1000"));
        cobrarDesdePagos(v.getIdVenta(), "600", new BigDecimal("600")); // completa el saldo

        assertEquals(0.0, saldoCuenta(idCuenta), 0.0001);
        assertEquals("PAGADA", estadoCuenta(idCuenta));
        // Sin contradiccion: la venta tambien queda PAGADA.
        assertEquals("PAGADA", estadoVenta(v.getIdVenta()));
        // Dos abonos, cero pagos (no se duplico el dinero).
        assertEquals(2, cuentaService.listarAbonos(idCuenta).size());
        assertEquals(0.0, pagoService.totalPagado(v.getIdVenta()).doubleValue(), 0.0001);
    }

    @Test
    void pagoMayorAlSaldoSeRechazaSinCambios() throws SQLException {
        Venta v = crearVentaCredito("1000");
        int idCuenta = idCuentaDe(v.getIdVenta());

        assertThrows(ValidacionException.class,
                () -> cobrarDesdePagos(v.getIdVenta(), "1500", new BigDecimal("1000")));

        // Nada cambio: saldo intacto, venta pendiente, sin abonos ni pagos.
        assertEquals(1000.0, saldoCuenta(idCuenta), 0.0001);
        assertEquals("PENDIENTE_PAGO", estadoVenta(v.getIdVenta()));
        assertEquals(0, cuentaService.listarAbonos(idCuenta).size());
        assertEquals(0.0, pagoService.totalPagado(v.getIdVenta()).doubleValue(), 0.0001);
    }

    // ---- consultas auxiliares ----

    private static int idCuentaDe(int idVenta) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta = ?")) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static String estadoVenta(int idVenta) throws SQLException {
        return unString("SELECT estado FROM ventas WHERE id_venta = ?", idVenta);
    }

    private static String estadoCuenta(int idCuenta) throws SQLException {
        return unString("SELECT estado FROM cuentas_por_cobrar WHERE id_cuenta = ?", idCuenta);
    }

    private static double saldoCuenta(int idCuenta) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT saldo_pendiente FROM cuentas_por_cobrar WHERE id_cuenta = ?")) {
            ps.setInt(1, idCuenta);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : -1;
            }
        }
    }

    private static String unString(String sql, int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ventasZ = "SELECT id_venta FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBPAGOTEST%')";
            st.executeUpdate("DELETE FROM abonos_cuenta WHERE id_cuenta IN "
                    + "(SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + "))");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBPAGOTEST%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBPAGOTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZBPAGOTEST%'");
        }
    }
}
