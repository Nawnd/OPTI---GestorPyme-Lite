package com.gestorpyme.view.empresa;

import com.gestorpyme.controller.BackupController;
import com.gestorpyme.controller.EmpresaController;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.EmpresaConfiguracion;
import com.gestorpyme.repository.EmpresaRepository;
import com.gestorpyme.service.EmpresaService;
import com.gestorpyme.view.components.UiScaleManager;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.DoubleConsumer;

/**
 * Vista del modulo Configuracion de empresa.
 *
 * Formulario de REGISTRO UNICO: al construirse carga la configuracion existente
 * (si la hay) y el boton Guardar la crea o actualiza. La vista NO ejecuta SQL:
 * delega en el controlador.
 */
public class ConfiguracionView extends JPanel {

    private final EmpresaController controller;
    private final BackupController backupController = new BackupController();
    /** Accion para aplicar la escala de interfaz (la provee el Dashboard). */
    private final DoubleConsumer aplicarEscala;

    private final JTextField txtNombre = campo();
    private final JTextField txtDocumento = campo();
    private final JTextField txtDireccion = campo();
    private final JTextField txtTelefono = campo();
    private final JTextField txtCorreo = campo();
    private final JTextField txtMoneda = campo();
    private final JTextField txtRutaLogo = campo();
    private final JTextArea txtMensajeRecibo = new JTextArea(3, 20);

    /** Constructor por defecto: aplica la escala directamente con UiScaleManager. */
    public ConfiguracionView() {
        this(UiScaleManager::aplicarEscala);
    }

    /**
     * @param aplicarEscala accion que aplica la escala de interfaz (el Dashboard la
     *                      conecta con su zoom para reconstruir la vista al cambiarla).
     */
    public ConfiguracionView(DoubleConsumer aplicarEscala) {
        this.aplicarEscala = aplicarEscala;
        this.controller = new EmpresaController(new EmpresaService(new EmpresaRepository()));

        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        add(crearTitulo(), BorderLayout.NORTH);
        add(crearTarjetaFormulario(), BorderLayout.CENTER);
        add(crearZonaInferior(), BorderLayout.SOUTH);

        cargarDatos();
    }

    /**
     * Apila verticalmente las tarjetas inferiores (Preferencias y Base de datos) sin que el
     * BorderLayout estire ninguna: GridBag con weighty 0 y anclaje al norte mantiene la altura
     * preferida de cada tarjeta.
     */
    private JComponent crearZonaInferior() {
        JPanel zona = new JPanel(new GridBagLayout());
        zona.setBackground(UiTheme.FONDO);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.NORTH;

        g.gridy = 0;
        g.insets = new Insets(0, 0, UiTheme.ESPACIO, 0);
        zona.add(crearPreferencias(), g);

        g.gridy = 1;
        g.insets = new Insets(0, 0, 0, 0);
        zona.add(crearSeccionBaseDatos(), g);
        return zona;
    }

    /** Tarjeta "Base de datos": respaldo y restauración del archivo SQLite local (Paso T). */
    private JComponent crearSeccionBaseDatos() {
        JPanel tarjeta = new JPanel(new GridBagLayout());
        tarjeta.setBackground(UiTheme.SUPERFICIE);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDE),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        JLabel titulo = new JLabel("Base de datos");
        titulo.setFont(UiTheme.fuenteSubtitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 3;
        tarjeta.add(titulo, g);

        JLabel hint = new JLabel("Cree copias de seguridad de sus datos y restaurelas cuando lo necesite. "
                + "Despues de restaurar, cierre y vuelva a abrir la aplicacion.");
        hint.setFont(UiTheme.fuentePequena());
        hint.setForeground(UiTheme.TEXTO_TENUE);
        g.gridx = 0; g.gridy = 1; g.gridwidth = 3;
        tarjeta.add(hint, g);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        botones.setOpaque(false);
        JButton btnCrear = UiTheme.botonPrimario(new JButton("Crear respaldo"));
        btnCrear.addActionListener(e -> crearRespaldo());
        JButton btnRestaurar = UiTheme.botonSecundario(new JButton("Restaurar respaldo"));
        btnRestaurar.addActionListener(e -> restaurarRespaldo());
        botones.add(btnCrear);
        botones.add(btnRestaurar);
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3;
        tarjeta.add(botones, g);

        return tarjeta;
    }

