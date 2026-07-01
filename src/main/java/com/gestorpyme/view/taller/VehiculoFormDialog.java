package com.gestorpyme.view.taller;

import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.controller.VehiculoController;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;

/**
 * Formulario de creacion/edicion de vehiculos (Paso U.1).
 *
 * Capa: view (Swing). No contiene reglas de negocio: arma el {@link Vehiculo} y delega en
 * {@link VehiculoController}. El cliente se elige con busqueda inteligente
 * ({@link EntityLookupField} + {@link SearchSpecs#clientes}).
 */
public class VehiculoFormDialog extends JDialog {

    private static final String FUENTE = "Segoe UI";

    private final transient VehiculoController controller;
    private final transient Vehiculo original; // null = creacion

    private final EntityLookupField<Tercero> lookupCliente;
    private final JTextField txtPlaca = new JTextField();
    private final JTextField txtMarca = new JTextField();
    private final JTextField txtLinea = new JTextField();
    private final JTextField txtAnio = new JTextField();
    private final JTextField txtColor = new JTextField();
    private final JTextField txtKilometraje = new JTextField();
    private final JTextArea txtObservaciones = new JTextArea(3, 20);

    private boolean guardado;

    public VehiculoFormDialog(Window propietario, VehiculoController controller, Vehiculo vehiculo) {
        super(propietario, vehiculo == null ? "Nuevo vehiculo" : "Editar vehiculo",
                ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.original = vehiculo;
        this.lookupCliente = new EntityLookupField<>(SearchSpecs.clientes(new TerceroController()));

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        add(crearFormulario(), BorderLayout.CENTER);
        add(crearBotones(), BorderLayout.SOUTH);

        if (vehiculo != null) {
            cargar(vehiculo);
        }

        pack();
        setMinimumSize(new Dimension(420, getHeight()));
        setLocationRelativeTo(propietario);
    }

    /** @return true si el vehiculo fue guardado correctamente. */
    public boolean fueGuardado() {
        return guardado;
    }

    private JPanel crearFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregar(form, g, fila++, "Cliente:", lookupCliente);
        agregar(form, g, fila++, "Placa:", txtPlaca);
        agregar(form, g, fila++, "Marca:", txtMarca);
        agregar(form, g, fila++, "Linea / modelo:", txtLinea);
        agregar(form, g, fila++, "Anio:", txtAnio);
        agregar(form, g, fila++, "Color:", txtColor);
        agregar(form, g, fila++, "Kilometraje:", txtKilometraje);

        g.gridx = 0; g.gridy = fila; g.weightx = 0; g.anchor = GridBagConstraints.NORTHWEST;
        form.add(etiqueta("Observaciones:"), g);
        g.gridx = 1; g.weightx = 1; g.anchor = GridBagConstraints.WEST;
        txtObservaciones.setLineWrap(true);
        txtObservaciones.setWrapStyleWord(true);
        txtObservaciones.setFont(new Font(FUENTE, Font.PLAIN, 13));
        form.add(new JScrollPane(txtObservaciones), g);
        return form;
    }

    /** Agrega una fila etiqueta + campo al formulario. */
    private void agregar(JPanel form, GridBagConstraints g, int fila, String texto,
                         java.awt.Component campo) {
        g.gridx = 0; g.gridy = fila; g.weightx = 0;
        form.add(etiqueta(texto), g);
        g.gridx = 1; g.weightx = 1;
        if (campo instanceof JTextField) {
            ((JTextField) campo).setFont(new Font(FUENTE, Font.PLAIN, 13));
            campo.setPreferredSize(new Dimension(260, 28));
        }
        form.add(campo, g);
    }

    private JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font(FUENTE, Font.BOLD, 13));
        return l;
    }

    private JPanel crearBotones() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> dispose());
        JButton btnGuardar = new JButton("Guardar");
        btnGuardar.setFont(new Font(FUENTE, Font.BOLD, 13));
        btnGuardar.addActionListener(e -> guardar());
        barra.add(btnCancelar);
        barra.add(btnGuardar);
        return barra;
    }

    /** Carga los datos de un vehiculo existente en el formulario (modo edicion). */
    private void cargar(Vehiculo v) {
        if (v.getIdTercero() > 0) {
            Tercero cliente = new Tercero();
            cliente.setIdTercero(v.getIdTercero());
            cliente.setNombre(v.getNombreCliente());
            lookupCliente.setSeleccionado(cliente);
        }
        txtPlaca.setText(textoSeguro(v.getPlaca()));
        txtMarca.setText(textoSeguro(v.getMarca()));
        txtLinea.setText(textoSeguro(v.getLinea()));
        txtAnio.setText(v.getAnio() == null ? "" : String.valueOf(v.getAnio()));
        txtColor.setText(textoSeguro(v.getColor()));
        txtKilometraje.setText(String.format("%.0f", v.getKilometraje()));
        txtObservaciones.setText(textoSeguro(v.getObservaciones()));
    }

    /** Construye el vehiculo desde el formulario, lo valida via servicio y lo guarda. */
    private void guardar() {
        Integer anio;
        double kilometraje;
        try {
            anio = parsearAnio(txtAnio.getText());
            kilometraje = parsearKilometraje(txtKilometraje.getText());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Vehiculo v = (original == null) ? new Vehiculo() : original;
        Tercero cliente = lookupCliente.getSeleccionado();
        v.setIdTercero(cliente != null ? cliente.getIdTercero() : 0);
        v.setPlaca(txtPlaca.getText());
        v.setMarca(textoONull(txtMarca.getText()));
        v.setLinea(textoONull(txtLinea.getText()));
        v.setAnio(anio);
        v.setColor(textoONull(txtColor.getText()));
        v.setKilometraje(kilometraje);
        v.setObservaciones(textoONull(txtObservaciones.getText()));
        // En edicion se conserva el estado actual; en creacion el servicio asigna ACTIVO.
        if (original != null) {
            v.setEstado(original.getEstado());
        }

        try {
            controller.guardar(v);
            guardado = true;
            dispose();
        } catch (ValidacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ocurrio un error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** El año es opcional; si se ingresa debe ser un entero. La validacion de rango la hace el servicio. */
    private Integer parsearAnio(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(texto.trim());
        } catch (NumberFormatException ex) {
            throw new NumberFormatException("El anio debe ser un numero entero (p. ej. 2018).");
        }
    }

    /** El kilometraje es opcional; vacio equivale a 0. Debe ser numerico. */
    private double parsearKilometraje(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(texto.trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            throw new NumberFormatException("El kilometraje debe ser un numero (p. ej. 45000).");
        }
    }

    private String textoSeguro(String v) {
        return v != null ? v : "";
    }

    /** Convierte texto vacio en null (para no guardar cadenas vacias en campos opcionales). */
    private String textoONull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
