package com.gestorpyme.service;

import com.gestorpyme.controller.ItemController;
import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso H: stock visible en ventas (estado de disponibilidad) y optimizacion inicial de
 * lotes/vencimientos (estado de vencimiento, dias para vencer, filtros, exportacion).
 *
 * Aislamiento: datos marcados "ZPHTEST"; las aserciones consultan solo esas filas; los estados de
 * vencimiento se calculan respecto a hoy (fechas relativas), por lo que no dependen de datos reales.
 */
class PasoHStockVentaLotesTest {

    private static final ItemService itemService = new ItemService();
    private static final ItemController itemController = new ItemController();
    private static final LoteService loteService = new LoteService();
    private static final ExportacionService exportacionService = new ExportacionService();

    private static int idBodega;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idBodega = insertarBodega("ZPHTEST Bodega");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Bloque ventas: stock visible ----------------

    @Test
    void busquedaMuestraStockYEstadoDisponible() throws SQLException {
        Item it = inventariable("ZPHTEST-DISP", "5");
        itemService.guardar(it);
        seedStock(it.getIdItem(), idBodega, "10");
        Item leido = buscar("ZPHTEST-DISP");
        assertNotNull(leido, "El item debe aparecer en la busqueda de ventas.");
        assertEquals(0, new BigDecimal("10").compareTo(leido.getStockTotal()), "Debe mostrar el stock total.");
        assertEquals("DISPONIBLE", leido.estadoDisponibilidadVenta());
    }

    @Test
    void servicioMuestraEstadoServicio() throws SQLException {
        Item s = servicio("ZPHTEST-SERV");
        itemService.guardar(s);
        Item leido = buscar("ZPHTEST-SERV");
        assertNotNull(leido);
        assertEquals("SERVICIO", leido.estadoDisponibilidadVenta(), "Un servicio no se bloquea por stock.");
    }

    @Test
    void productoSinStockMuestraSinStock() throws SQLException {
        Item it = inventariable("ZPHTEST-CERO", "5");
        itemService.guardar(it); // sin existencias
        Item leido = buscar("ZPHTEST-CERO");
        assertEquals(0, BigDecimal.ZERO.compareTo(leido.getStockTotal()));
        assertEquals("SIN STOCK", leido.estadoDisponibilidadVenta());
    }

    @Test
    void productoStockBajoMuestraBajo() throws SQLException {
        Item it = inventariable("ZPHTEST-BAJO", "5");
        itemService.guardar(it);
        seedStock(it.getIdItem(), idBodega, "3"); // 0 < 3 <= 5
        assertEquals("BAJO", buscar("ZPHTEST-BAJO").estadoDisponibilidadVenta());
    }

    @Test
    void consultarDisponibilidadNoModificaInventario() throws SQLException {
        Item it = inventariable("ZPHTEST-NOTOUCH", "5");
        itemService.guardar(it);
        seedStock(it.getIdItem(), idBodega, "8");
        BigDecimal antes = sumaInventario();
        buscar("ZPHTEST-NOTOUCH");
        buscar("ZPHTEST");
        buscar("ZPHTEST-NOTOUCH");
        assertEquals(0, antes.compareTo(sumaInventario()), "La consulta de disponibilidad no debe alterar el stock.");
    }

    // ---------------- Bloque lotes: estado de vencimiento ----------------

    @Test
    void loteSinFechaEsSinFecha() {
        assertEquals("SIN FECHA", lote(null).estadoVencimiento());
    }

    @Test
    void loteVencidoEsVencido() {
        assertEquals("VENCIDO", lote(LocalDate.now().minusDays(5).toString()).estadoVencimiento());
    }

    @Test
    void lotePorVencerEsPorVencer() {
        assertEquals("POR VENCER", lote(LocalDate.now().plusDays(10).toString()).estadoVencimiento());
    }

    @Test
    void loteVigenteEsVigente() {
        assertEquals("VIGENTE", lote(LocalDate.now().plusDays(60).toString()).estadoVencimiento());
    }

    // ---------------- Bloque lotes: exportacion ----------------

