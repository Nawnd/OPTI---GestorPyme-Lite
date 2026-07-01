package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.LoteDisponible;
import com.gestorpyme.domain.model.RecepcionDetalle;
import com.gestorpyme.domain.model.RecepcionMercancia;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.LoteRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso M (recepción conectada a lotes y trazabilidad de entrada), a través de
 * {@link RecepcionService#registrar}. Marcador "ZPMTEST" + datos propios (proveedor, bodega, ítems y orden
 * dedicados) para aislar de los datos reales. Cubre: creación y engrose de lote, suma de cantidades, rechazo
 * de vencimientos mezclados, rechazo de lote vencido, servicio/no inventariable con lote, recepción sin lote,
 * Kardex con id_lote, trazabilidad en recepciones_detalles, rollback transaccional y disponibilidad FEFO del
 * lote recibido. No se creó la tabla recepcion_detalle_lotes ni se hizo migración (se reutiliza
 * recepciones_detalles.id_lote).
 */
class PasoMRecepcionLotesTest {

    private static final RecepcionService recepcionService = new RecepcionService();
    private static final LoteRepository loteRepository = new LoteRepository();

    private static int idProveedor;
    private static int idBodega;
    private static int idItemInv;     // inventariable
    private static int idItemServ;    // servicio (controla_inventario = 0)
    private static final String FUTURO_1 = LocalDate.now().plusYears(2).toString();
    private static final String FUTURO_2 = LocalDate.now().plusYears(3).toString();
    private static final String PASADO = LocalDate.now().minusYears(1).toString();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idProveedor = idDe("INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'PROVEEDOR', 'ACTIVO')",
                "ZPMTEST Proveedor");
        idBodega = idDe("INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", "ZPMTEST Bodega");
        idItemInv = item("ZPM-INV", 1);
        idItemServ = item("ZPM-SRV", 0);
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    @Test
    void recepcionInventariableCreaLoteConCantidades() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, "LOTE-A", FUTURO_1, "20");
        double[] c = loteCantidades(idItemInv, "LOTE-A");
        assertEquals(20d, c[0], 0.001, "cantidad_inicial del lote nuevo");
        assertEquals(20d, c[1], 0.001, "cantidad_disponible del lote nuevo");
    }

    @Test
    void recepcionEngrosaLoteExistente() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, "LOTE-B", FUTURO_1, "10");
        int oc2 = ordenConDetalle(idItemInv, 100);
        recibir(oc2, idItemInv, "LOTE-B", FUTURO_1, "5");
        double[] c = loteCantidades(idItemInv, "LOTE-B");
        assertEquals(15d, c[0], 0.001, "engrose suma cantidad_inicial");
        assertEquals(15d, c[1], 0.001, "engrose suma cantidad_disponible");
    }

    @Test
    void rechazaVencimientosDistintosEnMismoLote() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, "LOTE-C", FUTURO_1, "8");
        int oc2 = ordenConDetalle(idItemInv, 100);
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> recibir(oc2, idItemInv, "LOTE-C", FUTURO_2, "3"));
        assertTrue(ex.getMessage().toLowerCase().contains("vencimiento"));
    }

    @Test
    void rechazaLoteVencido() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> recibir(oc, idItemInv, "LOTE-VENC", PASADO, "5"));
        assertTrue(ex.getMessage().toLowerCase().contains("vencido"));
    }

    @Test
    void rechazaServicioConLote() throws SQLException {
        int oc = ordenConDetalle(idItemServ, 100);
        ValidacionException ex = assertThrows(ValidacionException.class,
                () -> recibir(oc, idItemServ, "LOTE-X", FUTURO_1, "5"));
        assertTrue(ex.getMessage().toLowerCase().contains("servicio"));
    }

    @Test
    void recepcionSinLoteMantieneFlujo() throws SQLException {
        double stockAntes = stock(idItemInv, idBodega);
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, null, null, "7");
        assertEquals(stockAntes + 7d, stock(idItemInv, idBodega), 0.001, "el stock sube aunque no haya lote");
        // El último movimiento de este ítem no debe tener id_lote.
        assertNull(ultimoIdLoteKardex(idItemInv), "recepción sin lote: Kardex sin id_lote");
    }

    @Test
    void kardexEntradaCompraRegistraIdLote() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, "LOTE-K", FUTURO_1, "4");
        Integer idLote = loteId(idItemInv, "LOTE-K");
        Integer idLoteKardex = ultimoIdLoteKardex(idItemInv);
        assertEquals(idLote, idLoteKardex, "el Kardex ENTRADA_COMPRA debe registrar el id_lote del lote recibido");
    }

    @Test
    void recepcionDetalleRegistraIdLote() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, "LOTE-T", FUTURO_1, "6");
        Integer idLote = loteId(idItemInv, "LOTE-T");
        assertEquals(idLote, ultimoIdLoteRecepcionDetalle(idItemInv),
                "recepciones_detalles.id_lote debe quedar registrado (trazabilidad)");
    }

    @Test
    void rollbackSiFallaUnaLineaNoDejaStockParcial() throws SQLException {
        // Pre-crea LOTE-R con un vencimiento.
        int oc0 = ordenConDetalle(idItemInv, 100);
        recibir(oc0, idItemInv, "LOTE-R", FUTURO_1, "9");
        double stockAntes = stock(idItemInv, idBodega);

        // Recepción con dos líneas: la 1ª crearía LOTE-NUEVO; la 2ª choca el vencimiento de LOTE-R.
        int oc = ordenConDosDetalles(idItemInv, idItemInv, 100, 100);
        int[] dets = detallesDe(oc);
        RecepcionMercancia rec = new RecepcionMercancia();
        rec.setIdOrden(oc);
        rec.getDetalles().add(linea(dets[0], idItemInv, "LOTE-NUEVO", FUTURO_1, "5"));
        rec.getDetalles().add(linea(dets[1], idItemInv, "LOTE-R", FUTURO_2, "5")); // choque -> falla en tx

        assertThrows(ValidacionException.class, () -> recepcionService.registrar(rec));
        // La 1ª línea no debe haber dejado rastro: ni LOTE-NUEVO ni cambio de stock.
        assertFalse(existeLote(idItemInv, "LOTE-NUEVO"), "rollback: el lote de la 1ª línea no debe persistir");
        assertEquals(stockAntes, stock(idItemInv, idBodega), 0.001, "rollback: el stock no debe cambiar");
    }

    @Test
    void loteRecibidoQuedaDisponibleParaFefo() throws SQLException {
        int oc = ordenConDetalle(idItemInv, 100);
        recibir(oc, idItemInv, "LOTE-F", FUTURO_1, "12");
        List<LoteDisponible> disp = loteRepository.disponiblesFefo(idItemInv, idBodega);
        boolean halla = disp.stream().anyMatch(l -> l.getCantidadDisponible().doubleValue() >= 12d);
        assertTrue(halla, "el lote recibido debe quedar disponible para consumo FEFO");
    }

    // ---------------- auxiliares de negocio ----------------

    private static void recibir(int idOrden, int idItem, String numeroLote, String venc, String cantidad)
            throws SQLException {
        int[] dets = detallesDe(idOrden);
        RecepcionMercancia rec = new RecepcionMercancia();
        rec.setIdOrden(idOrden);
        rec.getDetalles().add(linea(dets[0], idItem, numeroLote, venc, cantidad));
        recepcionService.registrar(rec);
    }

    private static RecepcionDetalle linea(int idDetalleOc, int idItem, String numeroLote, String venc,
                                          String cantidad) {
        RecepcionDetalle rd = new RecepcionDetalle();
        rd.setIdDetalleOc(idDetalleOc);
        rd.setIdItem(idItem);
        rd.setIdBodega(idBodega);
        rd.setCantidadRecibida(new BigDecimal(cantidad));
        rd.setNumeroLote(numeroLote);
        rd.setFechaVencimiento(venc);
        return rd;
    }

    // ---------------- siembra de datos ----------------

    private static int item(String codigo, int controla) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO items (codigo, nombre, tipo_item, id_categoria, precio_compra, precio_venta, "
                     + "controla_inventario, stock_minimo, estado, fecha_creacion, modo_calculo_servicio, unidad_medida) "
                     + "VALUES (?, ?, ?, 1, 100, 200, ?, 0, 'ACTIVO', date('now'), 'FIJO', 'Unidad')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, "ZPMTEST " + codigo);
            ps.setString(3, controla == 1 ? "REPUESTO" : "SERVICIO");
            ps.setInt(4, controla);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static int ordenConDetalle(int idItem, double solicitada) throws SQLException {
        int idOrden = nuevaOrden();
        nuevoDetalleOc(idOrden, idItem, solicitada);
        return idOrden;
    }

    private static int ordenConDosDetalles(int idItemA, int idItemB, double sa, double sb) throws SQLException {
        int idOrden = nuevaOrden();
        nuevoDetalleOc(idOrden, idItemA, sa);
        nuevoDetalleOc(idOrden, idItemB, sb);
        return idOrden;
    }

    private static int nuevaOrden() throws SQLException {
        String numero = "OC-ZPM-" + System.nanoTime();
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ordenes_compra (numero_orden, id_proveedor, fecha_orden, estado, subtotal, total, "
                     + "fecha_creacion) VALUES (?, ?, date('now'), 'EMITIDA', 0, 0, date('now'))",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, idProveedor);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void nuevoDetalleOc(int idOrden, int idItem, double solicitada) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO ordenes_compra_detalles (id_orden, id_item, id_bodega_destino, "
                     + "cantidad_solicitada, cantidad_recibida, precio_unitario, subtotal) "
                     + "VALUES (?, ?, ?, ?, 0, 100, ?)")) {
            ps.setInt(1, idOrden);
            ps.setInt(2, idItem);
            ps.setInt(3, idBodega);
            ps.setDouble(4, solicitada);
            ps.setDouble(5, solicitada * 100);
            ps.executeUpdate();
        }
    }

    private static int[] detallesDe(int idOrden) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_detalle FROM ordenes_compra_detalles WHERE id_orden = ? ORDER BY id_detalle")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<Integer> ids = new java.util.ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
                int[] arr = new int[ids.size()];
                for (int i = 0; i < arr.length; i++) {
                    arr[i] = ids.get(i);
                }
                return arr;
            }
        }
    }

    // ---------------- consultas de verificación ----------------

    private static double[] loteCantidades(int idItem, String numero) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT cantidad_inicial, cantidad_disponible FROM lotes "
                     + "WHERE id_item = ? AND id_bodega = ? AND numero_lote = ?")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, numero);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new double[]{-1, -1};
                }
                return new double[]{rs.getDouble(1), rs.getDouble(2)};
            }
        }
    }

    private static Integer loteId(int idItem, String numero) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_lote FROM lotes WHERE id_item = ? AND id_bodega = ? AND numero_lote = ?")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, numero);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static boolean existeLote(int idItem, String numero) throws SQLException {
        return loteId(idItem, numero) != null;
    }

    private static double stock(int idItem, int idBod) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COALESCE(cantidad,0) FROM inventario_stock WHERE id_item = ? AND id_bodega = ?")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBod);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0d;
            }
        }
    }

    private static Integer ultimoIdLoteKardex(int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_lote FROM inventario_movimientos WHERE id_item = ? AND tipo_movimiento = 'ENTRADA_COMPRA' "
                     + "ORDER BY id_movimiento DESC LIMIT 1")) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    private static Integer ultimoIdLoteRecepcionDetalle(int idItem) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id_lote FROM recepciones_detalles WHERE id_item = ? "
                     + "ORDER BY id_detalle_rec DESC LIMIT 1")) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int v = rs.getInt(1);
                return rs.wasNull() ? null : v;
            }
        }
    }

    private static int idDe(String sql, String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ordenesZ = "SELECT id_orden FROM ordenes_compra WHERE numero_orden LIKE 'OC-ZPM-%'";
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPM-%'";
            String recZ = "SELECT id_recepcion FROM recepciones_mercancia WHERE id_orden IN (" + ordenesZ + ")";
            st.executeUpdate("DELETE FROM recepciones_detalles WHERE id_recepcion IN (" + recZ + ")");
            st.executeUpdate("DELETE FROM recepciones_mercancia WHERE id_orden IN (" + ordenesZ + ")");
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM lotes WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM ordenes_compra_detalles WHERE id_orden IN (" + ordenesZ + ")");
            st.executeUpdate("DELETE FROM ordenes_compra WHERE numero_orden LIKE 'OC-ZPM-%'");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPM-%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPMTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPMTEST%'");
        }
    }
}
