package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.domain.model.VentaDetalleLote;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.LoteRepository;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso J (FEFO / lote inteligente por linea de venta): consumo de lotes por FEFO, reduccion
 * de saldo, marca AGOTADO, trazabilidad venta_detalle_lotes, Kardex con id_lote, rechazo y rollback
 * transaccional, compatibilidad sin lotes y exportacion. Datos marcados "ZPJTEST" (aislados por codigo).
 */
class PasoJLoteFefoVentaTest {

    private static final VentaService ventaService = new VentaService();
    private static final ItemService itemService = new ItemService();
    private static final LoteRepository loteRepository = new LoteRepository();
    private static final ExportacionService exportacionService = new ExportacionService();

    private static int idCliente;
    private static int idBodega;

    private static final String HOY = LocalDate.now().toString();
    private static final String CERCA = LocalDate.now().plusDays(10).toString();
    private static final String LEJOS = LocalDate.now().plusDays(60).toString();
    private static final String VENCIDA = LocalDate.now().minusDays(5).toString();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idCliente = idDe("INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'CLIENTE', 'ACTIVO')", "ZPJTEST Cliente");
        idBodega = idDe("INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", "ZPJTEST Bodega");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Migraciones ----------------

    @Test
    void migracionCantidadDisponibleIdempotente() throws SQLException {
        DatabaseInitializer.initialize(); // segunda corrida no debe fallar
        assertTrue(columnaExiste("lotes", "cantidad_disponible"), "lotes.cantidad_disponible debe existir.");
    }

