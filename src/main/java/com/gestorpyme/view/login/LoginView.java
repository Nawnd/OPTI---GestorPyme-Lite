package com.gestorpyme.view.login;
import com.gestorpyme.view.components.LogoLoader;

import com.gestorpyme.controller.LoginController;
import com.gestorpyme.domain.model.Usuario;
import com.gestorpyme.service.ResultadoAutenticacion;
import com.gestorpyme.view.dashboard.DashboardView;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;

/**
 * Pantalla de inicio de sesion (Swing).
 * Solo captura datos y muestra resultados: NO contiene reglas de negocio ni SQL.
 * Delega la autenticacion en {@link LoginController}.
 * La paleta es basica y puede afinarse luego segun el Pre-diseno UI/UX.
 * Capa: view.
 */
public class LoginView extends JFrame {

    // --- Paleta basica (provisional; alinear despues con el Pre-diseno UI/UX) ---
    private static final Color COLOR_FONDO = new Color(0xF1F5F9);
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final Color COLOR_TEXTO = new Color(0x0F172A);
    private static final Color COLOR_ERROR = new Color(0xDC2626);
    private static final Color COLOR_BORDE = new Color(0xCBD5E1);
    /** Tipografia: fallback de SF Pro Display en Windows. */
    private static final String FUENTE = "Segoe UI";

    private final LoginController controller;

    private final JTextField campoUsuario = new JTextField(18);
    private final JPasswordField campoPassword = new JPasswordField(18);
    private final JLabel etiquetaMensaje = new JLabel(" ");

    /**
     * @param controller controlador que procesara los intentos de inicio de sesion.
     */
    public LoginView(LoginController controller) {
        this.controller = controller;
        configurarVentana();
        construirContenido();
    }

    private void configurarVentana() {
        setTitle("OPTI - GestorPyme Lite");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(420, 470);
        setLocationRelativeTo(null); // centrar en pantalla
        setResizable(false);
    }

    private void construirContenido() {
        JPanel contenedor = new JPanel(new GridBagLayout());
        contenedor.setBackground(COLOR_FONDO);

        // "Tarjeta" central con los campos del formulario.
        JPanel tarjeta = new JPanel();
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));
        tarjeta.setBackground(Color.WHITE);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE2E8F0)),
                BorderFactory.createEmptyBorder(28, 32, 28, 32)));

        // Marca: logo del proyecto (o texto "OPTI" como alternativa si no existe el archivo).
        JLabel titulo = LogoLoader.construirMarca(56, COLOR_PRIMARIO);
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitulo = new JLabel("GestorPyme Lite");
        subtitulo.setFont(new Font(FUENTE, Font.PLAIN, 15));
        subtitulo.setForeground(COLOR_TEXTO);
        subtitulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        configurarCampo(campoUsuario);
        configurarCampo(campoPassword);

        JButton botonEntrar = new JButton("Iniciar sesion");
        botonEntrar.setAlignmentX(Component.CENTER_ALIGNMENT);
        botonEntrar.setBackground(COLOR_PRIMARIO);
        botonEntrar.setForeground(Color.WHITE);
        botonEntrar.setFocusPainted(false);
        botonEntrar.setFont(new Font(FUENTE, Font.BOLD, 14));
        botonEntrar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        botonEntrar.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        botonEntrar.addActionListener(e -> intentarLogin());

        etiquetaMensaje.setFont(new Font(FUENTE, Font.PLAIN, 13));
        etiquetaMensaje.setForeground(COLOR_ERROR);
        etiquetaMensaje.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Permitir iniciar sesion pulsando Enter en el campo de contrasena.
        campoPassword.addActionListener(e -> intentarLogin());

        tarjeta.add(titulo);
        tarjeta.add(espacio(2));
        tarjeta.add(subtitulo);
        tarjeta.add(espacio(22));
        tarjeta.add(alinearIzquierda(etiquetaCampo("Usuario")));
        tarjeta.add(espacio(4));
        tarjeta.add(campoUsuario);
        tarjeta.add(espacio(14));
        tarjeta.add(alinearIzquierda(etiquetaCampo("Contrasena")));
        tarjeta.add(espacio(4));
        tarjeta.add(campoPassword);
        tarjeta.add(espacio(22));
        tarjeta.add(botonEntrar);
        tarjeta.add(espacio(14));
        tarjeta.add(etiquetaMensaje);

        contenedor.add(tarjeta);
        setContentPane(contenedor);
    }

    private JLabel etiquetaCampo(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(new Font(FUENTE, Font.PLAIN, 13));
        etiqueta.setForeground(COLOR_TEXTO);
        return etiqueta;
    }

    private void configurarCampo(JTextField campo) {
        campo.setFont(new Font(FUENTE, Font.PLAIN, 14));
        campo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        campo.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(COLOR_BORDE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    /** Envuelve un componente para que quede alineado a la izquierda dentro del BoxLayout. */
    private Component alinearIzquierda(JComponent componente) {
        JPanel fila = new JPanel(new BorderLayout());
        fila.setOpaque(false);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, componente.getPreferredSize().height));
        fila.add(componente, BorderLayout.WEST);
        return fila;
    }

    private Component espacio(int alto) {
        return Box.createRigidArea(new Dimension(0, alto));
    }

    /** Captura los datos, delega en el controlador y muestra el resultado. */
    private void intentarLogin() {
        String usuario = campoUsuario.getText();
        String password = new String(campoPassword.getPassword());

        ResultadoAutenticacion resultado = controller.autenticar(usuario, password);

        if (resultado.esExito()) {
            // Abrir el panel principal y cerrar esta ventana de login.
            // Estamos en el hilo de eventos (EDT), por lo que se puede crear la vista aqui.
            Usuario autenticado = resultado.getUsuario();
            dispose();
            new DashboardView(autenticado).setVisible(true);
        } else {
            // Muestra el mensaje del resultado (credenciales, inactivo o error).
            etiquetaMensaje.setText(resultado.getMensaje());
            campoPassword.setText(""); // limpiar la contrasena tras un intento fallido
        }
    }
}
