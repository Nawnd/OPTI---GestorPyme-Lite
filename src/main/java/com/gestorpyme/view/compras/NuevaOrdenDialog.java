package com.gestorpyme.view.compras;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.OrdenCompraController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.DatePickerField;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;
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
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialogo modal para crear una orden de compra (cabecera + lineas). NO ejecuta SQL ni
 * reglas de negocio: arma el modelo y delega en {@link OrdenCompraController}. El stock NO
 * cambia al crear la orden (solo al recibir). Reutiliza la busqueda inteligente (Paso A).
 * Capa: view.
 */
public class NuevaOrdenDialog extends JDialog {

    private static final long serialVersionUID = 1L;

    private final OrdenCompraController ordenController = new OrdenCompraController();
    private final ItemController itemController = new ItemController();
    private final BodegaController bodegaController = new BodegaController();
    private final TerceroController terceroController = new TerceroController();

    private final EntityLookupField<Tercero> lkpProveedor =
            new EntityLookupField<>(SearchSpecs.proveedores(terceroController));
    private final DatePickerField fechaEstimada = new DatePickerField();
    private final JTextField txtObservaciones = new JTextField();

    private final EntityLookupField<Item> lkpItem =
            new EntityLookupField<>(SearchSpecs.items(itemController));
    private final EntityLookupField<Bodega> lkpBodega =
            new EntityLookupField<>(SearchSpecs.bodegas(bodegaController));
    private final JTextField txtCantidad = new JTextField(6);
    private final JTextField txtPrecio = new JTextField(8);

