package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.InventarioRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del piloto Mano de Obra (v0.7.3): calculo del valor de servicio (FIJO y
 * PORCENTAJE_REPUESTOS), validaciones de configuracion y la garantia de que un
 * servicio no descuenta inventario. Datos de prueba marcados con "ZBMOTEST".
 */
class ManoObraServicioTest {

    private static final ItemService itemService = new ItemService();
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final VentaService ventaService = new VentaService();
    private static final InventarioRepository inventarioRepo = new InventarioRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize(); // aplica la migracion de Mano de Obra (idempotente)
        limpiar();
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------- Calculadora (logica pura) ----------

    @Test
    void fijoConservaElPrecioDeVenta() {
        Item s = servicioEnMemoria(ModoCalculoServicio.FIJO, null, "40000");
        BigDecimal v = ManoObraCalculator.valorSugerido(s, lineas(repuesto("6000000")));
        assertEquals(0, v.compareTo(new BigDecimal("40000")), "FIJO debe devolver el precio de venta.");
    }

    @Test
    void porcentajeCalculaSoloSobreBienesFisicos() {
        Item s = servicioEnMemoria(ModoCalculoServicio.PORCENTAJE_REPUESTOS, "10", "0");
        // La lista incluye un repuesto fisico (6.000.000) y otra linea de servicio (excluida).
        List<VentaDetalle> lineas = lineas(repuesto("6000000"), servicioLinea("500000"));
        BigDecimal v = ManoObraCalculator.valorSugerido(s, lineas);
        assertEquals(0, v.compareTo(new BigDecimal("600000")),
                "10% de 6.000.000 (solo bienes fisicos) = 600.000; no debe contar el otro servicio.");
    }

    @Test
    void sinBienesFisicosSugiereCero() {
        Item s = servicioEnMemoria(ModoCalculoServicio.PORCENTAJE_REPUESTOS, "10", "0");
        BigDecimal v = ManoObraCalculator.valorSugerido(s, lineas(servicioLinea("500000")));
        assertEquals(0, v.compareTo(BigDecimal.ZERO), "Sin repuestos/productos, el sugerido es 0.");
    }

    @Test
    void porcentajeNegativoSeClampeaAceroEnElCalculo() {
        Item s = servicioEnMemoria(ModoCalculoServicio.PORCENTAJE_REPUESTOS, "-10", "0");
        BigDecimal v = ManoObraCalculator.valorSugerido(s, lineas(repuesto("6000000")));
        assertEquals(0, v.compareTo(BigDecimal.ZERO), "El calculo nunca devuelve un valor negativo.");
    }

    // ---------- Validaciones de ItemService ----------

    @Test
    void porcentajeNegativoSeRechazaAlGuardar() {
        Item s = servicioPersistible("ZBMOTEST-NEG", ModoCalculoServicio.PORCENTAJE_REPUESTOS, "-5");
        assertThrows(ValidacionException.class, () -> itemService.guardar(s));
    }

    @Test
    void modoPorcentajeEnItemConInventarioSeRechaza() {
        Item it = servicioPersistible("ZBMOTEST-INV", ModoCalculoServicio.PORCENTAJE_REPUESTOS, "10");
        it.setTipoItem(TipoItem.REPUESTO);
        it.setControlaInventario(true); // un bien fisico no puede usar el modo porcentaje
        assertThrows(ValidacionException.class, () -> itemService.guardar(it));
    }

    @Test
    void servicioSinModoExplicitoSeAsumeFijo() throws SQLException {
        Item it = new Item();
        it.setCodigo("ZBMOTEST-DEF");
        it.setNombre("ZBMOTEST Servicio sin modo");
        it.setTipoItem(TipoItem.SERVICIO);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("30000"));
        it.setControlaInventario(false);
        it.setStockMinimo(BigDecimal.ZERO);
        it.setEstado(EstadoRegistro.ACTIVO);
        // No se fija modo: el modelo trae FIJO por defecto.
        itemService.guardar(it);

