package com.gestorpyme.view.taller;

import com.gestorpyme.controller.OrdenTrabajoController;
import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajo;
import com.gestorpyme.util.MoneyFormatter;
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
import java.util.Optional;

/**
 * Modulo "Ordenes de trabajo" (Paso U.2): lista las OT y permite crear, editar/ver detalle, cambiar
 * estado y cancelar. La OT es un documento de trabajo: aqui no se factura ni se descuenta inventario
 * (el cierre a venta es U.3, por eso el boton correspondiente esta deshabilitado en el formulario).
 *
 * Capa: view (Swing). No ejecuta SQL ni contiene reglas de negocio (delega en
 * {@link OrdenTrabajoController}).
 */
public class OrdenesTrabajoView extends JPanel {

    private static final String FUENTE = "Segoe UI";
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final Color COLOR_TEXTO = new Color(0x0F172A);
    private static final String[] COLUMNAS =
            {"Numero OT", "Cliente", "Vehiculo", "Fecha ingreso", "Estado", "Total", "Motivo"};
    /** Estados operativos seleccionables desde "Cambiar estado" (ENTREGADA es U.3; CANCELADA tiene su boton). */
    private static final EstadoOrdenTrabajo[] ESTADOS_OPERATIVOS = {
            EstadoOrdenTrabajo.ABIERTA, EstadoOrdenTrabajo.DIAGNOSTICO, EstadoOrdenTrabajo.APROBADA,
            EstadoOrdenTrabajo.EN_PROCESO, EstadoOrdenTrabajo.TERMINADA
    };

    private final OrdenTrabajoController controller = new OrdenTrabajoController();
    private final DefaultTableModel modelo;
    private final JTable tabla;

    /** Copia en memoria de la lista mostrada (fila N = ordenes.get(N)). */
    private List<OrdenTrabajo> ordenes = new ArrayList<>();

    public OrdenesTrabajoView() {
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

        JLabel titulo = new JLabel("Ordenes de trabajo");
        titulo.setFont(new Font(FUENTE, Font.BOLD, 20));
        titulo.setForeground(COLOR_TEXTO);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(crearBoton("Nueva OT", true, e -> abrirFormulario(null)));
        botones.add(crearBoton("Editar / Ver detalle", false, e -> editarSeleccionada()));
        botones.add(crearBoton("Cambiar estado", false, e -> cambiarEstadoSeleccionada()));
        botones.add(crearBoton("Cancelar OT", false, e -> cancelarSeleccionada()));
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

    /** Recarga la tabla de OT desde la base de datos. */
    private void recargar() {
        try {
            ordenes = controller.listar();
            modelo.setRowCount(0);
            for (OrdenTrabajo ot : ordenes) {
                modelo.addRow(new Object[]{
                        ot.getNumeroOt(),
                        textoSeguro(ot.getNombreCliente()),
                        textoSeguro(ot.getPlacaVehiculo()),
                        soloFecha(ot.getFechaIngreso()),
                        ot.getEstado() == null ? "" : ot.getEstado().getEtiqueta(),
                        MoneyFormatter.cop(ot.getTotal()),
                        textoSeguro(ot.getMotivoIngreso())
                });
            }
        } catch (SQLException e) {
            mostrarError("No se pudieron cargar las ordenes de trabajo:\n" + e.getMessage());
        }
    }

    /** Abre el formulario (crear si ot == null, o editar cargando su detalle) y recarga si se guardo. */
    private void abrirFormulario(OrdenTrabajo cabecera) {
        OrdenTrabajo completa = null;
        if (cabecera != null) {
            try {
                Optional<OrdenTrabajo> cargada = controller.buscarConDetalles(cabecera.getIdOrdenTrabajo());
                if (cargada.isEmpty()) {
                    mostrarError("La orden de trabajo ya no existe.");
                    recargar();
                    return;
                }
                completa = cargada.get();
            } catch (SQLException e) {
                mostrarError("No se pudo cargar la orden de trabajo:\n" + e.getMessage());
                return;
            }
        }
        OrdenTrabajoFormDialog dialogo = new OrdenTrabajoFormDialog(
                SwingUtilities.getWindowAncestor(this), controller, completa);
        dialogo.setVisible(true);
        if (dialogo.fueGuardado()) {
            recargar();
        }
    }

    private void editarSeleccionada() {
        OrdenTrabajo seleccionada = obtenerSeleccionada();
        if (seleccionada != null) {
            abrirFormulario(seleccionada);
        }
    }

    private void cambiarEstadoSeleccionada() {
        OrdenTrabajo seleccionada = obtenerSeleccionada();
        if (seleccionada == null) {
            return;
        }
        EstadoOrdenTrabajo nuevo = (EstadoOrdenTrabajo) JOptionPane.showInputDialog(
                this, "Nuevo estado para " + seleccionada.getNumeroOt() + ":", "Cambiar estado",
                JOptionPane.QUESTION_MESSAGE, null, ESTADOS_OPERATIVOS, seleccionada.getEstado());
        if (nuevo == null) {
            return;
        }
        try {
            controller.cambiarEstado(seleccionada.getIdOrdenTrabajo(), nuevo);
            recargar();
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
    }

    private void cancelarSeleccionada() {
        OrdenTrabajo seleccionada = obtenerSeleccionada();
        if (seleccionada == null) {
            return;
        }
        int r = JOptionPane.showConfirmDialog(this,
                "Cancelar la orden " + seleccionada.getNumeroOt() + "?\n"
                        + "Una OT cancelada no puede editarse.",
                "Confirmar cancelacion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (r != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            controller.cambiarEstado(seleccionada.getIdOrdenTrabajo(), EstadoOrdenTrabajo.CANCELADA);
            recargar();
        } catch (Exception e) {
            mostrarError(e.getMessage());
        }
    }

    private OrdenTrabajo obtenerSeleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione una orden de la tabla.",
                    "Sin seleccion", JOptionPane.INFORMATION_MESSAGE);
            return null;
        }
        return ordenes.get(fila);
    }

    /** Muestra solo la parte de fecha (yyyy-MM-dd) de un timestamp ISO. */
    private String soloFecha(String iso) {
        if (iso == null) {
            return "";
        }
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