    /** Pide ruta de destino y crea un respaldo de la base actual (confirma si el archivo existe). */
    private void crearRespaldo() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Guardar respaldo de la base de datos");
        ch.setSelectedFile(new File(backupController.nombreRespaldoSugerido()));
        if (ch.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destino = ch.getSelectedFile().toPath();
        if (Files.exists(destino)) {
            int r = JOptionPane.showConfirmDialog(this,
                    "El archivo ya existe. Desea reemplazarlo?",
                    "Confirmar", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) {
                return;
            }
        }
        try {
            backupController.crearRespaldo(destino);
            JOptionPane.showMessageDialog(this,
                    "Respaldo creado correctamente en:\n" + destino,
                    "Respaldo", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo crear el respaldo: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Pide un archivo de respaldo, advierte que reemplazara la base actual y restaura tras confirmar. */
    private void restaurarRespaldo() {
        JFileChooser ch = new JFileChooser();
        ch.setDialogTitle("Seleccionar respaldo a restaurar");
        if (ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path origen = ch.getSelectedFile().toPath();
        int r = JOptionPane.showConfirmDialog(this,
                "Esto reemplazara la base actual con el respaldo seleccionado.\n"
                        + "Se recomienda cerrar y volver a abrir la aplicacion despues de restaurar.\n\n"
                        + "Desea continuar?",
                "Confirmar restauracion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            backupController.restaurarRespaldo(origen);
            JOptionPane.showMessageDialog(this,
                    "Restauracion completada.\n"
                            + "Cierre y vuelva a abrir la aplicacion para usar los datos restaurados.",
                    "Restauracion", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "No se pudo restaurar el respaldo: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Tarjeta "Preferencias de interfaz": tamano/escala de la interfaz. */
    private JComponent crearPreferencias() {
        JPanel tarjeta = new JPanel(new GridBagLayout());
        tarjeta.setBackground(UiTheme.SUPERFICIE);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDE),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        JLabel titulo = new JLabel("Preferencias de interfaz");
        titulo.setFont(UiTheme.fuenteSubtitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 3;
        tarjeta.add(titulo, g);
        g.gridwidth = 1;

        JLabel lbl = new JLabel("Tamano de interfaz:");
        lbl.setFont(UiTheme.fuenteNegrita());
        lbl.setForeground(UiTheme.TEXTO);
        g.gridx = 0; g.gridy = 1;
        tarjeta.add(lbl, g);

        final String[] opciones = {"Pequeno (90%)", "Normal (100%)", "Grande (110%)",
                "Muy grande (125%)", "Maximo (150%)"};
        final double[] escalas = {0.90, 1.00, 1.10, 1.25, 1.50};
        JComboBox<String> combo = new JComboBox<>(opciones);
        combo.setSelectedIndex(indiceEscalaActual(escalas));
        combo.setPreferredSize(new Dimension(200, 28));
        combo.addActionListener(e -> {
            int i = combo.getSelectedIndex();
            if (i >= 0) {
                aplicarEscala.accept(escalas[i]);
            }
        });
        g.gridx = 1; g.gridy = 1;
        tarjeta.add(combo, g);

        JLabel hint = new JLabel("Atajos: Ctrl + rueda del mouse, Ctrl con + / - y Ctrl con 0 (100%). "
                + "Paleta oficial activa.");
        hint.setFont(UiTheme.fuentePequena());
        hint.setForeground(UiTheme.TEXTO_TENUE);
        g.gridx = 0; g.gridy = 2; g.gridwidth = 3;
        tarjeta.add(hint, g);

        return tarjeta;
    }

    /** Indice de la opcion cuya escala es la mas cercana a la actual. */
    private int indiceEscalaActual(double[] escalas) {
        double actual = UiScaleManager.getEscala();
        int mejor = 1;
        double dif = Double.MAX_VALUE;
        for (int i = 0; i < escalas.length; i++) {
            double d = Math.abs(escalas[i] - actual);
            if (d < dif) {
                dif = d;
                mejor = i;
            }
        }
        return mejor;
    }

    private JComponent crearTitulo() {
        JLabel titulo = new JLabel("Configuracion de la empresa");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        return titulo;
    }

    /** Tarjeta blanca con el formulario y la barra de botones. */
    private JComponent crearTarjetaFormulario() {
        JPanel tarjeta = new JPanel(new BorderLayout());
        tarjeta.setBackground(UiTheme.SUPERFICIE);
        tarjeta.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SUPERFICIE);
        form.setBorder(UiTheme.margen());

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarCampo(form, g, fila++, "Nombre empresa *:", txtNombre);
        agregarCampo(form, g, fila++, "Documento (NIT):", txtDocumento);
        agregarCampo(form, g, fila++, "Direccion:", txtDireccion);
        agregarCampo(form, g, fila++, "Telefono:", txtTelefono);
        agregarCampo(form, g, fila++, "Correo:", txtCorreo);
        agregarCampo(form, g, fila++, "Moneda:", txtMoneda);
        agregarCampo(form, g, fila++, "Ruta del logo:", txtRutaLogo);

        g.gridx = 0; g.gridy = fila; g.weightx = 0; g.anchor = GridBagConstraints.NORTHWEST;
        form.add(etiqueta("Mensaje recibo:"), g);
        g.gridx = 1; g.weightx = 1; g.anchor = GridBagConstraints.WEST;
        txtMensajeRecibo.setLineWrap(true);
        txtMensajeRecibo.setWrapStyleWord(true);
        txtMensajeRecibo.setFont(UiTheme.fuenteNormal());
        JScrollPane sp = new JScrollPane(txtMensajeRecibo);
        sp.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        form.add(sp, g);

        tarjeta.add(form, BorderLayout.CENTER);
        tarjeta.add(crearBarraBotones(), BorderLayout.SOUTH);

        // Contenedor con scroll por si la ventana es pequena.
        JScrollPane scrollTarjeta = new JScrollPane(tarjeta);
        scrollTarjeta.setBorder(null);
        scrollTarjeta.getViewport().setBackground(UiTheme.FONDO);
        return scrollTarjeta;
    }

    private void agregarCampo(JPanel form, GridBagConstraints g, int fila, String texto, JTextField campo) {
        g.gridx = 0; g.gridy = fila; g.weightx = 0;
        form.add(etiqueta(texto), g);
        g.gridx = 1; g.weightx = 1;
        form.add(campo, g);
    }

    private JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        return l;
    }

    private static JTextField campo() {
        JTextField c = new JTextField();
        c.setFont(UiTheme.fuenteNormal());
        c.setPreferredSize(new Dimension(320, 28));
        return c;
    }

    private JComponent crearBarraBotones() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, UiTheme.ESPACIO));
        barra.setBackground(UiTheme.SUPERFICIE);
        barra.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDE));

        JButton btnGuardar = UiTheme.botonPrimario(new JButton("Guardar"));
        btnGuardar.addActionListener(e -> guardar());
        barra.add(btnGuardar);
        return barra;
    }

