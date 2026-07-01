package com.gestorpyme.view.cuentas;

import com.gestorpyme.controller.CuentaController;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.view.components.DatePickerField;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Formulario modal para registrar un abono a una cuenta por cobrar.
 * El valor debe ser mayor a 0 (servicio) y no superar el saldo (verificado de forma
 * atomica en el repositorio). No contiene SQL.
 */
public class AbonoFormDialog extends JDialog {

    private final CuentaController cuentaController;
    private final int idCuenta;
    private final BigDecimal saldoPendiente;

    private final JComboBox<MedioPago> cmbMedio = new JComboBox<>(MedioPago.comunes());
    private final JTextField txtValor = new JTextField();
    private final DatePickerField fecha = new DatePickerField();
    private final JTextField txtObs = new JTextField();

    private boolean guardado = false;

    public AbonoFormDialog(Window owner, CuentaController cuentaController, int idCuenta,
                           String numeroVenta, BigDecimal saldoPendiente) {
        super(owner, "Registrar abono - " + numeroVenta, ModalityType.APPLICATION_MODAL);
        this.cuentaController = cuentaController;
        this.idCuenta = idCuenta;
        this.saldoPendiente = saldoPendiente;

        fecha.setFecha(LocalDate.now());

        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setBackground(UiTheme.SUPERFICIE);
        contenedor.add(crearFormulario(numeroVenta), BorderLayout.CENTER);
        contenedor.add(crearBotones(), BorderLayout.SOUTH);
        setContentPane(contenedor);

        setSize(460, 320);
        setLocationRelativeTo(owner);
    }

    private JComponent crearFormulario(String numeroVenta) {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UiTheme.SUPERFICIE);
        form.setBorder(UiTheme.margen());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.fill = GridBagConstraints.HORIZONTAL;
        g.anchor = GridBagConstraints.WEST;

        JLabel info = new JLabel("Venta " + numeroVenta + " | Saldo pendiente: " + saldoPendiente.toPlainString());
        info.setFont(UiTheme.fuenteNegrita());
        info.setForeground(UiTheme.PRIMARIO);
        g.gridx = 0; g.gridy = 0; g.gridwidth = 2; form.add(info, g);
        g.gridwidth = 1;

        int f = 1;
        fila(form, g, f++, "Medio de pago:", cmbMedio);
        fila(form, g, f++, "Valor *:", txtValor);
        fila(form, g, f++, "Fecha:", fecha);
        fila(form, g, f++, "Observaciones:", txtObs);
        return form;
    }

    private void fila(JPanel form, GridBagConstraints g, int fila, String texto, JComponent campo) {
        g.gridx = 0; g.gridy = fila; g.weightx = 0;
        JLabel l = new JLabel(texto);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        form.add(l, g);
        g.gridx = 1; g.weightx = 1;
        campo.setFont(UiTheme.fuenteNormal());
        if (campo instanceof JTextField) {
            campo.setPreferredSize(new Dimension(220, 28));
        }
        form.add(campo, g);
    }

    private JComponent crearBotones() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, UiTheme.ESPACIO));
        barra.setBackground(UiTheme.SUPERFICIE);
        barra.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDE));
        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        btnCancelar.addActionListener(e -> dispose());
        JButton btnGuardar = UiTheme.botonPrimario(new JButton("Registrar abono"));
        btnGuardar.addActionListener(e -> guardar());
        barra.add(btnCancelar);
        barra.add(btnGuardar);
        return barra;
    }

    private void guardar() {
        BigDecimal valor;
        try {
            String t = txtValor.getText().trim().replace(",", ".");
            if (t.isEmpty()) {
                aviso("Ingrese el valor del abono.");
                return;
            }
            valor = new BigDecimal(t);
        } catch (NumberFormatException e) {
            aviso("El valor debe ser un numero valido.");
            return;
        }

        Abono abono = new Abono();
        abono.setIdCuenta(idCuenta);
        abono.setValor(valor);
        abono.setMedioPago((MedioPago) cmbMedio.getSelectedItem());
        abono.setObservaciones(txtObs.getText().trim());
        try {
            abono.setFecha(fecha.getTextoIso());
            cuentaController.registrarAbono(abono);
            guardado = true;
            JOptionPane.showMessageDialog(this, "Abono registrado correctamente.",
                    "Cuentas por cobrar", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IllegalArgumentException | ValidacionException e) {
            aviso(e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo registrar el abono:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos", JOptionPane.WARNING_MESSAGE);
    }

    public boolean isGuardado() {
        return guardado;
    }
}
