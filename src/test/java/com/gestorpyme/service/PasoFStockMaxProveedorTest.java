package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.domain.enums.TipoExportacion;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso F (stock maximo + proveedor preferido).
 *
 * Cubre: persistencia y carga de stock maximo (null/valido), validaciones (negativo, menor que el
 * minimo, limpieza cuando no controla inventario), proveedor preferido (valido, cliente rechazado,
 * proveedor inactivo rechazado, null permitido), formula de sugerido con techo, exportacion CSV de
 * Productos y Reabastecimiento, y migracion idempotente.
 *
 * Aislamiento: todos los datos se marcan con prefijo "ZPFTEST" y las aserciones consultan solo esas
 * filas; no se leen agregados globales, por lo que son independientes de los datos reales.
 */
class PasoFStockMaxProveedorTest {

    private static final ItemService itemService = new ItemService();
    private static final ItemRepository itemRepository = new ItemRepository();
    private static final InventarioLogisticoService logistico = new InventarioLogisticoService();
    private static final ExportacionService exportacionService = new ExportacionService();

    private static int idProveedorActivo;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idProveedorActivo = insertarTercero("ZPFTEST Proveedor Bueno", "PROVEEDOR", "ACTIVO");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Stock maximo: persistencia y validaciones ----------------

    @Test
    void productoExistenteCargaStockMaximoNull() throws SQLException {
        Item it = inventariable("ZPFTEST-NULL", "5");
        itemService.guardar(it);
        Optional<Item> leido = itemRepository.buscarPorId(it.getIdItem());
        assertTrue(leido.isPresent());
        assertNull(leido.get().getStockMaximo(), "Sin definir, el stock maximo debe ser null.");
    }

    @Test
    void guardaStockMaximoValido() throws SQLException {
        Item it = inventariable("ZPFTEST-MAXOK", "10");
        it.setStockMaximo(new BigDecimal("30"));
        itemService.guardar(it);
        Item leido = itemRepository.buscarPorId(it.getIdItem()).orElseThrow();
        assertEquals(0, new BigDecimal("30").compareTo(leido.getStockMaximo()));
    }

    @Test
    void stockMaximoNegativoSeRechaza() {
        Item it = inventariable("ZPFTEST-NEG", "5");
        it.setStockMaximo(new BigDecimal("-1"));
        assertThrows(ValidacionException.class, () -> itemService.guardar(it));
    }

    @Test
    void stockMaximoMenorQueMinimoSeRechaza() {
        Item it = inventariable("ZPFTEST-MENOR", "10");
        it.setStockMaximo(new BigDecimal("5"));
        assertThrows(ValidacionException.class, () -> itemService.guardar(it));
    }

    @Test
    void itemSinInventarioLimpiaStockMaximo() throws SQLException {
        Item it = inventariable("ZPFTEST-SERV", "0");
        it.setControlaInventario(false);
        it.setTipoItem(TipoItem.SERVICIO);
        it.setStockMaximo(new BigDecimal("20"));
        itemService.guardar(it);
        assertNull(it.getStockMaximo(), "Si no controla inventario, el stock maximo se limpia a null.");
        Item leido = itemRepository.buscarPorId(it.getIdItem()).orElseThrow();
        assertNull(leido.getStockMaximo());
    }

    // ---------------- Proveedor preferido ----------------

    @Test
    void guardaProveedorPreferidoValido() throws SQLException {
        Item it = inventariable("ZPFTEST-PROV", "5");
        it.setIdProveedorPreferido(idProveedorActivo);
        itemService.guardar(it);
        Item leido = itemRepository.buscarPorId(it.getIdItem()).orElseThrow();
        assertEquals(Integer.valueOf(idProveedorActivo), leido.getIdProveedorPreferido());
        assertEquals("ZPFTEST Proveedor Bueno", leido.getNombreProveedorPreferido());
    }

    @Test
    void clienteNoPuedeSerProveedorPreferido() throws SQLException {
        int idCliente = insertarTercero("ZPFTEST Cliente", "CLIENTE", "ACTIVO");
        Item it = inventariable("ZPFTEST-CLI", "5");
        it.setIdProveedorPreferido(idCliente);
        assertThrows(ValidacionException.class, () -> itemService.guardar(it));
    }

    @Test
    void proveedorInactivoNoPuedeSerPreferido() throws SQLException {
        int idInactivo = insertarTercero("ZPFTEST Proveedor Malo", "PROVEEDOR", "INACTIVO");
        Item it = inventariable("ZPFTEST-INACT", "5");
        it.setIdProveedorPreferido(idInactivo);
        assertThrows(ValidacionException.class, () -> itemService.guardar(it));
    }