    /** Carga en el formulario la configuracion existente, si la hay. */
    private void cargarDatos() {
        Optional<EmpresaConfiguracion> actual = controller.obtener();
        if (actual.isPresent()) {
            EmpresaConfiguracion e = actual.get();
            txtNombre.setText(valor(e.getNombreEmpresa()));
            txtDocumento.setText(valor(e.getDocumento()));
            txtDireccion.setText(valor(e.getDireccion()));
            txtTelefono.setText(valor(e.getTelefono()));
            txtCorreo.setText(valor(e.getCorreo()));
            txtMoneda.setText(valor(e.getMoneda()));
            txtRutaLogo.setText(valor(e.getRutaLogo()));
            txtMensajeRecibo.setText(valor(e.getMensajeRecibo()));
        } else {
            txtMoneda.setText("COP");
        }
    }

    /** Toma los datos del formulario, los valida via servicio y los guarda. */
    private void guardar() {
        EmpresaConfiguracion e = new EmpresaConfiguracion();
        e.setNombreEmpresa(txtNombre.getText().trim());
        e.setDocumento(txtDocumento.getText().trim());
        e.setDireccion(txtDireccion.getText().trim());
        e.setTelefono(txtTelefono.getText().trim());
        e.setCorreo(txtCorreo.getText().trim());
        e.setMoneda(txtMoneda.getText().trim());
        e.setRutaLogo(txtRutaLogo.getText().trim());
        e.setMensajeRecibo(txtMensajeRecibo.getText().trim());

        try {
            controller.guardar(e);
            JOptionPane.showMessageDialog(this, "Configuracion guardada correctamente.",
                    "Configuracion", JOptionPane.INFORMATION_MESSAGE);
            cargarDatos();
        } catch (ValidacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion", JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "Ocurrio un error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String valor(String s) {
        return s == null ? "" : s;
    }
}
