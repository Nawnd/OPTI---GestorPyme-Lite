package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueba del registro transaccional de movimientos de inventario.
 * Crea un item y una bodega temporales, registra una entrada y una salida,
 * verifica el stock resultante y al final elimina todos los datos de prueba.
 */
class InventarioRepositoryTest {

    private final InventarioRepository inventarioRepository = new InventarioRepository();
    private final ItemRepository itemRepository = new ItemRepository();
    private final BodegaRepository bodegaRepository = new BodegaRepository();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void entradaYSalidaActualizanElStock() throws SQLException {
        int idItem = 0;
        int idBodega = 0;
        try {
            idItem = crearItemInventariable();
            idBodega = crearBodega();

            // Entrada de 10 -> stock = 10
            inventarioRepository.registrarMovimiento(idItem, idBodega,
                    TipoMovimiento.ENTRADA, new BigDecimal("10"), "Carga inicial", null);
            assertEquals(0, new BigDecimal("10").compareTo(inventarioRepository.obtenerCantidad(idItem, idBodega)),
                    "Tras la entrada el stock deberia ser 10");

            // Salida de 3 -> stock = 7
            inventarioRepository.registrarMovimiento(idItem, idBodega,
                    TipoMovimiento.SALIDA, new BigDecimal("3"), "Consumo", null);
            assertEquals(0, new BigDecimal("7").compareTo(inventarioRepository.obtenerCantidad(idItem, idBodega)),
                    "Tras la salida el stock deberia ser 7");
        } finally {
            limpiar(idItem, idBodega);
        }
    }

    private int crearItemInventariable() throws SQLException {
        Item item = new Item();
        item.setNombre("PRUEBA Item Inventario");
        item.setTipoItem(TipoItem.PRODUCTO);
        item.setControlaInventario(true);
        item.setEstado(EstadoRegistro.ACTIVO);
        return itemRepository.insertar(item);
    }

    private int crearBodega() throws SQLException {
        Bodega bodega = new Bodega();
        bodega.setNombre("PRUEBA Bodega " + System.nanoTime());
        bodega.setEstado(EstadoRegistro.ACTIVO);
        return bodegaRepository.insertar(bodega);
    }

    /** Limpieza: borra movimientos, stock, item y bodega de prueba (en orden por las FK). */
    private void limpiar(int idItem, int idBodega) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            if (idItem > 0) {
                ejecutar(conn, "DELETE FROM inventario_movimientos WHERE id_item = ?", idItem);
                ejecutar(conn, "DELETE FROM inventario_stock WHERE id_item = ?", idItem);
                ejecutar(conn, "DELETE FROM items WHERE id_item = ?", idItem);
            }
            if (idBodega > 0) {
                ejecutar(conn, "DELETE FROM bodegas WHERE id_bodega = ?", idBodega);
            }
        }
    }

    private void ejecutar(Connection conn, String sql, int id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }
}
