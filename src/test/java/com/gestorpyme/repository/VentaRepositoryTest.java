package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.service.VentaService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de integracion del registro transaccional de ventas:
 * - genera consecutivo continuo (V-NNNNNN, +1 entre ventas),
 * - descuenta stock de los productos que controlan inventario,
 * - genera el movimiento SALIDA_VENTA,
 * - una venta de solo servicio NO descuenta inventario.
 * Crea datos temporales y los elimina al finalizar.
 */
class VentaRepositoryTest {

    private final VentaService ventaService = new VentaService();
    private final ItemRepository itemRepository = new ItemRepository();
    private final BodegaRepository bodegaRepository = new BodegaRepository();
    private final TerceroRepository terceroRepository = new TerceroRepository();
    private final InventarioRepository inventarioRepository = new InventarioRepository();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void ventaTransaccionalCompleta() throws SQLException {
        int idProducto = 0;
        int idServicio = 0;
        int idBodega = 0;
        int idTercero = 0;
        List<Integer> ventasCreadas = new ArrayList<>();
        try {
            idProducto = crearProducto();        // controla inventario
            idServicio = crearServicio();        // NO controla inventario
            idBodega = crearBodega();
            idTercero = crearCliente();

            // Stock inicial del producto: 10
            inventarioRepository.registrarMovimiento(idProducto, idBodega,
                    TipoMovimiento.ENTRADA, new BigDecimal("10"), "Carga inicial prueba", null);

            // --- Venta 1: producto, cantidad 2, contado ---
            Venta v1 = new Venta();
            v1.setIdTercero(idTercero);
            String numero1 = ventaService.registrarVenta(v1,
                    Collections.singletonList(linea(idProducto, true, "2", "100")),
                    idBodega, true, null, MedioPago.EFECTIVO);
            ventasCreadas.add(v1.getIdVenta());

            assertTrue(numero1.matches("V-\\d{6}"), "El numero debe tener el formato V-NNNNNN");
            assertEquals(0, new BigDecimal("8").compareTo(inventarioRepository.obtenerCantidad(idProducto, idBodega)),
                    "Tras vender 2 de 10, el stock debe ser 8");
            assertTrue(contarSalidasVenta(idProducto) >= 1,
                    "Debe existir un movimiento SALIDA_VENTA para el producto");

            // --- Venta 2: producto, cantidad 1, contado (consecutivo +1) ---
            Venta v2 = new Venta();
            v2.setIdTercero(idTercero);
            String numero2 = ventaService.registrarVenta(v2,
                    Collections.singletonList(linea(idProducto, true, "1", "100")),
                    idBodega, true, null, MedioPago.EFECTIVO);
            ventasCreadas.add(v2.getIdVenta());

            assertEquals(sufijo(numero1) + 1, sufijo(numero2),
                    "El consecutivo de la segunda venta debe ser el siguiente");
            assertEquals(0, new BigDecimal("7").compareTo(inventarioRepository.obtenerCantidad(idProducto, idBodega)),
                    "Tras vender 1 mas, el stock debe ser 7");

            // --- Venta 3: solo servicio (no descuenta inventario) ---
            BigDecimal stockAntes = inventarioRepository.obtenerCantidad(idProducto, idBodega);
            Venta v3 = new Venta();
            v3.setIdTercero(idTercero);
            ventaService.registrarVenta(v3,
                    Collections.singletonList(linea(idServicio, false, "1", "5000")),
                    0, true, null, MedioPago.EFECTIVO);
            ventasCreadas.add(v3.getIdVenta());

            assertEquals(0, stockAntes.compareTo(inventarioRepository.obtenerCantidad(idProducto, idBodega)),
                    "Una venta de solo servicio no debe cambiar el stock del producto");
            assertEquals(0, contarSalidasVenta(idServicio),
                    "Un servicio no debe generar movimientos SALIDA_VENTA");
        } finally {
            limpiar(ventasCreadas, idProducto, idServicio, idBodega, idTercero);
        }
    }

    private VentaDetalle linea(int idItem, boolean controla, String cantidad, String precio) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setControlaInventario(controla);
        d.setCantidad(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoLinea(BigDecimal.ZERO);
        return d;
    }

    private int crearProducto() throws SQLException {
        Item it = new Item();
        it.setNombre("PRUEBA Producto Venta");
        it.setTipoItem(TipoItem.PRODUCTO);
        it.setControlaInventario(true);
        it.setPrecioVenta(new BigDecimal("100"));
        it.setEstado(EstadoRegistro.ACTIVO);
        return itemRepository.insertar(it);
    }

    private int crearServicio() throws SQLException {
        Item it = new Item();
        it.setNombre("PRUEBA Servicio Venta");
        it.setTipoItem(TipoItem.SERVICIO);
        it.setControlaInventario(false);
        it.setPrecioVenta(new BigDecimal("5000"));
        it.setEstado(EstadoRegistro.ACTIVO);
        return itemRepository.insertar(it);
    }

    private int crearBodega() throws SQLException {
        Bodega b = new Bodega();
        b.setNombre("PRUEBA Bodega Venta " + System.nanoTime());
        b.setEstado(EstadoRegistro.ACTIVO);
        return bodegaRepository.insertar(b);
    }

    private int crearCliente() throws SQLException {
        Tercero t = new Tercero();
        t.setNombre("PRUEBA Cliente Venta");
        t.setTipoTercero(TipoTercero.CLIENTE);
        t.setEstado(EstadoRegistro.ACTIVO);
        return terceroRepository.insertar(t);
    }

    private int contarSalidasVenta(int idItem) throws SQLException {
        String sql = "SELECT COUNT(*) FROM inventario_movimientos "
                   + "WHERE id_item = ? AND tipo_movimiento = 'SALIDA_VENTA'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Extrae la parte numerica de un numero "V-NNNNNN". */
    private int sufijo(String numero) {
        return Integer.parseInt(numero.substring(2));
    }

    private void limpiar(List<Integer> ventas, int idProducto, int idServicio, int idBodega, int idTercero)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            for (int idVenta : ventas) {
                ejecutar(conn, "DELETE FROM pagos WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM cuentas_por_cobrar WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM inventario_movimientos WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM venta_detalles WHERE id_venta = ?", idVenta);
                ejecutar(conn, "DELETE FROM ventas WHERE id_venta = ?", idVenta);
            }
            for (int idItem : new int[]{idProducto, idServicio}) {
                if (idItem > 0) {
                    ejecutar(conn, "DELETE FROM inventario_movimientos WHERE id_item = ?", idItem);
                    ejecutar(conn, "DELETE FROM inventario_stock WHERE id_item = ?", idItem);
                    ejecutar(conn, "DELETE FROM items WHERE id_item = ?", idItem);
                }
            }
            if (idTercero > 0) {
                ejecutar(conn, "DELETE FROM terceros WHERE id_tercero = ?", idTercero);
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