    @Test
    void exportLotesIncluyeEstadoVencimientoYDias() throws SQLException, IOException {
        Item it = inventariable("ZPHTEST-LOTE", "0");
        itemService.guardar(it);
        String venc = LocalDate.now().plusDays(10).toString();
        insertarLote(it.getIdItem(), idBodega, "ZPHTEST-L1", "7", venc);

        Path archivo = Files.createTempFile("zphtest", ".csv");
        exportacionService.exportar(TipoExportacion.LOTES, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);

        String fila = lineas.stream().filter(l -> l.contains("ZPHTEST-L1")).findFirst().orElse("");
        assertTrue(fila.contains("POR VENCER"), "El CSV debe incluir Estado_Vencimiento. Linea: " + fila);
        // Columnas: ...;Dias_Para_Vencer;Cantidad;Estado;Estado_Vencimiento -> indice 6 = dias.
        String[] campos = fila.split(";", -1);
        long diasEsperado = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(venc));
        assertEquals(String.valueOf(diasEsperado), campos[6], "Debe exportar Dias_Para_Vencer. Linea: " + fila);
    }

    // ---------------- Bloque lotes: filtro ----------------

    @Test
    void filtroPorEstadoVencimientoFunciona() {
        List<Lote> base = new ArrayList<>();
        base.add(lote(LocalDate.now().plusDays(10).toString())); // POR VENCER
        base.add(lote(LocalDate.now().minusDays(2).toString()));  // VENCIDO
        base.add(lote(null));                                      // SIN FECHA
        List<Lote> vencidos = loteService.filtrar(base, null, null, "VENCIDO");
        assertEquals(1, vencidos.size());
        assertEquals("VENCIDO", vencidos.get(0).estadoVencimiento());
    }

    @Test
    void listadoDeLotesNoSeRompe() throws SQLException {
        // El listado y el "proximos a vencer" siguen disponibles tras los cambios.
        assertNotNull(loteService.listar());
        assertNotNull(loteService.listarProximosAVencer(30));
        assertNotNull(new DashboardService().obtenerResumen());
    }

    // ---------------- auxiliares ----------------

    private static Item buscar(String codigo) throws SQLException {
        for (Item i : itemController.buscar(codigo, 50)) {
            if (codigo.equals(i.getCodigo())) {
                return i;
            }
        }
        return itemController.buscar(codigo, 50).stream().findFirst().orElse(null);
    }

    private static Item inventariable(String codigo, String min) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPHTEST " + codigo);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal(min));
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static Item servicio(String codigo) {
        Item s = new Item();
        s.setCodigo(codigo);
        s.setNombre("ZPHTEST " + codigo);
        s.setTipoItem(TipoItem.SERVICIO);
        s.setIdCategoria(1);
        s.setPrecioCompra(BigDecimal.ZERO);
        s.setPrecioVenta(new BigDecimal("50000"));
        s.setControlaInventario(false);
        s.setStockMinimo(BigDecimal.ZERO);
        s.setEstado(EstadoRegistro.ACTIVO);
        return s;
    }

    /** Construye un lote en memoria con la fecha de vencimiento dada (o null). */
    private static Lote lote(String fechaVencimiento) {
        Lote l = new Lote();
        l.setNumeroLote("ZPHTEST-MEM");
        l.setCantidadInicial(BigDecimal.ONE);
        l.setFechaIngreso(LocalDate.now().toString());
        l.setFechaVencimiento(fechaVencimiento);
        l.setEstado(EstadoLote.ACTIVO);
        return l;
    }

    private static int insertarBodega(String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", Statement.RETURN_GENERATED_KEYS)) {
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

    private static void insertarLote(int idItem, int idBodega, String numero, String cantidad, String venc) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO lotes (id_item, id_bodega, numero_lote, cantidad_inicial, fecha_ingreso, fecha_vencimiento, estado) "
                     + "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVO')")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, numero);
            ps.setDouble(4, new BigDecimal(cantidad).doubleValue());
            ps.setString(5, LocalDate.now().toString());
            ps.setString(6, venc);
            ps.executeUpdate();
        }
    }

    private static BigDecimal sumaInventario() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT IFNULL(SUM(cantidad),0) FROM inventario_stock")) {
            return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM lotes WHERE numero_lote LIKE 'ZPHTEST%'");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (SELECT id_item FROM items WHERE codigo LIKE 'ZPHTEST%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPHTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPHTEST%'");
        }
    }
}