    @Test
    void migracionVentaDetalleLotesIdempotente() throws SQLException {
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='venta_detalle_lotes'");
             ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next(), "La tabla venta_detalle_lotes debe existir.");
        }
    }

    // ---------------- Saldo de lote ----------------

    @Test
    void loteNuevoIniciaDisponibleIgualInicial() throws SQLException {
        Item it = item("ZPJTEST-NEW");
        int idLote = crearLote(it.getIdItem(), "LN-1", "20", LEJOS, EstadoLote.ACTIVO);
        assertEquals(0, new BigDecimal("20").compareTo(saldoLote(idLote)),
                "Un lote nuevo nace con cantidad_disponible = cantidad_inicial.");
    }

    // ---------------- Compatibilidad sin lotes ----------------

    @Test
    void productoSinLotesFlujoActual() throws SQLException {
        Item it = item("ZPJTEST-NOLOT");
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "3", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        // Stock baja; no hay consumos de lote; Kardex sin id_lote.
        assertEquals(0, new BigDecimal("7").compareTo(stock(it.getIdItem())), "El stock debe descontarse.");
        int idDet = idDetalle(v.getIdVenta(), it.getIdItem());
        assertTrue(loteRepository.consumosPorDetalle(idDet).isEmpty(), "Sin lotes no debe haber consumos.");
        assertEquals(null, idLoteUltimoMovimiento(it.getIdItem()), "El Kardex no debe llevar id_lote sin lotes.");
    }

    @Test
    void servicioNoConsumeLote() throws SQLException {
        Item srv = servicio("ZPJTEST-SRV");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(srv.getIdItem(), false, "1", "50000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        int idDet = idDetalle(v.getIdVenta(), srv.getIdItem());
        assertTrue(loteRepository.consumosPorDetalle(idDet).isEmpty(), "Un servicio no consume lotes.");
    }

    // ---------------- FEFO ----------------

    @Test
    void fefoEligeVencimientoMasCercano() throws SQLException {
        Item it = item("ZPJTEST-FEFO");
        int cerca = crearLote(it.getIdItem(), "LC", "5", CERCA, EstadoLote.ACTIVO);
        int lejos = crearLote(it.getIdItem(), "LL", "5", LEJOS, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "3", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, new BigDecimal("2").compareTo(saldoLote(cerca)), "Debe consumir primero el de vencimiento cercano.");
        assertEquals(0, new BigDecimal("5").compareTo(saldoLote(lejos)), "El lote lejano no debe tocarse.");
    }

    @Test
    void loteVencidoNoSeConsume() throws SQLException {
        Item it = item("ZPJTEST-VEN");
        int vencido = crearLote(it.getIdItem(), "LV", "10", VENCIDA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "3", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        // El lote vencido no es usable -> se vende solo contra stock; el saldo del lote no cambia.
        assertEquals(0, new BigDecimal("10").compareTo(saldoLote(vencido)), "Un lote vencido no se consume automaticamente.");
        int idDet = idDetalle(v.getIdVenta(), it.getIdItem());
        assertTrue(loteRepository.consumosPorDetalle(idDet).isEmpty(), "Sin lotes usables no hay consumos.");
    }

    @Test
    void loteSinFechaDespuesDeVigentes() throws SQLException {
        Item it = item("ZPJTEST-SINF");
        int conFecha = crearLote(it.getIdItem(), "LF", "3", CERCA, EstadoLote.ACTIVO);
        int sinFecha = crearLote(it.getIdItem(), "LS", "10", null, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "13");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "5", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoLote(conFecha)), "Primero se agota el lote con fecha vigente.");
        assertEquals(0, new BigDecimal("8").compareTo(saldoLote(sinFecha)), "El lote sin fecha se consume despues.");
    }

    @Test
    void consumeUnSoloLoteSiAlcanza() throws SQLException {
        Item it = item("ZPJTEST-UNO");
        int idLote = crearLote(it.getIdItem(), "U1", "10", CERCA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "4", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, new BigDecimal("6").compareTo(saldoLote(idLote)));
        assertEquals(1, loteRepository.consumosPorDetalle(idDetalle(v.getIdVenta(), it.getIdItem())).size());
    }

    @Test
    void consumeVariosLotesSiPrimeroNoAlcanza() throws SQLException {
        Item it = item("ZPJTEST-MULTI");
        int a = crearLote(it.getIdItem(), "MA", "4", CERCA, EstadoLote.ACTIVO);
        int b = crearLote(it.getIdItem(), "MB", "6", LEJOS, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "10", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoLote(a)), "El primer lote queda en 0.");
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoLote(b)), "El segundo lote completa la cantidad.");
    }

    @Test
    void ventaDetalleLotesRegistraConsumos() throws SQLException {
        Item it = item("ZPJTEST-TRZ");
        crearLote(it.getIdItem(), "TA", "4", CERCA, EstadoLote.ACTIVO);
        crearLote(it.getIdItem(), "TB", "6", LEJOS, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "10", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        List<VentaDetalleLote> consumos = loteRepository.consumosPorDetalle(idDetalle(v.getIdVenta(), it.getIdItem()));
        assertEquals(2, consumos.size(), "Deben registrarse dos consumos.");
        assertEquals(0, new BigDecimal("4").compareTo(consumos.get(0).getCantidad()), "Primer consumo = 4 (FEFO).");
        assertEquals(0, new BigDecimal("6").compareTo(consumos.get(1).getCantidad()), "Segundo consumo = 6.");
    }

    @Test
    void cantidadDisponibleSeReduce() throws SQLException {
        Item it = item("ZPJTEST-RED");
        int idLote = crearLote(it.getIdItem(), "R1", "8", CERCA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "8");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "3", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, new BigDecimal("5").compareTo(saldoLote(idLote)), "El saldo del lote debe reducirse.");
    }

    @Test
    void loteLlegaACeroSeMarcaAgotado() throws SQLException {
        Item it = item("ZPJTEST-AGO");
        int idLote = crearLote(it.getIdItem(), "A1", "4", CERCA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "4");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "4", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, BigDecimal.ZERO.compareTo(saldoLote(idLote)));
        assertEquals("AGOTADO", estadoLote(idLote), "Un lote en 0 debe quedar AGOTADO.");
    }

    @Test
    void kardexRegistraIdLote() throws SQLException {
        Item it = item("ZPJTEST-KDX");
        int idLote = crearLote(it.getIdItem(), "K1", "10", CERCA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "2", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(Integer.valueOf(idLote), idLoteUltimoMovimiento(it.getIdItem()),
                "El Kardex de salida debe registrar el id_lote consumido.");
    }

    // ---------------- Rechazo y rollback ----------------

    @Test
    void lotesInsuficientesRechazaSinDescontar() throws SQLException {
        Item it = item("ZPJTEST-INS");
        int idLote = crearLote(it.getIdItem(), "I1", "3", CERCA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10"); // el stock alcanza, pero los lotes no
        Venta v = venta();
        assertThrows(ValidacionException.class, () ->
                ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "5", "1000"))),
                        idBodega, true, null, MedioPago.EFECTIVO));
        // Rollback: ni stock ni lote cambian.
        assertEquals(0, new BigDecimal("10").compareTo(stock(it.getIdItem())), "El stock no debe descontarse.");
        assertEquals(0, new BigDecimal("3").compareTo(saldoLote(idLote)), "El lote no debe descontarse.");
    }

    @Test
    void rollbackSiFallaSegundaLinea() throws SQLException {
        Item ok = item("ZPJTEST-RBOK");
        Item mal = item("ZPJTEST-RBMAL");
        int loteOk = crearLote(ok.getIdItem(), "OK1", "10", CERCA, EstadoLote.ACTIVO);
        crearLote(mal.getIdItem(), "MAL1", "1", CERCA, EstadoLote.ACTIVO); // no cubre
        seedStock(ok.getIdItem(), "10");
        seedStock(mal.getIdItem(), "10");
        int ventasAntes = ventasCliente();
        Venta v = venta();
        List<VentaDetalle> det = new ArrayList<>();
        det.add(linea(ok.getIdItem(), true, "4", "1000"));   // se procesaria primero
        det.add(linea(mal.getIdItem(), true, "5", "1000"));  // falla por lotes insuficientes
        assertThrows(ValidacionException.class, () ->
                ventaService.registrarVenta(v, det, idBodega, true, null, MedioPago.EFECTIVO));
        // El lote del primer item NO debe quedar descontado (rollback total).
        assertEquals(0, new BigDecimal("10").compareTo(saldoLote(loteOk)), "El primer lote no debe quedar descontado.");
        assertEquals(0, new BigDecimal("10").compareTo(stock(ok.getIdItem())), "El stock del primer item no debe cambiar.");
        assertEquals(ventasAntes, ventasCliente(), "No debe quedar una venta a medias.");
    }

    // ---------------- Exportacion e historicos ----------------

    @Test
    void exportLotesIncluyeCantidadDisponible() throws SQLException, IOException {
        Item it = item("ZPJTEST-EXPL");
        crearLote(it.getIdItem(), "EXPL1", "10", CERCA, EstadoLote.ACTIVO);
        Path archivo = Files.createTempFile("zpjl", ".csv");
        exportacionService.exportar(TipoExportacion.LOTES, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);
        String fila = lineas.stream().filter(l -> l.contains("EXPL1")).findFirst().orElse("");
        // Columnas LOTES: ...;Cantidad(7);Cantidad_Disponible(8);Estado(9);...
        String[] campos = fila.split(";", -1);
        assertEquals("10", campos[8], "LOTES debe exportar Cantidad_Disponible. Linea: " + fila);
    }

    @Test
    void exportVentaDetallesIncluyeLotesConsumidos() throws SQLException, IOException {
        Item it = item("ZPJTEST-EXPVD");
        crearLote(it.getIdItem(), "VD1", "10", CERCA, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "2", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        Path archivo = Files.createTempFile("zpjvd", ".csv");
        exportacionService.exportar(TipoExportacion.VENTA_DETALLES, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);
        String fila = lineas.stream().filter(l -> l.contains(v.getNumeroVenta())).findFirst().orElse("");
        assertTrue(fila.contains("VD1:2"), "VENTA_DETALLES debe exportar Lotes_Consumidos. Linea: " + fila);
    }

    @Test
    void ventaSinLoteExportaNoAplica() throws SQLException, IOException {
        Item it = item("ZPJTEST-EXPNA");
        seedStock(it.getIdItem(), "10"); // sin lotes
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "2", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        Path archivo = Files.createTempFile("zpjna", ".csv");
        exportacionService.exportar(TipoExportacion.VENTA_DETALLES, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);
        String fila = lineas.stream().filter(l -> l.contains(v.getNumeroVenta())).findFirst().orElse("");
        assertTrue(fila.contains("No aplica"), "Sin lote, Lotes_Consumidos debe ser 'No aplica'. Linea: " + fila);
    }

    @Test
    void loteAgotadoNoSeConsume() throws SQLException {
        Item it = item("ZPJTEST-EXCL");
        int agotado = crearLote(it.getIdItem(), "X-AGO", "5", CERCA, EstadoLote.AGOTADO); // mas cercano pero AGOTADO
        int activo = crearLote(it.getIdItem(), "X-ACT", "5", LEJOS, EstadoLote.ACTIVO);
        seedStock(it.getIdItem(), "10");
        Venta v = venta();
        ventaService.registrarVenta(v, new ArrayList<>(List.of(linea(it.getIdItem(), true, "3", "1000"))),
                idBodega, true, null, MedioPago.EFECTIVO);
        assertEquals(0, new BigDecimal("5").compareTo(saldoLote(agotado)), "Un lote AGOTADO no se consume.");
        assertEquals(0, new BigDecimal("2").compareTo(saldoLote(activo)), "Debe consumir el lote ACTIVO.");
    }

    // ---------------- auxiliares ----------------

    private static Venta venta() {
        Venta v = new Venta();
        v.setIdTercero(idCliente);
        v.setDescuento(BigDecimal.ZERO);
        return v;
    }

    private static VentaDetalle linea(int idItem, boolean controlaInv, String cantidad, String precio) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setNombreItem("ZPJTEST item " + idItem);
        d.setControlaInventario(controlaInv);
        d.setCantidad(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoLinea(BigDecimal.ZERO);
        return d;
    }

    private static Item item(String codigo) throws SQLException {
        Item it = base(codigo, true, TipoItem.REPUESTO);
        itemService.guardar(it);
        return it;
    }

    private static Item servicio(String codigo) throws SQLException {
        Item it = base(codigo, false, TipoItem.SERVICIO);
        itemService.guardar(it);
        return it;
    }

    private static Item base(String codigo, boolean inv, TipoItem tipo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPJTEST " + codigo);
        it.setTipoItem(tipo);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(inv);
        it.setStockMinimo(BigDecimal.ZERO);
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static int crearLote(int idItem, String numero, String cantidad, String vencimiento, EstadoLote estado)
            throws SQLException {
        Lote l = new Lote();
        l.setIdItem(idItem);
        l.setIdBodega(idBodega);
        l.setNumeroLote(numero);
        l.setCantidadInicial(new BigDecimal(cantidad));
        l.setFechaIngreso(HOY);
        l.setFechaVencimiento(vencimiento);
        l.setEstado(estado);
        return loteRepository.insertar(l);
    }

    private static void seedStock(int idItem, String cantidad) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setDouble(3, new BigDecimal(cantidad).doubleValue());
            ps.executeUpdate();
        }
    }

    private static BigDecimal saldoLote(int idLote) throws SQLException {
        return unNumero("SELECT IFNULL(cantidad_disponible, cantidad_inicial) FROM lotes WHERE id_lote=?", idLote);
    }

    private static String estadoLote(int idLote) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT estado FROM lotes WHERE id_lote=?")) {
            ps.setInt(1, idLote);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static BigDecimal stock(int idItem) throws SQLException {
        return unNumero("SELECT cantidad FROM inventario_stock WHERE id_item=? AND id_bodega=" + idBodega, idItem);
    }

    private static BigDecimal unNumero(String sql, int id) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
            }
        }
    }

    private static Integer idLoteUltimoMovimiento(int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_lote FROM inventario_movimientos WHERE id_item=? AND tipo_movimiento='SALIDA_VENTA' "
                     + "ORDER BY id_movimiento DESC LIMIT 1")) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int v = rs.getInt(1);
                    return rs.wasNull() ? null : v;
                }
                return null;
            }
        }
    }

    private static int idDetalle(int idVenta, int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_detalle FROM venta_detalles WHERE id_venta=? AND id_item=? ORDER BY id_detalle LIMIT 1")) {
            ps.setInt(1, idVenta);
            ps.setInt(2, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int ventasCliente() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ventas WHERE id_tercero=" + idCliente)) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static boolean columnaExiste(String tabla, String col) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tabla + ")")) {
            while (rs.next()) {
                if (col.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
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
            String ventasZ = "SELECT id_venta FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZPJTEST%')";
            String detallesZ = "SELECT id_detalle FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")";
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPJTEST%'";
            String lotesZ = "SELECT id_lote FROM lotes WHERE id_item IN (" + itemsZ + ")";
            // venta_detalle_lotes primero (FK a venta_detalles y a lotes).
            st.executeUpdate("DELETE FROM venta_detalle_lotes WHERE id_detalle IN (" + detallesZ + ")");
            st.executeUpdate("DELETE FROM venta_detalle_lotes WHERE id_lote IN (" + lotesZ + ")");
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZPJTEST%')");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM lotes WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPJTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPJTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPJTEST%'");
        }
    }
}
