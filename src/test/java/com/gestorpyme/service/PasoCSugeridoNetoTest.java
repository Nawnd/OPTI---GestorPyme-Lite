package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.ItemLogistico;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso C (sugerido neto en reabastecimiento, v0.8 / RF-04):
 * sugerido = max(0, stock_minimo - stock_actual - en_pedido). Verifica la formula y su
 * efecto extremo a extremo (un producto ya cubierto por una OC pendiente sugiere 0, por lo
 * que "Generar OC" lo ignora). Datos de prueba marcados "ZPCTEST". Solo lectura/calculo.
 */
class PasoCSugeridoNetoTest {

    private static final InventarioLogisticoService logistico = new InventarioLogisticoService();
    private static final ItemService itemService = new ItemService();
    private static final ExportacionService exportacionService = new ExportacionService();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- Formula (servicio puro) ----------------

    @Test
    void sugeridoSinPedido() {
        assertEquals(0, n("7").compareTo(logistico.calcularSugerido(n("3"), n("10"), n("0"))));
    }

    @Test
    void sugeridoConPedidoParcial() {
        assertEquals(0, n("2").compareTo(logistico.calcularSugerido(n("3"), n("10"), n("5"))));
    }

    @Test
    void sugeridoConPedidoSuficienteEsCero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(logistico.calcularSugerido(n("3"), n("10"), n("10"))));
    }

    @Test
    void sugeridoNuncaNegativo() {
        assertEquals(0, BigDecimal.ZERO.compareTo(logistico.calcularSugerido(n("3"), n("10"), n("20"))));
    }

    @Test
    void sugeridoCeroSiStockNormal() {
        assertEquals(0, BigDecimal.ZERO.compareTo(logistico.calcularSugerido(n("12"), n("10"), n("0"))));
    }

    // ---------------- Integracion: en pedido descuenta el sugerido ----------------

    @Test
    void productoCubiertoPorPedidoSugiereCeroYSeIgnoraEnOC() throws SQLException {
        int idProv = insertarProveedor("ZPCTEST Proveedor");
        int idBodega = insertarBodega("ZPCTEST Bodega");

        // Item cubierto: min 10, actual 3, en pedido 10 -> sugerido neto 0.
        Item cubierto = inventariable("ZPCTEST-COV", "10");
        itemService.guardar(cubierto);
        sembrarStock(cubierto.getIdItem(), idBodega, "3");
        ordenPendiente(idProv, "ZPCTEST-OC-COV", cubierto.getIdItem(), "10");

        // Item parcial: min 10, actual 3, en pedido 5 -> sugerido neto 2.
        Item parcial = inventariable("ZPCTEST-PAR", "10");
        itemService.guardar(parcial);
        sembrarStock(parcial.getIdItem(), idBodega, "3");
        ordenPendiente(idProv, "ZPCTEST-OC-PAR", parcial.getIdItem(), "5");

        ItemLogistico cov = buscar("ZPCTEST-COV");
        ItemLogistico par = buscar("ZPCTEST-PAR");
        assertNotNull(cov);
        assertNotNull(par);
        assertEquals(0, BigDecimal.ZERO.compareTo(cov.getSugerido()),
                "Con en pedido suficiente, el sugerido neto es 0 (la OC debe ignorarlo).");
        assertEquals(0, n("2").compareTo(par.getSugerido()),
                "Con en pedido parcial, el sugerido neto es 10 - 3 - 5 = 2.");
        // La generacion de OC ignora items con sugerido <= 0 (regla ya vigente en la vista).
        assertTrue(cov.getSugerido().signum() <= 0, "El item cubierto no se incluiria en la OC.");
        assertTrue(par.getSugerido().signum() > 0, "El item parcial si se incluiria en la OC.");
    }

    @Test
    void exportReabastecimientoReflejaSugeridoNeto() throws SQLException, IOException {
        int idProv = insertarProveedor("ZPCTEST Prov2");
        int idBodega = insertarBodega("ZPCTEST Bodega2");
        Item parcial = inventariable("ZPCTEST-EXP", "10");
        itemService.guardar(parcial);
        sembrarStock(parcial.getIdItem(), idBodega, "3");
        ordenPendiente(idProv, "ZPCTEST-OC-EXP", parcial.getIdItem(), "5"); // sugerido neto 2

        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "zpc_reab.csv");
        exportacionService.exportar(TipoExportacion.REABASTECIMIENTO, archivo.toString(), 1);
        String contenido = new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);

        String linea = "";
        for (String l : contenido.split("\r?\n")) {
            if (l.contains("ZPCTEST-EXP")) {
                linea = l;
                break;
            }
        }
        // Columnas (Paso F): Codigo_Item;Item;Stock_Actual;Stock_Minimo;Stock_Maximo;En_Pedido;Sugerido;Proveedor_Preferido;Estado_Logistico
        // El item ZPCTEST-EXP no tiene stock maximo ni proveedor preferido (campos vacios).
        assertTrue(linea.contains(";3;10;;5;2;;"),
                "El CSV debe mostrar el sugerido NETO (2) tras descontar en pedido. Linea: " + linea);
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zpc_%'");
        }
    }

    // ---------------- auxiliares ----------------

    private static BigDecimal n(String s) {
        return new BigDecimal(s);
    }

    private static ItemLogistico buscar(String codigo) throws SQLException {
        for (ItemLogistico it : logistico.listar()) {
            if (codigo.equals(it.getCodigo())) {
                return it;
            }
        }
        return null;
    }

    private static Item inventariable(String codigo, String stockMinimo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPCTEST " + codigo);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal(stockMinimo));
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
    }

    private static int insertarProveedor(String nombre) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO terceros (nombre, tipo_tercero, estado) VALUES (?, 'PROVEEDOR', 'ACTIVO')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
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

    /** Crea una OC EMITIDA con un detalle pendiente (cantidad_recibida 0) para generar "en pedido". */
    private static void ordenPendiente(int idProveedor, String numero, int idItem, String solicitada) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            int idOrden;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ordenes_compra (numero_orden, id_proveedor, fecha_orden, estado) "
                    + "VALUES (?, ?, '2026-06-18', 'EMITIDA')", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, numero);
                ps.setInt(2, idProveedor);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    idOrden = rs.next() ? rs.getInt(1) : -1;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ordenes_compra_detalles (id_orden, id_item, cantidad_solicitada, cantidad_recibida) "
                    + "VALUES (?, ?, ?, 0)")) {
                ps.setInt(1, idOrden);
                ps.setInt(2, idItem);
                ps.setDouble(3, new BigDecimal(solicitada).doubleValue());
                ps.executeUpdate();
            }
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPCTEST%'";
            String ocZ = "SELECT id_orden FROM ordenes_compra WHERE numero_orden LIKE 'ZPCTEST%'";
            st.executeUpdate("DELETE FROM ordenes_compra_detalles WHERE id_orden IN (" + ocZ + ")");
            st.executeUpdate("DELETE FROM ordenes_compra WHERE numero_orden LIKE 'ZPCTEST%'");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPCTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPCTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZPCTEST%'");
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zpc_%'");
        }
    }
}
