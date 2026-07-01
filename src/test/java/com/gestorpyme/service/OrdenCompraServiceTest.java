package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.repository.TerceroRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link OrdenCompraService}. Verifican las validaciones de negocio y que una
 * orden valida genera consecutivo OC-. Usan datos marcados con "ZBPASOB" que se eliminan
 * al final (la migracion del Paso B la aplica DatabaseInitializer en @BeforeAll).
 */
class OrdenCompraServiceTest {

    private static final OrdenCompraService servicio = new OrdenCompraService();
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();

    private static int idProveedor;
    private static int idItem;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize(); // aplica la migracion de compras si hace falta
        limpiar();

        Tercero prov = new Tercero();
        prov.setNombre("ZBPASOB Proveedor OC");
        prov.setTipoTercero(TipoTercero.PROVEEDOR);
        prov.setEstado(EstadoRegistro.ACTIVO);
        idProveedor = terceroRepo.insertar(prov);

        Item it = new Item();
        it.setCodigo("ZBPASOB-OC-IT");
        it.setNombre("ZBPASOB Item OC");
        it.setTipoItem(TipoItem.PRODUCTO);
        it.setPrecioCompra(new BigDecimal("100"));
        it.setPrecioVenta(new BigDecimal("150"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal("5"));
        it.setEstado(EstadoRegistro.ACTIVO);
        idItem = itemRepo.insertar(it);
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    private OrdenCompraDetalle linea() {
        OrdenCompraDetalle d = new OrdenCompraDetalle();
        d.setIdItem(idItem);
        d.setCantidadSolicitada(new BigDecimal("5"));
        d.setPrecioUnitario(new BigDecimal("100"));
        return d;
    }

    @Test
    void crearOrdenValidaGeneraNumero() throws SQLException {
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(idProveedor);
        o.getDetalles().add(linea());
        String numero = servicio.crear(o);
        assertTrue(numero.startsWith("OC-"), "El numero de orden debe iniciar con OC-");
    }

    @Test
    void sinProveedorLanzaExcepcion() {
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(0);
        o.getDetalles().add(linea());
        assertThrows(ValidacionException.class, () -> servicio.crear(o));
    }

    @Test
    void sinDetalleLanzaExcepcion() {
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(idProveedor);
        assertThrows(ValidacionException.class, () -> servicio.crear(o));
    }

    @Test
    void cantidadCeroLanzaExcepcion() {
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(idProveedor);
        OrdenCompraDetalle d = linea();
        d.setCantidadSolicitada(BigDecimal.ZERO);
        o.getDetalles().add(d);
        assertThrows(ValidacionException.class, () -> servicio.crear(o));
    }

    @Test
    void precioNegativoLanzaExcepcion() {
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(idProveedor);
        OrdenCompraDetalle d = linea();
        d.setPrecioUnitario(new BigDecimal("-1"));
        o.getDetalles().add(d);
        assertThrows(ValidacionException.class, () -> servicio.crear(o));
    }

    @Test
    void crearOrdenNoAumentaStock() throws SQLException {
        double antes = stockDe(idItem);
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(idProveedor);
        o.getDetalles().add(linea());
        servicio.crear(o);
        assertEquals(antes, stockDe(idItem), 0.0001,
                "Crear la orden NO debe modificar el stock (solo la recepcion lo aumenta).");
    }

    @Test
    void listarProveedoresIncluyeElCreado() throws SQLException {
        boolean encontrado = new com.gestorpyme.controller.TerceroController()
                .listarProveedores().stream()
                .anyMatch(t -> t.getIdTercero() == idProveedor);
        assertTrue(encontrado, "El proveedor creado debe aparecer en el modulo Proveedores.");
    }

    private static double stockDe(int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT IFNULL(SUM(cantidad),0) FROM inventario_stock WHERE id_item = ?")) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM ordenes_compra_detalles WHERE id_orden IN "
                    + "(SELECT id_orden FROM ordenes_compra WHERE numero_orden LIKE 'OC-%' "
                    + " AND id_proveedor IN (SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBPASOB%'))");
            st.executeUpdate("DELETE FROM ordenes_compra WHERE id_proveedor IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBPASOB%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBPASOB%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZBPASOB%'");
        }
    }
}
