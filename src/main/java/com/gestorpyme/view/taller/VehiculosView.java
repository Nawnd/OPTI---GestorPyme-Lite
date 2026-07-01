package com.gestorpyme.view.taller;

import com.gestorpyme.controller.VehiculoController;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Vehiculo;
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
 * Modulo "Vehiculos": lista los vehiculos por cliente y permite crear, editar y activar/inactivar.
 * Base del futuro modulo Taller / Orden de trabajo (Paso U.1).
 *
 * Capa: view (Swing). No ejecuta SQL ni contiene reglas de negocio (delega en
 * {@link VehiculoController}).
 */
public class VehiculosView extends JPanel {

    private static final String FUENTE = "Segoe UI";
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final Color COLOR_TEXTO = new Color(0x0F172A);
    private static final String[] COLUMNAS =
            {"ID", "Placa", "Cliente", "Marca", "Linea", "Anio", "Color", "Kilometraje", "Estado"};

    private final VehiculoController controller = new VehiculoController();
    private final DefaultTableModel modelo;
    private final JTable tabla;

    /** Copia en memoria de la lista mostrada (fila N = vehiculos.get(N)). */
    private List<Vehiculo> vehiculos = new ArrayList<>();

    public VehiculosView() {
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

        JLabel titulo = new JLabel("Vehiculos");
        titulo.setFont(new Font(FUENTE, Font.BOLD, 20));
        titulo.setForeground(COLOR_TEXTO);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(crearBoton("Nuevo vehiculo", true, e -> abrirFormulario(null)));
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

    /** Recarga la tabla de vehiculos desde la base de datos. */
    private void recargar() {
        try {
            vehiculos = controller.listar();
            modelo.setRowCount(0);
            for (Vehiculo v : vehiculos) {
                modelo.addRow(new Object[]{
                        v.getIdVehiculo(),
                        v.getPlaca(),
                        textoSeguro(v.getNombreCliente()),
                        textoSeguro(v.getMarca()),
                        textoSeguro(v.getLinea()),
                        v.getAnio() == null ? "" : v.getAnio(),
                        textoSeguro(v.getColor()),
                        formatoKilometraje(v.getKilometraje()),
                        v.getEstado().name()
                });
            }
        } catch (SQLException e) {
            mostrarError("No se pudieron cargar los vehiculos:\n" + e.getMessage());
        }
    }

    /** Abre el formulario (crear si vehiculo == null, o editar) y recarga si se guardo. */
    private void abrirFormulario(Vehiculo vehiculo) {
        VehiculoFormDialog dialogo = new VehiculoFormDialog(
                SwingUtilities.getWindowAncestor(this), controller, vehiculo);
        dialogo.setVisible(true);
        if (dialogo.fueGuardado()) {
            recargar();
        }
    }

    private void editarSeleccionado() {
        Vehiculo seleccionado = obtenerSeleccionado();
        if (seleccionado != null) {
            abrirFormulario(seleccionado);
        }
    }

    private void cambiarEstadoSeleccionado() {
        Vehiculo seleccionado = obtenerSeleccionado();
        if (seleccionado == null) {
            return;
        }
        EstadoRegistro nuevoEstado = seleccionado.getEstado() == EstadoRegistro.ACTIVO
                ? EstadoRegistro.INACTIVO
                : EstadoRegistro.ACTIVO;
        try {
            controller.cambiarEstado(seleccionado.getIdVehiculo(), nuevoEstado);
            recargar();
        } catch (SQLException e) {
            mostrarError("No se pudo cambiar el estado:\n" + e.getMessage());
        }
    }

    private Vehiculo obtenerSeleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un vehiculo de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return vehiculos.get(fila);
    }

    /** Muestra el kilometraje sin decimales (los odometros se manejan en enteros). */
    private String formatoKilometraje(double km) {
        return String.format("%.0f", km);
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
