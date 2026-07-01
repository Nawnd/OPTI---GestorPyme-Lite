package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.MovimientoInventario;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.InventarioRepository;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del Paso D (Kardex, v0.8): RF-05 motivo obligatorio en ajustes
 * (AJUSTE_POSITIVO / AJUSTE_NEGATIVO) y RF-06 usuario responsable visible en el Kardex
 * (con fallback "Sistema" si no hay usuario). Datos marcados "ZPDTEST". Sin migraciones.
 */
class PasoDKardexTest {

    private static final InventarioService inventarioService = new InventarioService();
    private static final InventarioRepository inventarioRepo = new InventarioRepository();
    private static final ItemService itemService = new ItemService();
    private static final ExportacionService exportacionService = new ExportacionService();

    private static int idBodega;
    private static int idItem;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
        idBodega = insertarBodega("ZPDTEST Bodega");
        Item it = inventariable("ZPDTEST-ITM");
        itemService.guardar(it);
        idItem = it.getIdItem();
        sembrarStock(idItem, idBodega, "100"); // stock holgado para no chocar con validaciones
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- RF-05: motivo obligatorio en ajustes ----------------

    @Test
    void ajustePositivoSinMotivoSeRechaza() {
        ValidacionException ex = assertThrows(ValidacionException.class, () ->
                inventarioService.registrarMovimiento(idItem, idBodega,
                        TipoMovimiento.AJUSTE_POSITIVO, new BigDecimal("5"), null, 1));
        assertTrue(ex.getMessage().toLowerCase().contains("motivo"));
    }

    @Test
    void ajusteNegativoSinMotivoSeRechaza() {
        // Motivo en blanco (solo espacios) tambien se rechaza.
        assertThrows(ValidacionException.class, () ->
                inventarioService.registrarMovimiento(idItem, idBodega,
                        TipoMovimiento.AJUSTE_NEGATIVO, new BigDecimal("1"), "   ", 1));
    }

    @Test
    void ajustePositivoConMotivoSeRegistra() throws SQLException {
        inventarioService.registrarMovimiento(idItem, idBodega,
                TipoMovimiento.AJUSTE_POSITIVO, new BigDecimal("5"), "ZPDTEST Conteo fisico", 1);
        boolean registrado = inventarioRepo.listarMovimientos(idItem, idBodega, null, "", "").stream()
                .anyMatch(m -> "ZPDTEST Conteo fisico".equals(m.getMotivo()));
        assertTrue(registrado, "El ajuste con motivo debe quedar registrado en el Kardex.");
    }

    @Test
    void entradaSinMotivoSePermite() {
        // ENTRADA no es ajuste: el motivo sigue siendo opcional (comportamiento previo).
        assertDoesNotThrow(() ->
                inventarioService.registrarMovimiento(idItem, idBodega,
                        TipoMovimiento.ENTRADA, new BigDecimal("3"), null, 1));
    }

    // ---------------- RF-06: usuario responsable en Kardex ----------------

    @Test
    void kardexMuestraUsuarioResponsable() throws SQLException {
        inventarioService.registrarMovimiento(idItem, idBodega,
                TipoMovimiento.AJUSTE_POSITIVO, new BigDecimal("2"), "ZPDTEST con usuario", 1);
        MovimientoInventario m = inventarioRepo.listarMovimientos(idItem, idBodega, null, "", "").stream()
                .filter(x -> "ZPDTEST con usuario".equals(x.getMotivo()))
                .findFirst().orElse(null);
        assertNotNull(m);
        // El usuario 1 (admin) tiene nombre_completo "Administrador General" (COALESCE lo prefiere).
        assertEquals("Administrador General", m.getNombreUsuario());
    }

    @Test
    void kardexNoFallaCuandoUsuarioEsNull() throws SQLException {
        // Movimiento historico sin usuario (id_usuario NULL): la vista debe mostrar "Sistema".
        insertarMovimientoSinUsuario(idItem, idBodega, "ZPDTEST sin usuario");
        MovimientoInventario m = inventarioRepo.listarMovimientos(idItem, idBodega, null, "", "").stream()
                .filter(x -> "ZPDTEST sin usuario".equals(x.getMotivo()))
                .findFirst().orElse(null);
        assertNotNull(m, "El movimiento sin usuario debe listarse sin romper la consulta.");
        assertEquals("Sistema", m.getNombreUsuario());
    }

    // ---------------- Exportacion Kardex incluye Usuario ----------------

    @Test
    void exportKardexIncluyeUsuario() throws SQLException, IOException {
        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "zpd_kardex.csv");
        exportacionService.exportar(TipoExportacion.KARDEX, archivo.toString(), 1);
        String contenido = new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);
        if (contenido.startsWith("\uFEFF")) {
            contenido = contenido.substring(1);
        }
        String encabezado = contenido.split("\r?\n", 2)[0];
        assertEquals("Fecha;Item;Bodega;Tipo;Cantidad;Motivo;Usuario;Lote", encabezado);
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zpd_%'");
        }
    }

    // ---------------- auxiliares ----------------

    private static Item inventariable(String codigo) {
        Item it = new Item();
        it.setCodigo(codigo);
        it.setNombre("ZPDTEST " + codigo);
        it.setTipoItem(TipoItem.REPUESTO);
        it.setIdCategoria(1);
        it.setPrecioCompra(BigDecimal.ZERO);
        it.setPrecioVenta(new BigDecimal("1000"));
        it.setControlaInventario(true);
        it.setStockMinimo(new BigDecimal("5"));
        it.setEstado(EstadoRegistro.ACTIVO);
        return it;
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

    /** Inserta un movimiento con id_usuario NULL para validar el fallback "Sistema". */
    private static void insertarMovimientoSinUsuario(int idItem, int idBodega, String motivo) throws SQLException {
        try (Connection c = DatabaseConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO inventario_movimientos (id_item, id_bodega, tipo_movimiento, cantidad, motivo, id_usuario, fecha) "
                     + "VALUES (?, ?, 'ENTRADA', 1, ?, NULL, '2026-06-18 10:00:00')")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, motivo);
            ps.executeUpdate();
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String itemsZ = "SELECT id_item FROM items WHERE codigo LIKE 'ZPDTEST%'";
            st.executeUpdate("DELETE FROM inventario_movimientos WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM inventario_stock WHERE id_item IN (" + itemsZ + ")");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZPDTEST%'");
            st.executeUpdate("DELETE FROM bodegas WHERE nombre LIKE 'ZPDTEST%'");
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zpd_%'");
        }
    }
}
