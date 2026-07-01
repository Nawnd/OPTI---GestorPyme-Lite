package com.gestorpyme.view.components;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

import com.gestorpyme.util.MoneyFormatter;

/**
 * Grafica de barras sencilla dibujada con Swing puro (sin librerias externas).
 * Pensada para "ventas por dia". Si no hay datos (o todos en cero), muestra un
 * mensaje amigable. Los colores derivan de la paleta oficial.
 */
public class MiniBarChartPanel extends JPanel {

    private final List<String> etiquetas;
    private final List<Double> valores;
    private final Color[] colores = {
            AppPalette.PRIMARIO, AppPalette.SECUNDARIO, AppPalette.TEXTO_SECUNDARIO, AppPalette.NEUTRO
    };

    /**
     * @param etiquetas etiquetas del eje X (p. ej. dias).
     * @param valores   valores de cada barra (mismo tamano que etiquetas).
     */
    public MiniBarChartPanel(List<String> etiquetas, List<Double> valores) {
        this.etiquetas = etiquetas;
        this.valores = valores;
        setOpaque(false);
        setPreferredSize(new Dimension(420, 180));
        setMinimumSize(new Dimension(280, 150));
    }

    private boolean sinDatos() {
        if (valores == null || valores.isEmpty()) {
            return true;
        }
        for (Double v : valores) {
            if (v != null && v > 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (sinDatos()) {
            String msg = "Aun no hay ventas suficientes para graficar";
            g2.setFont(new Font(UiTheme.FUENTE, Font.ITALIC, 13));
            g2.setColor(UiTheme.TEXTO_TENUE);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(msg, (w - fm.stringWidth(msg)) / 2, h / 2);
            g2.dispose();
            return;
        }

        double maximo = 0;
        for (Double v : valores) {
            if (v != null && v > maximo) {
                maximo = v;
            }
        }
        int margenInferior = 34; // espacio para etiquetas + valor
        int margenSuperior = 16;
        int areaAlta = h - margenInferior - margenSuperior;
        int n = valores.size();
        int espacio = w / n;
        int anchoBarra = (int) (espacio * 0.55);

        g2.setFont(new Font(UiTheme.FUENTE, Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < n; i++) {
            double v = valores.get(i) == null ? 0 : valores.get(i);
            int alturaBarra = (maximo == 0) ? 0 : (int) Math.round(areaAlta * (v / maximo));
            int x = i * espacio + (espacio - anchoBarra) / 2;
            int y = margenSuperior + (areaAlta - alturaBarra);

            g2.setColor(colores[i % colores.length]);
            g2.fillRoundRect(x, y, anchoBarra, alturaBarra, 8, 8);

            // Etiqueta del eje X.
            String et = etiquetas.get(i);
            g2.setColor(UiTheme.TEXTO_TENUE);
            g2.drawString(et, i * espacio + (espacio - fm.stringWidth(et)) / 2, h - margenInferior + 16);

            // Valor abreviado encima de la barra (en miles).
            if (v > 0) {
                String val = MoneyFormatter.entero(Math.round(v / 1000.0)) + "k";
                g2.setColor(UiTheme.TEXTO);
                g2.drawString(val, i * espacio + (espacio - fm.stringWidth(val)) / 2, Math.max(margenSuperior + 10, y - 4));
            }
        }
        g2.dispose();
    }
}
