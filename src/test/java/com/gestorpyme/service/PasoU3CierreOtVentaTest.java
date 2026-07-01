package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.LoteRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso U.3: cierre de una Orden de Trabajo a venta real.
 *
 * <p>Verifican que {@link OrdenTrabajoService#cerrarYFacturar} DELEGA correctamente en
 * {@link VentaService} (venta, descuento de stock, FEFO, Kardex, pago/cartera) y que enlaza la OT
 * con la venta (estado ENTREGADA + id_venta). Tambien comprueban las validaciones de cierre
 * (cancelada, ya facturada, sin lineas) y que un fallo por stock insuficiente deja la OT intacta.</p>
 *
 * <p>Aislamiento: clientes/items/bodegas/vehiculos marcados con {@code ZPU3} y limpieza FK-segura en
 * {@link #limpiar()} (incluye las ventas, cartera, pagos, Kardex y lotes generados por los cierres).
 * El esquema se asegura con {@link DatabaseInitializer#initialize()} (las pruebas no arrancan la app).</p>
 */
class PasoU3CierreOtVentaTest {

    private static final String MARCA = "ZPU3";

    private static final OrdenTrabajoService otService = new OrdenTrabajoService();
    private static final VentaService ventaService = new VentaService();
    private static final LoteRepository loteRepository = new LoteRepository();

    private static int idCliente;
    private static int idBodega;
    private static int idVehiculo;
    private static int idServicio;   // SERVICIO (no controla inventario), precio 50.000

    private static int seq = 0; // para numeros de OT unicos

    private static final String LEJOS = LocalDate.now().plusDays(60).toString();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiarTodo(c);
            idCliente = idDe(c, "INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'CLIENTE', 'ACTIVO')",
                    MARCA + " Cliente");
            idBodega = idDe(c, "INSERT INTO bodegas (nombre, estado, fecha_creacion) "
                    + "VALUES (?, 'ACTIVO', datetime('now','localtime'))", MARCA + " Bodega");
            idVehiculo = idDe(c, "INSERT INTO vehiculos (id_tercero, placa, marca, estado) "
                    + "VALUES (" + idCliente + ", ?, 'Generico', 'ACTIVO')", MARCA + "-PLACA");
            idServicio = insertarItem(c, MARCA + "-SRV", MARCA + " Servicio", "SERVICIO", 50000, 0);
        }
    }

    @AfterAll
    static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiarTodo(c);
        }
    }

    // --------------------------------------------------------------- casos de cierre

    @Test
    void cierreContadoCreaVentaYEntregaOt() throws SQLException {
        int idProd = nuevoProducto("A", "10");
        int idOt = crearOt(MARCA + "-A", "ABIERTA");
        addServicio(idOt, idServicio, "1", "50000");
        addRepuesto(idOt, idProd, "2", "10000");

        String numero = otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, "Cierre de prueba");

        assertNotNull(numero, "Debe devolver el numero de venta.");
        assertTrue(numero.startsWith("V-"), "El numero de venta debe tener formato V-NNNNNN.");
        int idVenta = ventaService.idVentaPorNumero(numero).orElse(-1);
        assertTrue(idVenta > 0, "La venta debe existir y ser localizable.");
        assertEquals("ENTREGADA", otEstado(idOt), "La OT debe quedar ENTREGADA tras cerrar.");
        assertEquals(Integer.valueOf(idVenta), otIdVenta(idOt), "La OT debe guardar el id_venta generado.");
    }

    @Test
    void cierreCreditoGeneraCuentaPorCobrar() throws SQLException {
        int idOt = crearOt(MARCA + "-B", "APROBADA");
        addServicio(idOt, idServicio, "1", "50000"); // solo servicio: no requiere stock

        String numero = otService.cerrarYFacturar(idOt, false, null, LEJOS, null);
        int idVenta = ventaService.idVentaPorNumero(numero).orElse(-1);

        assertEquals(1, contar("SELECT COUNT(*) FROM cuentas_por_cobrar WHERE id_venta=" + idVenta),
                "Una venta a credito debe generar su cuenta por cobrar.");
        assertEquals(0, contar("SELECT COUNT(*) FROM pagos WHERE id_venta=" + idVenta),
                "Una venta a credito no registra pago de contado.");
    }

    @Test
    void cierreContadoRegistraPagoYNoCartera() throws SQLException {
        int idOt = crearOt(MARCA + "-C", "TERMINADA");
        addServicio(idOt, idServicio, "1", "50000");

        String numero = otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null);
        int idVenta = ventaService.idVentaPorNumero(numero).orElse(-1);

        assertEquals(1, contar("SELECT COUNT(*) FROM pagos WHERE id_venta=" + idVenta),
                "Una venta de contado registra su pago.");
        assertEquals(0, contar("SELECT COUNT(*) FROM cuentas_por_cobrar WHERE id_venta=" + idVenta),
                "Una venta de contado no deja cuenta por cobrar pendiente.");
    }

    @Test
    void cierreConRepuestoDescuentaInventario() throws SQLException {
        int idProd = nuevoProducto("D", "10");
        int idOt = crearOt(MARCA + "-D", "ABIERTA");
        addRepuesto(idOt, idProd, "3", "10000");

        otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null);

        assertEquals(0, new BigDecimal("7").compareTo(stock(idProd)),
                "El stock debe bajar de 10 a 7 tras vender 3 repuestos.");
    }

    @Test
    void cierreConRepuestoRegistraKardexSalidaVenta() throws SQLException {
        int idProd = nuevoProducto("E", "10");
        int antes = contar("SELECT COUNT(*) FROM inventario_movimientos WHERE id_item=" + idProd
                + " AND tipo_movimiento='SALIDA_VENTA'");
        int idOt = crearOt(MARCA + "-E", "ABIERTA");
        addRepuesto(idOt, idProd, "2", "10000");

        otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null);

        int despues = contar("SELECT COUNT(*) FROM inventario_movimientos WHERE id_item=" + idProd
                + " AND tipo_movimiento='SALIDA_VENTA'");
        assertEquals(antes + 1, despues, "El cierre debe registrar un movimiento Kardex SALIDA_VENTA.");
    }

    @Test
    void cierreConLoteAplicaFefoYRegistraConsumo() throws SQLException {
        int idProd = nuevoProducto("F", "10");
        int idLote = crearLote(idProd, MARCA + "-L1", "10", LEJOS);
        int idOt = crearOt(MARCA + "-F", "ABIERTA");
        addRepuesto(idOt, idProd, "4", "10000");

        String numero = otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null);
        int idVenta = ventaService.idVentaPorNumero(numero).orElse(-1);

        assertTrue(contar("SELECT COUNT(*) FROM venta_detalle_lotes vdl "
                + "JOIN venta_detalles d ON d.id_detalle = vdl.id_detalle WHERE d.id_venta=" + idVenta) >= 1,
                "El cierre con lote debe registrar el consumo FEFO en venta_detalle_lotes.");
        assertEquals(0, new BigDecimal("6").compareTo(saldoLote(idLote)),
                "El lote debe pasar de 10 a 6 disponibles tras consumir 4 por FEFO.");
    }

    // --------------------------------------------------------------- validaciones

    @Test
    void noPermiteCerrarOtCancelada() throws SQLException {
        int idOt = crearOt(MARCA + "-G", "CANCELADA");
        addServicio(idOt, idServicio, "1", "50000");
        assertThrows(ValidacionException.class,
                () -> otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null),
                "No debe poder cerrarse una OT cancelada.");
    }

    @Test
    void noPermiteCerrarDosVeces() throws SQLException {
        int idOt = crearOt(MARCA + "-H", "ABIERTA");
        addServicio(idOt, idServicio, "1", "50000");
        otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null); // primer cierre OK
        assertThrows(ValidacionException.class,
                () -> otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null),
                "Una OT ya facturada (con id_venta) no debe poder cerrarse de nuevo.");
    }

    @Test
    void noPermiteCerrarSinLineas() throws SQLException {
        int idOt = crearOt(MARCA + "-I", "ABIERTA"); // sin servicios ni repuestos
        assertThrows(ValidacionException.class,
                () -> otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null),
                "No debe poder cerrarse una OT sin lineas.");
    }

    @Test
    void stockInsuficienteDejaOtIntacta() throws SQLException {
        int idProd = nuevoProducto("J", "1"); // stock insuficiente para 5
        int idOt = crearOt(MARCA + "-J", "ABIERTA");
        addRepuesto(idOt, idProd, "5", "10000");

        assertThrows(Exception.class,
                () -> otService.cerrarYFacturar(idOt, true, MedioPago.EFECTIVO, null, null),
                "Un cierre con stock insuficiente debe fallar.");

        assertEquals("ABIERTA", otEstado(idOt), "Si falla la venta, la OT no debe quedar ENTREGADA.");
        assertNull(otIdVenta(idOt), "Si falla la venta, la OT no debe guardar id_venta.");
        assertEquals(0, new BigDecimal("1").compareTo(stock(idProd)),
                "Si falla la venta, el stock no debe descontarse.");
    }

    // --------------------------------------------------------------- helpers de datos

    /** Crea un PRODUCTO inventariable propio de la prueba (aislado) y le fija un stock inicial. */
    private static int nuevoProducto(String sufijo, String stock) throws SQLException {
        int id;
        try (Connection c = DatabaseConnection.getConnection()) {
            id = insertarItem(c, MARCA + "-P" + sufijo, MARCA + " Producto " + sufijo, "PRODUCTO", 10000, 1);
        }
        seedStock(id, stock);
        return id;
    }

    private static int crearOt(String numeroOt, String estado) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            return idDe(c, "INSERT INTO ordenes_trabajo (numero_ot, id_tercero, id_vehiculo, estado, "
                    + "subtotal_servicios, subtotal_repuestos, total) VALUES (?, " + idCliente + ", "
                    + idVehiculo + ", '" + estado + "', 0, 0, 0)", numeroOt + "-" + (++seq));
        }
    }

    private static void addServicio(int idOt, int idItem, String cant, String precio) throws SQLException {
        BigDecimal sub = new BigDecimal(cant).multiply(new BigDecimal(precio));
        ejecutar("INSERT INTO orden_trabajo_servicios (id_orden_trabajo, id_item, cantidad, precio_unitario, subtotal) "
                + "VALUES (" + idOt + ", " + idItem + ", " + cant + ", " + precio + ", " + sub.toPlainString() + ")");
    }

    private static void addRepuesto(int idOt, int idItem, String cant, String precio) throws SQLException {
        BigDecimal sub = new BigDecimal(cant).multiply(new BigDecimal(precio));
        ejecutar("INSERT INTO orden_trabajo_repuestos (id_orden_trabajo, id_item, id_bodega_salida, cantidad, "
                + "precio_unitario, subtotal) VALUES (" + idOt + ", " + idItem + ", " + idBodega + ", " + cant
                + ", " + precio + ", " + sub.toPlainString() + ")");
    }

    /** Fija un stock conocido para (item, bodega): borra la fila previa e inserta la cantidad indicada. */
    private static void seedStock(int idItem, String cantidad) throws SQLException {
        ejecutar("DELETE FROM inventario_stock WHERE id_item=" + idItem + " AND id_bodega=" + idBodega);
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setDouble(3, new BigDecimal(cantidad).doubleValue());
            ps.executeUpdate();
        }
    }

    private static int crearLote(int idItem, String numero, String cantidad, String vencimiento) throws SQLException {
        Lote l = new Lote();
        l.setIdItem(idItem);
        l.setIdBodega(idBodega);
        l.setNumeroLote(numero);
        l.setCantidadInicial(new BigDecimal(cantidad));
        l.setFechaIngreso(LocalDate.now().toString());
        l.setFechaVencimiento(vencimiento);
        l.setEstado(EstadoLote.ACTIVO);
        return loteRepository.insertar(l);
    }

    // --------------------------------------------------------------- helpers de lectura

    private static BigDecimal stock(int idItem) throws SQLException {
        return unNumero("SELECT cantidad FROM inventario_stock WHERE id_item=" + idItem
                + " AND id_bodega=" + idBodega);
    }

    private static BigDecimal saldoLote(int idLote) throws SQLException {
        return unNumero("SELECT IFNULL(cantidad_disponible, cantidad_inicial) FROM lotes WHERE id_lote=" + idLote);
    }

    private static String otEstado(int idOt) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT estado FROM ordenes_trabajo WHERE id_orden_trabajo=?")) {
            ps.setInt(1, idOt);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static Integer otIdVenta(int idOt) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_venta FROM ordenes_trabajo WHERE id_orden_trabajo=?")) {
            ps.setInt(1, idOt);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    private static int contar(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static BigDecimal unNumero(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : null;
        }
    }

    // --------------------------------------------------------------- helpers de escritura/limpieza

    private static int insertarItem(Connection c, String codigo, String nombre, String tipo,
                                    long precioVenta, int controla) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO items "
                + "(codigo, nombre, tipo_item, precio_compra, precio_venta, controla_inventario, "
                + "stock_minimo, estado, fecha_creacion, modo_calculo_servicio, unidad_medida) VALUES ("
                + "?, ?, '" + tipo + "', 0, " + precioVenta + ", " + controla + ", 0, 'ACTIVO', "
                + "datetime('now','localtime'), 'FIJO', 'Unidad')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int idDe(Connection c, String sql, String texto) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, texto);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void ejecutar(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.executeUpdate();
        }
    }

    private static void limpiarLotes(int idItem) throws SQLException {
        ejecutar("DELETE FROM venta_detalle_lotes WHERE id_lote IN (SELECT id_lote FROM lotes WHERE id_item=" + idItem + ")");
        ejecutar("DELETE FROM lotes WHERE id_item=" + idItem);
    }

    /**
     * Limpieza FK-segura de todo lo generado por la prueba: primero los hijos de las ventas creadas
     * por los cierres (lotes de detalle, cartera, pagos, Kardex, detalles), luego las ventas, despues
     * la OT y su detalle, y por ultimo lotes/stock/vehiculo/items/bodega/cliente.
     */
    private static void limpiarTodo(Connection c) throws SQLException {
        String items = "SELECT id_item FROM items WHERE codigo LIKE '" + MARCA + "%'";
        String ventas = "SELECT v.id_venta FROM ventas v JOIN terceros t ON t.id_tercero = v.id_tercero "
                + "WHERE t.nombre LIKE '" + MARCA + "%'";
        String[] sentencias = {
            "DELETE FROM venta_detalle_lotes WHERE id_detalle IN (SELECT id_detalle FROM venta_detalles WHERE id_venta IN (" + ventas + "))",
            "DELETE FROM abonos_cuenta WHERE id_cuenta IN (SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta IN (" + ventas + "))",
            "DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventas + ")",
            "DELETE FROM pagos WHERE id_venta IN (" + ventas + ")",
            "DELETE FROM inventario_movimientos WHERE id_item IN (" + items + ")",
            // La OT referencia id_venta: hay que borrarla ANTES que las ventas.
            "DELETE FROM orden_trabajo_servicios WHERE id_orden_trabajo IN (SELECT id_orden_trabajo FROM ordenes_trabajo WHERE id_tercero IN (SELECT id_tercero FROM terceros WHERE nombre LIKE '" + MARCA + "%'))",
            "DELETE FROM orden_trabajo_repuestos WHERE id_orden_trabajo IN (SELECT id_orden_trabajo FROM ordenes_trabajo WHERE id_tercero IN (SELECT id_tercero FROM terceros WHERE nombre LIKE '" + MARCA + "%'))",
            "DELETE FROM ordenes_trabajo WHERE id_tercero IN (SELECT id_tercero FROM terceros WHERE nombre LIKE '" + MARCA + "%')",
            "DELETE FROM venta_detalles WHERE id_venta IN (" + ventas + ")",
            "DELETE FROM ventas WHERE id_venta IN (" + ventas + ")",
            "DELETE FROM venta_detalle_lotes WHERE id_lote IN (SELECT id_lote FROM lotes WHERE id_item IN (" + items + "))",
            "DELETE FROM lotes WHERE id_item IN (" + items + ")",
            "DELETE FROM inventario_stock WHERE id_item IN (" + items + ")",
            "DELETE FROM vehiculos WHERE id_tercero IN (SELECT id_tercero FROM terceros WHERE nombre LIKE '" + MARCA + "%')",
            "DELETE FROM items WHERE codigo LIKE '" + MARCA + "%'",
            "DELETE FROM terceros WHERE nombre LIKE '" + MARCA + "%'",
            "DELETE FROM bodegas WHERE nombre LIKE '" + MARCA + "%'"
        };
        try (Statement st = c.createStatement()) {
            for (String sql : sentencias) {
                st.executeUpdate(sql);
            }
        }
    }
}
