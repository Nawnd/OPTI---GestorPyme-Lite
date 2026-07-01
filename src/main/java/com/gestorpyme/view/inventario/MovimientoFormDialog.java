package com.gestorpyme.view.inventario;

import com.gestorpyme.controller.InventarioController;
import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Formulario modal para registrar un movimiento de inventario (entrada, salida o
 * ajuste). Captura los datos y delega la validacion y el registro transaccional en
 * {@link InventarioController}. No contiene reglas de negocio ni SQL.
 * Capa: view.
 */
public class MovimientoFormDialog extends JDialog {

    private static final String FUENTE = "Segoe UI";
    /** Tipos que puede registrar manualmente el usuario (los demas son del sistema). */
    private static final TipoMovimiento[] TIPOS_MANUALES = {
        TipoMovimiento.ENTRADA, TipoMovimiento.SALIDA,
        TipoMovimiento.AJUSTE_POSITIVO, TipoMovimiento.AJUSTE_NEGATIVO
    };

    private final InventarioController controller;
    private final Integer idUsuario;

    private final JComboBox<Item> comboItem = new JComboBox<>();
    private final JComboBox<Bodega> comboBodega = new JComboBox<>();
    private final JComboBox<TipoMovimiento> comboTipo = new JComboBox<>(TIPOS_MANUALES);
    private final JTextField campoCantidad = new JTextField(18);
    private final JTextField campoMotivo = new JTextField(18);

    private boolean guardado = false;

    public MovimientoFormDialog(Window propietario, InventarioController controller, Integer idUsuario) {
        super(propietario, ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.idUsuario = idUsuario;
        setTitle("Registrar movimiento de inventario");
        construir();
        cargarCombos();
        pack();
        setLocationRelativeTo(propietario);
    }

    public boolean fueGuardado() {
        return guardado;
    }

    private void construir() {
        // Renderizadores reutilizables: muestran texto amigable (codigo - nombre / nombre).
        comboItem.setRenderer(com.gestorpyme.view.components.ComboBoxRenderers.amigable());
        comboBodega.setRenderer(com.gestorpyme.view.components.ComboBoxRenderers.amigable());

        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarFila(formulario, g, fila++, "Item *", comboItem);
        agregarFila(formulario, g, fila++, "Bodega *", comboBodega);
        agregarFila(formulario, g, fila++, "Movimiento *", comboTipo);
        agregarFila(formulario, g, fila++, "Cantidad *", campoCantidad);
        agregarFila(formulario, g, fila, "Motivo", campoMotivo);

        JButton botonCancelar = com.gestorpyme.view.components.UiTheme.botonSecundario(new JButton("Cancelar"));
        botonCancelar.addActionListener(e -> dispose());
        JButton botonGuardar = com.gestorpyme.view.components.UiTheme.botonPrimario(new JButton("Registrar"));
        botonGuardar.addActionListener(e -> registrar());

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

    /** Carga los selectores de items inventariables y bodegas activas. */
    private void cargarCombos() {
        try {
            List<Item> items = controller.itemsInventariables();
            for (Item item : items) {
                comboItem.addItem(item);
            }
            List<Bodega> bodegas = controller.bodegasActivas();
            for (Bodega bodega : bodegas) {
                comboBodega.addItem(bodega);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar los datos:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void registrar() {
        Item item = (Item) comboItem.getSelectedItem();
        Bodega bodega = (Bodega) comboBodega.getSelectedItem();
        if (item == null) {
            mostrarAviso("Seleccione un item. Debe ser un producto que controle inventario y este activo.");
            return;
        }
        if (bodega == null) {
            mostrarAviso("Seleccione una bodega. Cree una en la pestana 'Bodegas' si no existe.");
            return;
        }
        TipoMovimiento tipo = (TipoMovimiento) comboTipo.getSelectedItem();

        try {
            BigDecimal cantidad = parsearCantidad(campoCantidad.getText());
            controller.registrarMovimiento(item.getIdItem(), bodega.getIdBodega(), tipo,
                    cantidad, vacioANull(campoMotivo.getText()), idUsuario);
            guardado = true;
            dispose();
        } catch (ValidacionException e) {
            mostrarAviso(e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo registrar:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private BigDecimal parsearCantidad(String texto) {
        String limpio = texto.trim().replace(",", ".");
        if (limpio.isEmpty()) {
            throw new ValidacionException("La cantidad es obligatoria.");
        }
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw new ValidacionException("La cantidad debe ser un numero valido.");
        }
    }

    private void mostrarAviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos", JOptionPane.WARNING_MESSAGE);
    }

    private String vacioANull(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
