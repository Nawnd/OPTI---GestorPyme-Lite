package com.gestorpyme.view.components;

import com.gestorpyme.domain.model.DashboardChartData;
import com.gestorpyme.domain.model.DashboardChartSegment;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Gráfica de barras horizontales clicables, dibujada a mano (sin librerías externas). Capa: view.components.
 *
 * Cada barra corresponde a un {@link DashboardChartSegment}; al hacer clic sobre ella, el componente notifica
 * al {@link MetricSelectionListener} con el tipo y la referencia del segmento (para el desglose). Aplica
 * hit-testing por fila, cursor de mano sobre las barras y tooltip con etiqueta + valor. Limita el número de
 * barras visibles para preservar legibilidad. Solo presentación; no contiene lógica de negocio.
 *
 * Se eligen barras horizontales porque las etiquetas (bodegas, productos, estados, días) son de longitud
 * variable y así nunca se solapan ni se recortan.
 */
public class InteractiveBarChartPanel extends JPanel {

    /** Tope de barras visibles (legibilidad y precisión del clic). */
    private static final int MAX_BARRAS = 12;
    private static final int ALTO_FILA = 30;
    private static final int ANCHO_ETIQUETA = 140;
    private static final int ANCHO_VALOR = 90;
    private static final int PAD = 8;

    private final transient DashboardChartData datos;
    private transient MetricSelectionListener listener;
    private int filaHover = -1;

    public InteractiveBarChartPanel(DashboardChartData datos) {
        this.datos = datos;
        setOpaque(false);
        setFont(new Font("Segoe UI", Font.PLAIN, 12));
        int filas = visibles();
        int alto = Math.max(60, filas * ALTO_FILA + 2 * PAD);
        setPreferredSize(new Dimension(420, alto));
        setMinimumSize(new Dimension(240, alto));

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int fila = filaEn(e.getY());
                if (fila >= 0 && listener != null) {
                    DashboardChartSegment s = datos.getSegmentos().get(fila);
                    listener.onMetricSelected(s.getTipo(), s.getReferencia(), s.getEtiqueta());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int fila = filaEn(e.getY());
                setCursor(Cursor.getPredefinedCursor(fila >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                if (fila != filaHover) {
                    filaHover = fila;
                    if (fila >= 0) {
                        DashboardChartSegment s = datos.getSegmentos().get(fila);
                        setToolTipText(s.getEtiqueta() + ": " + fmt(s.getValor()));
                    } else {
                        setToolTipText(null);
                    }
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                filaHover = -1;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    /** Asigna el escucha de selección (clic en barra). */
    public void setMetricSelectionListener(MetricSelectionListener listener) {
        this.listener = listener;
    }

    private int visibles() {
        if (datos == null || datos.getSegmentos() == null) {
            return 0;
        }
        return Math.min(datos.getSegmentos().size(), MAX_BARRAS);
    }

    /** Devuelve el índice de fila/barra bajo la coordenada y, o -1 si no hay barra. */
    private int filaEn(int y) {
        int fila = (y - PAD) / ALTO_FILA;
        return (fila >= 0 && fila < visibles()) ? fila : -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setFont(getFont());

        if (datos == null || datos.estaVacia()) {
            g2.setColor(AppPalette.TEXTO_SECUNDARIO);
            g2.drawString("Sin datos para mostrar.", PAD, 28);
            g2.dispose();
            return;
        }

        List<DashboardChartSegment> segs = datos.getSegmentos();
        int filas = visibles();
        double max = 0;
        for (int i = 0; i < filas; i++) {
            max = Math.max(max, segs.get(i).getValor());
        }
        if (max <= 0) {
            max = 1;
        }

        int xBar = PAD + ANCHO_ETIQUETA;
        int anchoBarMax = Math.max(40, getWidth() - xBar - ANCHO_VALOR - PAD);

        for (int i = 0; i < filas; i++) {
            DashboardChartSegment s = segs.get(i);
            int y = PAD + i * ALTO_FILA;
            int yCentro = y + ALTO_FILA / 2;

            // Resaltado de fila al pasar el mouse.
            if (i == filaHover) {
                g2.setColor(AppPalette.TARJETA_SUAVE);
                g2.fillRoundRect(PAD - 4, y + 2, getWidth() - 2 * PAD + 4, ALTO_FILA - 4, 8, 8);
            }

            // Etiqueta (recortada con elipsis si excede).
            g2.setColor(AppPalette.PRIMARIO);
            g2.drawString(elipsis(g2, s.getEtiqueta(), ANCHO_ETIQUETA - 6), PAD, yCentro + 4);

            // Barra.
            int ancho = (int) Math.round(anchoBarMax * (s.getValor() / max));
            ancho = Math.max(2, ancho);
            Color color = colorPorHint(s.getColorHint());
            g2.setColor(color);
            g2.fillRoundRect(xBar, y + 7, ancho, ALTO_FILA - 14, 8, 8);
            g2.setColor(color.darker());
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(xBar, y + 7, ancho, ALTO_FILA - 14, 8, 8);

            // Valor a la derecha.
            g2.setColor(AppPalette.PRIMARIO);
            g2.drawString(fmt(s.getValor()), xBar + anchoBarMax + 6, yCentro + 4);
        }
        g2.dispose();
    }

    /** Formato compacto: enteros sin decimales; en miles, con separador. */
    private static String fmt(double v) {
        if (v == Math.floor(v)) {
            return String.format("%,.0f", v);
        }
        return String.format("%,.2f", v);
    }

    private String elipsis(Graphics2D g2, String texto, int anchoMax) {
        if (texto == null) {
            return "";
        }
        if (g2.getFontMetrics().stringWidth(texto) <= anchoMax) {
            return texto;
        }
        String t = texto;
        while (t.length() > 1 && g2.getFontMetrics().stringWidth(t + "…") > anchoMax) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }

    /**
     * Traduce una pista semántica de color a un color de la paleta. Punto único de mapeo reutilizable por
     * otros componentes del dashboard (listas, detalle), para no duplicar la correspondencia.
     */
    public static Color colorPorHint(String hint) {
        if (hint == null) {
            return AppPalette.PRIMARIO;
        }
        switch (hint) {
            case "EXITO": return AppPalette.EXITO;
            case "ADVERTENCIA": return AppPalette.ADVERTENCIA;
            case "PELIGRO": return AppPalette.PELIGRO;
            case "NEUTRO": return AppPalette.NEUTRO;
            case "SECUNDARIO": return AppPalette.SECUNDARIO;
            default: return AppPalette.PRIMARIO;
        }
    }
}
