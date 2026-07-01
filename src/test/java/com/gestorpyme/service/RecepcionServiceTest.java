package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.domain.model.RecepcionDetalle;
import com.gestorpyme.domain.model.RecepcionMercancia;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.BodegaRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de integracion de {@link RecepcionService} contra la base de datos real:
 * recepcion parcial (stock sube, Kardex ENTRADA_COMPRA, orden PARCIALMENTE_RECIBIDA),
 * rechazo al recibir mas de lo pendiente (sin alterar nada) y recepcion total (RECIBIDA).
 * Datos marcados "ZBPASOB"; se eliminan al final. Cada prueba usa un item propio para
 * aislar el stock. La migracion del Paso B la aplica DatabaseInitializer en @BeforeAll.
 */
class RecepcionServiceTest {

    private static final OrdenCompraService ordenService = new OrdenCompraService();
    private static final RecepcionService recepcionService = new RecepcionService();
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();
    private static final BodegaRepository bodegaRepo = new BodegaRepository();

    private static int idProveedor;
    private static int idBodega;
    private static final AtomicInteger seq = new AtomicInteger(0);

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();

        Tercero prov = new Tercero();
        prov.setNombre("ZBPASOB Proveedor REC");
        prov.setTipoTercero(TipoTercero.PROVEEDOR);
        prov.setEstado(EstadoRegistro.ACTIVO);
        idProveedor = terceroRepo.insertar(prov);

        Bodega b = new Bodega();
        b.setNombre("ZBPASOB Bodega REC");
        b.setUbicacion("Test");
        b.setEstado(EstadoRegistro.ACTIVO);
        idBodega = bodegaRepo.insertar(b);
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    /** Inserta un item nuevo (codigo unico) para aislar el stock de cada prueba. */
    private static int nuevoItem() throws SQLException {
        Item it = new Item();
        it.setCodigo("ZBPASOB-REC-" + seq.incrementAndGet());
        it.setNombre("ZBPASOB Item REC");
        it.setTipoItem(TipoItem.PRODUCTO);
        it.setPrecioCompra(new BigDecimal("100"));
        it.setPrecioVenta(new BigDecimal("150"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal("2"));
        it.setEstado(EstadoRegistro.ACTIVO);
        return itemRepo.insertar(it);
    }

    /** Crea una orden EMITIDA con una linea y la devuelve recargada (con id de detalle). */
    private OrdenCompra crearOrden(int idItem, String cantidad) throws SQLException {
        OrdenCompra o = new OrdenCompra();
        o.setIdProveedor(idProveedor);
        OrdenCompraDetalle d = new OrdenCompraDetalle();
        d.setIdItem(idItem);
        d.setIdBodegaDestino(idBodega);
        d.setCantidadSolicitada(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal("100"));
        o.getDetalles().add(d);
        ordenService.crear(o);
        return ordenService.buscarConDetalles(o.getIdOrden()).orElseThrow();
    }

    private RecepcionMercancia recepcionDe(OrdenCompra o, int idItem, String cantidad) {
        RecepcionMercancia r = new RecepcionMercancia();
        r.setIdOrden(o.getIdOrden());
        RecepcionDetalle rd = new RecepcionDetalle();
        rd.setIdDetalleOc(o.getDetalles().get(0).getIdDetalle());
        rd.setIdItem(idItem);
        rd.setIdBodega(idBodega);
        rd.setCantidadRecibida(new BigDecimal(cantidad));
        r.getDetalles().add(rd);
        return r;
    }

    @Test
    void recepcionParcialActualizaStockKardexYEstado() throws SQLException {
        int idItem = nuevoItem();
        OrdenCompra o = crearOrden(idItem, "10");

        recepcionService.registrar(recepcionDe(o, idItem, "4"));

        assertEquals(4.0, stockDe(idItem), 0.0001, "El stock debe subir a 4 al recibir.");
        assertTrue(kardexEntradaCompra(idItem) >= 1, "Debe existir un movimiento ENTRADA_COMPRA.");
        assertEquals("PARCIALMENTE_RECIBIDA", estadoOrden(o.getIdOrden()));
    }

    @Test
    void recibirMasDeLoPendienteSeRechazaSinCambios() throws SQLException {
        int idItem = nuevoItem();
        OrdenCompra o = crearOrden(idItem, "5");

        assertThrows(ValidacionException.class,
                () -> recepcionService.registrar(recepcionDe(o, idItem, "9")));

        assertEquals(0.0, stockDe(idItem), 0.0001, "El stock no debe cambiar al rechazar.");
        assertEquals(0, kardexEntradaCompra(idItem), "No debe generarse Kardex al rechazar.");
        assertEquals("EMITIDA", estadoOrden(o.getIdOrden()));
    }

    @Test
    void recepcionTotalDejaOrdenRecibida() throws SQLException {
        int idItem = nuevoItem();
        OrdenCompra o = crearOrden(idItem, "10");

        recepcionService.registrar(recepcionDe(o, idItem, "6"));
        // Recargar para tomar el pendiente actualizado (4).
        OrdenCompra o2 = ordenService.buscarConDetalles(o.getIdOrden()).orElseThrow();
        recepcionService.registrar(recepcionDe(o2, idItem, "4"));

        assertEquals(10.0, stockDe(idItem), 0.0001, "El stock debe llegar a 10.");
        assertEquals("RECIBIDA", estadoOrden(o.getIdOrden()));
    }

    // ---- consultas auxiliares de verificacion ----

    private static double stockDe(int idItem) throws SQLException {
        String sql = "SELECT IFNULL(SUM(cantidad),0) FROM inventario_stock WHERE id_item = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        }
    }

    private static int kardexEntradaCompra(int idItem) throws SQLException {
        String sql = "SELECT COUNT(*) FROM inventario_movimientos "
                   + "WHERE id_item = ? AND tipo_movimiento = 'ENTRADA_COMPRA'";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static String estadoOrden(int idOrden) throws SQLException {
        String sql = "SELECT estado FROM ordenes_compra WHERE id_orden = ?";
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ordenesZ = "SELECT id_orden FROM ordenes_compra WHERE id_proveedor IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBPASOB%')";
            st.executeUpdate("DELETE FROM recepciones_detalles WHERE id_recepcion IN "
                    + "(SELECT id_recepcion FROM recepciones_mercancia WHERE id_orden IN (" + ordenesZ + "))");
            st.executeUpdate("DELETE FROM recepciones_mercancia WHERE id_orden IN (" + ordenesZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN "
                    + "(SELECT id_item FROM items WHERE codigo LIKE 'ZBPASOB%')");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN "
                    + "(SELECT id_item FROM items WHERE codigo LIKE 'ZBPASOB%')");
            st.executeUpdate("DELETE FROM ordenes_compra_detalles WHERE id_orden IN (" + ordenesZ + ")");
            st.executeUpdate("DELETE FROM ordenes_compra WHERE id_proveedor IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBPASOB%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBPASOB%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZBPASOB%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZBPASOB%'");
        }
    }
}
