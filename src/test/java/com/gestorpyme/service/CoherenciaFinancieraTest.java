package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.repository.TerceroRepository;
import com.gestorpyme.repository.VentaRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coherencia financiera entre Ventas y Cuentas por cobrar (Paso B.1):
 *  - venta a credito con abono parcial: cuenta ABONADA, venta sigue PENDIENTE_PAGO;
 *  - venta a credito totalmente abonada: cuenta PAGADA y venta PAGADA (sin contradiccion).
 * Tambien valida la busqueda de ventas (VentaRepository.buscar) por consecutivo y por cliente.
 * Datos marcados "ZBFINTEST"; se eliminan al final.
 */
class CoherenciaFinancieraTest {

    private static final VentaService ventaService = new VentaService();
    private static final CuentaService cuentaService = new CuentaService();
    private static final VentaRepository ventaRepo = new VentaRepository();
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();

    private static int idCliente;
    private static int idItem;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();

        Tercero cli = new Tercero();
        cli.setNombre("ZBFINTEST Cliente Fin");
        cli.setTipoTercero(TipoTercero.CLIENTE);
        cli.setEstado(EstadoRegistro.ACTIVO);
        idCliente = terceroRepo.insertar(cli);

        // Item de servicio (no controla inventario): la venta no necesita stock.
        Item it = new Item();
        it.setCodigo("ZBFINTEST-SRV");
        it.setNombre("ZBFINTEST Servicio");
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
        d.setNombreItem("ZBFINTEST Servicio");
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

    private void abonar(int idCuenta, String valor) throws SQLException {
        Abono a = new Abono();
        a.setIdCuenta(idCuenta);
        a.setValor(new BigDecimal(valor));
        a.setMedioPago(MedioPago.EFECTIVO);
        cuentaService.registrarAbono(a);
    }

    @Test
    void abonoParcialMantieneVentaPendiente() throws SQLException {
        Venta v = crearVentaCredito("1000");
        int idCuenta = idCuentaDe(v.getIdVenta());

        abonar(idCuenta, "400");

        assertEquals("PENDIENTE_PAGO", estadoVenta(v.getIdVenta()));
        assertEquals("ABONADA", estadoCuenta(idCuenta));
        assertEquals(600.0, saldoCuenta(idCuenta), 0.0001);
    }

    @Test
    void abonoTotalSaldaCuentaYVenta() throws SQLException {
        Venta v = crearVentaCredito("1000");
        int idCuenta = idCuentaDe(v.getIdVenta());

        abonar(idCuenta, "400");
        abonar(idCuenta, "600"); // completa el saldo

        assertEquals(0.0, saldoCuenta(idCuenta), 0.0001);
        assertEquals("PAGADA", estadoCuenta(idCuenta));
        // Sin contradiccion: la venta tambien queda PAGADA.
        assertEquals("PAGADA", estadoVenta(v.getIdVenta()));
    }

    @Test
    void busquedaDeVentaPorConsecutivoYPorCliente() throws SQLException {
        Venta v = crearVentaCredito("1000");

        List<Venta> porNumero = ventaRepo.buscar(v.getNumeroVenta(), 50);
        assertTrue(porNumero.stream().anyMatch(x -> x.getIdVenta() == v.getIdVenta()),
                "Debe encontrar la venta por su consecutivo.");

        List<Venta> porCliente = ventaRepo.buscar("ZBFINTEST", 50);
        assertTrue(porCliente.stream().anyMatch(x -> x.getIdVenta() == v.getIdVenta()),
                "Debe encontrar la venta por el nombre del cliente.");
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
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBFINTEST%')";
            st.executeUpdate("DELETE FROM abonos_cuenta WHERE id_cuenta IN "
                    + "(SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + "))");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBFINTEST%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBFINTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZBFINTEST%'");
        }
    }
}
