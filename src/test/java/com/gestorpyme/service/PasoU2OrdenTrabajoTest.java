package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.OrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajoRepuesto;
import com.gestorpyme.domain.model.OrdenTrabajoServicio;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paso U.2 — Orden de Trabajo como documento de trabajo. Marcador ZPU2TEST.
 *
 * Garantiza el esquema con {@link DatabaseInitializer#initialize()} (crea las tablas de OT de forma
 * idempotente). Crea datos de prueba propios (cliente, vehiculo, item servicio, item producto
 * inventariable, bodega) y limpia en @AfterAll en orden FK-seguro. Verifica que la OT NO descuenta
 * inventario ni crea venta. No toca VentaService, inventario, lotes, Kardex ni FEFO.
 */
public class PasoU2OrdenTrabajoTest {

    private static final OrdenTrabajoService service = new OrdenTrabajoService();

    private static int idCliente;
    private static int idOtroCliente;
    private static int idVehiculo;
    private static int idServicio;   // item tipo SERVICIO
    private static int idProducto;   // item PRODUCTO inventariable
    private static int idBodega;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiar(c);
            idCliente = insertarCliente(c, "ZPU2 Cliente");
            idOtroCliente = insertarCliente(c, "ZPU2 Otro");
            idVehiculo = insertarVehiculo(c, idCliente, "ZPU2-001");
            idServicio = insertarItem(c, "ZPU2S", "ZPU2 Servicio", "SERVICIO", 0, 50000);
            idProducto = insertarItem(c, "ZPU2P", "ZPU2 Repuesto", "PRODUCTO", 1, 20000);
            idBodega = insertarBodega(c, "ZPU2 Bodega");
        }
    }

    @AfterAll
    static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiar(c);
        }
    }

    @Test
    void crearOtValidaConDetalleYTotales() throws Exception {
        OrdenTrabajo ot = base();
        ot.getServicios().add(servicio(idServicio, "1", "50000"));
        ot.getRepuestos().add(repuesto(idProducto, idBodega, "2", "20000"));
        service.guardar(ot);

        assertTrue(ot.getIdOrdenTrabajo() > 0, "Debe asignarse id");
        assertNotNull(ot.getNumeroOt());
        assertTrue(ot.getNumeroOt().startsWith("OT-"), "Numero con prefijo OT-");

        Optional<OrdenTrabajo> recargada = service.buscarConDetalles(ot.getIdOrdenTrabajo());
        assertTrue(recargada.isPresent());
        OrdenTrabajo r = recargada.get();
        assertEquals(1, r.getServicios().size(), "Un servicio");
        assertEquals(1, r.getRepuestos().size(), "Un repuesto");
        assertEquals(0, new BigDecimal("50000").compareTo(r.getSubtotalServicios()), "Subtotal servicios");
        assertEquals(0, new BigDecimal("40000").compareTo(r.getSubtotalRepuestos()), "Subtotal repuestos");
        assertEquals(0, new BigDecimal("90000").compareTo(r.getTotal()), "Total = 90000");
    }

    @Test
    void numeroOtConsecutivo() throws Exception {
        OrdenTrabajo a = base();
        service.guardar(a);
        OrdenTrabajo b = base();
        service.guardar(b);
        int na = Integer.parseInt(a.getNumeroOt().substring(3));
        int nb = Integer.parseInt(b.getNumeroOt().substring(3));
        assertEquals(na + 1, nb, "El consecutivo debe incrementar de a uno");
    }

    @Test
    void rechazaSinCliente() {
        OrdenTrabajo ot = base();
        ot.setIdTercero(0);
        assertThrows(ValidacionException.class, () -> service.guardar(ot));
    }

    @Test
    void rechazaSinVehiculo() {
        OrdenTrabajo ot = base();
        ot.setIdVehiculo(0);
        assertThrows(ValidacionException.class, () -> service.guardar(ot));
    }

    @Test
    void rechazaVehiculoQueNoPerteneceAlCliente() {
        OrdenTrabajo ot = base();
        ot.setIdTercero(idOtroCliente); // el vehiculo es del primer cliente
        assertThrows(ValidacionException.class, () -> service.guardar(ot));
    }

    @Test
    void rechazaServicioConItemNoPermitido() {
        OrdenTrabajo ot = base();
        ot.getServicios().add(servicio(idProducto, "1", "1000")); // un PRODUCTO no es servicio
        assertThrows(ValidacionException.class, () -> service.guardar(ot));
    }

    @Test
    void rechazaRepuestoNoInventariable() {
        OrdenTrabajo ot = base();
        ot.getRepuestos().add(repuesto(idServicio, idBodega, "1", "1000")); // un SERVICIO no es inventariable
        assertThrows(ValidacionException.class, () -> service.guardar(ot));
    }

    @Test
    void cancelarYNoPermitirEditar() throws Exception {
        OrdenTrabajo ot = base();
        service.guardar(ot);
        service.cambiarEstado(ot.getIdOrdenTrabajo(), EstadoOrdenTrabajo.CANCELADA);

        Optional<OrdenTrabajo> recargada = service.buscarConDetalles(ot.getIdOrdenTrabajo());
        assertTrue(recargada.isPresent());
        assertEquals(EstadoOrdenTrabajo.CANCELADA, recargada.get().getEstado());

        OrdenTrabajo edit = recargada.get();
        edit.setMotivoIngreso("intento de edicion");
        assertThrows(ValidacionException.class, () -> service.guardar(edit),
                "No debe poder editarse una OT cancelada");
    }

    @Test
    void noModificaInventarioNiCreaVenta() throws Exception {
        BigDecimal stockAntes = sumaStock();
        int ventasAntes = contar("ventas");

        OrdenTrabajo ot = base();
        ot.getRepuestos().add(repuesto(idProducto, idBodega, "3", "20000"));
        service.guardar(ot);

        assertEquals(0, stockAntes.compareTo(sumaStock()), "El inventario_stock no debe cambiar");
        assertEquals(ventasAntes, contar("ventas"), "No se debe crear ninguna venta");
    }

    // ---------------------------------------------------------------- helpers de datos

    private static OrdenTrabajo base() {
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.setIdTercero(idCliente);
        ot.setIdVehiculo(idVehiculo);
        ot.setKilometrajeIngreso(10000);
        ot.setMotivoIngreso("ZPU2 mantenimiento");
        ot.setServicios(new ArrayList<>());
        ot.setRepuestos(new ArrayList<>());
        return ot;
    }

    private static OrdenTrabajoServicio servicio(int idItem, String cant, String precio) {
        OrdenTrabajoServicio s = new OrdenTrabajoServicio();
        s.setIdItem(idItem);
        s.setCantidad(new BigDecimal(cant));
        s.setPrecioUnitario(new BigDecimal(precio));
        return s;
    }

    private static OrdenTrabajoRepuesto repuesto(int idItem, int idBod, String cant, String precio) {
        OrdenTrabajoRepuesto r = new OrdenTrabajoRepuesto();
        r.setIdItem(idItem);
        r.setIdBodegaSalida(idBod);
        r.setCantidad(new BigDecimal(cant));
        r.setPrecioUnitario(new BigDecimal(precio));
        return r;
    }

    private static BigDecimal sumaStock() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT IFNULL(SUM(cantidad),0) FROM inventario_stock")) {
            return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
        }
    }

    private static int contar(String tabla) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int insertarCliente(Connection c, String nombre) throws SQLException {
        return insertarConId(c, "INSERT INTO terceros (tipo_tercero, nombre, estado, fecha_creacion) "
                + "VALUES ('CLIENTE', '" + nombre + "', 'ACTIVO', datetime('now','localtime'))");
    }

    private static int insertarVehiculo(Connection c, int idTercero, String placa) throws SQLException {
        return insertarConId(c, "INSERT INTO vehiculos (id_tercero, placa, estado) VALUES ("
                + idTercero + ", '" + placa + "', 'ACTIVO')");
    }

    private static int insertarItem(Connection c, String codigo, String nombre, String tipo,
                                    int controla, double precioVenta) throws SQLException {
        return insertarConId(c, "INSERT INTO items "
                + "(codigo, nombre, tipo_item, precio_compra, precio_venta, controla_inventario, "
                + "stock_minimo, estado, fecha_creacion, modo_calculo_servicio, unidad_medida) VALUES ("
                + "'" + codigo + "', '" + nombre + "', '" + tipo + "', 0, " + precioVenta + ", "
                + controla + ", 0, 'ACTIVO', datetime('now','localtime'), 'FIJO', 'Unidad')");
    }

    private static int insertarBodega(Connection c, String nombre) throws SQLException {
        return insertarConId(c, "INSERT INTO bodegas (nombre, estado, fecha_creacion) VALUES ('"
                + nombre + "', 'ACTIVO', datetime('now','localtime'))");
    }

    private static int insertarConId(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se pudo insertar: " + sql);
    }

    /** Limpia el detalle y las OT de los clientes de prueba, y luego los datos maestros ZPU2. */
    private static void limpiar(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            String otsTest = "SELECT id_orden_trabajo FROM ordenes_trabajo WHERE id_tercero IN "
                           + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZPU2%')";
            st.executeUpdate("DELETE FROM orden_trabajo_servicios WHERE id_orden_trabajo IN (" + otsTest + ")");
            st.executeUpdate("DELETE FROM orden_trabajo_repuestos WHERE id_orden_trabajo IN (" + otsTest + ")");
            st.executeUpdate("DELETE FROM ordenes_trabajo WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZPU2%')");
            st.executeUpdate("DELETE FROM vehiculos WHERE placa LIKE 'ZPU2%'");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPU2%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPU2%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPU2%'");
        }
    }
}
