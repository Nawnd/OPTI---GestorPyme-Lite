package com.gestorpyme.service;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paso T — Respaldo y restauración de la base de datos SQLite. Marcador ZPTTEST.
 *
 * Las pruebas operan con <b>archivos temporales</b>, nunca borran datos: el respaldo solo
 * <i>lee</i> la base actual; la prueba de restauración restaura un <b>snapshot idéntico</b> recién
 * creado de la propia base, de modo que su contenido no cambia. En @AfterAll se eliminan los
 * temporales. No se toca el esquema ni se ejecuta SQL.
 */
public class PasoTBackupRestoreTest {

    private static final String PREFIJO = "zpt_backup";
    private static final List<Path> TEMPORALES = new ArrayList<>();
    private static final BackupService service = new BackupService();

    @AfterAll
    static void limpiar() throws IOException {
        for (Path p : TEMPORALES) {
            Files.deleteIfExists(p);
        }
    }

    @Test
    void crearRespaldoCopiaArchivoExistente() throws Exception {
        Path destino = nuevoTemporal();
        Files.deleteIfExists(destino); // partir de "no existe" para validar la creación real
        service.crearRespaldo(destino);
        assertTrue(Files.exists(destino), "El respaldo debe existir tras crearlo");
        assertTrue(Files.size(destino) > 0, "El respaldo no debe estar vacío");
    }

    @Test
    void respaldoConservaContenido() throws Exception {
        Path destino = nuevoTemporal();
        service.crearRespaldo(destino);
        byte[] original = Files.readAllBytes(service.rutaBaseDatos());
        byte[] copia = Files.readAllBytes(destino);
        assertArrayEquals(original, copia, "El respaldo debe ser idéntico a la base actual");
    }

    @Test
    void restauracionReemplazaArchivoDestino() throws Exception {
        // Se respalda la base actual y se restaura ese mismo snapshot: contenido idéntico, sin pérdida.
        Path snapshot = nuevoTemporal();
        service.crearRespaldo(snapshot);
        service.restaurarRespaldo(snapshot);
        byte[] base = Files.readAllBytes(service.rutaBaseDatos());
        byte[] esperado = Files.readAllBytes(snapshot);
        assertArrayEquals(esperado, base, "Tras restaurar, la base debe coincidir con el respaldo");
    }

    @Test
    void errorControladoSiOrigenNoExiste() throws Exception {
        Path inexistente = Files.createTempDirectory("zpt_dir").resolve("no_existe_zpt.db");
        assertThrows(IOException.class, () -> service.restaurarRespaldo(inexistente),
                "Restaurar un archivo inexistente debe lanzar IOException");
    }

    @Test
    void validaArgumentosYEvitaMismaBase() {
        assertThrows(IllegalArgumentException.class, () -> service.crearRespaldo(null),
                "Destino nulo debe rechazarse");
        assertThrows(IOException.class, () -> service.crearRespaldo(service.rutaBaseDatos()),
                "No se debe respaldar sobre la misma base en uso");
    }

    // ---------------------------------------------------------------------

    private static Path nuevoTemporal() throws IOException {
        Path tmp = Files.createTempFile(PREFIJO, ".db");
        TEMPORALES.add(tmp);
        return tmp;
    }
}
