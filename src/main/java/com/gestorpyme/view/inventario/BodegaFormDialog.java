package com.gestorpyme.view.inventario;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;

/**
 * Formulario modal para crear o editar una bodega.
 * Captura los datos y delega la validacion y el guardado en {@link BodegaController}.
 * No contiene reglas de negocio ni SQL.
 * Capa: view.
 */
public class BodegaFormDialog extends JDialog {

    private static final String FUENTE = "Segoe UI";

    private final BodegaController controller;
    private final Bodega bodegaEnEdicion; // null = nueva

    private final JTextField campoNombre = new JTextField(22);
    private final JTextField campoUbicacion = new JTextField(22);

    private boolean guardado = false;

    public BodegaFormDialog(Window propietario, BodegaController controller, Bodega bodega) {
        super(propietario, ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.bodegaEnEdicion = bodega;
        setTitle(bodega == null ? "Nueva bodega" : "Editar bodega");
        construir();
        if (bodega != null) {
            campoNombre.setText(textoSeguro(bodega.getNombre()));
            campoUbicacion.setText(textoSeguro(bodega.getUbicacion()));
        }
        pack();
        setLocationRelativeTo(propietario);
    }

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

        agregarFila(formulario, g, 0, "Nombre *", campoNombre);
        agregarFila(formulario, g, 1, "Ubicacion", campoUbicacion);

        JButton botonCancelar = new JButton("Cancelar");
        botonCancelar.addActionListener(e -> dispose());
        JButton botonGuardar = new JButton("Guardar");
        botonGuardar.addActionListener(e -> guardar());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
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

    private void guardar() {
        Bodega bodega = (bodegaEnEdicion != null) ? bodegaEnEdicion : new Bodega();
        bodega.setNombre(campoNombre.getText().trim());
        bodega.setUbicacion(vacioANull(campoUbicacion.getText()));

        try {
            controller.guardar(bodega);
            guardado = true;
            dispose();
        } catch (ValidacionException e) {
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

    private String vacioANull(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
