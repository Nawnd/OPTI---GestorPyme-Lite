package com.gestorpyme.view.components;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;

import com.gestorpyme.util.MoneyFormatter;

/**
 * Indicador circular (anillo) de progreso, dibujado con Swing puro.
 * Muestra una proporcion 0..1 como porcentaje en el centro. Pensado para el
 * "porcentaje de cartera recuperada". Sin librerias externas.
 */
public class CircularProgressPanel extends JPanel {

    private double proporcion; // 0..1
    private final Color color;
    private final String etiqueta;

    /**
     * @param proporcion valor entre 0 y 1.
     * @param color      color del arco de progreso.
     * @param etiqueta   texto bajo el porcentaje (puede ser null).
     */
    public CircularProgressPanel(double proporcion, Color color, String etiqueta) {
        this.proporcion = Math.max(0, Math.min(1, proporcion));
        this.color = color;
        this.etiqueta = etiqueta;
        setOpaque(false);
        setPreferredSize(new Dimension(180, 170));
        setMinimumSize(new Dimension(150, 150));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();
        int grosor = Math.max(12, Math.min(w, h) / 10);
        int diametro = Math.min(w, h) - grosor - 8;
        int x = (w - diametro) / 2;
        int y = (h - diametro) / 2 - 4;

        // Anillo de fondo.
        g2.setStroke(new BasicStroke(grosor, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(AppPalette.aclarar(AppPalette.NEUTRO, 0.45));
        g2.draw(new Arc2D.Double(x, y, diametro, diametro, 90, -360, Arc2D.OPEN));

        // Arco de progreso (empieza arriba y avanza en sentido horario).
        g2.setColor(color);
        g2.draw(new Arc2D.Double(x, y, diametro, diametro, 90, -360 * proporcion, Arc2D.OPEN));

        // Texto central: porcentaje.
        String texto = MoneyFormatter.porcentaje(proporcion);
        g2.setFont(new Font(UiTheme.FUENTE, Font.BOLD, Math.max(18, diametro / 6)));
        g2.setColor(UiTheme.TEXTO);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (diametro - fm.stringWidth(texto)) / 2;
        int ty = y + diametro / 2 + fm.getAscent() / 2 - 2;
        g2.drawString(texto, tx, ty);

        // Etiqueta inferior (opcional).
        if (etiqueta != null && !etiqueta.isEmpty()) {
            g2.setFont(new Font(UiTheme.FUENTE, Font.PLAIN, 12));
            g2.setColor(UiTheme.TEXTO_TENUE);
            FontMetrics fm2 = g2.getFontMetrics();
            int ex = (w - fm2.stringWidth(etiqueta)) / 2;
            g2.drawString(etiqueta, ex, y + diametro + grosor / 2 + 4);
        }
        g2.dispose();
    }
}
