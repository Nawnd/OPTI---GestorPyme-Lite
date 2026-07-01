package com.gestorpyme.view.inventario;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.LoteController;
import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;
import com.gestorpyme.view.components.DatePickerField;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;

/**
 * Formulario modal para registrar un lote nuevo.
 * Captura los datos y delega la validacion/persistencia en {@link LoteController}.
 * Las fechas usan {@link DatePickerField} (manual dd/MM/yyyy o selector visual) y se
 * convierten a ISO (yyyy-MM-dd) para la base de datos. No contiene SQL (capa view).
 */
public class LoteFormDialog extends JDialog {

    private final LoteController loteController;

    private EntityLookupField<Item> lkpItem;
    private EntityLookupField<Bodega> lkpBodega;
    private final JTextField txtNumero = new JTextField();
    private final JTextField txtCantidad = new JTextField();
    private final DatePickerField fechaIngreso = new DatePickerField();
    private final DatePickerField fechaVenc = new DatePickerField();
    private final JComboBox<EstadoLote> cmbEstado = new JComboBox<>(EstadoLote.values());

    private boolean guardado = false;

    public LoteFormDialog(Window owner, LoteController loteController,
                          ItemController itemController, BodegaController bodegaController) {
        super(owner, "Nuevo lote", ModalityType.APPLICATION_MODAL);
        this.loteController = loteController;

        // Busqueda inteligente para item y bodega (reemplazan combos largos).
        lkpItem = new EntityLookupField<>(SearchSpecs.items(itemController));
        lkpBodega = new EntityLookupField<>(SearchSpecs.bodegas(bodegaController));

        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setBackground(UiTheme.SUPERFICIE);
        contenedor.add(crearFormulario(), BorderLayout.CENTER);
        contenedor.add(crearBotones(), BorderLayout.SOUTH);
        setContentPane(contenedor);


        setSize(480, 400);
        setLocationRelativeTo(owner);
    }

    private JComponent crearFormulario() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SUPERFICIE);
        form.setBorder(UiTheme.margen());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        int f = 0;
        fila(form, g, f++, "Item *:", lkpItem);
        fila(form, g, f++, "Bodega:", lkpBodega);
        fila(form, g, f++, "Numero de lote *:", txtNumero);
        fila(form, g, f++, "Cantidad inicial:", txtCantidad);
        fila(form, g, f++, "Fecha ingreso:", fechaIngreso);
        fila(form, g, f++, "Fecha vencimiento:", fechaVenc);
        fila(form, g, f++, "Estado:", cmbEstado);

        JLabel hint = new JLabel("Fechas en dd/MM/yyyy (opcionales). Si no indica ingreso, se usa hoy.");
        hint.setFont(UiTheme.fuentePequena());
        hint.setForeground(UiTheme.TEXTO_TENUE);
        g.gridx = 0; g.gridy = f; g.gridwidth = 2;
        form.add(hint, g);

        return form;
    }

    private void fila(JPanel form, GridBagConstraints g, int fila, String texto, JComponent campo) {
        g.gridwidth = 1;
        g.gridx = 0; g.gridy = fila; g.weightx = 0;
        JLabel l = new JLabel(texto);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        form.add(l, g);

        g.gridx = 1; g.weightx = 1;
        campo.setFont(UiTheme.fuenteNormal());
        if (campo instanceof JTextField) {
            campo.setPreferredSize(new Dimension(240, 28));
        }
        form.add(campo, g);
    }

    private JComponent crearBotones() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, UiTheme.ESPACIO));
        barra.setBackground(UiTheme.SUPERFICIE);
        barra.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDE));

        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        btnCancelar.addActionListener(e -> dispose());
        JButton btnGuardar = UiTheme.botonPrimario(new JButton("Guardar"));
        btnGuardar.addActionListener(e -> guardar());

        barra.add(btnCancelar);
        barra.add(btnGuardar);
        return barra;
    }


    /** Toma los datos, los arma en un Lote y delega en el controlador. */
    private void guardar() {
        Item item = lkpItem.getSeleccionado();
        if (item == null) {
            aviso("Seleccione un item.");
            return;
        }
        Bodega bodega = lkpBodega.getSeleccionado();

        Lote lote = new Lote();
        lote.setIdItem(item.getIdItem());
        lote.setIdBodega((bodega == null || bodega.getIdBodega() == 0) ? null : bodega.getIdBodega());
        lote.setNumeroLote(txtNumero.getText().trim());
        lote.setEstado((EstadoLote) cmbEstado.getSelectedItem());

        try {
            // Conversion de fechas (dd/MM/yyyy -> ISO). Lanzan si el texto es invalido.
            lote.setFechaIngreso(fechaIngreso.getTextoIso());
            lote.setFechaVencimiento(fechaVenc.getTextoIso());
            lote.setCantidadInicial(parsearCantidad(txtCantidad.getText()));
            loteController.registrar(lote);
            guardado = true;
            dispose();
        } catch (IllegalArgumentException e) {
            aviso(e.getMessage());
        } catch (ValidacionException e) {
            aviso(e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo registrar:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Convierte el texto a BigDecimal aceptando coma o punto; vacio = 0. */
    private BigDecimal parsearCantidad(String texto) {
        String limpio = texto.trim().replace(",", ".");
        if (limpio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw new ValidacionException("La cantidad inicial debe ser un numero valido.");
        }
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos", JOptionPane.WARNING_MESSAGE);
    }

    public boolean isGuardado() {
        return guardado;
    }
}
