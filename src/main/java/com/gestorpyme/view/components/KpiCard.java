package com.gestorpyme.view.components;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

/**
 * Tarjeta de indicador (KPI) para el Dashboard: titulo corto, NUMERO PRINCIPAL
 * destacado, texto secundario y una franja de acento a la izquierda.
 *
 * Usa {@link GridBagLayout} con un relleno inferior para anclar el contenido
 * arriba, de modo que el numero principal SIEMPRE sea visible (no se corta aunque
 * la tarjeta sea baja). Fondo blanco, esquinas redondeadas y tooltips para textos
 * largos. Colores: valor #06141B, texto secundario #7E919F, acento configurable.
 */
public class KpiCard extends RoundedPanel {

    /**
     * @param titulo    titulo corto del indicador.
     * @param valor     numero/monto principal ya formateado (si es vacio se muestra "-").
     * @param subtitulo texto secundario (puede ser null).
     * @param acento    color de la franja de acento.
     * @param icono     emoji/icono breve opcional (puede ser null).
     */
    public KpiCard(String titulo, String valor, String subtitulo, Color acento, String icono) {
        super(UiTheme.SUPERFICIE, 16);
        setColorBorde(UiTheme.BORDE);
        setLayout(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        setMinimumSize(new Dimension(190, 104));
        setPreferredSize(new Dimension(230, 112));

        // Franja de acento a la izquierda.
        RoundedPanel franja = new RoundedPanel(acento, 6);
        franja.setColorBorde(null);
        franja.setPreferredSize(new Dimension(5, 0));
        franja.setMinimumSize(new Dimension(5, 10));
        add(franja, BorderLayout.WEST);

        JPanel textos = new JPanel(new GridBagLayout());
        textos.setOpaque(false);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        JLabel lblTitulo = new JLabel(iconoMas(icono, titulo));
        lblTitulo.setFont(UiTheme.fuente(Font.PLAIN, 12));
        lblTitulo.setForeground(UiTheme.TEXTO_TENUE);
        lblTitulo.setToolTipText(titulo);
        g.gridy = 0;
        g.insets = new Insets(0, 0, 0, 0);
        textos.add(lblTitulo, g);

        // Numero principal (grande y visible).
        JLabel lblValor = new JLabel((valor == null || valor.isEmpty()) ? "-" : valor);
        lblValor.setFont(UiTheme.fuente(Font.BOLD, 26));
        lblValor.setForeground(UiTheme.TEXTO); // #06141B
        lblValor.setToolTipText(valor);
        g.gridy = 1;
        g.insets = new Insets(6, 0, 0, 0);
        textos.add(lblValor, g);

        if (subtitulo != null && !subtitulo.isEmpty()) {
            JLabel lblSub = new JLabel(subtitulo);
            lblSub.setFont(UiTheme.fuente(Font.PLAIN, 11));
            lblSub.setForeground(UiTheme.TEXTO_TENUE); // #7E919F
            lblSub.setToolTipText(subtitulo);
            g.gridy = 2;
            g.insets = new Insets(4, 0, 0, 0);
            textos.add(lblSub, g);
        }

        // Relleno que empuja el contenido hacia arriba (el valor nunca se corta).
        g.gridy = 3;
        g.weighty = 1;
        g.fill = GridBagConstraints.BOTH;
        JPanel relleno = new JPanel();
        relleno.setOpaque(false);
        textos.add(relleno, g);

        add(textos, BorderLayout.CENTER);
    }

    private String iconoMas(String icono, String titulo) {
        return (icono == null || icono.isEmpty()) ? titulo : icono + "  " + titulo;
    }
}
