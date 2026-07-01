package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.DisponibilidadBodegaItem;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.VentaRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso I (bodega inteligente por linea de venta): asignacion segun disponibilidad,
 * descuento y Kardex por bodega real, servicios sin bodega, revalidacion transaccional, exportacion
 * y compatibilidad con ventas historicas. Datos marcados "ZPITEST".
 */
class PasoIBodegaPorLineaTest {

    private static final VentaService ventaService = new VentaService();
    private static final ItemService itemService = new ItemService();
    private static final ExportacionService exportacionService = new ExportacionService();
    private static final VentaRepository ventaRepository = new VentaRepository();

    private static int idCliente;
    private static int idBodegaA;
    private static int idBodegaB;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idCliente = insertarCliente("ZPITEST Cliente");
        idBodegaA = insertarBodega("ZPITEST Bodega A");
        idBodegaB = insertarBodega("ZPITEST Bodega B");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Regla pura de eleccion ----------------

    @Test
    void elegirBodegaUsaPreferidaSiAlcanza() {
        List<DisponibilidadBodegaItem> disp = Arrays.asList(
                disp(1, 10, "A"), disp(2, 3, "B"));
        DisponibilidadBodegaItem e = ventaService.elegirBodega(disp, new BigDecimal("2"), 2);
        assertEquals(2, e.getIdBodega(), "Si la preferida (2) alcanza, se usa.");
    }

    @Test
    void elegirBodegaMenorExcedenteSuficiente() {
        // Requerido 2; A=0, B=10, C=3 -> elegir C (menor excedente suficiente).
        List<DisponibilidadBodegaItem> disp = Arrays.asList(
                disp(10, 0, "A"), disp(11, 10, "B"), disp(12, 3, "C"));
        DisponibilidadBodegaItem e = ventaService.elegirBodega(disp, new BigDecimal("2"), 0);
        assertEquals(12, e.getIdBodega(), "Debe elegir la bodega con menor excedente suficiente (C).");
    }

    // ---------------- Asignacion (servicio, con BD) ----------------

    @Test
    void stockSuficienteEnPreferidaUsaPreferida() throws SQLException {
        Item it = guardarItem("ZPITEST-P1");
        seedStock(it.getIdItem(), idBodegaA, "10");
        seedStock(it.getIdItem(), idBodegaB, "10");
        DisponibilidadBodegaItem e = ventaService.resolverBodegaSalida(it.getIdItem(), new BigDecimal("2"), idBodegaA);
        assertEquals(idBodegaA, e.getIdBodega());
    }

    @Test
    void sinStockEnPreferidaUsaOtraBodega() throws SQLException {
        Item it = guardarItem("ZPITEST-P2");
        seedStock(it.getIdItem(), idBodegaA, "0");
        seedStock(it.getIdItem(), idBodegaB, "5");
        DisponibilidadBodegaItem e = ventaService.resolverBodegaSalida(it.getIdItem(), new BigDecimal("2"), idBodegaA);
        assertEquals(idBodegaB, e.getIdBodega(), "Si la preferida no alcanza, usa otra bodega con stock.");
    }

