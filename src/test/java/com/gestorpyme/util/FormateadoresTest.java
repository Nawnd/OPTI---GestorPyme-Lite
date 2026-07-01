package com.gestorpyme.util;

import com.gestorpyme.domain.exception.ValidacionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las tres utilidades de valores, con responsabilidades separadas:
 * MoneyFormatter (UI), NumberParser (entrada del usuario) y ExportValueFormatter (CSV/Excel).
 * No requieren base de datos.
 */
class FormateadoresTest {

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    // ---------- MoneyFormatter (solo UI) ----------

    @Test
    void moneyFormatterMuestraPesosConSeparadorDeMiles() {
        assertEquals("$ 30.000", MoneyFormatter.cop(n("30000")));
        assertEquals("$ 976.548", MoneyFormatter.cop(n("976548")));
        assertEquals("$ 0", MoneyFormatter.cop(BigDecimal.ZERO));
    }

    @Test
    void moneyFormatterCantidadSinCerosSobrantes() {
        assertEquals("85", MoneyFormatter.cantidad(n("85")));
        assertEquals("85,5", MoneyFormatter.cantidad(n("85.5")));
    }

    // ---------- NumberParser (entrada del usuario) ----------

    @Test
    void numberParserNormalizaEntradas() {
        assertEquals(0, n("30000").compareTo(NumberParser.parse("30000")));
        assertEquals(0, n("30000").compareTo(NumberParser.parse("30.000")));
        assertEquals(0, n("30000").compareTo(NumberParser.parse("$ 30.000")));
        assertEquals(0, n("1250000.50").compareTo(NumberParser.parse("1.250.000,50")));
        assertEquals(0, n("1250000.50").compareTo(NumberParser.parse("1250000,50")));
    }

    @Test
    void numberParserRechazaTextoNoNumerico() {
        assertThrows(ValidacionException.class, () -> NumberParser.parse("abc"));
        assertThrows(ValidacionException.class, () -> NumberParser.parse("   "));
    }

    @Test
    void numberParserRechazaNegativoCuandoNoSePermite() {
        assertThrows(ValidacionException.class, () -> NumberParser.parsePositivo("-5000"));
        assertThrows(ValidacionException.class, () -> NumberParser.parseNoNegativo("-5000"));
        assertThrows(ValidacionException.class, () -> NumberParser.parsePositivo("0"));
    }

    @Test
    void numberParserValidaTope() {
        assertThrows(ValidacionException.class,
                () -> NumberParser.validarTope(n("1500"), n("1000"), "El abono no puede superar el saldo."));
    }

    // ---------- ExportValueFormatter (CSV / Excel) ----------

    @Test
    void exportMoneyEsNumeroLimpio() {
        assertEquals("976548", ExportValueFormatter.formatMoneyForCsv(n("976548")));
        assertEquals("30000", ExportValueFormatter.formatMoneyForCsv(n("30000")));
        assertEquals("0", ExportValueFormatter.formatMoneyForCsv(BigDecimal.ZERO));
    }

    @Test
    void exportMoneyDecimalUsaComaSoloSiHayDecimales() {
        assertEquals("1250000,50", ExportValueFormatter.formatMoneyForCsvDecimal(n("1250000.50")));
        assertEquals("976548", ExportValueFormatter.formatMoneyForCsvDecimal(n("976548")));
    }

    @Test
    void exportCantidadSinCerosSobrantes() {
        assertEquals("85", ExportValueFormatter.formatQuantityForCsv(n("85")));
        assertEquals("85,5", ExportValueFormatter.formatQuantityForCsv(n("85.5")));
    }

    @Test
    void exportEnteroLimpio() {
        assertEquals("120", ExportValueFormatter.formatIntegerForCsv(120));
    }

    @Test
    void exportNuncaIncluyeSimboloMilesNiPuntoCero() {
        String m = ExportValueFormatter.formatMoneyForCsv(n("976548"));
        assertFalse(m.contains("$"), "No debe incluir $");
        assertFalse(m.contains("."), "No debe incluir puntos de miles");
        assertFalse(m.endsWith(".0"), "No debe terminar en .0");
    }

    @Test
    void exportTextoNoRompeColumnas() {
        // El salto de linea se vuelve espacio y el ';' se vuelve ',' para no romper columnas.
        assertEquals("a b,c", ExportValueFormatter.formatTextForCsv("a\nb;c"));
    }
}
