package com.gestorpyme.view.dashboard;
import com.gestorpyme.view.components.LogoLoader;
import com.gestorpyme.view.components.UiScaleManager;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.dashboard.InicioView;
import java.util.LinkedHashMap;
import java.util.Map;
import com.gestorpyme.view.ventas.VentasView;
import com.gestorpyme.view.pagos.PagosView;
import com.gestorpyme.view.cuentas.CuentasPorCobrarView;
import com.gestorpyme.view.reportes.ExportacionView;

import com.gestorpyme.controller.LoginController;
import com.gestorpyme.domain.model.Usuario;
import com.gestorpyme.view.clientes.ClientesView;
import com.gestorpyme.view.crm.CrmView;
import com.gestorpyme.view.empresa.ConfiguracionView;
import com.gestorpyme.view.compras.ComprasView;
import com.gestorpyme.view.proveedores.ProveedoresView;
import com.gestorpyme.view.taller.OrdenesTrabajoView;
import com.gestorpyme.view.taller.VehiculosView;import com.gestorpyme.view.inventario.InventarioView;
import com.gestorpyme.view.inventario.KardexView;
import com.gestorpyme.view.inventario.LotesView;
import com.gestorpyme.view.login.LoginView;
import com.gestorpyme.view.productos.ProductosView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.AbstractAction;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagLayout;

/**
 * Ventana principal (panel de control) que se muestra tras un inicio de sesion correcto.
 * Es el "esqueleto" de la aplicacion: encabezado con el usuario, navegacion lateral de los
 * modulos v1 y un area central de contenido. Solo presenta informacion y navega; NO contiene
 * reglas de negocio ni SQL. Los modulos se iran conectando uno a uno.
 * Capa: view.
 */
public class DashboardView extends JFrame {

    // --- Paleta basica (provisional; alinear despues con el Pre-diseno UI/UX) ---
    private static final Color COLOR_FONDO = AppPalette.FONDO;
    private static final Color COLOR_SIDEBAR = AppPalette.PRIMARIO;          // #06141B
    private static final Color COLOR_SIDEBAR_TEXTO = AppPalette.SIDEBAR_TEXTO;
    private static final Color COLOR_SIDEBAR_ACTIVO = AppPalette.SECUNDARIO; // #57707A modulo activo
    private static final Color COLOR_PRIMARIO = AppPalette.PRIMARIO;         // #06141B
    private static final Color COLOR_TEXTO = AppPalette.PRIMARIO;
    private static final Color COLOR_TENUE = AppPalette.TEXTO_SECUNDARIO;
    private static final Color COLOR_BORDE = AppPalette.BORDE;
    private static final String FUENTE = "Segoe UI"; // fallback de SF Pro Display

    /** Modulos de navegacion previstos para la version 1. */
    private static final String[] MODULOS = {
        "Inicio", "Clientes / Prospectos", "Proveedores", "Productos / Servicios",
        "Bodegas e inventario", "Kardex", "Lotes y vencimientos",
        "Compras", "Ventas", "Pagos", "Cuentas por cobrar", "CRM", "Vehiculos", "Ordenes de trabajo", "Configuracion", "Exportacion CSV"
    };

    private final Usuario usuario;
    private final JPanel panelContenido = new JPanel(new BorderLayout());
    /** Nombre del modulo mostrado actualmente (para reconstruirlo al cambiar el zoom). */
    private String moduloActual = "Inicio";
    /** Botones del menu lateral, para resaltar el modulo activo. */
    private final Map<String, JButton> botonesNav = new LinkedHashMap<>();

    /**
     * @param usuario usuario autenticado cuya informacion se muestra en el panel.
     */
    public DashboardView(Usuario usuario) {
        this.usuario = usuario;
        configurarVentana();
        construir();
        UiScaleManager.registrar(this); // permite refrescar la ventana al cambiar el zoom
        registrarAtajosZoom();          // Ctrl + +/-/0 y Ctrl + rueda del mouse
        mostrarInicio();
    }

    private void configurarVentana() {
        setTitle("OPTI - GestorPyme Lite");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(960, 600);
        setMinimumSize(new Dimension(820, 520));
        setLocationRelativeTo(null); // centrar en pantalla
    }

    private void construir() {
        setLayout(new BorderLayout());
        add(crearEncabezado(), BorderLayout.NORTH);
        add(crearNavegacion(), BorderLayout.WEST);

        panelContenido.setBackground(COLOR_FONDO);
        panelContenido.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        add(panelContenido, BorderLayout.CENTER);
    }

