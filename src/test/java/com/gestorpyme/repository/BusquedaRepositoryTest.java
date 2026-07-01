package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la busqueda inteligente en repositorios (LIKE + LIMIT). Inserta datos
 * marcados con el prefijo "ZBUSCATEST" y los elimina al final para no dejar residuos.
 */
class BusquedaRepositoryTest {

    private static final ItemRepository itemRepo = new ItemRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();

        Item it = new Item();
        it.setCodigo("ZBUSCATEST-IT");
        it.setNombre("ZBUSCATEST Tornillo Especial");
        it.setTipoItem(TipoItem.PRODUCTO);
        it.setPrecioCompra(new BigDecimal("100"));
        it.setPrecioVenta(new BigDecimal("150"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal("5"));
        it.setEstado(EstadoRegistro.ACTIVO);
        itemRepo.insertar(it);

        terceroRepo.insertar(tercero("ZBUSCATEST Cliente Ana", TipoTercero.CLIENTE));
        terceroRepo.insertar(tercero("ZBUSCATEST Proveedor Beta", TipoTercero.PROVEEDOR));
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    @Test
    void buscaItemPorCodigo() throws SQLException {
        List<Item> r = itemRepo.buscar("ZBUSCATEST-IT", 20);
        assertTrue(r.stream().anyMatch(i -> "ZBUSCATEST-IT".equals(i.getCodigo())),
                "Debe encontrar el item por codigo");
    }

    @Test
    void buscaItemPorNombre() throws SQLException {
        List<Item> r = itemRepo.buscar("Tornillo Especial", 20);
        assertTrue(r.stream().anyMatch(i -> i.getNombre().contains("Tornillo Especial")),
                "Debe encontrar el item por nombre");
    }

    @Test
    void buscaClientePorNombre() throws SQLException {
        List<Tercero> r = terceroRepo.buscarClientesYProspectos("ZBUSCATEST Cliente", 20);
        assertTrue(r.stream().anyMatch(t -> t.getNombre().contains("Cliente Ana")),
                "Debe encontrar el cliente por nombre");
    }

    @Test
    void buscaProveedorPorNombre() throws SQLException {
        List<Tercero> r = terceroRepo.buscarProveedores("ZBUSCATEST Proveedor", 20);
        assertTrue(r.stream().anyMatch(t -> t.getNombre().contains("Proveedor Beta")),
                "Debe encontrar el proveedor por nombre");
    }

    @Test
    void proveedorNoApareceEnBusquedaDeClientes() throws SQLException {
        List<Tercero> r = terceroRepo.buscarClientesYProspectos("ZBUSCATEST Proveedor", 20);
        assertFalse(r.stream().anyMatch(t -> t.getNombre().contains("Proveedor Beta")),
                "Un proveedor no debe aparecer en la busqueda de clientes/prospectos");
    }

    @Test
    void busquedaSinResultados() throws SQLException {
        assertTrue(itemRepo.buscar("xyz_no_existe_zbuscatest_999", 20).isEmpty(),
                "Una busqueda sin coincidencias debe devolver lista vacia");
    }

    private static Tercero tercero(String nombre, TipoTercero tipo) {
        Tercero t = new Tercero();
        t.setNombre(nombre);
        t.setTipoTercero(tipo);
        t.setEstado(EstadoRegistro.ACTIVO);
        return t;
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBUSCATEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZBUSCATEST%'");
        }
    }
}
