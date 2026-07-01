package com.gestorpyme.view.compras;

import com.gestorpyme.controller.OrdenCompraController;
import com.gestorpyme.domain.enums.EstadoOrdenCompra;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Modulo "Compras". Lista las ordenes de compra y, al seleccionar una, muestra su detalle por linea
 * (item, bodega, solicitada, recibida, pendiente, precio y subtotal). Permite crear una orden, ver el
 * detalle, recibir mercancia (parcial o total) y cancelar; ademas muestra indicadores rapidos
 * (total / pendientes / parciales / recibidas).
 *
 * Capa: view. Solo presenta datos y delega en {@link OrdenCompraController}; NO contiene reglas de
 * negocio ni SQL. Reutiliza los metodos de lectura existentes ({@code listar}, {@code buscarConDetalles})
 * y el calculo {@code OrdenCompraDetalle.getPendiente()}; no modifica la logica de recepcion/lotes/Kardex.
 */
public class ComprasView extends JPanel {

    private static final long serialVersionUID = 1L;

    private final OrdenCompraController controller = new OrdenCompraController();
    private final Integer idUsuario;

    /** Tabla de cabeceras de orden. */
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Numero", "Proveedor", "Fecha", "Fecha estimada", "Estado", "Total", "Observaciones"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);

    /** Tabla de detalle (lineas) de la orden seleccionada. */
    private final DefaultTableModel modeloDetalle = new DefaultTableModel(
            new String[]{"Item", "Bodega", "Solicitada", "Recibida", "Pendiente", "Precio", "Subtotal"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaDetalle = new JTable(modeloDetalle);
    private final JLabel lblDetalle = new JLabel();

    /** Indicadores rapidos. */
    private final JLabel lblTotal = new JLabel();
    private final JLabel lblPendientes = new JLabel();
    private final JLabel lblParciales = new JLabel();
    private final JLabel lblRecibidas = new JLabel();

    private final List<OrdenCompra> ordenes = new ArrayList<>();
    /** Evita recargar el detalle por los eventos de seleccion que dispara la recarga de la tabla. */
    private boolean cargando = false;

    public ComprasView(Integer idUsuario) {
        this.idUsuario = idUsuario;
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        add(crearEncabezado(), BorderLayout.NORTH);
        add(crearCentro(), BorderLayout.CENTER);

        cargar();
    }

    /** Encabezado: titulo, botonera e indicadores. */
    private JComponent crearEncabezado() {
        JPanel cont = new JPanel(new BorderLayout(0, 8));
        cont.setOpaque(false);

        JLabel titulo = new JLabel("Compras / Ordenes de compra");
        titulo.setFont(UiTheme.fuenteTitulo());

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barra.setOpaque(false);
        JButton btnNueva = UiTheme.botonPrimario(new JButton("Nueva orden"));
        btnNueva.addActionListener(e -> nuevaOrden());
        JButton btnDetalle = UiTheme.botonSecundario(new JButton("Ver detalle"));
        btnDetalle.addActionListener(e -> verDetalle());
        JButton btnRecibir = UiTheme.botonSecundario(new JButton("Recibir mercancia"));
        btnRecibir.addActionListener(e -> recibir());
        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar orden"));
        btnCancelar.addActionListener(e -> cancelar());
        JButton btnRefrescar = UiTheme.botonSecundario(new JButton("Refrescar"));
        btnRefrescar.addActionListener(e -> cargar());
        barra.add(btnNueva);
        barra.add(btnDetalle);
        barra.add(btnRecibir);
        barra.add(btnCancelar);
        barra.add(btnRefrescar);

        JPanel indicadores = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        indicadores.setOpaque(false);
        for (JLabel l : new JLabel[]{lblTotal, lblPendientes, lblParciales, lblRecibidas}) {
            l.setFont(UiTheme.fuentePequena());
            l.setForeground(UiTheme.TEXTO_TENUE);
            indicadores.add(l);
        }

        JPanel inferior = new JPanel(new BorderLayout(0, 6));
        inferior.setOpaque(false);
        inferior.add(barra, BorderLayout.NORTH);
        inferior.add(indicadores, BorderLayout.SOUTH);

        cont.add(titulo, BorderLayout.NORTH);
        cont.add(inferior, BorderLayout.SOUTH);
        return cont;
    }

    /** Centro: tabla de ordenes (arriba) y panel de detalle (abajo) en un divisor vertical. */
    private JComponent crearCentro() {
        TableUtils.estilizar(tabla);
        tabla.setRowHeight(26);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabla.getTableHeader().setReorderingAllowed(false);
        TableUtils.alinearDerecha(tabla, 5); // Total
        // Al cambiar la seleccion, se carga el detalle de la orden (ignorando eventos durante la recarga).
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onSeleccionCambio();
            }
        });