    /** Barra superior: marca + nombre/rol del usuario + boton de cerrar sesion. */
    private JComponent crearEncabezado() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(Color.WHITE);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, COLOR_BORDE),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        // Marca: logo del proyecto (o texto "OPTI" como alternativa si no existe el archivo).
        JComponent marca = LogoLoader.construirMarca(32, COLOR_PRIMARIO);

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        derecha.setOpaque(false);

        JLabel lblUsuario = new JLabel(nombreVisible() + "  -  " + usuario.getRol().name());
        lblUsuario.setFont(new Font(FUENTE, Font.PLAIN, 13));
        lblUsuario.setForeground(COLOR_TEXTO);

        JButton botonSalir = new JButton("Cerrar sesion");
        botonSalir.setFocusPainted(false);
        botonSalir.setFont(new Font(FUENTE, Font.PLAIN, 12));
        botonSalir.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        botonSalir.addActionListener(e -> cerrarSesion());

        derecha.add(lblUsuario);
        derecha.add(botonSalir);

        barra.add(marca, BorderLayout.WEST);
        barra.add(derecha, BorderLayout.EAST);
        return barra;
    }

    /**
     * Aplica una escala de interfaz y reconstruye el modulo actual para que tome los
     * nuevos tamanos. Lo usan los atajos de teclado, Ctrl+rueda y Configuracion.
     *
     * @param factor escala (se ajusta al rango permitido por UiScaleManager).
     */
    public void aplicarZoom(double factor) {
        UiScaleManager.aplicarEscala(factor);
        mostrarModulo(moduloActual);
    }

    private void aumentarZoom() {
        aplicarZoom(UiScaleManager.getEscala() + 0.10);
    }

    private void reducirZoom() {
        aplicarZoom(UiScaleManager.getEscala() - 0.10);
    }

    private void restablecerZoom() {
        aplicarZoom(1.0);
    }

    /**
     * Registra el zoom "natural": atajos de teclado (Ctrl + +, Ctrl + -, Ctrl + 0) y
     * Ctrl + rueda del mouse. El gesto de pellizco (pinch) del touchpad no es capturable
     * de forma fiable en Swing puro; Windows suele traducirlo a Ctrl+rueda, que si se
     * soporta aqui.
     */
    private void registrarAtajosZoom() {
        JRootPane raiz = getRootPane();
        var entrada = raiz.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        var acciones = raiz.getActionMap();

        int ctrl = InputEvent.CTRL_DOWN_MASK;
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, ctrl), "zoomMas");
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, ctrl), "zoomMas");
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_ADD, ctrl), "zoomMas");
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, ctrl), "zoomMenos");
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, ctrl), "zoomMenos");
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_0, ctrl), "zoomReset");
        entrada.put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, ctrl), "zoomReset");

        acciones.put("zoomMas", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { aumentarZoom(); }
        });
        acciones.put("zoomMenos", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { reducirZoom(); }
        });
        acciones.put("zoomReset", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { restablecerZoom(); }
        });

        // Ctrl + rueda del mouse: aumenta/reduce la escala mientras se mantiene Ctrl.
        Toolkit.getDefaultToolkit().addAWTEventListener(ruedaZoom, AWTEvent.MOUSE_WHEEL_EVENT_MASK);
    }

    /** Listener global de rueda; solo actua con Ctrl presionado y sobre esta ventana. */
    private final AWTEventListener ruedaZoom = evento -> {
        if (evento instanceof MouseWheelEvent) {
            MouseWheelEvent e = (MouseWheelEvent) evento;
            if (e.isControlDown() && SwingUtilities.getWindowAncestor(e.getComponent()) == this) {
                if (e.getWheelRotation() < 0) {
                    aumentarZoom();
                } else {
                    reducirZoom();
                }
            }
        }
    };

    @Override
    public void dispose() {
        // Evita fugas del listener global al cerrar la ventana.
        Toolkit.getDefaultToolkit().removeAWTEventListener(ruedaZoom);
        super.dispose();
    }

    /** Panel lateral con un boton por cada modulo. */
    private JComponent crearNavegacion() {
        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBackground(COLOR_SIDEBAR);
        nav.setBorder(BorderFactory.createEmptyBorder(16, 12, 16, 12));
        nav.setPreferredSize(new Dimension(220, 0));

        JLabel titulo = new JLabel("GestorPyme Lite");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(new Font(FUENTE, Font.BOLD, 15));
        titulo.setAlignmentX(Component.LEFT_ALIGNMENT);
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 6, 16, 0));
        nav.add(titulo);

        for (String modulo : MODULOS) {
            nav.add(crearBotonNav(modulo));
            nav.add(Box.createRigidArea(new Dimension(0, 4)));
        }
        return nav;
    }

    private JButton crearBotonNav(String modulo) {
        JButton boton = new JButton(modulo);
        boton.setAlignmentX(Component.LEFT_ALIGNMENT);
        boton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        boton.setHorizontalAlignment(SwingConstants.LEFT);
        boton.setForeground(COLOR_SIDEBAR_TEXTO);
        boton.setBackground(COLOR_SIDEBAR);
        boton.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        boton.setFocusPainted(false);
        boton.setContentAreaFilled(false);
        boton.setOpaque(true);
        boton.setFont(new Font(FUENTE, Font.PLAIN, 13));
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boton.addActionListener(e -> mostrarModulo(modulo));
        botonesNav.put(modulo, boton);
        return boton;
    }

    /** Resalta el boton del modulo activo con el color de acento (#57707A). */
    private void resaltarActivo(String modulo) {
        for (Map.Entry<String, JButton> e : botonesNav.entrySet()) {
            boolean activo = e.getKey().equals(modulo);
            JButton b = e.getValue();
            b.setBackground(activo ? COLOR_SIDEBAR_ACTIVO : COLOR_SIDEBAR);
            b.setForeground(activo ? Color.WHITE : COLOR_SIDEBAR_TEXTO);
            b.setFont(new Font(FUENTE, activo ? Font.BOLD : Font.PLAIN, 13));
        }
    }

    /** Decide que contenido mostrar segun el modulo elegido en la navegacion. */
    private void mostrarModulo(String modulo) {
        moduloActual = modulo; // se recuerda para poder reconstruirlo al cambiar el zoom
        resaltarActivo(modulo); // resalta el modulo activo en el menu
        switch (modulo) {
            case "Inicio":
                mostrarInicio();
                break;
            case "Clientes / Prospectos":
                reemplazarContenido(new ClientesView());
                break;
            case "Proveedores":
                reemplazarContenido(new ProveedoresView());
                break;
            case "Productos / Servicios":
                reemplazarContenido(new ProductosView());
                break;
            case "Bodegas e inventario":
                reemplazarContenido(new InventarioView(usuario.getIdUsuario()));
                break;
            case "Kardex":
                reemplazarContenido(new KardexView());
                break;
            case "Lotes y vencimientos":
                reemplazarContenido(new LotesView());
                break;
            case "Compras":
                reemplazarContenido(new ComprasView(usuario.getIdUsuario()));
                break;
            case "Ventas":
                reemplazarContenido(new VentasView(usuario.getIdUsuario()));
                break;
            case "Pagos":
                reemplazarContenido(new PagosView(usuario.getIdUsuario()));
                break;
            case "Cuentas por cobrar":
                reemplazarContenido(new CuentasPorCobrarView(usuario.getIdUsuario()));
                break;
            case "Exportacion CSV":
                reemplazarContenido(new ExportacionView(usuario.getIdUsuario()));
                break;
            case "CRM":
                reemplazarContenido(new CrmView(usuario.getIdUsuario()));
                break;
            case "Vehiculos":
                reemplazarContenido(new VehiculosView());
                break;
            case "Ordenes de trabajo":
                reemplazarContenido(new OrdenesTrabajoView());
                break;
            case "Configuracion":
                reemplazarContenido(new ConfiguracionView(this::aplicarZoom));
                break;
            default:
                mostrarPlaceholder(modulo);
                break;
        }
    }

    /** Contenido de la pestana "Inicio": el Dashboard gerencial con datos reales. */
    private void mostrarInicio() {
        reemplazarContenido(new InicioView(nombreVisible()));
    }

    /** Contenido provisional para los modulos aun no implementados. */
    private void mostrarPlaceholder(String modulo) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        JLabel etiqueta = new JLabel("Modulo \"" + modulo + "\" en construccion");
        etiqueta.setFont(new Font(FUENTE, Font.PLAIN, 18));
        etiqueta.setForeground(new Color(0x475569));
        panel.add(etiqueta);
        reemplazarContenido(panel);
    }

    private void reemplazarContenido(JComponent nuevo) {
        panelContenido.removeAll();
        panelContenido.add(nuevo, BorderLayout.CENTER);
        panelContenido.revalidate();
        panelContenido.repaint();
    }

    /** Cierra esta ventana y vuelve a mostrar el inicio de sesion. */
    private void cerrarSesion() {
        dispose();
        new LoginView(new LoginController()).setVisible(true);
    }

    /** Nombre a mostrar: nombre completo si existe; si no, el nombre de usuario. */
    private String nombreVisible() {
        return usuario.getNombreCompleto() != null ? usuario.getNombreCompleto() : usuario.getNombreUsuario();
    }
}
