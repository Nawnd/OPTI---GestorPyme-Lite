package com.gestorpyme.view.crm;

import com.gestorpyme.controller.SeguimientoController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.domain.enums.EstadoSeguimiento;
import com.gestorpyme.domain.model.Seguimiento;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista del modulo CRM basico (seguimientos de clientes y prospectos).
 *
 * Lista los seguimientos en una tabla estilizada y permite registrar uno nuevo
 * y marcar un seguimiento como CERRADO. La vista NO ejecuta SQL.
 */
public class CrmView extends JPanel {

    private final SeguimientoController seguimientoController = new SeguimientoController();
    private final TerceroController terceroController = new TerceroController();

    private final Integer idUsuario;

    private final DefaultTableModel modeloTabla;
    private final JTable tabla;

    // Cada fila de la tabla corresponde al seguimiento del mismo indice.
    private List<Seguimiento> seguimientos = new ArrayList<>();

    /**
     * @param idUsuario id del usuario en sesion (autor del seguimiento; puede ser null).
     */
    public CrmView(Integer idUsuario) {
        this.idUsuario = idUsuario;

        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        modeloTabla = new DefaultTableModel(
                new Object[]{"ID", "Fecha", "Cliente / Prospecto", "Tipo", "Estado", "Descripcion"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modeloTabla);
        TableUtils.estilizar(tabla);
        TableUtils.anchos(tabla, 50, 140, 200, 110, 110, 300);

        JPanel norte = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        norte.setOpaque(false);
        norte.add(crearTitulo(), BorderLayout.NORTH);
        norte.add(crearBarraBotones(), BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        cargar();
    }

    private JComponent crearTitulo() {
        JLabel titulo = new JLabel("CRM - Seguimientos");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        return titulo;
    }

    private JComponent crearBarraBotones() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        barra.setOpaque(false);

        JButton btnNuevo = UiTheme.botonPrimario(new JButton("Nuevo seguimiento"));
        btnNuevo.addActionListener(e -> abrirFormularioNuevo());

        JButton btnCerrar = UiTheme.botonSecundario(new JButton("Marcar cerrado"));
        btnCerrar.addActionListener(e -> marcarCerrado());

        JButton btnRefrescar = UiTheme.botonSecundario(new JButton("Refrescar"));
        btnRefrescar.addActionListener(e -> cargar());

        barra.add(btnNuevo);
        barra.add(btnCerrar);
        barra.add(btnRefrescar);
        return barra;
    }

    /** Carga los seguimientos en la tabla. */
    private void cargar() {
        try {
            seguimientos = seguimientoController.listar();
            modeloTabla.setRowCount(0);
            for (Seguimiento s : seguimientos) {
                modeloTabla.addRow(new Object[]{
                        s.getIdSeguimiento(),
                        s.getFecha(),
                        s.getNombreTercero(),
                        s.getTipo(),
                        s.getEstado(),
                        s.getDescripcion()
                });
            }
        } catch (Exception ex) {
            error("No se pudieron cargar los seguimientos: " + ex.getMessage());
        }
    }

    private void abrirFormularioNuevo() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        SeguimientoFormDialog dialog =
                new SeguimientoFormDialog(owner, seguimientoController, terceroController, idUsuario);
        dialog.setVisible(true);
        if (dialog.isGuardado()) {
            cargar();
        }
    }

    /** Marca el seguimiento seleccionado como CERRADO. */
    private void marcarCerrado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione un seguimiento en la tabla.");
            return;
        }
        Seguimiento s = seguimientos.get(fila);
        try {
            seguimientoController.cambiarEstado(s.getIdSeguimiento(), EstadoSeguimiento.CERRADO);
            cargar();
        } catch (Exception ex) {
            error("No se pudo cambiar el estado: " + ex.getMessage());
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "CRM", JOptionPane.WARNING_MESSAGE);
    }
}
