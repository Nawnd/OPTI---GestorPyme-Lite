package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoCuenta;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.service.CuentaService;
import com.gestorpyme.service.VentaService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prueba de integracion de cuentas por cobrar:
 * - una venta a credito genera la cuenta con saldo = total y estado PENDIENTE,
 * - los abonos actualizan saldo y estado (ABONADA -> PAGADA),
 * - un abono mayor al saldo es rechazado.
 * Crea datos temporales y los elimina al finalizar.
 */
class CuentaRepositoryTest {

    private final VentaService ventaService = new VentaService();
    private final CuentaService cuentaService = new CuentaService();
    private final CuentaRepository cuentaRepository = new CuentaRepository();
    private final ItemRepository itemRepository = new ItemRepository();
    private final TerceroRepository terceroRepository = new TerceroRepository();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void creditoGeneraCuentaYAbonosActualizanSaldo() throws SQLException {
        int idServicio = 0;
        int idTercero = 0;
        int idVenta = 0;
        try {
            idServicio = crearServicio();
            idTercero = crearCliente();

            // Venta a credito de 1000 (servicio, sin inventario)
            Venta venta = new Venta();
            venta.setIdTercero(idTercero);
            ventaService.registrarVenta(venta,
                    Collections.singletonList(linea(idServicio, "1", "1000")),
                    0, false, "2026-12-31", null);
            idVenta = venta.getIdVenta();

            CuentaPorCobrar cuenta = buscarCuentaPorVenta(idVenta);
            assertNotNull(cuenta, "La venta a credito debe generar una cuenta por cobrar");
            assertEquals(0, new BigDecimal("1000").compareTo(cuenta.getSaldoPendiente()),
                    "El saldo inicial debe ser igual al total");
            assertEquals(EstadoCuenta.PENDIENTE, cuenta.getEstado(), "El estado inicial debe ser PENDIENTE");

            int idCuenta = cuenta.getIdCuenta();

            // Abono parcial de 400 -> saldo 600, estado ABONADA (parcial)
            cuentaService.registrarAbono(abono(idCuenta, "400"));
            CuentaPorCobrar tras1 = buscarCuentaPorId(idCuenta);
            assertEquals(0, new BigDecimal("600").compareTo(tras1.getSaldoPendiente()),
                    "Tras abonar 400, el saldo debe ser 600");
            assertEquals(EstadoCuenta.ABONADA, tras1.getEstado(), "Con saldo pendiente debe quedar ABONADA (parcial)");

            // Abono final de 600 -> saldo 0, estado PAGADA
            cuentaService.registrarAbono(abono(idCuenta, "600"));
            CuentaPorCobrar tras2 = buscarCuentaPorId(idCuenta);
            assertEquals(0, BigDecimal.ZERO.compareTo(tras2.getSaldoPendiente()),
                    "Tras abonar el resto, el saldo debe ser 0");
            assertEquals(EstadoCuenta.PAGADA, tras2.getEstado(), "Sin saldo debe quedar PAGADA");

            // Un abono adicional (saldo 0) debe ser rechazado
            final int idCuentaFinal = idCuenta;
            assertThrows(ValidacionException.class, () -> cuentaService.registrarAbono(abono(idCuentaFinal, "1")),
                    "No se debe permitir abonar por encima del saldo");
        } finally {
            limpiar(idVenta, idServicio, idTercero);
        }
    }

    private VentaDetalle linea(int idItem, String cantidad, String precio) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setControlaInventario(false);
        d.setCantidad(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoLinea(BigDecimal.ZERO);
        return d;
    }

    private Abono abono(int idCuenta, String valor) {
        Abono a = new Abono();
        a.setIdCuenta(idCuenta);
        a.setValor(new BigDecimal(valor));
        a.setMedioPago(MedioPago.EFECTIVO);
        return a;
    }

    private CuentaPorCobrar buscarCuentaPorVenta(int idVenta) throws SQLException {
        for (CuentaPorCobrar c : cuentaRepository.listar()) {
            if (c.getIdVenta() == idVenta) {
                return c;
            }
        }
        return null;
    }

    private CuentaPorCobrar buscarCuentaPorId(int idCuenta) throws SQLException {
        for (CuentaPorCobrar c : cuentaRepository.listar()) {
            if (c.getIdCuenta() == idCuenta) {
                return c;
            }
        }
        return null;
    }

    private int crearServicio() throws SQLException {
        Item it = new Item();
        it.setNombre("PRUEBA Servicio Cartera");
        it.setTipoItem(TipoItem.SERVICIO);
        it.setControlaInventario(false);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setEstado(EstadoRegistro.ACTIVO);
        return itemRepository.insertar(it);
    }

    private int crearCliente() throws SQLException {
        Tercero t = new Tercero();
        t.setNombre("PRUEBA Cliente Cartera");
        t.setTipoTercero(TipoTercero.CLIENTE);
        t.setEstado(EstadoRegistro.ACTIVO);
        return terceroRepository.insertar(t);
    }

    private void limpiar(int idVenta, int idServicio, int idTercero) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (idVenta > 0) {
                // Primero los abonos de la(s) cuenta(s) de esta venta.
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM abonos_cuenta WHERE id_cuenta IN "
                                + "(SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta = ?)")) {
                    ps.setInt(1, idVenta);
                    ps.executeUpdate();
                }
                ejecutar(conn, "DELETE FROM cuentas_por_cobrar WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM pagos WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM venta_detalles WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM ventas WHERE id_venta = ?", idVenta);
            }
            if (idServicio > 0) {
                ejecutar(conn, "DELETE FROM items WHERE id_item = ?", idServicio);
            }
            if (idTercero > 0) {
                ejecutar(conn, "DELETE FROM terceros WHERE id_tercero = ?", idTercero);
            }
        }
    }

    private void ejecutar(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
