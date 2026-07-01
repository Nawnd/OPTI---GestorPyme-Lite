package com.gestorpyme.view.components;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Panel con fondo de esquinas redondeadas (tarjeta tipo "isla").
 * Se usa como base para las tarjetas del Dashboard. Pinta un rectangulo
 * redondeado con color de fondo y, opcionalmente, un borde sutil.
 */
public class RoundedPanel extends JPanel {

    private final int arco;
    private final Color fondo;
    private Color colorBorde;

    /**
     * @param fondo color de relleno de la tarjeta.
     * @param arco  radio de redondeo de las esquinas (px).
     */
    public RoundedPanel(Color fondo, int arco) {
        this.fondo = fondo;
        this.arco = arco;
        this.colorBorde = UiTheme.BORDE;
        setOpaque(false);
    }

    /** Cambia (o quita con null) el color del borde. */
    public void setColorBorde(Color colorBorde) {
        this.colorBorde = colorBorde;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(fondo);
        g2.fillRoundRect(0, 0, w - 1, h - 1, arco, arco);
        if (colorBorde != null) {
            g2.setColor(colorBorde);
            g2.drawRoundRect(0, 0, w - 1, h - 1, arco, arco);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
