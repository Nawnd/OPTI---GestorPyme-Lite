package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.InventarioRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso B (filtros de inventario + estado de stock, v0.8): calculo de estado
 * (SIN STOCK / BAJO / NORMAL), filtros combinables (categoria, subtipo, ubicacion parcial)
 * y exportacion de Inventario con Stock_Minimo/Estado_Stock. Datos marcados "ZPBTEST".
 */
class PasoBFiltrosInventarioTest {

    private static final ItemService itemService = new ItemService();
    private static final InventarioService inventarioService = new InventarioService();
    private static final InventarioRepository inventarioRepo = new InventarioRepository();
    private static final ExportacionService exportacionService = new ExportacionService();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Estado de stock (modelo, calculado) ----------------

    @Test
    void estadoSinStockBajoYNormalSeCalculan() {
        assertEquals("SIN STOCK", existencia("0", "5").getEstadoStock());
        assertEquals("BAJO", existencia("3", "5").getEstadoStock());
        assertEquals("NORMAL", existencia("10", "5").getEstadoStock());
        assertEquals("NORMAL", existencia("5", "5").getEstadoStock(), "cantidad = minimo es NORMAL");
        assertEquals("NORMAL", existencia("2", "0").getEstadoStock(), "minimo 0 y cantidad>0 es NORMAL");
        assertEquals("SIN STOCK", existencia("0", "0").getEstadoStock(), "minimo 0 y cantidad 0 es SIN STOCK");
    }

    // ---------------- Existencias: inventariables si, servicios no ----------------

    @Test
    void inventariableApareceConStockMinimoYEstado() throws SQLException {
        int idBodega = insertarBodega("ZPBTEST Bodega");
        Item prod = inventariable("ZPBTEST-INV", "5"); // stock minimo 5
        itemService.guardar(prod);
        sembrarStock(prod.getIdItem(), idBodega, "3"); // BAJO

        ExistenciaStock e = buscar(prod.getIdItem(), idBodega);
        assertTrue(e != null, "El producto inventariable debe aparecer en existencias.");
        assertEquals(0, e.getStockMinimo().compareTo(new BigDecimal("5")));
        assertEquals("BAJO", e.getEstadoStock());
    }

    @Test
    void servicioNoApareceEnExistencias() throws SQLException {
        Item serv = new Item();
        serv.setCodigo("ZPBTEST-SRV");
        serv.setNombre("ZPBTEST Servicio");
        serv.setTipoItem(TipoItem.SERVICIO);
        serv.setPrecioCompra(BigDecimal.ZERO);
        serv.setPrecioVenta(new BigDecimal("1000"));
        serv.setControlaInventario(false);
        serv.setStockMinimo(BigDecimal.ZERO);
        serv.setEstado(EstadoRegistro.ACTIVO);
        itemService.guardar(serv);

        boolean aparece = inventarioRepo.listarExistencias().stream()
                .anyMatch(x -> "ZPBTEST-SRV".equals(x.getCodigoItem()));
        assertFalse(aparece, "Un servicio no debe aparecer como stock fisico.");
    }

    // ---------------- Filtros (servicio puro) ----------------

    @Test
    void filtraPorCategoriaSubtipoYUbicacionParcial() {
        List<ExistenciaStock> base = List.of(
                conDatos("R_0001", "Repuestos", "Frenos", "Estante A1", "10", "5"),
                conDatos("R_0002", "Repuestos", "Motor", "Estante B2", "2", "5"),
                conDatos("I_0001", "Insumos", "Lubricante", "Bodega Fondo", "0", "1"));

        // Categoria
        assertEquals(2, inventarioService.filtrarExistencias(base, null, null, "Repuestos", null, null, null).size());
        // Subtipo
        assertEquals(1, inventarioService.filtrarExistencias(base, null, null, null, "Motor", null, null).size());
        // Ubicacion parcial (case-insensitive): "a1" encuentra "Estante A1"
        List<ExistenciaStock> porUbic = inventarioService.filtrarExistencias(base, null, null, null, null, null, "a1");
        assertEquals(1, porUbic.size());
        assertEquals("R_0001", porUbic.get(0).getCodigoItem());
        // Estado de stock
        assertEquals(1, inventarioService.filtrarExistencias(base, null, null, null, null, "SIN STOCK", null).size());
    }

    @Test
    void filtrosCombinadosSeAplicanConY() {
        List<ExistenciaStock> base = List.of(
                conDatos("R_0001", "Repuestos", "Frenos", "Estante A1", "10", "5"),
                conDatos("R_0002", "Repuestos", "Frenos", "Estante A2", "2", "5"));
        // Categoria Repuestos + estado BAJO -> solo R_0002 (cantidad 2 < min 5)
        List<ExistenciaStock> r = inventarioService.filtrarExistencias(
                base, null, null, "Repuestos", "Frenos", "BAJO", null);
        assertEquals(1, r.size());
        assertEquals("R_0002", r.get(0).getCodigoItem());
        // Sin filtros -> todas
        assertEquals(2, inventarioService.filtrarExistencias(base, null, null, null, null, null, null).size());
    }

    // ---------------- Exportacion incluye Estado_Stock ----------------

    @Test
    void exportInventarioIncluyeStockMinimoYEstado() throws SQLException, IOException {
        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "zpb_inventario.csv");
        exportacionService.exportar(TipoExportacion.INVENTARIO, archivo.toString(), 1);
        String contenido = new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);
        if (contenido.startsWith("\uFEFF")) {
            contenido = contenido.substring(1);
        }
        String encabezado = contenido.split("\r?\n", 2)[0];
        assertEquals("Codigo item;Item;Bodega;Ubicacion;Cantidad;Stock_Minimo;Estado_Stock", encabezado);
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zpb_%'");
        }
    }

    // ---------------- auxiliares ----------------

    private static ExistenciaStock existencia(String cantidad, String stockMinimo) {
        return new ExistenciaStock(0, 0, "X", "X", "Cat", "Sub", "Bod",
                new BigDecimal(cantidad), new BigDecimal(stockMinimo), null);
    }

    private static ExistenciaStock conDatos(String codigo, String categoria, String subtipo,
                                            String ubicacion, String cantidad, String stockMinimo) {
        return new ExistenciaStock(0, 0, codigo, "Item " + codigo, categoria, subtipo, "Bodega Principal",
                new BigDecimal(cantidad), new BigDecimal(stockMinimo), ubicacion);
    }

    private static Item inventariable(String codigo, String stockMinimo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPBTEST " + codigo);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setIdCategoria(1); // categoria semilla "General"
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal(stockMinimo));
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static ExistenciaStock buscar(int idItem, int idBodega) throws SQLException {
        for (ExistenciaStock e : inventarioRepo.listarExistencias()) {
            if (e.getIdItem() == idItem && e.getIdBodega() == idBodega) {
                return e;
            }
        }
        return null;
    }

    private static int insertarBodega(String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void sembrarStock(int idItem, int idBodega, String cantidad) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setDouble(3, new BigDecimal(cantidad).doubleValue());
            ps.executeUpdate();
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPBTEST%'";
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPBTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPBTEST%'");
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zpb_%'");
        }
    }
}