    @Test
    void ningunaBodegaIndividualSuficienteRechazaConDetalle() throws SQLException {
        Item it = guardarItem("ZPITEST-P3");
        seedStock(it.getIdItem(), idBodegaA, "1");
        seedStock(it.getIdItem(), idBodegaB, "1");
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> ventaService.resolverBodegaSalida(it.getIdItem(), new BigDecimal("2"), idBodegaA));
        assertTrue(ex.getMessage().contains("No hay una bodega individual"), "Mensaje claro de disponibilidad.");
        assertTrue(ex.getMessage().contains("Total disponible"), "Debe incluir el desglose por bodega.");
    }

    // ---------------- Venta completa (integracion) ----------------

    @Test
    void dosProductosSalenDeBodegasDiferentes() throws SQLException {
        Item a = guardarItem("ZPITEST-A");
        Item b = guardarItem("ZPITEST-B");
        seedStock(a.getIdItem(), idBodegaA, "10"); // A solo en bodega A
        seedStock(b.getIdItem(), idBodegaB, "10"); // B solo en bodega B
        Venta v = nuevaVenta();
        List<VentaDetalle> det = new ArrayList<>();
        det.add(linea(a.getIdItem(), true, "2", "1000"));
        det.add(linea(b.getIdItem(), true, "2", "1000"));
        ventaService.registrarVenta(v, det, idBodegaA, true, null, MedioPago.EFECTIVO);

        List<VentaDetalle> guardado = ventaRepository.listarDetalles(v.getIdVenta());
        assertEquals(Integer.valueOf(idBodegaA), bodegaDe(guardado, a.getIdItem()));
        assertEquals(Integer.valueOf(idBodegaB), bodegaDe(guardado, b.getIdItem()));
    }

    @Test
    void servicioNoRequiereBodegaNiDescuentaInventario() throws SQLException {
        Item srv = guardarServicio("ZPITEST-SRV");
        BigDecimal antes = sumaInventario();
        Venta v = nuevaVenta();
        List<VentaDetalle> det = new ArrayList<>();
        det.add(linea(srv.getIdItem(), false, "1", "50000"));
        ventaService.registrarVenta(v, det, idBodegaA, true, null, MedioPago.EFECTIVO);

        assertNull(bodegaDe(ventaRepository.listarDetalles(v.getIdVenta()), srv.getIdItem()),
                "El servicio no lleva bodega de salida.");
        assertEquals(0, antes.compareTo(sumaInventario()), "El servicio no descuenta inventario.");
    }

    @Test
    void descuentaStockYKardexEnBodegaAsignada() throws SQLException {
        Item it = guardarItem("ZPITEST-DESC");
        seedStock(it.getIdItem(), idBodegaA, "10");
        seedStock(it.getIdItem(), idBodegaB, "5");
        Venta v = nuevaVenta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "2", "1000"))),
                idBodegaA, true, null, MedioPago.EFECTIVO);

        // Stock: A baja a 8, B intacto.
        assertEquals(0, new BigDecimal("8").compareTo(stock(it.getIdItem(), idBodegaA)));
        assertEquals(0, new BigDecimal("5").compareTo(stock(it.getIdItem(), idBodegaB)));
        // Kardex: la salida quedo registrada en la bodega A.
        assertEquals(Integer.valueOf(idBodegaA), bodegaUltimoMovimiento(it.getIdItem()));
    }

    @Test
    void guardaIdBodegaSalidaEnDetalle() throws SQLException {
        Item it = guardarItem("ZPITEST-COL");
        seedStock(it.getIdItem(), idBodegaA, "5");
        Venta v = nuevaVenta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "1", "1000"))),
                idBodegaA, true, null, MedioPago.EFECTIVO);
        assertEquals(Integer.valueOf(idBodegaA), bodegaDe(ventaRepository.listarDetalles(v.getIdVenta()), it.getIdItem()));
    }

    @Test
    void revalidaTransaccionalSiBodegaAsignadaNoTieneStock() throws SQLException {
        // Simula "el stock cambio antes de guardar": detalle apuntando a una bodega sin stock suficiente.
        Item it = guardarItem("ZPITEST-RV");
        seedStock(it.getIdItem(), idBodegaA, "1");
        Venta v = nuevaVenta();
        v.setSubtotal(new BigDecimal("5000"));
        v.setDescuento(BigDecimal.ZERO);
        v.setTotal(new BigDecimal("5000"));
        v.setEstado(com.gestorpyme.domain.enums.EstadoVenta.PAGADA);
        VentaDetalle d = linea(it.getIdItem(), true, "5", "1000"); // requiere 5, hay 1
        d.setIdBodegaSalida(idBodegaA);
        assertThrows(ValidacionException.class, () ->
                ventaRepository.crearVentaCompleta(v, new ArrayList<>(List.of(d)), idBodegaA, true, null, MedioPago.EFECTIVO));
    }

    // ---------------- Exportacion e historicos ----------------

    @Test
    void exportVentaDetallesIncluyeBodegaSalida() throws SQLException, IOException {
        Item it = guardarItem("ZPITEST-EXP");
        seedStock(it.getIdItem(), idBodegaA, "5");
        Venta v = nuevaVenta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "1", "1000"))),
                idBodegaA, true, null, MedioPago.EFECTIVO);

        Path archivo = Files.createTempFile("zpitest", ".csv");
        exportacionService.exportar(TipoExportacion.VENTA_DETALLES, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);
        String fila = lineas.stream().filter(l -> l.contains(v.getNumeroVenta())).findFirst().orElse("");
        assertTrue(fila.contains("ZPITEST Bodega A"), "VENTA_DETALLES debe exportar Bodega_Salida. Linea: " + fila);
    }

    @Test
    void ventaHistoricaSinBodegaNoSeRompeYExportaNoRegistrado() throws SQLException, IOException {
        // Inserta una venta "historica" con detalle inventariable SIN bodega de salida (null).
        Item it = guardarItem("ZPITEST-HIST");
        int idVenta = insertarVentaCruda("V-ZPI-HIST");
        insertarDetalleCrudoSinBodega(idVenta, it.getIdItem());

        List<VentaDetalle> det = ventaRepository.listarDetalles(idVenta);
        assertEquals(1, det.size());
        assertNull(det.get(0).getIdBodegaSalida(), "La venta historica no tiene bodega de salida.");

        Path archivo = Files.createTempFile("zpitesth", ".csv");
        exportacionService.exportar(TipoExportacion.VENTA_DETALLES, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);
        String fila = lineas.stream().filter(l -> l.contains("V-ZPI-HIST")).findFirst().orElse("");
        assertTrue(fila.contains("No registrado"), "Sin bodega debe exportar 'No registrado'. Linea: " + fila);
    }

    @Test
    void migracionIdempotente() throws SQLException {
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("PRAGMA table_info(venta_detalles)");
             ResultSet rs = ps.executeQuery()) {
            boolean existe = false;
            while (rs.next()) {
                if ("id_bodega_salida".equalsIgnoreCase(rs.getString("name"))) {
                    existe = true;
                }
            }
            assertTrue(existe, "venta_detalles.id_bodega_salida debe existir.");
        }
    }

    // ---------------- auxiliares ----------------

    private static DisponibilidadBodegaItem disp(int idBodega, int cant, String nombre) {
        return new DisponibilidadBodegaItem(1, idBodega, nombre, new BigDecimal(cant));
    }

    private static Venta nuevaVenta() {
        Venta v = new Venta();
        v.setIdTercero(idCliente);
        v.setDescuento(BigDecimal.ZERO);
        return v;
    }

    private static VentaDetalle linea(int idItem, boolean controlaInv, String cantidad, String precio) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setNombreItem("ZPITEST item " + idItem);
        d.setControlaInventario(controlaInv);
        d.setCantidad(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoLinea(BigDecimal.ZERO);
        return d;
    }

    private static Integer bodegaDe(List<VentaDetalle> detalles, int idItem) {
        for (VentaDetalle d : detalles) {
            if (d.getIdItem() == idItem) {
                return d.getIdBodegaSalida();
            }
        }
        return null;
    }

    private static Item guardarItem(String codigo) throws SQLException {
        Item it = base(codigo, true, TipoItem.REPUESTO);
        itemService.guardar(it);
        return it;
    }

    private static Item guardarServicio(String codigo) throws SQLException {
        Item it = base(codigo, false, TipoItem.SERVICIO);
        itemService.guardar(it);
        return it;
    }

    private static Item base(String codigo, boolean inv, TipoItem tipo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPITEST " + codigo);
        it.setTipoItem(tipo);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(inv);
        it.setStockMinimo(BigDecimal.ZERO);
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static int insertarCliente(String nombre) throws SQLException {
        return idDe("INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'CLIENTE', 'ACTIVO')", nombre);
    }

    private static int insertarBodega(String nombre) throws SQLException {
        return idDe("INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", nombre);
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

    private static void seedStock(int idItem, int idBodega, String cantidad) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setDouble(3, new BigDecimal(cantidad).doubleValue());
            ps.executeUpdate();
        }
    }

    private static BigDecimal stock(int idItem, int idBodega) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT cantidad FROM inventario_stock WHERE id_item=? AND id_bodega=?")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
            }
        }
    }

    private static Integer bodegaUltimoMovimiento(int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_bodega FROM inventario_movimientos WHERE id_item=? AND tipo_movimiento='SALIDA_VENTA' "
                     + "ORDER BY id_movimiento DESC LIMIT 1")) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static BigDecimal sumaInventario() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT IFNULL(SUM(cantidad),0) FROM inventario_stock")) {
            return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
        }
    }

    private static int insertarVentaCruda(String numero) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ventas (numero_venta, id_tercero, subtotal, descuento, total, estado) "
                     + "VALUES (?, ?, 1000, 0, 1000, 'PAGADA')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, idCliente);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void insertarDetalleCrudoSinBodega(int idVenta, int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO venta_detalles (id_venta, id_item, cantidad, precio_unitario, descuento_linea, subtotal_linea) "
                     + "VALUES (?, ?, 1, 1000, 0, 1000)")) { // sin id_bodega_salida -> null
            ps.setInt(1, idVenta);
            ps.setInt(2, idItem);
            ps.executeUpdate();
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ventasZ = "SELECT id_venta FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZPITEST%') "
                    + "OR numero_venta LIKE 'V-ZPI-%'";
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPITEST%'";
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZPITEST%') OR numero_venta LIKE 'V-ZPI-%'");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM lotes WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPITEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPITEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPITEST%'");
        }
    }
}
