package com.gestorpyme.service;

import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paso N — Exportacion de detalle de recepciones con lote/vencimiento (tipo DETALLE_RECEPCIONES).
 *
 * Marcador ZPNTEST. Para aislar la prueba de la logica de recepcion (que se valida en el Paso M),
 * siembra filas crudas via JDBC: un proveedor, una bodega, dos items inventariables, un lote para el
 * item A (vencimiento centinela 2099-09-09), una orden de compra con sus detalles, una recepcion y
 * dos lineas de detalle: la primera CON lote (item A) y la segunda SIN lote (item B).
 *
 * La exportacion se escribe a un archivo temporal y se vuelve a leer para verificar: encabezado,
 * separador ';', BOM UTF-8, fila con lote (numero de lote, vencimiento e id) y fila sin lote
 * (campos de lote vacios). Tambien verifica que el tipo RECEPCIONES (cabeceras) no se rompe.
 *
 * Como la exportacion incluye TODAS las recepciones de la base, las aserciones localizan las filas
 * propias por el numero de recepcion unico {@code RC-ZPN-1}; el resto de filas (datos reales) se ignora.
 * En @AfterAll se limpian las filas sembradas (orden FK-seguro), los registros de exportaciones_log
 * de los archivos temporales y los propios archivos temporales.
 */
public class PasoNExportRecepcionDetalleTest {

    private static final String NUM_RC = "RC-ZPN-1";
    private static final String NUM_OC = "OC-ZPN-1";
    private static final String NUM_LOTE = "ZPN-L1";
    private static final String VENC = "2099-09-09";
    private static final String ITEM_A = "ZPN Item A";
    private static final String ITEM_B = "ZPN Item B";
    private static final String BODEGA = "ZPN Bodega";
    private static final String PROVEEDOR = "ZPN Proveedor";
    private static final String TMP_PREFIJO = "zpn_export";

    private static final String CAB_DETALLE =
            "Numero_RC;Fecha;Numero_OC;Item;Bodega;Cantidad;Numero_Lote;Fecha_Vencimiento;Id_Lote";
    private static final String CAB_RECEPCIONES = "Numero_RC;Numero_OC;Fecha;Proveedor;Estado";

    private static int idProveedor;
    private static int idBodega;
    private static int idItemA;
    private static int idItemB;
    private static int idOrden;
    private static int idDetalleOcA;
    private static int idDetalleOcB;
    private static int idRecepcion;
    private static int idLoteA;

    private static final List<Path> TEMPORALES = new ArrayList<>();
    private static final ExportacionService service = new ExportacionService();

    @BeforeAll
    static void sembrar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            idProveedor = insertar(c,
                    "INSERT INTO terceros (tipo_tercero, nombre, estado) VALUES ('PROVEEDOR', ?, 'ACTIVO')",
                    PROVEEDOR);
            idBodega = insertar(c,
                    "INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", BODEGA);
            idItemA = insertarItem(c, ITEM_A);
            idItemB = insertarItem(c, ITEM_B);

            // Lote del item A con vencimiento centinela.
            idLoteA = insertar(c,
                    "INSERT INTO lotes (id_item, id_bodega, numero_lote, cantidad_inicial, "
                    + "fecha_vencimiento, estado, cantidad_disponible) "
                    + "VALUES (?, ?, ?, 5, ?, 'ACTIVO', 5)",
                    idItemA, idBodega, NUM_LOTE, VENC);

            // Orden de compra + detalles (id_detalle_oc es NOT NULL en recepciones_detalles).
            idOrden = insertar(c,
                    "INSERT INTO ordenes_compra (numero_orden, id_proveedor, fecha_orden, estado, "
                    + "subtotal, total) VALUES (?, ?, '2099-01-10', 'EMITIDA', 0, 0)",
                    NUM_OC, idProveedor);
            idDetalleOcA = insertar(c,
                    "INSERT INTO ordenes_compra_detalles (id_orden, id_item, cantidad_solicitada, "
                    + "cantidad_recibida, precio_unitario, subtotal) VALUES (?, ?, 5, 0, 0, 0)",
                    idOrden, idItemA);
            idDetalleOcB = insertar(c,
                    "INSERT INTO ordenes_compra_detalles (id_orden, id_item, cantidad_solicitada, "
                    + "cantidad_recibida, precio_unitario, subtotal) VALUES (?, ?, 3, 0, 0, 0)",
                    idOrden, idItemB);