        Optional<Item> rec = itemRepo.buscarPorId(it.getIdItem());
        assertTrue(rec.isPresent());
        assertEquals(ModoCalculoServicio.FIJO, rec.get().getModoCalculoServicio());
    }

    // ---------- Integracion: el servicio no descuenta inventario ----------

    @Test
    void ventaConServicioPorcentualNoDescuentaInventario() throws SQLException {
        int idCliente = insertarCliente();
        int idBodega = insertarBodega("ZBMOTEST Bodega");

        // Bien fisico con stock inicial 10.
        Item fisico = new Item();
        fisico.setCodigo("ZBMOTEST-FIS");
        fisico.setNombre("ZBMOTEST Repuesto");
        fisico.setTipoItem(TipoItem.REPUESTO);
        fisico.setPrecioCompra(BigDecimal.ZERO);
        fisico.setPrecioVenta(new BigDecimal("100"));
        fisico.setControlaInventario(true);
        fisico.setStockMinimo(BigDecimal.ZERO);
        fisico.setEstado(EstadoRegistro.ACTIVO);
        itemService.guardar(fisico);
        sembrarStock(fisico.getIdItem(), idBodega, "10");

        // Servicio porcentual (no controla inventario).
        Item servicio = servicioPersistible("ZBMOTEST-SRV", ModoCalculoServicio.PORCENTAJE_REPUESTOS, "10");
        itemService.guardar(servicio);

        // Venta de contado: 2 unidades del repuesto + 1 linea de servicio.
        Venta v = new Venta();
        v.setIdTercero(idCliente);
        v.setIdUsuario(1);
        List<VentaDetalle> det = new ArrayList<>();
        det.add(linea(fisico.getIdItem(), "ZBMOTEST Repuesto", true, "2", "100", "200"));
        det.add(linea(servicio.getIdItem(), "ZBMOTEST Servicio", false, "1", "20", "20"));
        ventaService.registrarVenta(v, det, idBodega, true, null, MedioPago.EFECTIVO);

        // El stock baja solo por el repuesto (10 - 2 = 8); el servicio no afecta inventario.
        assertEquals(0, inventarioRepo.obtenerCantidad(fisico.getIdItem(), idBodega)
                .compareTo(new BigDecimal("8")), "El servicio no debe descontar inventario.");
    }

    // ---------- auxiliares ----------

    private static Item servicioEnMemoria(ModoCalculoServicio modo, String porcentaje, String precioVenta) {
        Item s = new Item();
        s.setTipoItem(TipoItem.SERVICIO);
        s.setControlaInventario(false);
        s.setPrecioVenta(new BigDecimal(precioVenta));
        s.setModoCalculoServicio(modo);
        s.setPorcentajeServicio(porcentaje == null ? null : new BigDecimal(porcentaje));
        return s;
    }

    private static Item servicioPersistible(String codigo, ModoCalculoServicio modo, String porcentaje) {
        Item s = new Item();
        s.setCodigo(codigo);
        s.setNombre("ZBMOTEST " + codigo);
        s.setTipoItem(TipoItem.SERVICIO);
        s.setPrecioCompra(BigDecimal.ZERO);
        s.setPrecioVenta(new BigDecimal("40000"));
        s.setControlaInventario(false);
        s.setStockMinimo(BigDecimal.ZERO);
        s.setEstado(EstadoRegistro.ACTIVO);
        s.setModoCalculoServicio(modo);
        s.setPorcentajeServicio(porcentaje == null ? null : new BigDecimal(porcentaje));
        return s;
    }

    private static VentaDetalle repuesto(String subtotal) {
        return linea(0, "Repuesto", true, "1", subtotal, subtotal);
    }

    private static VentaDetalle servicioLinea(String subtotal) {
        return linea(0, "Servicio", false, "1", subtotal, subtotal);
    }

    private static VentaDetalle linea(int idItem, String nombre, boolean controlaInv,
                                      String cantidad, String precio, String subtotal) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setNombreItem(nombre);
        d.setControlaInventario(controlaInv);
        d.setCantidad(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoLinea(BigDecimal.ZERO);
        d.setSubtotalLinea(new BigDecimal(subtotal));
        return d;
    }

    private static List<VentaDetalle> lineas(VentaDetalle... ds) {
        List<VentaDetalle> l = new ArrayList<>();
        for (VentaDetalle d : ds) {
            l.add(d);
        }
        return l;
    }

    private static int insertarCliente() throws SQLException {
        Tercero t = new Tercero();
        t.setNombre("ZBMOTEST Cliente");
        t.setTipoTercero(TipoTercero.CLIENTE);
        t.setEstado(EstadoRegistro.ACTIVO);
        return terceroRepo.insertar(t);
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
            String ventasZ = "SELECT id_venta FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBMOTEST%')";
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZBMOTEST%'";
            // Orden seguro segun llaves foraneas: primero lo que referencia ventas/items.
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZBMOTEST%')");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZBMOTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZBMOTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZBMOTEST%'");
        }
    }
}
