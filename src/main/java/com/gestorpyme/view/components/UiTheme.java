package com.gestorpyme.view.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;

/**
 * Tema visual centralizado de OPTI - GestorPyme Lite.
 *
 * Reune en un solo lugar los colores corporativos, las fuentes y el espaciado
 * para mantener una apariencia consistente y profesional en todas las vistas.
 * Cambiar un valor aqui se refleja en toda la aplicacion.
 *
 * Color corporativo principal: #051E22.
 * Esta clase no tiene estado: solo constantes y metodos de ayuda estaticos.
 */
public final class UiTheme {

    private UiTheme() {
        // Clase de utilidades: no se instancia.
    }

    // ----------------------------- Colores -----------------------------
    // Se referencia la paleta oficial (AppPalette) como fuente unica de color.

    /** Color principal (#06141B): menu lateral, encabezados, botones primarios. */
    public static final Color PRIMARIO = AppPalette.PRIMARIO;
    /** Variante mas clara del primario, para hover y resaltados. */
    public static final Color PRIMARIO_HOVER = AppPalette.PRIMARIO_HOVER;
    /** Acento institucional (#57707A) para selecciones y estados activos. */
    public static final Color ACENTO = AppPalette.SECUNDARIO;

    /** Fondo general del area de contenido (#DEDCDC). */
    public static final Color FONDO = AppPalette.FONDO;
    /** Superficie de tarjetas/paneles/formularios. */
    public static final Color SUPERFICIE = AppPalette.BLANCO;
    /** Borde sutil para separar paneles y campos (#C5BAC4). */
    public static final Color BORDE = AppPalette.BORDE;

    /** Texto principal sobre fondos claros (#06141B). */
    public static final Color TEXTO = AppPalette.PRIMARIO;
    /** Texto secundario/tenue (#7E919F). */
    public static final Color TEXTO_TENUE = AppPalette.TEXTO_SECUNDARIO;
    /** Texto sobre fondos oscuros (menu, encabezados). */
    public static final Color TEXTO_CLARO = AppPalette.BLANCO;

    /** Fondo del encabezado de las tablas. */
    public static final Color TABLA_ENCABEZADO = PRIMARIO;
    /** Texto del encabezado de las tablas. */
    public static final Color TABLA_ENCABEZADO_TEXTO = AppPalette.BLANCO;
    /** Fondo de fila seleccionada en tablas. */
    public static final Color TABLA_SELECCION = new Color(0xD3, 0xD9, 0xDC);
    /** Texto de fila seleccionada. */
    public static final Color TABLA_SELECCION_TEXTO = AppPalette.PRIMARIO;
    /** Fondo de filas alternas (efecto cebra). */
    public static final Color TABLA_FILA_ALTERNA = new Color(0xF2, 0xF1, 0xF1);
    /** Color de la rejilla de la tabla. */
    public static final Color TABLA_REJILLA = new Color(0xD7, 0xD5, 0xD5);

    // ----------------------------- Fuentes -----------------------------

    /** Familia tipografica base (fallback de SF Pro Display segun estandarizacion). */
    public static final String FUENTE = "Segoe UI";

    /**
     * Ruta del logo en el classpath. El archivo fisico va en
     * src/main/resources/images/logo.png. Si no existe, se usa el texto "OPTI".
     */
    public static final String RUTA_LOGO = "/images/logo.png";

    /** Fuente generica con tamano sensible al zoom (ver {@link UiScaleManager}). */
    public static Font fuente(int estilo, int tamanoBase) {
        return new Font(FUENTE, estilo, UiScaleManager.escalar(tamanoBase));
    }

    /** Fuente de la marca "OPTI" (cuando no hay logo). */
    public static Font fuenteMarca() {
        return fuente(Font.BOLD, 20);
    }

    /** Fuente de titulo de seccion. */
    public static Font fuenteTitulo() {
        return fuente(Font.BOLD, 18);
    }

    /** Fuente de subtitulo. */
    public static Font fuenteSubtitulo() {
        return fuente(Font.BOLD, 14);
    }

    /** Fuente normal de cuerpo. */
    public static Font fuenteNormal() {
        return fuente(Font.PLAIN, 13);
    }

    /** Fuente en negrita de cuerpo. */
    public static Font fuenteNegrita() {
        return fuente(Font.BOLD, 13);
    }

    /** Fuente pequena para pistas/ayudas. */
    public static Font fuentePequena() {
        return fuente(Font.PLAIN, 11);
    }

    // ----------------------------- Espaciado -----------------------------

    /** Margen estandar de paneles. */
    public static final int MARGEN = 16;
    /** Separacion estandar entre componentes. */
    public static final int ESPACIO = 8;

    /** Borde vacio uniforme con el margen estandar. */
    public static Border margen() {
        return BorderFactory.createEmptyBorder(MARGEN, MARGEN, MARGEN, MARGEN);
    }

    /** Borde vacio con un valor personalizado en los cuatro lados. */
    public static Border margen(int valor) {
        return BorderFactory.createEmptyBorder(valor, valor, valor, valor);
    }

    /** Borde de tarjeta: linea sutil + relleno interno. */
    public static Border tarjeta() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE),
                BorderFactory.createEmptyBorder(MARGEN, MARGEN, MARGEN, MARGEN));
    }

    // ----------------------------- Botones -----------------------------

    /**
     * Da estilo a un boton principal (fondo corporativo, texto blanco).
     *
     * @param boton boton a estilizar.
     * @return el mismo boton, para encadenar.
     */
    public static JButton botonPrimario(JButton boton) {
        boton.setBackground(PRIMARIO);
        boton.setForeground(TEXTO_CLARO);
        boton.setFocusPainted(false);
        boton.setFont(fuenteNegrita());
        boton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return boton;
    }

    /**
     * Da estilo a un boton secundario (fondo claro, borde, texto oscuro).
     *
     * @param boton boton a estilizar.
     * @return el mismo boton, para encadenar.
     */
    public static JButton botonSecundario(JButton boton) {
        boton.setBackground(SUPERFICIE);
        boton.setForeground(TEXTO);
        boton.setFocusPainted(false);
        boton.setFont(fuenteNegrita());
        boton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDE),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)));
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return boton;
    }
}
