package com.gestorpyme.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Prepara valores para EXPORTACION (CSV/Excel), sin formato visual humano: sin simbolo '$',
 * sin separador de miles y sin decimales innecesarios (nada de ".0"). Usa coma decimal solo
 * cuando hay parte decimal, por compatibilidad regional. Responsabilidad UNICA: exportacion
 * (separada de MoneyFormatter, que es solo UI, y de NumberParser, que es solo entrada).
 *
 * Ejemplos:
 *   formatMoneyForCsv(976548)        -> "976548"
 *   formatMoneyForCsvDecimal(1250000.50) -> "1250000,50"
 *   formatQuantityForCsv(85)         -> "85"
 *   formatQuantityForCsv(85.5)       -> "85,5"
 *
 * No depende de Swing. Clase de utilidad: no instanciable.
 */
public final class ExportValueFormatter {

    private ExportValueFormatter() {
    }

    /**
     * Monto sin decimales (pesos enteros): redondea al entero mas cercano. Ej.: 976548.
     *
     * @param valor monto (null se trata como 0).
     */
    public static String formatMoneyForCsv(BigDecimal valor) {
        BigDecimal v = (valor == null) ? BigDecimal.ZERO : valor.setScale(0, RoundingMode.HALF_UP);
        return v.toPlainString();
    }

    /**
     * Monto con decimales solo si los tiene: entero -> "976548"; con decimales -> "976548,50"
     * (dos decimales, coma). Nunca agrega ",00" innecesario.
     *
     * @param valor monto (null se trata como 0).
     */
    public static String formatMoneyForCsvDecimal(BigDecimal valor) {
        BigDecimal v = (valor == null) ? BigDecimal.ZERO : valor;
        BigDecimal sinCeros = v.stripTrailingZeros();
        if (sinCeros.scale() <= 0) {
            return sinCeros.toPlainString(); // es entero: sin decimales
        }
        return v.setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    /**
     * Cantidad sin ceros decimales sobrantes: 85, 85,5. Coma decimal, sin miles.
     *
     * @param cantidad cantidad (null se trata como 0).
     */
    public static String formatQuantityForCsv(BigDecimal cantidad) {
        BigDecimal v = (cantidad == null) ? BigDecimal.ZERO : cantidad.stripTrailingZeros();
        if (v.scale() < 0) {
            v = v.setScale(0);
        }
        return v.toPlainString().replace('.', ',');
    }

    /**
     * Entero limpio para CSV. Ej.: 120.
     *
     * @param valor numero (null se trata como 0).
     */
    public static String formatIntegerForCsv(Number valor) {
        if (valor == null) {
            return "0";
        }
        return Long.toString(Math.round(valor.doubleValue()));
    }

    /**
     * Limpia un texto para no romper columnas del CSV: elimina saltos de linea y reemplaza el
     * separador ';' por ',' (para que no se interprete como nueva columna). Recorta espacios.
     *
     * @param texto texto a limpiar (null -> "").
     */
    public static String formatTextForCsv(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\r", " ")
                    .replace("\n", " ")
                    .replace(";", ",")
                    .trim();
    }

    /**
     * Normaliza una fecha almacenada (texto) a formato ISO para exportacion:
     * <ul>
     *   <li>Fecha sola -> {@code yyyy-MM-dd}.</li>
     *   <li>Fecha con hora -> {@code yyyy-MM-dd HH:mm} (se omiten los segundos).</li>
     * </ul>
     * Acepta tanto el separador ' ' como 'T' entre fecha y hora. Si el valor no tiene el
     * patron de fecha esperado, se devuelve tal cual (recortado) para no corromper el dato.
     *
     * @param fecha fecha almacenada (p. ej. "2026-06-13" o "2026-06-13 10:30:00"); null -> "".
     * @return fecha normalizada para CSV.
     */
    public static String formatDateForCsv(String fecha) {
        if (fecha == null) {
            return "";
        }
        String f = fecha.trim();
        if (f.isEmpty()) {
            return "";
        }
        f = f.replace('T', ' '); // admite formato ISO con 'T'
        // Verifica prefijo de fecha yyyy-MM-dd (posiciones de los guiones).
        if (f.length() >= 10 && f.charAt(4) == '-' && f.charAt(7) == '-') {
            String dia = f.substring(0, 10);
            // Si trae hora valida (HH:mm), la conserva sin segundos.
            if (f.length() >= 16 && f.charAt(10) == ' ' && f.charAt(13) == ':') {
                return dia + " " + f.substring(11, 16);
            }
            return dia;
        }
        return f; // formato desconocido: no se altera
    }

    /**
     * Devuelve la etiqueta legible de un estado para exportacion. Los enums del proyecto
     * sobreescriben {@code toString()} para devolver su etiqueta (p. ej. ABONADA -> "Parcial",
     * PENDIENTE_PAGO -> "Pendiente de pago"), por lo que se usa esa representacion en vez del
     * nombre crudo {@code name()}.
     *
     * @param estado estado (enum u objeto); null -> "".
     * @return etiqueta legible del estado.
     */
    public static String formatEstadoForCsv(Object estado) {
        if (estado == null) {
            return "";
        }
        return estado.toString().trim();
    }
}
