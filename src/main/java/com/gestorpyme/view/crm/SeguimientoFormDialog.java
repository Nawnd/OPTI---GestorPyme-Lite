package com.gestorpyme.view.crm;

import com.gestorpyme.controller.SeguimientoController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.domain.enums.EstadoSeguimiento;
import com.gestorpyme.domain.enums.TipoSeguimiento;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Seguimiento;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

/**
 * Formulario modal para registrar un seguimiento de CRM.
 * Captura los datos y delega validacion/persistencia en {@link SeguimientoController}.
 * No contiene reglas de negocio ni SQL (capa view).
 */
public class SeguimientoFormDialog extends JDialog {

    private final SeguimientoController seguimientoController;
    private final Integer idUsuario;

    private EntityLookupField<Tercero> lkpTercero;
    private final JComboBox<TipoSeguimiento> cmbTipo = new JComboBox<>(TipoSeguimiento.values());
    private final JComboBox<EstadoSeguimiento> cmbEstado = new JComboBox<>(EstadoSeguimiento.values());
    private final JTextArea txtDescripcion = new JTextArea(4, 20);

    private boolean guardado = false;

    public SeguimientoFormDialog(Window owner, SeguimientoController seguimientoController,
                                 TerceroController terceroController, Integer idUsuario) {
        super(owner, "Nuevo seguimiento", ModalityType.APPLICATION_MODAL);
        this.seguimientoController = seguimientoController;
        this.idUsuario = idUsuario;

        // Renderizador: muestra el nombre del tercero en vez del objeto.
        // Busqueda inteligente de cliente / prospecto (reemplaza combo largo).
        lkpTercero = new EntityLookupField<>(SearchSpecs.clientes(terceroController));

        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setBackground(UiTheme.SUPERFICIE);
        contenedor.add(crearFormulario(), BorderLayout.CENTER);
        contenedor.add(crearBotones(), BorderLayout.SOUTH);
        setContentPane(contenedor);


        setSize(480, 360);
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
        fila(form, g, f++, "Cliente / Prospecto *:", lkpTercero);
        fila(form, g, f++, "Tipo *:", cmbTipo);
        fila(form, g, f++, "Estado:", cmbEstado);

        g.gridwidth = 1;
        g.gridx = 0; g.gridy = f; g.weightx = 0; g.anchor = GridBagConstraints.NORTHWEST;
        JLabel l = new JLabel("Descripcion *:");
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        form.add(l, g);

        g.gridx = 1; g.weightx = 1; g.fill = GridBagConstraints.BOTH; g.weighty = 1;
        txtDescripcion.setLineWrap(true);
        txtDescripcion.setWrapStyleWord(true);
        txtDescripcion.setFont(UiTheme.fuenteNormal());
        JScrollPane sp = new JScrollPane(txtDescripcion);
        sp.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        form.add(sp, g);

        return form;
    }

    private void fila(JPanel form, GridBagConstraints g, int fila, String texto, JComponent campo) {
        g.gridwidth = 1; g.fill = GridBagConstraints.HORIZONTAL; g.weighty = 0;
        g.gridx = 0; g.gridy = fila; g.weightx = 0; g.anchor = GridBagConstraints.WEST;
        JLabel l = new JLabel(texto);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        form.add(l, g);

        g.gridx = 1; g.weightx = 1;
        campo.setFont(UiTheme.fuenteNormal());
        campo.setPreferredSize(new Dimension(260, 28));
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

    /** Carga los clientes y prospectos en el combo. */
    /** Arma el seguimiento con los datos del formulario y lo registra. */
    private void guardar() {
        Tercero tercero = lkpTercero.getSeleccionado();
        if (tercero == null) {
            aviso("Seleccione un cliente o prospecto. Registre uno en 'Clientes / Prospectos' si no existe.");
            return;
        }

        Seguimiento s = new Seguimiento();
        s.setIdTercero(tercero.getIdTercero());
        s.setTipo((TipoSeguimiento) cmbTipo.getSelectedItem());
        s.setEstado((EstadoSeguimiento) cmbEstado.getSelectedItem());
        s.setDescripcion(txtDescripcion.getText().trim());
        s.setIdUsuario(idUsuario);

        try {
            seguimientoController.registrar(s);
            guardado = true;
            dispose();
        } catch (ValidacionException e) {
            aviso(e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo registrar:\n" + e.getMessage(),
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
