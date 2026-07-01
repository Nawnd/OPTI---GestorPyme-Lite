package com.gestorpyme.view.dashboard;

import com.gestorpyme.controller.DashboardController;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * Vista de inicio (Dashboard) de OPTI - GestorPyme Lite. Capa: view.dashboard.
 *
 * A partir del Paso L actúa como <b>host delgado</b>: muestra un encabezado de bienvenida y delega todo el
 * contenido en {@link DashboardTabbedPanel}, que organiza las pestañas "Dashboard General" (operación diaria,
 * con gráficas clicables y desglose) y "Dashboard Gerencial" (dirección y análisis). No ejecuta consultas ni
 * contiene lógica de negocio; solo compone la pantalla. Conserva el constructor {@code InicioView(String)}
 * para no romper a quien la instancia.
 */
public class InicioView extends JPanel {

    private final transient DashboardController controller = new DashboardController();
    private final String nombreUsuario;

    /**
     * @param nombreUsuario nombre del usuario en sesión (para el saludo del encabezado).
     */
    public InicioView(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        add(crearEncabezado(), BorderLayout.NORTH);
        add(new DashboardTabbedPanel(controller), BorderLayout.CENTER);
    }

    /** Encabezado de bienvenida con el nombre del usuario. */
    private JPanel crearEncabezado() {
        JPanel enc = new JPanel(new BorderLayout());
        enc.setOpaque(false);

        JLabel saludo = new JLabel("Hola, " + (nombreUsuario == null || nombreUsuario.isBlank()
                ? "usuario" : nombreUsuario));
        saludo.setFont(UiTheme.fuente(Font.BOLD, 20));
        saludo.setForeground(AppPalette.PRIMARIO);

        JLabel sub = new JLabel("Resumen del negocio");
        sub.setFont(UiTheme.fuente(Font.PLAIN, 13));
        sub.setForeground(AppPalette.TEXTO_SECUNDARIO);

        enc.add(saludo, BorderLayout.NORTH);
        enc.add(sub, BorderLayout.SOUTH);
        return enc;
    }
}
