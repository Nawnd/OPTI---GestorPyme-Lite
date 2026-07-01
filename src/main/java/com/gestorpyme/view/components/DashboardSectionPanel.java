package com.gestorpyme.view.components;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Tarjeta de seccion del Dashboard: encabezado con titulo + contenido (tabla,
 * lista, grafica o estado vacio). Fondo blanco con esquinas redondeadas, para
 * dar la sensacion de panel/"isla" coherente en todo el tablero.
 */
public class DashboardSectionPanel extends RoundedPanel {

    /**
     * @param titulo    titulo de la seccion.
     * @param contenido componente central (p. ej. un JScrollPane con una tabla).
     */
    public DashboardSectionPanel(String titulo, JComponent contenido) {
        super(UiTheme.SUPERFICIE, 18);
        setColorBorde(UiTheme.BORDE);
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        setMinimumSize(new Dimension(260, 200));

        JLabel cabecera = new JLabel(titulo);
        cabecera.setFont(UiTheme.fuente(Font.BOLD, 15));
        cabecera.setForeground(UiTheme.PRIMARIO);

        add(cabecera, BorderLayout.NORTH);
        add(contenido, BorderLayout.CENTER);
    }
}
