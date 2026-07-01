package com.gestorpyme.util;

import com.gestorpyme.domain.exception.ValidacionException;

import java.math.BigDecimal;

/**
 * Convierte lo que el usuario escribe en formularios a {@link BigDecimal} de forma segura,
 * usando convencion regional colombiana: el punto '.' es separador de miles y la coma ','
 * es separador decimal. Quita el simbolo '$' y los espacios.
 *
 * Ejemplos de normalizacion:
 *   "30000"        -> 30000
 *   "30.000"       -> 30000
 *   "$ 30.000"     -> 30000
 *   "1.250.000,50" -> 1250000.50
 *   "1250000,50"   -> 1250000.50
 *
 * Rechaza texto no numerico, vacios (cuando es obligatorio) y, segun el metodo, negativos
 * o valores que superan un tope (saldo, pendiente, stock). Responsabilidad UNICA: parseo de
 * entrada (separada de MoneyFormatter, que es solo para mostrar, y de ExportValueFormatter,
 * que es solo para CSV/Excel). No depende de Swing. Clase de utilidad: no instanciable.
 */
public final class NumberParser {

    private NumberParser() {
    }

    /**
     * Normaliza y convierte el texto a BigDecimal.
     *
     * @param texto entrada del usuario (admite '$', espacios, miles con '.', decimal con ',').
     * @return el valor como BigDecimal.
     * @throws ValidacionException si esta vacio o no representa un numero valido.
     */
    public static BigDecimal parse(String texto) {
        String limpio = normalizar(texto);
        if (limpio.isEmpty()) {
            throw new ValidacionException("Ingrese un valor numerico valido.");
        }
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw new ValidacionException("Ingrese un valor numerico valido.");
        }
    }

    /**
     * Parsea y exige que el valor sea mayor que cero.
     *
     * @throws ValidacionException si no es numerico o es &lt;= 0.
     */
    public static BigDecimal parsePositivo(String texto) {
        BigDecimal v = parse(texto);
        if (v.signum() <= 0) {
            throw new ValidacionException("El valor debe ser mayor que cero.");
        }
        return v;
    }

    /**
     * Parsea y exige que el valor no sea negativo (permite cero).
     *
     * @throws ValidacionException si no es numerico o es &lt; 0.
     */
    public static BigDecimal parseNoNegativo(String texto) {
        BigDecimal v = parse(texto);
        if (v.signum() < 0) {
            throw new ValidacionException("El valor no puede ser negativo.");
        }
        return v;
    }

    /**
     * Valida que un valor no supere un tope (saldo, pendiente, stock, etc.).
     *
     * @param valor   valor a validar.
     * @param tope    maximo permitido.
     * @param mensaje mensaje a mostrar si lo supera.
     * @throws ValidacionException si valor &gt; tope.
     */
    public static void validarTope(BigDecimal valor, BigDecimal tope, String mensaje) {
        if (valor != null && tope != null && valor.compareTo(tope) > 0) {
            throw new ValidacionException(mensaje);
        }
    }

    /**
     * Normaliza el texto: quita '$', espacios y separadores de miles '.'; convierte la coma
     * decimal ',' en punto. Devuelve "" si la entrada es nula o queda vacia.
     */
    private static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = texto.trim();
        if (s.isEmpty()) {
            return "";
        }
        s = s.replace("$", "").replace(" ", "").replace("\u00A0", "");
        // Convencion CO: '.' = miles (se elimina), ',' = decimal (se vuelve '.').
        s = s.replace(".", "");
        s = s.replace(",", ".");
        return s;
    }
}
