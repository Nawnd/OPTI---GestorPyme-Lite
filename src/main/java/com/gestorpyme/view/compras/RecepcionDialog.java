package com.gestorpyme.view.compras;

import com.gestorpyme.controller.RecepcionController;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.domain.model.RecepcionDetalle;
import com.gestorpyme.domain.model.RecepcionMercancia;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialogo modal para registrar la recepcion (parcial o total) de una orden de compra.
 * Muestra las lineas pendientes; el usuario indica cuanto recibe por linea. NO ejecuta SQL:
 * delega en {@link RecepcionController}, que valida y persiste en una transaccion (stock +
 * Kardex ENTRADA_COMPRA + estado de la orden). Capa: view.
 */
public class RecepcionDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Columna editable: cantidad a recibir en esta operacion. */
    private static final int COL_A_RECIBIR = 5;
    /** Paso M: columna editable opcional, numero de lote. */
    private static final int COL_LOTE = 6;
    /** Paso M: columna editable opcional, fecha de vencimiento ISO (AAAA-MM-DD). */
    private static final int COL_VENCE = 7;

    private final RecepcionController recepcionController = new RecepcionController();
    private final OrdenCompra orden;
    private final Integer idUsuario;

    private final List<OrdenCompraDetalle> filasDetalle = new ArrayList<>();
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Item", "Solicitada", "Recibida", "Pendiente", "Bodega", "A recibir",
                    "Lote (opc.)", "Vence AAAA-MM-DD (opc.)"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return columna == COL_A_RECIBIR || columna == COL_LOTE || columna == COL_VENCE;
        }
    };
    private final JTable tabla = new JTable(modelo);

    private boolean recibida = false;

    /**
     * @param owner     ventana padre.
     * @param orden     orden con sus detalles ya cargados (buscarConDetalles).
     * @param idUsuario usuario que registra la recepcion (queda en el Kardex).
     */
    public RecepcionDialog(Window owner, OrdenCompra orden, Integer idUsuario) {
        super(owner, "Recibir mercancia", ModalityType.APPLICATION_MODAL);
        this.orden = orden;
        this.idUsuario = idUsuario;
        setLayout(new BorderLayout(0, 10));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        add(crearEncabezado(), BorderLayout.NORTH);
        add(crearTabla(), BorderLayout.CENTER);
        add(crearPie(), BorderLayout.SOUTH);

        cargarPendientes();

        setSize(680, 460);
        setLocationRelativeTo(owner);
    }

    private JComponent crearEncabezado() {
        JLabel titulo = new JLabel("Orden " + orden.getNumeroOrden()
                + "  -  " + orden.getNombreProveedor()
                + "  (" + orden.getEstado().getEtiqueta() + ")");
        titulo.setFont(UiTheme.fuenteSubtitulo());
        return titulo;
    }

    private JComponent crearTabla() {
        TableUtils.estilizar(tabla);
        tabla.setRowHeight(26);
        TableUtils.alinearDerecha(tabla, 1, 2, 3, 5);
        return new JScrollPane(tabla);
    }

    private JComponent crearPie() {
        JPanel pie = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        btnCancelar.addActionListener(e -> dispose());
        JButton btnRecibir = UiTheme.botonPrimario(new JButton("Recibir"));
        btnRecibir.addActionListener(e -> recibir());
        pie.add(btnCancelar);
        pie.add(btnRecibir);
        return pie;
    }

    /** Llena la tabla con las lineas que aun tienen cantidad pendiente. */
    private void cargarPendientes() {
        for (OrdenCompraDetalle d : orden.getDetalles()) {
            if (d.getPendiente().signum() <= 0) {
                continue;
            }
            filasDetalle.add(d);
            modelo.addRow(new Object[]{
                    d.getNombreItem(),
                    d.getCantidadSolicitada().stripTrailingZeros().toPlainString(),
                    d.getCantidadRecibida().stripTrailingZeros().toPlainString(),
                    d.getPendiente().stripTrailingZeros().toPlainString(),
                    d.getNombreBodega() == null ? "(sin bodega)" : d.getNombreBodega(),
                    "0", "", ""
            });
        }
    }

    private void recibir() {
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();
        }
        RecepcionMercancia rec = new RecepcionMercancia();
        rec.setIdOrden(orden.getIdOrden());
        rec.setIdUsuario(idUsuario);

        for (int i = 0; i < filasDetalle.size(); i++) {
            OrdenCompraDetalle det = filasDetalle.get(i);
            BigDecimal aRecibir = leerDecimal(String.valueOf(modelo.getValueAt(i, COL_A_RECIBIR)));
            if (aRecibir == null) {
                return; // valor invalido: se mostro el error
            }
            if (aRecibir.signum() <= 0) {
                continue; // linea no recibida en esta operacion
            }
            if (det.getIdBodegaDestino() == null) {
                error("La linea '" + det.getNombreItem() + "' no tiene bodega destino; "
                        + "no se puede recibir.");
                return;
            }
            RecepcionDetalle rd = new RecepcionDetalle();
            rd.setIdDetalleOc(det.getIdDetalle());
            rd.setIdItem(det.getIdItem());
            rd.setIdBodega(det.getIdBodegaDestino());
            rd.setCantidadRecibida(aRecibir);
            // Paso M: lote y vencimiento opcionales por linea (la validacion vive en el servicio/repositorio).
            String lote = textoCelda(i, COL_LOTE);
            String vence = textoCelda(i, COL_VENCE);
            rd.setNumeroLote(lote.isBlank() ? null : lote);
            rd.setFechaVencimiento(vence.isBlank() ? null : vence);
            rec.getDetalles().add(rd);
        }

        if (rec.getDetalles().isEmpty()) {
            error("Indique al menos una cantidad a recibir.");
            return;
        }

        try {
            String numero = recepcionController.registrar(rec);
            recibida = true;
            JOptionPane.showMessageDialog(this, "Recepcion registrada: " + numero,
                    "Recepcion de mercancia", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    /** Lee una celda del modelo como texto recortado (cadena vacia si es null). */
    private String textoCelda(int fila, int col) {
        Object v = modelo.getValueAt(fila, col);
        return v == null ? "" : v.toString().trim();
    }

    private BigDecimal leerDecimal(String texto) {
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            error("Cantidad a recibir invalida: '" + texto + "'.");
            return null;
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Validacion", JOptionPane.WARNING_MESSAGE);
    }

    /** @return true si se registro la recepcion. */
    public boolean fueRecibida() {
        return recibida;
    }
}
