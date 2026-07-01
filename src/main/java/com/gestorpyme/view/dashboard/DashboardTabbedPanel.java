package com.gestorpyme.view.dashboard;

import com.gestorpyme.controller.DashboardController;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.components.ScrollableContentPanel;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;

/**
 * Contenedor de pestañas del Dashboard (Paso L). Capa: view.dashboard.
 *
 * Aloja las pestañas "Dashboard General" y "Dashboard Gerencial", cada una dentro de su propio
 * {@code JScrollPane} vertical (sin scroll horizontal), lo que evita que su contenido se recorte. Aplica
 * <b>carga perezosa</b>: la pestaña General se carga al construirse; la Gerencial, solo la primera vez que se
 * selecciona, para no consultar la base de datos hasta que se necesita. Solo coordina la presentación.
 */
public class DashboardTabbedPanel extends JPanel {

    private final transient DashboardController controller;
    private final GeneralDashboardPanel generalPanel;
    private final ExecutiveDashboardPanel ejecutivoPanel;
    private final JTabbedPane tabs = new JTabbedPane();

    public DashboardTabbedPanel(DashboardController controller) {
        this.controller = controller;
        this.generalPanel = new GeneralDashboardPanel(controller);
        this.ejecutivoPanel = new ExecutiveDashboardPanel(controller);

        setLayout(new BorderLayout());
        setOpaque(false);
        tabs.setBackground(AppPalette.FONDO);

        tabs.addTab("Dashboard General", envolverScroll(generalPanel));
        tabs.addTab("Dashboard Gerencial", envolverScroll(ejecutivoPanel));

        // Carga perezosa: la pestaña seleccionada se carga al mostrarse (idempotente).
        tabs.addChangeListener(e -> cargarSeleccion());

        add(tabs, BorderLayout.CENTER);

        // La primera pestaña (General) se carga de inmediato.
        generalPanel.cargar();
    }

    /** @return el controlador usado por las pestañas (referencia compartida). */
    public DashboardController getController() {
        return controller;
    }

    private void cargarSeleccion() {
        int idx = tabs.getSelectedIndex();
        if (idx == 0) {
            generalPanel.cargar();
        } else if (idx == 1) {
            ejecutivoPanel.cargar();
        }
    }

    /** Envuelve un panel en un scroll vertical (sin scroll horizontal) que sigue el ancho del viewport. */
    private JScrollPane envolverScroll(JPanel panel) {
        // El host sigue el ancho visible (Scrollable), así el contenido se reorganiza en vez de recortarse.
        ScrollableContentPanel host = new ScrollableContentPanel(new BorderLayout());
        host.add(panel, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(host,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }
}
