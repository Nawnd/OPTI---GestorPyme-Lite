package com.gestorpyme.service;

import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paso R — Exportación gerencial del Dashboard (tipo DASHBOARD_GERENCIAL). Marcador ZPRTEST.
 *
 * La exportación reutiliza {@code DashboardService.resumenEjecutivo} (año actual) y arma un CSV con
 * secciones (Periodo, Financiero/Comercial, Inventario, Compras, Lotes, Operación, Metas). No requiere
 * sembrar datos: las secciones aparecen aunque los valores sean cero. La prueba escribe a un archivo
 * temporal y verifica encabezado, secciones, separador ';' y BOM UTF-8, y que no se rompen otras
 * exportaciones. En @AfterAll limpia los registros de exportaciones_log de los temporales y los archivos.
 */
public class PasoRExportDashboardGerencialTest {

    private static final String TMP_PREFIJO = "zpr_export";
    private static final String CAB = "Seccion;Indicador;Valor;Detalle";

    private static final List<Path> TEMPORALES = new ArrayList<>();
    private static final ExportacionService service = new ExportacionService();

    @AfterAll
    static void limpiar() throws SQLException, IOException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%" + TMP_PREFIJO + "%'");
        }
        for (Path p : TEMPORALES) {
            Files.deleteIfExists(p);
        }
    }

    @Test
    void encabezadoYSeparadorPuntoYComa() throws Exception {
        String texto = exportar(TipoExportacion.DASHBOARD_GERENCIAL);
        String header = texto.split("\\R", 2)[0];
        assertEquals(CAB, header, "Encabezado gerencial Seccion;Indicador;Valor;Detalle");
        assertEquals(4, header.split(";", -1).length, "El CSV usa ';' (4 columnas)");
    }

    @Test
    void contieneTodasLasSecciones() throws Exception {
        String texto = exportar(TipoExportacion.DASHBOARD_GERENCIAL);
        for (String seccion : new String[]{"Periodo", "Financiero / Comercial", "Inventario",
                "Compras", "Lotes", "Operacion", "Metas"}) {
            assertTrue(texto.contains(seccion), "Debe contener la sección: " + seccion);
        }
    }

    @Test
    void filaDeDatosTieneCuatroColumnas() throws Exception {
        String texto = exportar(TipoExportacion.DASHBOARD_GERENCIAL);
        String linea = buscarLinea(texto, "Ventas del periodo");
        assertNotNull(linea, "Debe existir la fila de Ventas del periodo");
        assertEquals(4, linea.split(";", -1).length, "Cada fila tiene 4 columnas");
    }

    @Test
    void bomUtf8Presente() throws Exception {
        byte[] bytes = exportarBytes(TipoExportacion.DASHBOARD_GERENCIAL);
        assertTrue(bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB
                        && (bytes[2] & 0xFF) == 0xBF,
                "El archivo debe iniciar con BOM UTF-8 (EF BB BF)");
    }

    @Test
    void noRompeOtrasExportaciones() throws Exception {
        // Detalle de recepciones (Paso N) conserva su encabezado.
        String detalle = exportar(TipoExportacion.DETALLE_RECEPCIONES);
        assertEquals("Numero_RC;Fecha;Numero_OC;Item;Bodega;Cantidad;Numero_Lote;Fecha_Vencimiento;Id_Lote",
                detalle.split("\\R", 2)[0], "El detalle de recepciones no debe cambiar");
        // Recepciones cabecera tampoco cambia.
        String recep = exportar(TipoExportacion.RECEPCIONES);
        assertEquals("Numero_RC;Numero_OC;Fecha;Proveedor;Estado",
                recep.split("\\R", 2)[0], "El export de cabeceras RECEPCIONES no debe cambiar");
    }

    // ---------------------------------------------------------------------

    private static String exportar(TipoExportacion tipo) throws Exception {
        Path tmp = nuevoTemporal();
        service.exportar(tipo, tmp.toString(), 1);
        byte[] bytes = Files.readAllBytes(tmp);
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        return s;
    }

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

    private static String buscarLinea(String texto, String fragmento) {
        for (String linea : texto.split("\\R")) {
            if (linea.contains(fragmento)) {
                return linea;
            }
        }
        return null;
    }
}
