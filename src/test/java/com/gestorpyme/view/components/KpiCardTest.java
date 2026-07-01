package com.gestorpyme.view.components;

import org.junit.jupiter.api.Test;

import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba de construccion de {@link KpiCard}: el numero principal debe existir como
 * texto dentro de la tarjeta (regresion del bug donde el valor no se mostraba).
 * Se ejecuta sin pantalla: solo construye el componente y recorre su arbol.
 */
class KpiCardTest {

    @Test
    void laTarjetaMuestraElValorPrincipal() {
        KpiCard card = new KpiCard("Ventas de hoy", "$ 1.250.000", "2 ventas hoy",
                AppPalette.SECUNDARIO, null);
        JLabel etiqueta = buscarLabelConTexto(card, "$ 1.250.000");
        assertNotNull(etiqueta, "La tarjeta KPI debe contener una etiqueta con el valor principal");
        assertTrue(etiqueta.getForeground().equals(UiTheme.TEXTO),
                "El valor debe usar el color principal (#06141B) para ser visible");
    }

    @Test
    void valorVacioMuestraGuion() {
        KpiCard card = new KpiCard("Sin datos", "", "n/a", AppPalette.NEUTRO, null);
        assertNotNull(buscarLabelConTexto(card, "-"),
                "Con valor vacio la tarjeta debe mostrar '-' (nunca quedar vacia)");
    }

    /** Busca recursivamente una JLabel cuyo texto coincida. */
    private JLabel buscarLabelConTexto(Container contenedor, String texto) {
        for (Component c : contenedor.getComponents()) {
            if (c instanceof JLabel && texto.equals(((JLabel) c).getText())) {
                return (JLabel) c;
            }
            if (c instanceof Container) {
                JLabel encontrada = buscarLabelConTexto((Container) c, texto);
                if (encontrada != null) {
                    return encontrada;
                }
            }
        }
        return null;
    }
}
