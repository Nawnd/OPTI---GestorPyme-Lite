package com.gestorpyme.infrastructure.export;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escritor de archivos CSV compatibles con Excel en espanol.
 *
 * - Separador de campos: punto y coma ';' (el que Excel-ES espera por defecto).
 * - Codificacion: UTF-8 con BOM, para que Excel muestre bien las tildes y la enie.
 * - Escape: los campos que contienen ';', comillas o saltos de linea se encierran
 *   entre comillas dobles y las comillas internas se duplican.
 *
 * Capa: infrastructure (entrada/salida). No depende de Swing.
 */
public final class CsvWriter {

    /** Separador de campos (Excel en espanol). */
    public static final char SEPARADOR = ';';

    private CsvWriter() {
    }

    /**
     * Escribe el archivo CSV con una fila de encabezados y las filas de datos.
     *
     * @param rutaArchivo ruta destino (se sobrescribe si existe).
     * @param encabezados nombres de columna.
     * @param filas       filas de datos (cada una con tantos campos como columnas).
     * @throws IOException si ocurre un error de escritura.
     */
    public static void escribir(String rutaArchivo, List<String> encabezados, List<List<String>> filas)
            throws IOException {
        try (FileOutputStream fos = new FileOutputStream(rutaArchivo);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            // BOM UTF-8 para que Excel detecte la codificacion.
            writer.write('\uFEFF');
            escribirFila(writer, encabezados);
            for (List<String> fila : filas) {
                escribirFila(writer, fila);
            }
        }
    }

    private static void escribirFila(BufferedWriter writer, List<String> campos) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < campos.size(); i++) {
            if (i > 0) {
                sb.append(SEPARADOR);
            }
            sb.append(escapar(campos.get(i)));
        }
        writer.write(sb.toString());
        writer.write("\r\n"); // fin de linea estilo Windows (mejor compatibilidad con Excel)
    }

    /** Encierra entre comillas y duplica comillas si el campo lo requiere. */
    private static String escapar(String campo) {
        String valor = (campo == null) ? "" : campo;
        boolean requiereComillas = valor.indexOf(SEPARADOR) >= 0
                || valor.indexOf('"') >= 0
                || valor.indexOf('\n') >= 0
                || valor.indexOf('\r') >= 0;
        if (!requiereComillas) {
            return valor;
        }
        return '"' + valor.replace("\"", "\"\"") + '"';
    }
}