    private final DefaultTableModel modeloLineas = new DefaultTableModel(
            new String[]{"Item", "Bodega", "Cantidad", "Precio", "Subtotal"}, 0) {
        private static final long serialVersionUID = 1L;
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaLineas = new JTable(modeloLineas);
    private final List<OrdenCompraDetalle> lineas = new ArrayList<>();
    private final JLabel lblTotal = new JLabel("Total: " + MoneyFormatter.cop(BigDecimal.ZERO));

    private boolean guardada = false;

    public NuevaOrdenDialog(Window owner) {
        super(owner, "Nueva orden de compra", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(0, 10));
        ((JComponent) getContentPane()).setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        add(crearFormulario(), BorderLayout.NORTH);
        add(crearCentro(), BorderLayout.CENTER);
        add(crearPie(), BorderLayout.SOUTH);

        // Al elegir un item, precarga el precio de compra como precio unitario.
        lkpItem.setAlCambiar(() -> {
            Item it = lkpItem.getSeleccionado();
            if (it != null && it.getPrecioCompra() != null) {
                txtPrecio.setText(it.getPrecioCompra().stripTrailingZeros().toPlainString());
            }
        });

        setSize(720, 560);
        setLocationRelativeTo(owner);
    }

    /**
     * Constructor para "Generar orden de compra" desde Reabastecimiento: precarga lineas
     * sugeridas (item + cantidad + precio + bodega destino). El usuario elige el proveedor,
     * ajusta cantidades y guarda. El stock NO cambia al crear la orden (solo al recibir).
     *
     * @param owner     ventana padre.
     * @param sugeridas lineas precargadas (cada una con idItem, cantidadSolicitada,
     *                  precioUnitario, idBodegaDestino y nombres para mostrar).
     */
    public NuevaOrdenDialog(Window owner, List<OrdenCompraDetalle> sugeridas) {
        this(owner);
        if (sugeridas != null) {
            for (OrdenCompraDetalle d : sugeridas) {
                agregarDetallePrecargado(d);
            }
        }
    }

    /**
     * Igual que el constructor con sugeridas, pero preselecciona un proveedor preferido (Paso F).
     * Se usa cuando todos los productos seleccionados comparten el mismo proveedor preferido.
     *
     * @param proveedorPreferido proveedor a preseleccionar (o null para seleccion manual).
     */
    public NuevaOrdenDialog(Window owner, List<OrdenCompraDetalle> sugeridas, Tercero proveedorPreferido) {
        this(owner, sugeridas);
        if (proveedorPreferido != null) {
            lkpProveedor.setSeleccionado(proveedorPreferido);
        }
    }

    /** Agrega a la tabla y a la lista una linea ya construida (al precargar sugerencias). */
    private void agregarDetallePrecargado(OrdenCompraDetalle d) {
        BigDecimal cant = d.getCantidadSolicitada() == null ? BigDecimal.ZERO : d.getCantidadSolicitada();
        BigDecimal precio = d.getPrecioUnitario() == null ? BigDecimal.ZERO : d.getPrecioUnitario();
        d.setSubtotal(cant.multiply(precio));
        lineas.add(d);
        modeloLineas.addRow(new Object[]{
                d.getNombreItem(),
                d.getNombreBodega() == null ? "(sin bodega)" : d.getNombreBodega(),
                cant.stripTrailingZeros().toPlainString(),
                MoneyFormatter.cop(precio),
                MoneyFormatter.cop(d.getSubtotal())
        });
        actualizarTotal();
    }

    private JComponent crearFormulario() {
        JPanel panel = new JPanel(new GridLayout(3, 2, 8, 8));
        panel.add(new JLabel("Proveedor:"));
        lkpProveedor.setPreferredSize(new Dimension(320, 32));
        panel.add(lkpProveedor);
        panel.add(new JLabel("Fecha estimada (opcional):"));
        panel.add(fechaEstimada);
        panel.add(new JLabel("Observaciones:"));
        panel.add(txtObservaciones);
        return panel;
    }

    private JComponent crearCentro() {
        JPanel centro = new JPanel(new BorderLayout(0, 8));

        JPanel editor = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lkpItem.setPreferredSize(new Dimension(220, 32));
        lkpBodega.setPreferredSize(new Dimension(180, 32));
        editor.add(new JLabel("Item:"));
        editor.add(lkpItem);
        editor.add(new JLabel("Bodega:"));
        editor.add(lkpBodega);
        editor.add(new JLabel("Cant.:"));
        editor.add(txtCantidad);
        editor.add(new JLabel("Precio:"));
        editor.add(txtPrecio);
        JButton btnAgregar = UiTheme.botonSecundario(new JButton("Agregar"));
        btnAgregar.addActionListener(e -> agregarLinea());
        editor.add(btnAgregar);

        TableUtils.estilizar(tablaLineas);
        tablaLineas.setRowHeight(24);
        TableUtils.alinearDerecha(tablaLineas, 2, 3, 4);

        centro.add(editor, BorderLayout.NORTH);
        centro.add(new JScrollPane(tablaLineas), BorderLayout.CENTER);
        return centro;
    }

    private JComponent crearPie() {
        JPanel pie = new JPanel(new BorderLayout());
        lblTotal.setFont(UiTheme.fuenteSubtitulo());
        pie.add(lblTotal, BorderLayout.WEST);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        btnCancelar.addActionListener(e -> dispose());
        JButton btnGuardar = UiTheme.botonPrimario(new JButton("Guardar orden"));
        btnGuardar.addActionListener(e -> guardar());
        botones.add(btnCancelar);
        botones.add(btnGuardar);
        pie.add(botones, BorderLayout.EAST);
        return pie;
    }

    private void agregarLinea() {
        Item item = lkpItem.getSeleccionado();
        Bodega bodega = lkpBodega.getSeleccionado();
        if (item == null) {
            error("Seleccione un item.");
            return;
        }
        if (bodega == null) {
            error("Seleccione la bodega destino de la linea.");
            return;
        }
        BigDecimal cantidad = leerDecimal(txtCantidad.getText(), "cantidad");
        BigDecimal precio = leerDecimal(txtPrecio.getText(), "precio");
        if (cantidad == null || precio == null) {
            return;
        }
        if (cantidad.signum() <= 0) {
            error("La cantidad debe ser mayor que cero.");
            return;
        }
        if (precio.signum() < 0) {
            error("El precio no puede ser negativo.");
            return;
        }

        OrdenCompraDetalle d = new OrdenCompraDetalle();
        d.setIdItem(item.getIdItem());
        d.setNombreItem(item.getNombre());
        d.setIdBodegaDestino(bodega.getIdBodega());
        d.setNombreBodega(bodega.getNombre());
        d.setCantidadSolicitada(cantidad);
        d.setPrecioUnitario(precio);
        BigDecimal subtotal = cantidad.multiply(precio);
        d.setSubtotal(subtotal);
        lineas.add(d);

        modeloLineas.addRow(new Object[]{
                item.getNombre(), bodega.getNombre(),
                cantidad.stripTrailingZeros().toPlainString(),
                MoneyFormatter.cop(precio), MoneyFormatter.cop(subtotal)
        });

        lkpItem.limpiar();
        txtCantidad.setText("");
        txtPrecio.setText("");
        actualizarTotal();
    }

    private void actualizarTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (OrdenCompraDetalle d : lineas) {
            total = total.add(d.getSubtotal());
        }
        lblTotal.setText("Total: " + MoneyFormatter.cop(total));
    }

    private void guardar() {
        Tercero proveedor = lkpProveedor.getSeleccionado();
        if (proveedor == null) {
            error("Seleccione un proveedor.");
            return;
        }
        if (lineas.isEmpty()) {
            error("Agregue al menos un item a la orden.");
            return;
        }
        OrdenCompra orden = new OrdenCompra();
        orden.setIdProveedor(proveedor.getIdTercero());
        if (!fechaEstimada.estaVacio()) {
            orden.setFechaEstimada(fechaEstimada.getTextoIso());
        }
        orden.setObservaciones(txtObservaciones.getText().trim());
        orden.getDetalles().addAll(lineas);

        try {
            String numero = ordenController.crear(orden);
            guardada = true;
            JOptionPane.showMessageDialog(this, "Orden creada: " + numero,
                    "Orden de compra", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (Exception ex) {
            error(ex.getMessage());
        }
    }

    /** Convierte texto a BigDecimal; muestra error y devuelve null si no es valido. */
    private BigDecimal leerDecimal(String texto, String campo) {
        try {
            return new BigDecimal(texto.trim());
        } catch (NumberFormatException e) {
            error("Valor invalido en " + campo + ".");
            return null;
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Validacion", JOptionPane.WARNING_MESSAGE);
    }

    /** @return true si la orden se creo correctamente. */
    public boolean fueGuardada() {
        return guardada;
    }
}
