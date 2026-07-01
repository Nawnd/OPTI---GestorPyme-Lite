package com.gestorpyme.infrastructure.export;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link CsvWriter}: genera el archivo, escribe encabezados y usa el
 * separador ';' con el escape correcto.
 */
class CsvWriterTest {

    @Test
    void generaArchivoConEncabezadosYSeparador() throws IOException {
        Path temp = Files.createTempFile("export_test", ".csv");
        try {
            List<String> encabezados = Arrays.asList("Codigo", "Nombre");
            List<List<String>> filas = Arrays.asList(
                    Arrays.asList("1", "Tornillo"),
                    Arrays.asList("2", "Cable; especial")); // contiene ';' -> debe ir entre comillas

            CsvWriter.escribir(temp.toString(), encabezados, filas);

            assertTrue(Files.exists(temp), "El archivo CSV deberia existir");
            String contenido = new String(Files.readAllBytes(temp), StandardCharsets.UTF_8);

            assertTrue(contenido.contains("Codigo;Nombre"), "Debe contener los encabezados separados por ';'");
            assertTrue(contenido.contains("1;Tornillo"), "Debe contener la primera fila");
            assertTrue(contenido.contains("\"Cable; especial\""),
                    "El campo con ';' debe ir entre comillas dobles");
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
