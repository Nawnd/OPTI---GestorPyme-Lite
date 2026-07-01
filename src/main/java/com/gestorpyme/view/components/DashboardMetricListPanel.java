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
 * Lista vertical de métricas "etiqueta … valor" para los paneles del Dashboard Ejecutivo (Paso K.1).
 * Capa: view.components. Evita saturar el tablero con demasiadas tarjetas: agrupa varios indicadores
 * en un panel legible, con la etiqueta a la izquierda y el valor (resaltado) a la derecha. Sin recortes
 * ni superposición. Solo presentación.
 */
public class DashboardMetricListPanel extends JPanel {

    public DashboardMetricListPanel() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    /**
     * Agrega una fila de métrica.
     *
     * @param etiqueta   nombre del indicador.
     * @param valor      valor formateado.
     * @param colorValor color del valor (semántico o {@link UiTheme#PRIMARIO}).
     */
    public void agregar(String etiqueta, String valor, Color colorValor) {
        JPanel fila = new JPanel(new BorderLayout(8, 0));
        fila.setOpaque(false);
        fila.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        fila.setAlignmentX(LEFT_ALIGNMENT);
        // Altura compacta y estable (evita estiramientos del BoxLayout).
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        JLabel lbl = new JLabel(etiqueta);
        lbl.setFont(UiTheme.fuente(Font.PLAIN, 13));
        lbl.setForeground(UiTheme.TEXTO_TENUE);

        JLabel val = new JLabel(valor);
        val.setFont(UiTheme.fuente(Font.BOLD, 13));
        val.setForeground(colorValor == null ? UiTheme.PRIMARIO : colorValor);
        val.setHorizontalAlignment(JLabel.RIGHT);

        fila.add(lbl, BorderLayout.WEST);
        fila.add(val, BorderLayout.CENTER);
        add(fila);
    }

    /** Agrega una fila con valor en color primario (informativo). */
    public void agregar(String etiqueta, String valor) {
        agregar(etiqueta, valor, UiTheme.PRIMARIO);
    }

    /** Agrega un separador visual sutil entre grupos de métricas. */
    public void separador() {
        JPanel sep = new JPanel();
        sep.setBackground(UiTheme.BORDE);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(LEFT_ALIGNMENT);
        add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
        add(sep);
        add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));
    }
}