    @Test
    void sinProveedorPreferidoFunciona() throws SQLException {
        Item it = inventariable("ZPFTEST-SINPROV", "5");
        it.setIdProveedorPreferido(null);
        itemService.guardar(it);
        Item leido = itemRepository.buscarPorId(it.getIdItem()).orElseThrow();
        assertNull(leido.getIdProveedorPreferido());
    }

    // ---------------- Formula de sugerido con stock maximo ----------------

    @Test
    void sugeridoConMaximoReponeHastaMaximo() {
        // min 10, max 30, actual 4, en pedido 5 -> faltante al minimo (1) > 0 -> 30 - 4 - 5 = 21.
        assertEquals(0, new BigDecimal("21").compareTo(
                logistico.calcularSugerido(n("4"), n("10"), n("30"), n("5"))));
    }

    @Test
    void sugeridoSinMaximoMantieneFormula() {
        // min 10, max null, actual 3, en pedido 5 -> 10 - 3 - 5 = 2.
        assertEquals(0, new BigDecimal("2").compareTo(
                logistico.calcularSugerido(n("3"), n("10"), null, n("5"))));
    }

    @Test
    void enPedidoCubreMinimoSugeridoCeroAunqueHayaMaximo() {
        // min 10, max 30, actual 3, en pedido 10 -> faltante al minimo (-3) <= 0 -> 0.
        assertEquals(0, BigDecimal.ZERO.compareTo(
                logistico.calcularSugerido(n("3"), n("10"), n("30"), n("10"))));
    }

    // ---------------- Exportacion CSV ----------------

    @Test
    void exportItemsIncluyeStockMaximoYProveedor() throws SQLException, IOException {
        Item it = inventariable("ZPFTEST-EXPI", "10");
        it.setPrecioCompra(new BigDecimal("1500"));
        it.setPrecioVenta(new BigDecimal("2500"));
        it.setStockMaximo(new BigDecimal("30"));
        it.setIdProveedorPreferido(idProveedorActivo);
        itemService.guardar(it);

        String linea = filaConCodigo(TipoExportacion.ITEMS, "ZPFTEST-EXPI");
        assertTrue(linea.contains(";10;30;"), "Items debe exportar Stock_Minimo;Stock_Maximo. Linea: " + linea);
        assertTrue(linea.contains("ZPFTEST Proveedor Bueno"), "Items debe exportar el proveedor preferido.");
    }

    @Test
    void exportReabastecimientoIncluyeStockMaximoYProveedor() throws SQLException, IOException {
        Item it = inventariable("ZPFTEST-EXPR", "10");
        it.setStockMaximo(new BigDecimal("30"));
        it.setIdProveedorPreferido(idProveedorActivo);
        itemService.guardar(it);

        String linea = filaConCodigo(TipoExportacion.REABASTECIMIENTO, "ZPFTEST-EXPR");
        // Sin stock ni en pedido: actual 0, min 10, max 30 -> sugerido 30. Columnas ...;10;30;0;30;Proveedor;...
        assertTrue(linea.contains(";10;30;"), "Reabastecimiento debe incluir Stock_Maximo. Linea: " + linea);
        assertTrue(linea.contains("ZPFTEST Proveedor Bueno"), "Reabastecimiento debe incluir el proveedor preferido.");
    }

    // ---------------- Migracion idempotente ----------------

    @Test
    void migracionIdempotente() throws SQLException {
        // Ejecutarla de nuevo no debe fallar ni duplicar columnas.
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.getConnection()) {
            assertTrue(columnaExiste(c, "stock_maximo"), "items.stock_maximo debe existir.");
            assertTrue(columnaExiste(c, "id_proveedor_preferido"), "items.id_proveedor_preferido debe existir.");
        }
    }

    // ---------------- auxiliares ----------------

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    private static Item inventariable(String codigo, String stockMinimo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPFTEST " + codigo);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal(stockMinimo));
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static int insertarTercero(String nombre, String tipo, String estado) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.setString(2, tipo);
            ps.setString(3, estado);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static boolean columnaExiste(Connection c, String columna) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("PRAGMA table_info(items)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (columna.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Exporta a un archivo temporal y devuelve la linea que contiene el codigo dado. */
    private static String filaConCodigo(TipoExportacion tipo, String codigo) throws SQLException, IOException {
        Path archivo = Files.createTempFile("zpftest", ".csv");
        exportacionService.exportar(tipo, archivo.toString(), 1);
        List<String> lineas = Files.readAllLines(archivo);
        Files.deleteIfExists(archivo);
        for (String l : lineas) {
            if (l.contains(codigo)) {
                return l;
            }
        }
        return "";
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (SELECT id_item FROM items WHERE codigo LIKE 'ZPFTEST%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPFTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPFTEST%'");
        }
    }
}
