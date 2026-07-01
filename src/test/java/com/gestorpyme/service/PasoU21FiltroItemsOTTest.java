package com.gestorpyme.service;

import com.gestorpyme.controller.ItemController;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Subpaso U.2.1: filtros de items para la Orden de Trabajo.
 *
 * <p>Verifican que las nuevas busquedas usadas por el formulario de OT separan correctamente
 * los items: la pestana Servicios solo debe ofrecer SERVICIO / MANO_OBRA, y la pestana Repuestos
 * solo productos inventariables (controla_inventario = 1, excluyendo servicios). Estas pruebas
 * NO tocan inventario ni ventas (el aviso de disponibilidad es solo lectura y la UI no se ejercita
 * aqui); el resto de la suite ya garantiza que la OT no mueve stock ni crea ventas (Paso U.2).</p>
 *
 * <p>Aislamiento: todos los items de prueba se marcan con el prefijo de codigo {@code ZPU21} y se
 * eliminan en {@link #limpiar()}. El esquema se asegura con {@link DatabaseInitializer#initialize()}
 * en {@link #preparar()} porque las pruebas no arrancan la aplicacion.</p>
 */
class PasoU21FiltroItemsOTTest {

    /** Marcador para aislar y limpiar los datos de esta prueba. */
    private static final String MARCA = "ZPU21";

    private static final ItemController itemController = new ItemController();

    private static int idServicio;
    private static int idManoObra;
    private static int idProductoInventariable;
    private static int idProductoNoInventariable;

    @BeforeAll
    static void preparar() throws SQLException {
        // La migracion solo corre al arrancar la app; en pruebas hay que invocarla explicitamente.
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiarItems(c);
            idServicio = insertarItem(c, MARCA + "-SRV", MARCA + " Servicio", "SERVICIO", 30000, 0);
            idManoObra = insertarItem(c, MARCA + "-MO", MARCA + " Mano de obra", "MANO_OBRA", 25000, 0);
            idProductoInventariable =
                    insertarItem(c, MARCA + "-PRODINV", MARCA + " Producto inventariable", "PRODUCTO", 18000, 1);
            idProductoNoInventariable =
                    insertarItem(c, MARCA + "-PRODNOINV", MARCA + " Producto no inventariable", "PRODUCTO", 12000, 0);
        }
    }

    @AfterAll
    static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiarItems(c);
        }
    }

    // ------------------------------------------------------------------ servicios

    @Test
    void serviciosIncluyeServicio() throws SQLException {
        assertTrue(codigos(itemController.buscarServicios(MARCA, 50)).contains(MARCA + "-SRV"),
                "La busqueda de servicios debe incluir el item SERVICIO");
    }

    @Test
    void serviciosIncluyeManoObra() throws SQLException {
        assertTrue(codigos(itemController.buscarServicios(MARCA, 50)).contains(MARCA + "-MO"),
                "La busqueda de servicios debe incluir el item MANO_OBRA");
    }

    @Test
    void serviciosExcluyeProducto() throws SQLException {
        Set<String> resultado = codigos(itemController.buscarServicios(MARCA, 50));
        assertFalse(resultado.contains(MARCA + "-PRODINV"),
                "La busqueda de servicios NO debe incluir un PRODUCTO inventariable");
        assertFalse(resultado.contains(MARCA + "-PRODNOINV"),
                "La busqueda de servicios NO debe incluir un PRODUCTO no inventariable");
    }

    // ------------------------------------------------------------------ repuestos

    @Test
    void repuestosIncluyeProductoInventariable() throws SQLException {
        assertTrue(codigos(itemController.buscarInventariables(MARCA, 50)).contains(MARCA + "-PRODINV"),
                "La busqueda de repuestos debe incluir un PRODUCTO inventariable");
    }

    @Test
    void repuestosExcluyeServicios() throws SQLException {
        Set<String> resultado = codigos(itemController.buscarInventariables(MARCA, 50));
        assertFalse(resultado.contains(MARCA + "-SRV"),
                "La busqueda de repuestos NO debe incluir un SERVICIO");
        assertFalse(resultado.contains(MARCA + "-MO"),
                "La busqueda de repuestos NO debe incluir MANO_OBRA");
    }

    @Test
    void repuestosExcluyeProductoNoInventariable() throws SQLException {
        assertFalse(codigos(itemController.buscarInventariables(MARCA, 50)).contains(MARCA + "-PRODNOINV"),
                "La busqueda de repuestos NO debe incluir un producto que no controla inventario");
    }

    // ------------------------------------------------------------------ helpers

    /** Extrae el conjunto de codigos de una lista de items. */
    private static Set<String> codigos(List<Item> items) {
        Set<String> set = new HashSet<>();
        for (Item it : items) {
            set.add(it.getCodigo());
        }
        return set;
    }

    /** Inserta un item de prueba y devuelve su id generado. */
    private static int insertarItem(Connection c, String codigo, String nombre, String tipo,
                                    long precioVenta, int controla) throws SQLException {
        return insertarConId(c, "INSERT INTO items "
                + "(codigo, nombre, tipo_item, precio_compra, precio_venta, controla_inventario, "
                + "stock_minimo, estado, fecha_creacion, modo_calculo_servicio, unidad_medida) VALUES ("
                + "'" + codigo + "', '" + nombre + "', '" + tipo + "', 0, " + precioVenta + ", "
                + controla + ", 0, 'ACTIVO', datetime('now','localtime'), 'FIJO', 'Unidad')");
    }

    /** Ejecuta un INSERT y devuelve la clave generada. */
    private static int insertarConId(Connection c, String sql) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Elimina los items marcados con el prefijo de la prueba (limpieza FK-segura: items no referenciados). */
    private static void limpiarItems(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM items WHERE codigo LIKE ?")) {
            ps.setString(1, MARCA + "%");
            ps.executeUpdate();
        }
    }
}