            // Recepcion (fecha por DEFAULT) y sus dos lineas: con lote (A) y sin lote (B).
            idRecepcion = insertar(c,
                    "INSERT INTO recepciones_mercancia (numero_recepcion, id_orden, id_usuario) "
                    + "VALUES (?, ?, 1)", NUM_RC, idOrden);
            insertar(c,
                    "INSERT INTO recepciones_detalles (id_recepcion, id_detalle_oc, id_item, id_bodega, "
                    + "cantidad_recibida, id_lote) VALUES (?, ?, ?, ?, 5, ?)",
                    idRecepcion, idDetalleOcA, idItemA, idBodega, idLoteA);
            insertar(c,
                    "INSERT INTO recepciones_detalles (id_recepcion, id_detalle_oc, id_item, id_bodega, "
                    + "cantidad_recibida, id_lote) VALUES (?, ?, ?, ?, 3, NULL)",
                    idRecepcion, idDetalleOcB, idItemB, idBodega);
        }
    }

    @AfterAll
    static void limpiar() throws SQLException, IOException {
        try (Connection c = DatabaseConnection.getConnection()) {
            ejecutar(c, "DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%" + TMP_PREFIJO + "%'");
            ejecutar(c, "DELETE FROM recepciones_detalles WHERE id_recepcion = " + idRecepcion);
            ejecutar(c, "DELETE FROM recepciones_mercancia WHERE id_recepcion = " + idRecepcion);
            ejecutar(c, "DELETE FROM lotes WHERE id_lote = " + idLoteA);
            ejecutar(c, "DELETE FROM ordenes_compra_detalles WHERE id_orden = " + idOrden);
            ejecutar(c, "DELETE FROM ordenes_compra WHERE id_orden = " + idOrden);
            ejecutar(c, "DELETE FROM items WHERE id_item IN (" + idItemA + ", " + idItemB + ")");
            ejecutar(c, "DELETE FROM bodegas WHERE id_bodega = " + idBodega);
            ejecutar(c, "DELETE FROM terceros WHERE id_tercero = " + idProveedor);
        }
        for (Path p : TEMPORALES) {
            Files.deleteIfExists(p);
        }
    }

    // ---------------------------------------------------------------------
    // Pruebas
    // ---------------------------------------------------------------------

    @Test
    void detalleConLote_incluyeNumeroLoteVencimientoEId() throws Exception {
        String texto = exportar(TipoExportacion.DETALLE_RECEPCIONES);
        String linea = buscarLinea(texto, NUM_RC, ITEM_A);
        assertNotNull(linea, "Debe existir la linea de detalle del item con lote");
        String[] col = linea.split(";", -1);
        assertEquals(NUM_LOTE, col[6], "Columna Numero_Lote");
        assertEquals(VENC, col[7], "Columna Fecha_Vencimiento (ISO)");
        assertEquals(String.valueOf(idLoteA), col[8], "Columna Id_Lote");
    }

    @Test
    void encabezadoEsperado() throws Exception {
        String texto = exportar(TipoExportacion.DETALLE_RECEPCIONES);
        String header = texto.split("\\R", 2)[0];
        assertEquals(CAB_DETALLE, header, "El encabezado debe listar las 9 columnas en orden");
    }

    @Test
    void separadorPuntoYComa() throws Exception {
        String texto = exportar(TipoExportacion.DETALLE_RECEPCIONES);
        String header = texto.split("\\R", 2)[0];
        assertEquals(9, header.split(";", -1).length, "El CSV usa ';' como separador (9 campos)");
    }

    @Test
    void bomUtf8Presente() throws Exception {
        byte[] bytes = exportarBytes(TipoExportacion.DETALLE_RECEPCIONES);
        assertTrue(bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB
                        && (bytes[2] & 0xFF) == 0xBF,
                "El archivo debe iniciar con BOM UTF-8 (EF BB BF)");
    }

    @Test
    void recepcionSinLote_camposVacios() throws Exception {
        String texto = exportar(TipoExportacion.DETALLE_RECEPCIONES);
        String linea = buscarLinea(texto, NUM_RC, ITEM_B);
        assertNotNull(linea, "Debe existir la linea de detalle del item sin lote");
        String[] col = linea.split(";", -1);
        assertEquals("", col[6], "Numero_Lote vacio cuando no hay lote");
        assertEquals("", col[7], "Fecha_Vencimiento vacia cuando no hay lote");
        assertEquals("", col[8], "Id_Lote vacio cuando no hay lote");
    }

    @Test
    void noRompeExportacionRecepcionesCabecera() throws Exception {
        String texto = exportar(TipoExportacion.RECEPCIONES);
        String header = texto.split("\\R", 2)[0];
        assertEquals(CAB_RECEPCIONES, header, "El export de cabeceras RECEPCIONES no debe cambiar");
        assertTrue(texto.contains(NUM_RC), "La cabecera debe seguir listando la recepcion sembrada");
    }

    // ---------------------------------------------------------------------
    // Utilidades
    // ---------------------------------------------------------------------

    /** Exporta el tipo a un archivo temporal y devuelve su texto (sin BOM). */
    private static String exportar(TipoExportacion tipo) throws Exception {
        Path tmp = nuevoTemporal();
        service.exportar(tipo, tmp.toString(), 1);
        byte[] bytes = Files.readAllBytes(tmp);
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1); // descarta BOM para comparaciones de texto
        }
        return s;
    }

    /** Exporta el tipo a un archivo temporal y devuelve sus bytes crudos (con BOM). */
    private static byte[] exportarBytes(TipoExportacion tipo) throws Exception {
        Path tmp = nuevoTemporal();
        service.exportar(tipo, tmp.toString(), 1);
        return Files.readAllBytes(tmp);
    }

    private static Path nuevoTemporal() throws IOException {
        Path tmp = Files.createTempFile(TMP_PREFIJO, ".csv");
        TEMPORALES.add(tmp);
        return tmp;
    }

    /** Primera linea que contiene todos los fragmentos indicados, o null. */
    private static String buscarLinea(String texto, String... fragmentos) {
        for (String linea : texto.split("\\R")) {
            boolean todos = true;
            for (String f : fragmentos) {
                if (!linea.contains(f)) { todos = false; break; }
            }
            if (todos) {
                return linea;
            }
        }
        return null;
    }

    private static int insertarItem(Connection c, String nombre) throws SQLException {
        return insertar(c,
                "INSERT INTO items (nombre, tipo_item, controla_inventario, estado, unidad_medida) "
                + "VALUES (?, 'PRODUCTO', 1, 'ACTIVO', 'Unidad')",
                nombre);
    }

    /** Inserta con parametros (String o Integer) y devuelve la clave generada. */
    private static int insertar(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < params.length; i++) {
                Object p = params[i];
                if (p instanceof Integer) {
                    ps.setInt(i + 1, (Integer) p);
                } else {
                    ps.setString(i + 1, (String) p);
                }
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
