package com.gestorpyme.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de {@link DateFormatter}: conversiones dd/MM/yyyy <-> LocalDate <-> ISO
 * y rechazo de fechas invalidas.
 */
class DateFormatterTest {

    @Test
    void aVista_formateaEnFormatoUsuario() {
        assertEquals("15/06/2026", DateFormatter.aVista(LocalDate.of(2026, 6, 15)));
    }

    @Test
    void aIso_formateaEnFormatoBaseDeDatos() {
        assertEquals("2026-06-15", DateFormatter.aIso(LocalDate.of(2026, 6, 15)));
    }

    @Test
    void desdeVista_interpretaFechaValida() {
        assertEquals(LocalDate.of(2026, 6, 15), DateFormatter.desdeVista("15/06/2026"));
    }

    @Test
    void desdeVista_vacioONuloDevuelveNull() {
        assertNull(DateFormatter.desdeVista(""));
        assertNull(DateFormatter.desdeVista("   "));
        assertNull(DateFormatter.desdeVista(null));
    }

    @Test
    void desdeVista_fechaInexistenteLanzaExcepcion() {
        // 31 de febrero no existe: el parseo estricto debe rechazarla.
        assertThrows(IllegalArgumentException.class, () -> DateFormatter.desdeVista("31/02/2026"));
    }

    @Test
    void desdeVista_formatoIncorrectoLanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> DateFormatter.desdeVista("2026-06-15"));
    }

    @Test
    void desdeIso_aceptaFechaConHora() {
        assertEquals(LocalDate.of(2026, 6, 14), DateFormatter.desdeIso("2026-06-14 19:05:00"));
    }

    @Test
    void vistaAIso_convierteCorrectamente() {
        assertEquals("2026-06-15", DateFormatter.vistaAIso("15/06/2026"));
    }

    @Test
    void isoAVista_convierteCorrectamente() {
        assertEquals("15/06/2026", DateFormatter.isoAVista("2026-06-15"));
    }

    @Test
    void valoresNulos_seManejanSinError() {
        assertEquals("", DateFormatter.aVista(null));
        assertNull(DateFormatter.aIso(null));
        assertEquals("", DateFormatter.isoAVista(null));
    }
}
