package com.gestorpyme.view.ventas;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.controller.VentaController;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.util.DateFormatter;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Vista del modulo Ventas: lista las ventas registradas y permite crear una nueva
 * o consultar su detalle. No ejecuta SQL: delega en {@link VentaController}.
 * Diseno responsive con tabla estilizada (tooltips + anchos minimos).
 */
public class VentasView extends JPanel {

    private final VentaController ventaController = new VentaController();
    private final TerceroController terceroController = new TerceroController();
    private final ItemController itemController = new ItemController();
    private final BodegaController bodegaController = new BodegaController();
    private final Integer idUsuario;

    private final DefaultTableModel modelo;
    private final JTable tabla;

    public VentasView(Integer idUsuario) {
        this.idUsuario = idUsuario;
        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        modelo = new DefaultTableModel(
                new Object[]{"Numero", "Fecha", "Cliente", "Total", "Estado"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        TableUtils.estilizar(tabla);
        TableUtils.conScrollHorizontal(tabla);
        TableUtils.anchos(tabla, 120, 110, 260, 120, 150);
        TableUtils.anchosMinimos(tabla, 100, 100, 160, 100, 120);
        TableUtils.alinearDerecha(tabla, 3);

        add(crearBarra(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(tabla,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);

        recargar();
    }

    private JComponent crearBarra() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);

        JLabel titulo = new JLabel("Ventas");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);

        JButton btnNueva = UiTheme.botonPrimario(new JButton("Nueva venta"));
        btnNueva.addActionListener(e -> nuevaVenta());
        JButton btnDetalle = UiTheme.botonSecundario(new JButton("Ver detalle"));
        btnDetalle.addActionListener(e -> verDetalle());

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, 0));
        acciones.setOpaque(false);
        acciones.add(btnDetalle);
        acciones.add(btnNueva);

        barra.add(titulo, BorderLayout.WEST);
        barra.add(acciones, BorderLayout.EAST);
        return barra;
    }

    /** Carga las ventas en la tabla. */
    private void recargar() {
        try {
            List<Venta> ventas = ventaController.listar();
            modelo.setRowCount(0);
            for (Venta v : ventas) {
                modelo.addRow(new Object[]{
                        v.getNumeroVenta(),
                        DateFormatter.isoAVista(v.getFecha()),
                        v.getNombreTercero() == null ? "(sin cliente)" : v.getNombreTercero(),
                        MoneyFormatter.cop(v.getTotal()),
                        v.getEstado()
                });
            }
        } catch (Exception e) {
            error("No se pudieron cargar las ventas: " + e.getMessage());
        }
    }

    private void nuevaVenta() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        VentaFormDialog dialog = new VentaFormDialog(owner, ventaController, terceroController,
                itemController, bodegaController, idUsuario);
        dialog.setVisible(true);
        if (dialog.isGuardado()) {
            recargar();
        }
    }

    /** Abre un dialogo de solo lectura con las lineas de la venta seleccionada. */
    private void verDetalle() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione una venta para ver su detalle.");
            return;
        }
        String numero = String.valueOf(modelo.getValueAt(fila, 0));
        try {
            // Se localiza la venta por su numero para obtener el id.
            int idVenta = -1;
            for (Venta v : ventaController.listar()) {
                if (numero.equals(v.getNumeroVenta())) {
                    idVenta = v.getIdVenta();
                    break;
                }
            }
            if (idVenta < 0) {
                error("No se encontro la venta seleccionada.");
                return;
            }
            List<VentaDetalle> detalles = ventaController.listarDetalles(idVenta);
            mostrarDetalle(numero, detalles);
        } catch (Exception e) {
            error("No se pudo cargar el detalle: " + e.getMessage());
        }
    }

    private void mostrarDetalle(String numero, List<VentaDetalle> detalles) {
        DefaultTableModel md = new DefaultTableModel(
                new Object[]{"Item", "Cantidad", "Precio", "Descuento", "Subtotal"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (VentaDetalle d : detalles) {
            md.addRow(new Object[]{
                    d.getNombreItem(),
                    MoneyFormatter.cantidad(d.getCantidad()),
                    MoneyFormatter.cop(d.getPrecioUnitario()),
                    MoneyFormatter.cop(d.getDescuentoLinea()),
                    MoneyFormatter.cop(d.getSubtotalLinea())});
        }
        JTable t = new JTable(md);
        TableUtils.estilizar(t);
        TableUtils.anchos(t, 240, 90, 110, 100, 120);
        TableUtils.alinearDerecha(t, 1, 2, 3, 4);

        JScrollPane scroll = new JScrollPane(t);
        scroll.setPreferredSize(new Dimension(620, 320));
        scroll.getViewport().setBackground(Color.WHITE);

        JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), scroll,
                "Detalle de la venta " + numero, JOptionPane.PLAIN_MESSAGE);
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Ventas", JOptionPane.ERROR_MESSAGE);
    }
}
