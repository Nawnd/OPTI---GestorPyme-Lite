package com.gestorpyme.view.components;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

/**
 * Tarjeta KPI principal (grande) del Dashboard Ejecutivo (Paso K.1). Capa: view.components.
 *
 * Pensada para la fila superior del tablero: título completo (sin recortes), valor grande y subtítulo
 * corto, con una franja de color semántico a la izquierda. Fondo blanco con esquinas redondeadas para
 * mantener la estética de "isla". Solo presentación; no contiene lógica de negocio.
 */
public class DashboardBigKpi extends RoundedPanel {

    /**
     * @param titulo    título del indicador (se muestra completo).
     * @param valor     valor principal (texto ya formateado).
     * @param subtitulo texto corto de apoyo.
     * @param acento    color semántico (franja y valor).
     */
    public DashboardBigKpi(String titulo, String valor, String subtitulo, Color acento) {
        super(UiTheme.SUPERFICIE, 18);
        setColorBorde(UiTheme.BORDE);
        setLayout(new BorderLayout(10, 0));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 16));
        setPreferredSize(new Dimension(220, 118));
        setMinimumSize(new Dimension(160, 110));

        // Franja de color a la izquierda.
        JPanel franja = new JPanel();
        franja.setBackground(acento);
        franja.setPreferredSize(new Dimension(6, 10));
        add(franja, BorderLayout.WEST);

        JPanel centro = new JPanel();
        centro.setOpaque(false);
        centro.setLayout(new BoxLayout(centro, BoxLayout.Y_AXIS));

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(UiTheme.fuente(Font.BOLD, 13));
        lblTitulo.setForeground(UiTheme.PRIMARIO);
        lblTitulo.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblValor = new JLabel(valor);
        lblValor.setFont(UiTheme.fuente(Font.BOLD, 24));
        lblValor.setForeground(acento);
        lblValor.setAlignmentX(LEFT_ALIGNMENT);

        JLabel lblSub = new JLabel(subtitulo);
        lblSub.setFont(UiTheme.fuente(Font.PLAIN, 11));
        lblSub.setForeground(UiTheme.TEXTO_TENUE);
        lblSub.setAlignmentX(LEFT_ALIGNMENT);

        centro.add(lblTitulo);
        centro.add(javax.swing.Box.createVerticalGlue());
        centro.add(lblValor);
        centro.add(javax.swing.Box.createRigidArea(new Dimension(0, 2)));
        centro.add(lblSub);

        add(centro, BorderLayout.CENTER);
    }
}
