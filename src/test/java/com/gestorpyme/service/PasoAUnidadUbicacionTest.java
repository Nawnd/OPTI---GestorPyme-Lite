package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.InventarioRepository;
import com.gestorpyme.repository.ItemRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso A (estandarizacion operativa, v0.8): unidad de medida en productos
 * (default "Unidad") y ubicacion interna en existencias (por item+bodega, no afecta stock).
 * Datos de prueba marcados con "ZPATEST". No altera cantidades ni Kardex.
 */
class PasoAUnidadUbicacionTest {

    private static final ItemService itemService = new ItemService();
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final InventarioService inventarioService = new InventarioService();
    private static final InventarioRepository inventarioRepo = new InventarioRepository();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize(); // aplica la migracion del Paso A (idempotente)
        limpiar();
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Unidad de medida ----------------

    @Test
    void unidadPorDefectoEsUnidadCuandoNoSeIndica() throws SQLException {
        Item it = nuevo("ZPATEST-U1", null); // sin unidad
        itemService.guardar(it);
        Optional<Item> rec = itemRepo.buscarPorId(it.getIdItem());
        assertTrue(rec.isPresent());
        assertEquals("Unidad", rec.get().getUnidadMedida());
    }

    @Test
    void unidadPersonalizadaSeGuarda() throws SQLException {
        Item it = nuevo("ZPATEST-U2", "Caja");
        itemService.guardar(it);
        assertEquals("Caja", itemRepo.buscarPorId(it.getIdItem()).orElseThrow().getUnidadMedida());
    }

    @Test
    void unidadVaciaSeNormalizaAUnidad() throws SQLException {
        Item it = nuevo("ZPATEST-U3", "   ");
        itemService.guardar(it);
        assertEquals("Unidad", itemRepo.buscarPorId(it.getIdItem()).orElseThrow().getUnidadMedida());
    }

    // ---------------- Ubicacion interna ----------------

    @Test
    void existenciaPuedeTenerUbicacionNullYLuegoGuardarla() throws SQLException {
        int idBodega = insertarBodega("ZPATEST Bodega");
        Item prod = nuevo("ZPATEST-P1", "Unidad");
        prod.setTipoItem(TipoItem.REPUESTO);
        prod.setControlaInventario(true);
        itemService.guardar(prod);
        sembrarStock(prod.getIdItem(), idBodega, "10");

        // Sin ubicacion: debe leerse null/vacia y la cantidad debe ser 10.
        ExistenciaStock antes = buscarExistencia(prod.getIdItem(), idBodega);
        assertTrue(antes != null, "Debe existir la existencia sembrada.");
        assertTrue(antes.getUbicacionInterna() == null || antes.getUbicacionInterna().isEmpty(),
                "La ubicacion inicial debe ser nula/vacia.");
        assertEquals(0, antes.getCantidad().compareTo(new BigDecimal("10")));

        // Se asigna ubicacion; la cantidad NO debe cambiar.
        inventarioService.actualizarUbicacion(prod.getIdItem(), idBodega, "  Estante A1  ");
        ExistenciaStock despues = buscarExistencia(prod.getIdItem(), idBodega);
        assertEquals("Estante A1", despues.getUbicacionInterna(), "Debe normalizar espacios (trim).");
        assertEquals(0, despues.getCantidad().compareTo(new BigDecimal("10")),
                "La cantidad no debe cambiar al editar la ubicacion.");
    }

    @Test
    void ubicacionVaciaSeGuardaComoNull() throws SQLException {
        int idBodega = insertarBodega("ZPATEST Bodega 2");
        Item prod = nuevo("ZPATEST-P2", "Unidad");
        prod.setTipoItem(TipoItem.REPUESTO);
        prod.setControlaInventario(true);
        itemService.guardar(prod);
        sembrarStock(prod.getIdItem(), idBodega, "5");

        inventarioService.actualizarUbicacion(prod.getIdItem(), idBodega, "Estante B2");
        inventarioService.actualizarUbicacion(prod.getIdItem(), idBodega, "   "); // se limpia
        assertNull(buscarExistencia(prod.getIdItem(), idBodega).getUbicacionInterna());
    }

    // ---------------- Migracion idempotente ----------------

    @Test
    void migracionEsIdempotenteYColumnasExisten() throws SQLException {
        // Segunda (y tercera) ejecucion no debe fallar ni duplicar columnas.
        DatabaseInitializer.initialize();
        DatabaseInitializer.initialize();
        assertTrue(columnaExiste("items", "unidad_medida"), "items.unidad_medida debe existir.");
        assertTrue(columnaExiste("inventario_stock", "ubicacion_interna"),
                "inventario_stock.ubicacion_interna debe existir.");
    }

    // ---------------- auxiliares ----------------

    private static Item nuevo(String codigo, String unidad) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPATEST " + codigo);
        it.setTipoItem(TipoItem.PRODUCTO);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(false);
        it.setStockMinimo(BigDecimal.ZERO);
        it.setEstado(EstadoRegistro.ACTIVO);
        it.setUnidadMedida(unidad);
        return it;
    }

    private static ExistenciaStock buscarExistencia(int idItem, int idBodega) throws SQLException {
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

    private static boolean columnaExiste(String tabla, String columna) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement("PRAGMA table_info(" + tabla + ")");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                if (columna.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPATEST%'";
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPATEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPATEST%'");
        }
    }
}
