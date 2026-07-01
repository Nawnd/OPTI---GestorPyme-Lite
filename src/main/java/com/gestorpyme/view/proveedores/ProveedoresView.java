package com.gestorpyme.view.proveedores;

import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.view.clientes.TerceroFormDialog;
import com.gestorpyme.view.components.TableUtils;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Modulo "Proveedores": lista los terceros de tipo PROVEEDOR y permite crear, editar, buscar
 * (filtro) y activar/desactivar (baja logica). Hace visible y explicito el alta de proveedores,
 * que antes solo era posible como tercero. Reutiliza {@link TerceroFormDialog} fijando el tipo
 * PROVEEDOR. Solo presenta datos y delega en {@link TerceroController}; NO contiene SQL.
 * Capa: view.
 */
public class ProveedoresView extends JPanel {

    private static final String FUENTE = "Segoe UI";
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final Color COLOR_TEXTO = new Color(0x0F172A);
    private static final String[] COLUMNAS =
            {"ID", "Nombre", "Documento", "Telefono", "Correo", "Estado"};
    private static final TipoTercero[] SOLO_PROVEEDOR = {TipoTercero.PROVEEDOR};

    private final TerceroController controller = new TerceroController();
    private final DefaultTableModel modelo;
    private final JTable tabla;

    /** Copia en memoria de la lista mostrada (fila N = proveedores.get(N)). */
    private List<Tercero> proveedores = new ArrayList<>();

    public ProveedoresView() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        add(crearBarraSuperior(), BorderLayout.NORTH);

        modelo = new DefaultTableModel(COLUMNAS, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        TableUtils.estilizar(tabla);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.setRowHeight(26);
        tabla.getTableHeader().setReorderingAllowed(false);
        tabla.setFont(new Font(FUENTE, Font.PLAIN, 13));

        add(new JScrollPane(tabla), BorderLayout.CENTER);

        recargar();
    }

    private JComponent crearBarraSuperior() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);

        JLabel titulo = new JLabel("Proveedores");
        titulo.setFont(new Font(FUENTE, Font.BOLD, 20));
        titulo.setForeground(COLOR_TEXTO);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(crearBoton("Nuevo proveedor", true, e -> abrirFormulario(null)));
        botones.add(crearBoton("Editar", false, e -> editarSeleccionado()));
        botones.add(crearBoton("Activar / Desactivar", false, e -> cambiarEstadoSeleccionado()));
        botones.add(crearBoton("Refrescar", false, e -> recargar()));

        barra.add(titulo, BorderLayout.WEST);
        barra.add(botones, BorderLayout.EAST);
        return barra;
    }

    private JButton crearBoton(String texto, boolean primario, ActionListener accion) {
        JButton boton = new JButton(texto);
        boton.setFocusPainted(false);
        boton.setFont(new Font(FUENTE, primario ? Font.BOLD : Font.PLAIN, 13));
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (primario) {
            boton.setBackground(COLOR_PRIMARIO);
            boton.setForeground(Color.WHITE);
        }
        boton.addActionListener(accion);
        return boton;
    }

    /** Recarga la tabla de proveedores desde la base de datos. */
    private void recargar() {
        try {
            proveedores = controller.listarProveedores();
            modelo.setRowCount(0);
            for (Tercero t : proveedores) {
                modelo.addRow(new Object[]{
                        t.getIdTercero(),
                        t.getNombre(),
                        textoSeguro(t.getDocumento()),
                        textoSeguro(t.getTelefono()),
                        textoSeguro(t.getCorreo()),
                        t.getEstado().name()
                });
            }
        } catch (SQLException e) {
            mostrarError("No se pudieron cargar los proveedores:\n" + e.getMessage());
        }
    }

    /** Abre el formulario fijando el tipo PROVEEDOR (crear si tercero == null, o editar). */
    private void abrirFormulario(Tercero tercero) {
        TerceroFormDialog dialogo = new TerceroFormDialog(
                SwingUtilities.getWindowAncestor(this), controller, tercero, SOLO_PROVEEDOR);
        dialogo.setVisible(true);
        if (dialogo.fueGuardado()) {
            recargar();
        }
    }

    private void editarSeleccionado() {
        Tercero seleccionado = obtenerSeleccionado();
        if (seleccionado != null) {
            abrirFormulario(seleccionado);
        }
    }

    private void cambiarEstadoSeleccionado() {
        Tercero seleccionado = obtenerSeleccionado();
        if (seleccionado == null) {
            return;
        }
        EstadoRegistro nuevoEstado = seleccionado.getEstado() == EstadoRegistro.ACTIVO
                ? EstadoRegistro.INACTIVO
                : EstadoRegistro.ACTIVO;
        try {
            controller.cambiarEstado(seleccionado.getIdTercero(), nuevoEstado);
            recargar();
        } catch (SQLException e) {
            mostrarError("No se pudo cambiar el estado:\n" + e.getMessage());
        }
    }

    private Tercero obtenerSeleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un proveedor de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return proveedores.get(fila);
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
