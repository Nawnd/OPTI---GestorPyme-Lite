package com.gestorpyme.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Formato de valores para mostrar en la interfaz (estilo Colombia):
 * - Moneda COP: {@code $ 1.250.000} (separador de miles '.', sin decimales).
 * - Porcentaje: {@code 41,0 %} (un decimal, coma decimal).
 * - Conteos: {@code 1.250} (separador de miles '.').
 *
 * Clase de utilidades: no se instancia. No depende de Swing.
 */
public final class MoneyFormatter {

    private static final DecimalFormatSymbols SIMBOLOS = construirSimbolos();
    private static final DecimalFormat FORMATO_MONEDA = new DecimalFormat("#,##0", SIMBOLOS);
    private static final DecimalFormat FORMATO_ENTERO = new DecimalFormat("#,##0", SIMBOLOS);
    private static final DecimalFormat FORMATO_PORCENTAJE = new DecimalFormat("0.0", SIMBOLOS);
    /** Cantidades: hasta 3 decimales, sin ceros sobrantes (85, 85,5, 1.250,5). */
    private static final DecimalFormat FORMATO_CANTIDAD = new DecimalFormat("#,##0.###", SIMBOLOS);

    private MoneyFormatter() {
    }

    private static DecimalFormatSymbols construirSimbolos() {
        DecimalFormatSymbols s = new DecimalFormatSymbols();
        s.setGroupingSeparator('.');
        s.setDecimalSeparator(',');
        return s;
    }

    /**
     * Formatea un monto como pesos colombianos: {@code $ 1.250.000}.
     *
     * @param valor monto (null se trata como 0).
     * @return texto con prefijo "$ " y separador de miles.
     */
    public static String cop(BigDecimal valor) {
        BigDecimal v = (valor == null) ? BigDecimal.ZERO : valor.setScale(0, RoundingMode.HALF_UP);
        return "$ " + FORMATO_MONEDA.format(v);
    }

    /**
     * Formatea un conteo entero con separador de miles: {@code 1.250}.
     *
     * @param valor cantidad.
     * @return texto formateado.
     */
    public static String entero(long valor) {
        return FORMATO_ENTERO.format(valor);
    }

    /**
     * Formatea una cantidad para mostrar en pantalla, sin ceros decimales sobrantes:
     * {@code 85}, {@code 85,5}, {@code 1.250,5}. Para columnas de cantidad en tablas.
     *
     * @param valor cantidad (null se trata como 0).
     * @return texto formateado (coma decimal, separador de miles).
     */
    public static String cantidad(BigDecimal valor) {
        BigDecimal v = (valor == null) ? BigDecimal.ZERO : valor;
        return FORMATO_CANTIDAD.format(v);
    }

    /**
     * Formatea un porcentaje a partir de una proporcion 0..1: {@code 41,0 %}.
     *
     * @param proporcion valor entre 0 y 1.
     * @return texto con un decimal y el simbolo "%".
     */
    public static String porcentaje(double proporcion) {
        double pct = proporcion * 100.0;
        if (pct < 0) {
            pct = 0;
        }
        return FORMATO_PORCENTAJE.format(pct) + " %";
    }
}
