package com.gestorpyme.view.components;

import javax.swing.JPanel;
import javax.swing.Scrollable;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;

/**
 * Contenedor para usar como vista de un {@code JScrollPane} que <b>sigue el ancho del viewport</b>
 * (no genera scroll horizontal) y permite scroll vertical cuando el contenido es más alto. Capa:
 * view.components.
 *
 * Al devolver {@code getScrollableTracksViewportWidth() == true}, el contenido se redimensiona al ancho
 * visible, de modo que los paneles responsive internos puedan reorganizarse en lugar de recortarse.
 */
public class ScrollableContentPanel extends JPanel implements Scrollable {

    private static final long serialVersionUID = 1L;

    public ScrollableContentPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(16, visibleRect.height - 16);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true; // el ancho se ajusta al viewport: sin scroll horizontal, contenido reorganizable
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false; // la altura puede exceder el viewport: se permite scroll vertical
    }
}
