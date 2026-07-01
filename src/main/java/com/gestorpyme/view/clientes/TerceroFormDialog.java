package com.gestorpyme.view.clientes;

import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Tercero;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;

/**
 * Formulario modal para crear o editar un cliente / prospecto.
 * Captura los datos y delega la validacion y el guardado en {@link TerceroController}.
 * No contiene reglas de negocio ni SQL.
 * Capa: view.
 */
public class TerceroFormDialog extends JDialog {

    private static final String FUENTE = "Segoe UI";

    private final TerceroController controller;
    /** Tercero que se esta editando; null cuando se crea uno nuevo. */
    private final Tercero terceroEnEdicion;

    private final JComboBox<TipoTercero> comboTipo =
            new JComboBox<>(new TipoTercero[]{TipoTercero.CLIENTE, TipoTercero.PROSPECTO});
    private final JTextField campoNombre = new JTextField(22);
    private final JTextField campoDocumento = new JTextField(22);
    private final JTextField campoTelefono = new JTextField(22);
    private final JTextField campoCorreo = new JTextField(22);
    private final JTextField campoDireccion = new JTextField(22);
    private final JTextArea areaObservaciones = new JTextArea(3, 22);

    /** Indica si el registro se guardo correctamente (lo consulta la vista lista). */
    private boolean guardado = false;

    /**
     * @param propietario ventana propietaria (para centrar y bloquear de forma modal).
     * @param controller  controlador que validara y guardara.
     * @param tercero     registro a editar, o null para crear uno nuevo.
     */
    public TerceroFormDialog(Window propietario, TerceroController controller, Tercero tercero) {
        this(propietario, controller, tercero,
                new TipoTercero[]{TipoTercero.CLIENTE, TipoTercero.PROSPECTO});
    }

    /**
     * @param propietario      ventana propietaria (modal).
     * @param controller       controlador que validara y guardara.
     * @param tercero          registro a editar, o null para crear.
     * @param tiposPermitidos  tipos que ofrece el combo (p. ej. solo PROVEEDOR para proveedores).
     */
    public TerceroFormDialog(Window propietario, TerceroController controller, Tercero tercero,
                             TipoTercero[] tiposPermitidos) {
        super(propietario, ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.terceroEnEdicion = tercero;
        comboTipo.setModel(new javax.swing.DefaultComboBoxModel<>(tiposPermitidos));
        boolean soloProveedor = tiposPermitidos.length == 1 && tiposPermitidos[0] == TipoTercero.PROVEEDOR;
        String entidad = soloProveedor ? "proveedor" : "cliente / prospecto";
        setTitle((tercero == null ? "Nuevo " : "Editar ") + entidad);
        construir();
        if (tercero != null) {
            cargarDatos(tercero);
        }
        pack();
        setLocationRelativeTo(propietario);
    }

    /** @return true si se guardo el registro (para que la lista se refresque). */
    public boolean fueGuardado() {
        return guardado;
    }

    private void construir() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarFila(formulario, g, fila++, "Tipo *", comboTipo);
        agregarFila(formulario, g, fila++, "Nombre *", campoNombre);
        agregarFila(formulario, g, fila++, "Documento", campoDocumento);
        agregarFila(formulario, g, fila++, "Telefono", campoTelefono);
        agregarFila(formulario, g, fila++, "Correo", campoCorreo);
        agregarFila(formulario, g, fila++, "Direccion", campoDireccion);

        areaObservaciones.setLineWrap(true);
        areaObservaciones.setWrapStyleWord(true);
        agregarFila(formulario, g, fila, "Observaciones", new JScrollPane(areaObservaciones));

        JButton botonCancelar = new JButton("Cancelar");
        botonCancelar.addActionListener(e -> dispose());
        JButton botonGuardar = new JButton("Guardar");
        botonGuardar.addActionListener(e -> guardar());

        JPanel botones = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 8));
        botones.add(botonCancelar);
        botones.add(botonGuardar);

        setLayout(new BorderLayout());
        add(formulario, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
    }

    private void agregarFila(JPanel panel, GridBagConstraints g, int fila, String etiqueta, JComponent campo) {
        g.gridx = 0;
        g.gridy = fila;
        g.weightx = 0;
        JLabel l = new JLabel(etiqueta);
        l.setFont(new Font(FUENTE, Font.PLAIN, 13));
        panel.add(l, g);

        g.gridx = 1;
        g.weightx = 1;
        panel.add(campo, g);
    }

    private void cargarDatos(Tercero t) {
        comboTipo.setSelectedItem(t.getTipoTercero());
        campoNombre.setText(textoSeguro(t.getNombre()));
        campoDocumento.setText(textoSeguro(t.getDocumento()));
        campoTelefono.setText(textoSeguro(t.getTelefono()));
        campoCorreo.setText(textoSeguro(t.getCorreo()));
        campoDireccion.setText(textoSeguro(t.getDireccion()));
        areaObservaciones.setText(textoSeguro(t.getObservaciones()));
    }

    /** Construye el tercero con los datos del formulario y lo manda a guardar. */
    private void guardar() {
        // Si es edicion, conserva el id y el estado del registro original.
        Tercero tercero = (terceroEnEdicion != null) ? terceroEnEdicion : new Tercero();
        tercero.setTipoTercero((TipoTercero) comboTipo.getSelectedItem());
        tercero.setNombre(campoNombre.getText().trim());
        tercero.setDocumento(vacioANull(campoDocumento.getText()));
        tercero.setTelefono(vacioANull(campoTelefono.getText()));
        tercero.setCorreo(vacioANull(campoCorreo.getText()));
        tercero.setDireccion(vacioANull(campoDireccion.getText()));
        tercero.setObservaciones(vacioANull(areaObservaciones.getText()));

        try {
            controller.guardar(tercero);
            guardado = true;
            dispose();
        } catch (ValidacionException e) {
            // Regla de negocio no cumplida: mensaje claro para el usuario.
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    /** Convierte un texto en blanco a null (para no guardar cadenas vacias). */
    private String vacioANull(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
