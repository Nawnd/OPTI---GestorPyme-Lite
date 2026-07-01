package com.gestorpyme.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas de {@link MoneyFormatter}: formato COP, conteos y porcentajes.
 */
class MoneyFormatterTest {

    @Test
    void copFormateaConSeparadorDeMiles() {
        assertEquals("$ 1.250.000", MoneyFormatter.cop(new BigDecimal("1250000")));
    }

    @Test
    void copRedondeaSinDecimales() {
        assertEquals("$ 1.235", MoneyFormatter.cop(new BigDecimal("1234.56")));
    }

    @Test
    void copConCeroYNulo() {
        assertEquals("$ 0", MoneyFormatter.cop(BigDecimal.ZERO));
        assertEquals("$ 0", MoneyFormatter.cop(null));
    }

    @Test
    void enteroConSeparadorDeMiles() {
        assertEquals("1.250", MoneyFormatter.entero(1250));
    }

    @Test
    void porcentajeConUnDecimalYComa() {
        assertEquals("41,0 %", MoneyFormatter.porcentaje(0.41));
        assertEquals("0,0 %", MoneyFormatter.porcentaje(0.0));
        assertEquals("100,0 %", MoneyFormatter.porcentaje(1.0));
    }
}
