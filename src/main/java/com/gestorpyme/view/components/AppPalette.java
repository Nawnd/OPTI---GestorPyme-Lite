package com.gestorpyme.view.components;

import java.awt.Color;

/**
 * Paleta de color OFICIAL de OPTI - GestorPyme Lite (fuente unica de verdad).
 *
 * Colores base (segun la guia visual del proyecto):
 *   #06141B  oscuro principal (sidebar, encabezados, botones primarios, texto destacado)
 *   #57707A  secundario institucional (botones secundarios, estados activos, acentos KPI)
 *   #7E919F  texto secundario, iconos suaves, subtitulos
 *   #979DAB  neutro medio (estados informativos, bordes/divisores sutiles)
 *   #C5BAC4  superficie suave de tarjetas secundarias / divisores
 *   #DEDCDC  fondo general claro
 *
 * Para estados (exito/advertencia/peligro) se usan tonos SOBRIOS y compatibles, no
 * colores saturados. {@link UiTheme} referencia esta paleta para armonizar toda la app.
 */
public final class AppPalette {

    // --- Base ---
    public static final Color PRIMARIO = new Color(0x06, 0x14, 0x1B);
    public static final Color SECUNDARIO = new Color(0x57, 0x70, 0x7A);
    public static final Color TEXTO_SECUNDARIO = new Color(0x7E, 0x91, 0x9F);
    public static final Color NEUTRO = new Color(0x97, 0x9D, 0xAB);
    public static final Color SUPERFICIE_SUAVE = new Color(0xC5, 0xBA, 0xC4);
    public static final Color FONDO = new Color(0xDE, 0xDC, 0xDC);
    public static final Color BLANCO = Color.WHITE;

    // --- Derivados ---
    /** Variante un poco mas clara del primario (hover de botones, gradientes). */
    public static final Color PRIMARIO_HOVER = new Color(0x0E, 0x27, 0x30);
    /** Texto del sidebar (gris claro legible sobre el oscuro). */
    public static final Color SIDEBAR_TEXTO = new Color(0xC7, 0xCD, 0xD3);
    /** Borde y divisores. */
    public static final Color BORDE = new Color(0xC5, 0xBA, 0xC4);
    public static final Color BORDE_SUAVE = new Color(0x97, 0x9D, 0xAB);
    /** Fondo de tarjeta secundaria muy suave. */
    public static final Color TARJETA_SUAVE = new Color(0xEC, 0xEA, 0xEA);

    // --- Estados (sobrios, no saturados) ---
    public static final Color EXITO = new Color(0x3F, 0x6B, 0x57);      // verde apagado
    public static final Color ADVERTENCIA = new Color(0xB0, 0x82, 0x38); // ambar apagado
    public static final Color PELIGRO = new Color(0x9E, 0x4A, 0x45);     // rojo apagado

    private AppPalette() {
    }

    /**
     * Mezcla un color con blanco para obtener una version clara (para fondos suaves).
     *
     * @param base  color base.
     * @param ratio 0 = blanco total, 1 = color base.
     * @return el color aclarado.
     */
    public static Color aclarar(Color base, double ratio) {
        double r = Math.max(0, Math.min(1, ratio));
        int rojo = (int) Math.round(255 + (base.getRed() - 255) * r);
        int verde = (int) Math.round(255 + (base.getGreen() - 255) * r);
        int azul = (int) Math.round(255 + (base.getBlue() - 255) * r);
        return new Color(rojo, verde, azul);
    }
}
