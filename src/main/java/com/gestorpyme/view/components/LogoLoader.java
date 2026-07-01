package com.gestorpyme.view.components;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import java.awt.Image;
import java.net.URL;

/**
 * Carga el logo del proyecto desde el classpath y construye el componente de marca.
 *
 * Ubicacion fisica esperada del archivo:
 *   src/main/resources/images/logo.png
 * Ruta en el classpath (la que usa la aplicacion):
 *   /images/logo.png  (configurable en {@link UiTheme#RUTA_LOGO})
 *
 * Comportamiento seguro: si el logo NO existe o no se puede leer, la aplicacion
 * NO falla; se usa el texto "OPTI" como alternativa (fallback).
 *
 * El escalado conserva la proporcion (no deforma el logo).
 *
 * Clase de utilidades estaticas.
 */
public final class LogoLoader {

    private LogoLoader() {
    }

    /**
     * Carga el logo escalado a una altura dada, conservando la proporcion.
     *
     * @param altura altura objetivo en pixeles.
     * @return el icono escalado, o {@code null} si no se encontro el recurso.
     */
    public static ImageIcon cargarEscalado(int altura) {
        URL url = LogoLoader.class.getResource(UiTheme.RUTA_LOGO);
        if (url == null) {
            return null; // No existe el recurso: el llamador usara el texto alternativo.
        }
        ImageIcon original = new ImageIcon(url);
        int anchoOrig = original.getIconWidth();
        int altoOrig = original.getIconHeight();
        if (anchoOrig <= 0 || altoOrig <= 0) {
            return null;
        }
        // Regla de tres para mantener la proporcion.
        int ancho = (int) Math.round((double) anchoOrig * altura / altoOrig);
        Image escalada = original.getImage().getScaledInstance(ancho, altura, Image.SCALE_SMOOTH);
        return new ImageIcon(escalada);
    }

    /**
     * Construye la etiqueta de marca para los encabezados: muestra el logo si
     * existe; si no, el texto "OPTI" con el estilo corporativo.
     *
     * @param altura       altura del logo en pixeles.
     * @param colorTexto   color del texto alternativo (cuando no hay logo).
     * @return un {@link JLabel} listo para colocar en la barra superior.
     */
    public static JLabel construirMarca(int altura, java.awt.Color colorTexto) {
        ImageIcon icono = cargarEscalado(altura);
        if (icono != null) {
            JLabel etiqueta = new JLabel(icono);
            etiqueta.setToolTipText("OPTI - GestorPyme Lite");
            return etiqueta;
        }
        // Fallback: texto "OPTI".
        JLabel texto = new JLabel("OPTI");
        texto.setFont(UiTheme.fuenteMarca());
        texto.setForeground(colorTexto);
        return texto;
    }
}
