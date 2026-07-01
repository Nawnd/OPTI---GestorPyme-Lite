package com.gestorpyme.view.components;

import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Panel de grilla <b>responsive</b>: reorganiza sus componentes en 1..maxColumnas según el ancho
 * disponible, de modo que nunca se recorten ni aparezca scroll horizontal. Capa: view.components.
 *
 * El número de columnas se calcula dividiendo el ancho entre un ancho mínimo de columna (acotado por
 * {@code maxColumnas} y por la cantidad de hijos). El cálculo se hace de forma síncrona en
 * {@link #doLayout()} (ajusta las columnas del {@link GridLayout}) y en {@link #getPreferredSize()}
 * (calcula la altura con esas columnas), por lo que no depende de la entrega de eventos de
 * redimensionamiento y la altura reservada por el contenedor siempre es correcta. Limita su altura
 * máxima a la preferida para comportarse bien dentro de un {@code BoxLayout} vertical. Solo presentación.
 */
public class ResponsiveGridPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final int maxColumnas;
    private final int anchoMinColumna;
    private final int hgap;
    private final int vgap;

    /**
     * @param maxColumnas     máximo de columnas cuando hay ancho de sobra (p. ej. 4 para KPIs).
     * @param anchoMinColumna ancho mínimo (px) por columna antes de reducir el número de columnas.
     * @param hgap            separación horizontal entre celdas.
     * @param vgap            separación vertical entre filas.
     */
    public ResponsiveGridPanel(int maxColumnas, int anchoMinColumna, int hgap, int vgap) {
        this.maxColumnas = Math.max(1, maxColumnas);
        this.anchoMinColumna = Math.max(1, anchoMinColumna);
        this.hgap = hgap;
        this.vgap = vgap;
        setOpaque(false);
        setLayout(new GridLayout(1, this.maxColumnas, hgap, vgap));
        setAlignmentX(LEFT_ALIGNMENT);
        // Al redimensionar, se re-valida para que el contenedor vuelva a pedir el tamaño preferido.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
            }
        });
    }

    /**
     * Columnas que caben en el ancho dado, acotadas por el máximo y la cantidad de hijos, y ajustadas
     * a escalones limpios {1, 2, maxColumnas} para evitar filas huérfanas (p. ej. 4 KPIs en 4/2/1).
     */
    private int columnasPara(int ancho) {
        int n = getComponentCount();
        int tope = Math.min(maxColumnas, Math.max(1, n));
        int crudas;
        if (ancho <= 0) {
            crudas = tope;
        } else {
            int posibles = (ancho + hgap) / (anchoMinColumna + hgap);
            crudas = Math.max(1, Math.min(tope, posibles));
        }
        // Escalones permitidos: 1, 2 y el máximo (sin pasarse del tope ni del máximo).
        int cols = 1;
        for (int escalon : new int[]{1, 2, maxColumnas}) {
            if (escalon <= tope && escalon <= crudas && escalon > cols) {
                cols = escalon;
            }
        }
        return cols;
    }

    /** Ancho de referencia: el propio si ya está dimensionado; si no, el del contenedor padre. */
    private int anchoReferencia() {
        int w = getWidth();
        if (w > 0) {
            return w;
        }
        Container p = getParent();
        return p != null ? p.getWidth() : 0;
    }

    @Override
    public void doLayout() {
        int cols = columnasPara(getWidth());
        GridLayout gl = (GridLayout) getLayout();
        if (gl.getColumns() != cols || gl.getRows() != 0) {
            setLayout(new GridLayout(0, cols, hgap, vgap));
        }
        super.doLayout();
    }

    @Override
    public Dimension getPreferredSize() {
        int n = getComponentCount();
        if (n == 0) {
            return super.getPreferredSize();
        }
        int cols = columnasPara(anchoReferencia());
        int filas = (n + cols - 1) / cols;
        int celdaW = 0;
        int celdaH = 0;
        for (Component c : getComponents()) {
            Dimension d = c.getPreferredSize();
            celdaW = Math.max(celdaW, d.width);
            celdaH = Math.max(celdaH, d.height);
        }
        int w = cols * celdaW + (cols - 1) * hgap;
        int h = filas * celdaH + (filas - 1) * vgap;
        return new Dimension(w, h);
    }

    @Override
    public Dimension getMaximumSize() {
        // Puede crecer horizontalmente (lo estira el BoxLayout) pero no verticalmente.
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
