package com.gestorpyme.view.components;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Fila de métrica con barra de progreso, dibujada a mano (sin librerías externas). Capa: view.components.
 *
 * Muestra una etiqueta, una barra cuya longitud representa una fracción 0..1 (los valores mayores a 1 se
 * dibujan llenos pero el porcentaje real se muestra en el texto) y el porcentaje. Útil para el cumplimiento
 * real vs meta y para la cartera recuperada, de forma más legible que un número suelto. Solo presentación.
 */
public class ProgressBarMetricPanel extends JPanel {

    private final String etiqueta;
    private final double fraccion;   // 0..1 (o mayor); null se representa como 0 con texto "—"
    private final boolean conDato;
    private final Color color;

    /**
     * @param etiqueta texto de la métrica.
     * @param fraccion fracción de cumplimiento (1.0 = 100%). Si es null, se muestra "—" sin barra.
     * @param color    color de la barra (semántico).
     */
    public ProgressBarMetricPanel(String etiqueta, Double fraccion, Color color) {
        this.etiqueta = etiqueta;
        this.conDato = fraccion != null;
        this.fraccion = fraccion == null ? 0d : Math.max(0d, fraccion);
        this.color = color == null ? AppPalette.PRIMARIO : color;
        setOpaque(false);
        setFont(new Font("Segoe UI", Font.PLAIN, 12));
        setPreferredSize(new Dimension(200, 40));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(getFont());

        int w = getWidth();
        int textoY = 15;
        // Etiqueta a la izquierda y porcentaje a la derecha.
        g2.setColor(AppPalette.TEXTO_SECUNDARIO);
        g2.drawString(etiqueta, 0, textoY);
        String pct = conDato ? String.format("%.0f%%", fraccion * 100) : "—";
        g2.setColor(conDato ? color.darker() : AppPalette.NEUTRO);
        int anchoPct = g2.getFontMetrics().stringWidth(pct);
        g2.drawString(pct, w - anchoPct, textoY);

        // Pista de la barra.
        int barY = 22;
        int barH = 10;
        g2.setColor(AppPalette.TARJETA_SUAVE);
        g2.fillRoundRect(0, barY, Math.max(1, w), barH, barH, barH);

        // Relleno proporcional (tope visual al 100%).
        if (conDato && fraccion > 0) {
            int relleno = (int) Math.round(Math.min(1d, fraccion) * w);
            relleno = Math.max(3, relleno);
            g2.setColor(color);
            g2.fillRoundRect(0, barY, relleno, barH, barH, barH);
        }
        g2.dispose();
    }
}
