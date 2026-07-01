package com.gestorpyme.view.inventario;
import com.gestorpyme.view.components.TableUtils;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Bodega;

import javax.swing.JButton;
import javax.swing.JComponent;
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
 * Panel CRUD de bodegas. Lista las bodegas y permite crear, editar y
 * activar/desactivar (baja logica). Se muestra como una pestaña dentro del
 * modulo "Bodegas e inventario". NO contiene reglas de negocio ni SQL.
 * Capa: view.
 */
public class BodegasView extends JPanel {

    private static final String FUENTE = "Segoe UI";
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final String[] COLUMNAS = {"ID", "Nombre", "Ubicacion", "Estado"};

    private final BodegaController controller = new BodegaController();
    private final DefaultTableModel modelo;
    private final JTable tabla;
    private List<Bodega> bodegas = new ArrayList<>();

    public BodegasView() {
        setLayout(new BorderLayout(0, 10));
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
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        botones.setOpaque(false);
        botones.add(crearBoton("Nueva bodega", true, e -> abrirFormulario(null)));
        botones.add(crearBoton("Editar", false, e -> editarSeleccionada()));
        botones.add(crearBoton("Activar / Desactivar", false, e -> cambiarEstadoSeleccionada()));
        botones.add(crearBoton("Refrescar", false, e -> recargar()));
        return botones;
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
            bodegas = controller.listar();
            modelo.setRowCount(0);
            for (Bodega b : bodegas) {
                modelo.addRow(new Object[]{
                    b.getIdBodega(), b.getNombre(), textoSeguro(b.getUbicacion()), b.getEstado().name()
                });
            }
        } catch (SQLException e) {
            mostrarError("No se pudieron cargar las bodegas:\n" + e.getMessage());
        }
    }

    private void abrirFormulario(Bodega bodega) {
        BodegaFormDialog dialogo = new BodegaFormDialog(
                SwingUtilities.getWindowAncestor(this), controller, bodega);
        dialogo.setVisible(true);
        if (dialogo.fueGuardado()) {
            recargar();
        }
    }

    private void editarSeleccionada() {
        Bodega seleccionada = obtenerSeleccionada();
        if (seleccionada != null) {
            abrirFormulario(seleccionada);
        }
    }

    private void cambiarEstadoSeleccionada() {
        Bodega seleccionada = obtenerSeleccionada();
        if (seleccionada == null) {
            return;
        }
        EstadoRegistro nuevoEstado = seleccionada.getEstado() == EstadoRegistro.ACTIVO
                ? EstadoRegistro.INACTIVO
                : EstadoRegistro.ACTIVO;
        try {
            controller.cambiarEstado(seleccionada.getIdBodega(), nuevoEstado);
            recargar();
        } catch (SQLException e) {
            mostrarError("No se pudo cambiar el estado:\n" + e.getMessage());
        }
    }

    private Bodega obtenerSeleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione una bodega de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return bodegas.get(fila);
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
