package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba del ciclo completo de acceso a datos de items.
 * Inserta un registro temporal, lo verifica y al final lo elimina fisicamente
 * (solo como limpieza de la prueba; el repositorio en si usa baja logica).
 */
class ItemRepositoryTest {

    private final ItemRepository repositorio = new ItemRepository();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void cicloInsertarBuscarActualizarCambiarEstado() throws SQLException {
        int id = 0;
        try {
            // Insertar
            Item nuevo = new Item();
            nuevo.setNombre("PRUEBA Producto Temporal");
            nuevo.setTipoItem(TipoItem.PRODUCTO);
            nuevo.setPrecioCompra(new BigDecimal("100.50"));
            nuevo.setPrecioVenta(new BigDecimal("150.00"));
            nuevo.setControlaInventario(true);
            nuevo.setStockMinimo(new BigDecimal("5"));
            nuevo.setEstado(EstadoRegistro.ACTIVO);
            id = repositorio.insertar(nuevo);
            assertTrue(id > 0, "El id generado deberia ser positivo");

            // Buscar
            Optional<Item> guardado = repositorio.buscarPorId(id);
            assertTrue(guardado.isPresent(), "Deberia encontrarse el item insertado");
            assertEquals("PRUEBA Producto Temporal", guardado.get().getNombre());
            assertEquals(0, new BigDecimal("150.00").compareTo(guardado.get().getPrecioVenta()),
                    "El precio de venta deberia conservarse");

            // Actualizar
            Item editar = guardado.get();
            editar.setNombre("PRUEBA Producto Editado");
            repositorio.actualizar(editar);
            assertEquals("PRUEBA Producto Editado",
                    repositorio.buscarPorId(id).orElseThrow().getNombre());

            // Cambiar estado (baja logica)
            repositorio.cambiarEstado(id, EstadoRegistro.INACTIVO);
            assertEquals(EstadoRegistro.INACTIVO,
                    repositorio.buscarPorId(id).orElseThrow().getEstado());
        } finally {
            if (id > 0) {
                eliminarFisicamente(id); // limpieza: el registro de prueba no debe quedar
            }
        }
    }

    /** Borrado fisico SOLO para limpieza de esta prueba. */
    private void eliminarFisicamente(int idItem) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id_item = ?")) {
            ps.setInt(1, idItem);
            ps.executeUpdate();
        }
    }
}