        TableUtils.estilizar(tablaDetalle);
        tablaDetalle.setRowHeight(24);
        tablaDetalle.getTableHeader().setReorderingAllowed(false);
        TableUtils.alinearDerecha(tablaDetalle, 2, 3, 4, 5, 6); // cantidades, precio y subtotal

        lblDetalle.setFont(UiTheme.fuenteSubtitulo());
        lblDetalle.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

        JPanel panelDetalle = new JPanel(new BorderLayout(0, 4));
        panelDetalle.setOpaque(false);
        panelDetalle.add(lblDetalle, BorderLayout.NORTH);
        panelDetalle.add(new JScrollPane(tablaDetalle), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(tabla), panelDetalle);
        split.setOpaque(false);
        split.setBorder(null);
        split.setResizeWeight(0.62);
        split.setDividerSize(8);

        limpiarDetalle();
        return split;
    }

    /** Carga la lista de ordenes desde el controlador y actualiza los indicadores. */
    private void cargar() {
        cargando = true;
        try {
            List<OrdenCompra> lista = controller.listar();
            ordenes.clear();
            ordenes.addAll(lista);
            modelo.setRowCount(0);
            for (OrdenCompra o : lista) {
                modelo.addRow(new Object[]{
                        o.getNumeroOrden(),
                        o.getNombreProveedor(),
                        o.getFechaOrden(),
                        textoOVacio(o.getFechaEstimada()),
                        o.getEstado().getEtiqueta(),
                        MoneyFormatter.cop(o.getTotal()),
                        resumir(o.getObservaciones(), 40)
                });
            }
            actualizarIndicadores();
        } catch (SQLException ex) {
            error("No se pudieron cargar las ordenes:\n" + ex.getMessage());
        } finally {
            cargando = false;
        }
        limpiarDetalle();
    }

    /** Cuenta ordenes por estado para los indicadores rapidos. */
    private void actualizarIndicadores() {
        int pend = 0, parc = 0, recib = 0;
        for (OrdenCompra o : ordenes) {
            EstadoOrdenCompra est = o.getEstado();
            if (est == EstadoOrdenCompra.EMITIDA) {
                pend++;
            } else if (est == EstadoOrdenCompra.PARCIALMENTE_RECIBIDA) {
                parc++;
            } else if (est == EstadoOrdenCompra.RECIBIDA) {
                recib++;
            }
        }
        lblTotal.setText("Total: " + ordenes.size());
        lblPendientes.setText("Pendientes: " + pend);
        lblParciales.setText("Parciales: " + parc);
        lblRecibidas.setText("Recibidas: " + recib);
    }

    /** Reaccion a un cambio de seleccion: muestra el detalle o lo limpia si no hay seleccion. */
    private void onSeleccionCambio() {
        if (cargando) {
            return;
        }
        int fila = tabla.getSelectedRow();
        if (fila >= 0 && fila < ordenes.size()) {
            mostrarDetalle(ordenes.get(fila));
        } else {
            limpiarDetalle();
        }
    }

    /** Carga en la tabla inferior las lineas de la orden indicada (relee para datos frescos). */
    private void mostrarDetalle(OrdenCompra base) {
        modeloDetalle.setRowCount(0);
        try {
            Optional<OrdenCompra> opt = controller.buscarConDetalles(base.getIdOrden());
            if (opt.isEmpty()) {
                lblDetalle.setText("Detalle de la orden: (no encontrada)");
                return;
            }
            OrdenCompra o = opt.get();
            lblDetalle.setText("Detalle de la orden " + o.getNumeroOrden()
                    + "  -  " + o.getEstado().getEtiqueta());
            for (OrdenCompraDetalle d : o.getDetalles()) {
                modeloDetalle.addRow(new Object[]{
                        d.getNombreItem(),
                        textoOVacio(d.getNombreBodega()),
                        MoneyFormatter.cantidad(d.getCantidadSolicitada()),
                        MoneyFormatter.cantidad(d.getCantidadRecibida()),
                        MoneyFormatter.cantidad(d.getPendiente()),
                        MoneyFormatter.cop(d.getPrecioUnitario()),
                        MoneyFormatter.cop(d.getSubtotal())
                });
            }
        } catch (SQLException ex) {
            error("No se pudo cargar el detalle de la orden:\n" + ex.getMessage());
        }
    }

    private void limpiarDetalle() {
        modeloDetalle.setRowCount(0);
        lblDetalle.setText("Detalle de la orden (seleccione una orden en la tabla)");
    }

    private OrdenCompra seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione una orden en la tabla.");
            return null;
        }
        return ordenes.get(fila);
    }

    /** Vuelve a seleccionar la orden por id (tras recibir), lo que recarga su detalle. */
    private void seleccionarPorId(int idOrden) {
        for (int i = 0; i < ordenes.size(); i++) {
            if (ordenes.get(i).getIdOrden() == idOrden) {
                tabla.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    private void verDetalle() {
        OrdenCompra base = seleccionada();
        if (base != null) {
            mostrarDetalle(base);
        }
    }

    private void nuevaOrden() {
        NuevaOrdenDialog dlg = new NuevaOrdenDialog(ventana());
        dlg.setVisible(true);
        if (dlg.fueGuardada()) {
            cargar();
        }
    }

    private void recibir() {
        OrdenCompra base = seleccionada();
        if (base == null) {
            return;
        }
        if (!base.getEstado().permiteRecepcion()) {
            error("La orden " + base.getNumeroOrden() + " no admite recepcion (estado: "
                    + base.getEstado().getEtiqueta() + ").");
            return;
        }
        try {
            Optional<OrdenCompra> conDetalles = controller.buscarConDetalles(base.getIdOrden());
            if (conDetalles.isEmpty()) {
                error("No se encontro la orden seleccionada.");
                return;
            }
            RecepcionDialog dlg = new RecepcionDialog(ventana(), conDetalles.get(), idUsuario);
            dlg.setVisible(true);
            if (dlg.fueRecibida()) {
                cargar();
                seleccionarPorId(base.getIdOrden()); // refresca la vista y el detalle de la misma orden
            }
        } catch (SQLException ex) {
            error("No se pudo abrir la recepcion:\n" + ex.getMessage());
        }
    }

    private void cancelar() {
        OrdenCompra base = seleccionada();
        if (base == null) {
            return;
        }
        int op = JOptionPane.showConfirmDialog(this,
                "Cancelar la orden " + base.getNumeroOrden() + "?\n"
                        + "No se reversa lo ya recibido; solo se cancela el saldo pendiente.",
                "Cancelar orden", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (op != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            controller.cancelar(base.getIdOrden());
            cargar();
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    /** Devuelve el texto o cadena vacia si es null/espacios (para celdas). */
    private static String textoOVacio(String v) {
        return (v == null || v.trim().isEmpty()) ? "" : v.trim();
    }

    /** Resume un texto largo para la columna de observaciones (con puntos suspensivos). */
    private static String resumir(String v, int max) {
        if (v == null) {
            return "";
        }
        String s = v.trim();
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + "\u2026";
    }

    private Window ventana() {
        return SwingUtilities.getWindowAncestor(this);
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Compras", JOptionPane.WARNING_MESSAGE);
    }
}
