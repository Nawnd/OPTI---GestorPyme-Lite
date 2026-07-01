package com.gestorpyme.view.clientes;
import com.gestorpyme.view.components.TableUtils;

import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Tercero;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
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
 * Panel del modulo Clientes / Prospectos. Muestra la lista en una tabla y permite
 * crear, editar y activar/desactivar (baja logica). Solo presenta datos y delega
 * en {@link TerceroController}; NO contiene reglas de negocio ni SQL.
 * Se inserta en el area de contenido del Dashboard.
 * Capa: view.
 */
public class ClientesView extends JPanel {

    private static final String FUENTE = "Segoe UI";
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final Color COLOR_TEXTO = new Color(0x0F172A);
    private static final String[] COLUMNAS =
            {"ID", "Tipo", "Nombre", "Documento", "Telefono", "Correo", "Estado"};

    private final TerceroController controller = new TerceroController();
    private final DefaultTableModel modelo;
    private final JTable tabla;

    /** Copia en memoria de la lista mostrada (la fila N corresponde a terceros.get(N)). */
    private List<Tercero> terceros = new ArrayList<>();

    public ClientesView() {
        setLayout(new BorderLayout(0, 12));
        setOpaque(false);

        add(crearBarraSuperior(), BorderLayout.NORTH);

        // Modelo de tabla de solo lectura.
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

        JLabel titulo = new JLabel("Clientes / Prospectos");
        titulo.setFont(new Font(FUENTE, Font.BOLD, 20));
        titulo.setForeground(COLOR_TEXTO);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(crearBoton("Nuevo", true, e -> abrirFormulario(null)));
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

    /** Recarga la tabla desde la base de datos. */
    private void recargar() {
        try {
            terceros = controller.listarClientesYProspectos();
            modelo.setRowCount(0);
            for (Tercero t : terceros) {
                modelo.addRow(new Object[]{
                    t.getIdTercero(),
                    t.getTipoTercero().getEtiqueta(),
                    t.getNombre(),
                    textoSeguro(t.getDocumento()),
                    textoSeguro(t.getTelefono()),
                    textoSeguro(t.getCorreo()),
                    t.getEstado().name()
                });
            }
        } catch (SQLException e) {
            mostrarError("No se pudieron cargar los registros:\n" + e.getMessage());
        }
    }

    /** Abre el formulario para crear (tercero == null) o editar un registro. */
    private void abrirFormulario(Tercero tercero) {
        TerceroFormDialog dialogo = new TerceroFormDialog(
                javax.swing.SwingUtilities.getWindowAncestor(this), controller, tercero);
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

    /** Devuelve el tercero de la fila seleccionada, o null si no hay seleccion. */
    private Tercero obtenerSeleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un registro de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return terceros.get(fila);
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
