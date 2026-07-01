package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Subtipo;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la clasificacion Productos / Servicios (v0.7.2): tabla parametrizable
 * de subtipos por categoria, migracion idempotente y persistencia de items con o
 * sin subtipo. Datos de prueba marcados con "ZBSUBTEST"; se eliminan al final.
 * Las semillas de la migracion (categorias y subtipos base) NO se tocan.
 */
class SubtipoServiceTest {

    private static final SubtipoService subtipoService = new SubtipoService();
    private static final ItemService itemService = new ItemService();
    private static final ItemRepository itemRepo = new ItemRepository();

    private static int idCatRepuestos;
    private static int idCatServicios;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize(); // aplica la migracion de subtipos (idempotente)
        limpiar();
        idCatRepuestos = idCategoria("Repuestos");
        idCatServicios = idCategoria("Servicios");
        assertTrue(idCatRepuestos > 0, "La migracion debe sembrar la categoria 'Repuestos'.");
        assertTrue(idCatServicios > 0, "La migracion debe sembrar la categoria 'Servicios'.");
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    @Test
    void listarSubtiposPorCategoriaDevuelveLosDeEsaCategoria() throws SQLException {
        List<Subtipo> repuestos = subtipoService.listarPorCategoria(idCatRepuestos);
        assertTrue(repuestos.stream().anyMatch(s -> s.getNombre().equals("Frenos")),
                "Repuestos debe incluir el subtipo 'Frenos'.");
        // Coherencia: todos pertenecen a la categoria consultada.
        assertTrue(repuestos.stream().allMatch(s -> s.getIdCategoria() == idCatRepuestos));
        // Y no aparecen subtipos de otra categoria (p. ej. 'Diagnostico' es de Servicios).
        assertFalse(repuestos.stream().anyMatch(s -> s.getNombre().equals("Diagnostico")),
                "Repuestos no debe incluir subtipos de Servicios.");
    }

    @Test
    void crearSubtipoAsociadoACategoria() throws SQLException {
        Subtipo s = new Subtipo();
        s.setIdCategoria(idCatServicios);
        s.setNombre("ZBSUBTEST Especial");
        int id = subtipoService.crear(s);
        assertTrue(id > 0);

        boolean aparece = subtipoService.listarPorCategoria(idCatServicios).stream()
                .anyMatch(x -> x.getIdSubtipo() == id && x.getNombre().equals("ZBSUBTEST Especial"));
        assertTrue(aparece, "El subtipo creado debe listarse en su categoria.");
    }

    @Test
    void migracionEsIdempotente_noDuplicaSemillas() throws SQLException {
        int antes = subtipoService.listarPorCategoria(idCatRepuestos).size();
        DatabaseInitializer.initialize(); // segunda ejecucion
        int despues = subtipoService.listarPorCategoria(idCatRepuestos).size();
        assertEquals(antes, despues, "Re-ejecutar la migracion no debe duplicar subtipos.");
    }

    @Test
    void itemPuedeGuardarseConSubtipoNull() throws SQLException {
        Item it = nuevoItem("ZBSUBTEST-N1", "ZBSUBTEST Sin subtipo");
        it.setIdCategoria(idCatRepuestos);
        it.setIdSubtipo(null);
        itemService.guardar(it);

        Optional<Item> rec = itemRepo.buscarPorId(it.getIdItem());
        assertTrue(rec.isPresent());
        assertNull(rec.get().getIdSubtipo(), "El subtipo debe poder quedar null.");
    }

    @Test
    void itemPuedeGuardarYRecuperarSubtipo() throws SQLException {
        Subtipo frenos = subtipoService.listarPorCategoria(idCatRepuestos).stream()
                .filter(s -> s.getNombre().equals("Frenos")).findFirst().orElseThrow();

        Item it = nuevoItem("ZBSUBTEST-S1", "ZBSUBTEST Con subtipo");
        it.setIdCategoria(idCatRepuestos);
        it.setIdSubtipo(frenos.getIdSubtipo());
        itemService.guardar(it);

        Optional<Item> rec = itemRepo.buscarPorId(it.getIdItem());
        assertTrue(rec.isPresent());
        assertNotNull(rec.get().getIdSubtipo());
        assertEquals(frenos.getIdSubtipo(), rec.get().getIdSubtipo().intValue());
        assertEquals("Frenos", rec.get().getNombreSubtipo(), "Debe recuperar el nombre del subtipo.");
    }

    @Test
    void subtipoDeOtraCategoriaSeRechaza() throws SQLException {
        // Subtipo de Servicios asignado a un item de categoria Repuestos: incoherente.
        Subtipo diag = subtipoService.listarPorCategoria(idCatServicios).stream()
                .filter(s -> s.getNombre().equals("Diagnostico")).findFirst().orElseThrow();

        Item it = nuevoItem("ZBSUBTEST-X1", "ZBSUBTEST Incoherente");
        it.setIdCategoria(idCatRepuestos);
        it.setIdSubtipo(diag.getIdSubtipo());

        assertThrows(ValidacionException.class, () -> itemService.guardar(it));
    }

    // ---- auxiliares ----

    private static Item nuevoItem(String codigo, String nombre) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre(nombre);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(false);
        it.setStockMinimo(BigDecimal.ZERO);
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static int idCategoria(String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_categoria FROM categorias WHERE nombre = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBSUBTEST%'");
            st.executeUpdate("DELETE FROM subtipos WHERE nombre LIKE 'ZBSUBTEST%'");
        }
    }
}
