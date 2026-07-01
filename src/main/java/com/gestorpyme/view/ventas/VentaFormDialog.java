package com.gestorpyme.view.ventas;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.controller.VentaController;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.DisponibilidadBodegaItem;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.domain.model.VentaDetalleLote;
import com.gestorpyme.service.ManoObraCalculator;
import com.gestorpyme.view.components.ComboBoxRenderers;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;
import com.gestorpyme.view.components.DatePickerField;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Formulario modal para registrar una venta.
 *
 * Captura cliente, bodega, tipo de pago (contado/credito) y las lineas de detalle
 * (item, cantidad, precio, descuento). Calcula el total y delega la persistencia
 * transaccional en {@link VentaController}. No contiene SQL ni reglas de negocio:
 * las validaciones definitivas y el calculo viven en el servicio.
 */
public class VentaFormDialog extends JDialog {

    private final VentaController ventaController;
    private final Integer idUsuario;

    private EntityLookupField<Tercero> lkpCliente;
    private final JComboBox<Bodega> cmbBodega = new JComboBox<>();
    private final JRadioButton rbContado = new JRadioButton("Contado", true);
    private final JRadioButton rbCredito = new JRadioButton("Credito");
    private final JComboBox<MedioPago> cmbMedio = new JComboBox<>(MedioPago.comunes());
    private final DatePickerField fechaVenc = new DatePickerField();

    private EntityLookupField<Item> lkpItem;
    private final JTextField txtCantidad = new JTextField("1");
    private final JTextField txtPrecio = new JTextField();
    private final JTextField txtDescLinea = new JTextField("0");
    private final JTextField txtDescuento = new JTextField("0");
    private final JLabel lblTotal = new JLabel("0");

    private final DefaultTableModel modeloLineas;
    private final JTable tablaLineas;

    /** Lineas en memoria (cada fila de la tabla corresponde a esta lista). */
    private final List<VentaDetalle> lineas = new ArrayList<>();
    private boolean guardado = false;

    public VentaFormDialog(Window owner, VentaController ventaController, TerceroController terceroController,
                           ItemController itemController, BodegaController bodegaController, Integer idUsuario) {
        super(owner, "Nueva venta", ModalityType.APPLICATION_MODAL);
        this.ventaController = ventaController;
        this.idUsuario = idUsuario;

        cmbBodega.setRenderer(ComboBoxRenderers.amigable());
        // Busqueda inteligente para cliente y producto (reemplazan combos largos).
        lkpCliente = new EntityLookupField<>(SearchSpecs.clientes(terceroController));
        lkpItem = new EntityLookupField<>(SearchSpecs.itemsParaVenta(itemController));
        lkpItem.setAlCambiar(this::actualizarPrecioDesdeItem);

        modeloLineas = new DefaultTableModel(
                new Object[]{"Item", "Bodega salida", "Lote(s)", "Cantidad", "Precio", "Descuento", "Subtotal"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tablaLineas = new JTable(modeloLineas);
        TableUtils.estilizar(tablaLineas);
        TableUtils.anchos(tablaLineas, 200, 120, 150, 80, 95, 95, 105);
        TableUtils.alinearDerecha(tablaLineas, 3, 4, 5, 6);

        JPanel contenedor = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        contenedor.setBackground(UiTheme.SUPERFICIE);
        contenedor.setBorder(UiTheme.margen());
        contenedor.add(crearEncabezado(), BorderLayout.NORTH);
        contenedor.add(crearCentro(), BorderLayout.CENTER);
        contenedor.add(crearPie(), BorderLayout.SOUTH);
        setContentPane(contenedor);

        cargarDatos(terceroController, itemController, bodegaController);
        configurarEventos();
        actualizarTipoPago();

        setSize(760, 620);
        setMinimumSize(new Dimension(680, 560));
        setLocationRelativeTo(owner);
    }

    /** Cabecera: cliente, bodega, tipo de pago. */
    private JComponent crearEncabezado() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.SUPERFICIE);
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 8);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        lkpCliente.setPreferredSize(new Dimension(320, 34));
        cmbBodega.setPreferredSize(new Dimension(200, 28));

        g.gridx = 0; g.gridy = 0; panel.add(etiqueta("Cliente *:"), g);
        g.gridx = 1; g.weightx = 1; panel.add(lkpCliente, g);
        g.weightx = 0;
        g.gridx = 2; panel.add(etiqueta("Bodega preferida:"), g);
        g.gridx = 3; panel.add(cmbBodega, g);

        ButtonGroup grupo = new ButtonGroup();
        grupo.add(rbContado);
        grupo.add(rbCredito);
        rbContado.setBackground(UiTheme.SUPERFICIE);
        rbCredito.setBackground(UiTheme.SUPERFICIE);
        JPanel pago = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        pago.setBackground(UiTheme.SUPERFICIE);
        pago.add(rbContado);
        pago.add(rbCredito);
        pago.add(etiqueta("Medio:"));
        cmbMedio.setPreferredSize(new Dimension(140, 28));
        pago.add(cmbMedio);
        pago.add(etiqueta("Vence:"));
        fechaVenc.setAnchoCampo(110);
        pago.add(fechaVenc);

        g.gridx = 0; g.gridy = 1; g.gridwidth = 4; panel.add(pago, g);
        g.gridwidth = 1;
        return panel;
    }

    /** Centro: alta de linea + tabla de lineas. */
    private JComponent crearCentro() {
        JPanel centro = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        centro.setBackground(UiTheme.SUPERFICIE);

        JPanel alta = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        alta.setBackground(UiTheme.SUPERFICIE);
        alta.setBorder(UiTheme.tarjeta());
        lkpItem.setPreferredSize(new Dimension(320, 34));
        txtCantidad.setPreferredSize(new Dimension(70, 28));
        txtPrecio.setPreferredSize(new Dimension(100, 28));
        txtDescLinea.setPreferredSize(new Dimension(90, 28));
        alta.add(etiqueta("Item:"));
        alta.add(lkpItem);
        alta.add(etiqueta("Cant.:"));
        alta.add(txtCantidad);
        alta.add(etiqueta("Precio:"));
        alta.add(txtPrecio);
        alta.add(etiqueta("Desc.:"));
        alta.add(txtDescLinea);
        JButton btnAgregar = UiTheme.botonPrimario(new JButton("Agregar"));
        btnAgregar.addActionListener(e -> agregarLinea());
        alta.add(btnAgregar);

        JScrollPane scroll = new JScrollPane(tablaLineas);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);

        JButton btnQuitar = UiTheme.botonSecundario(new JButton("Quitar linea"));
        btnQuitar.addActionListener(e -> quitarLinea());
        JPanel accionesTabla = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        accionesTabla.setBackground(UiTheme.SUPERFICIE);
        accionesTabla.add(btnQuitar);

        JPanel norte = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        norte.setBackground(UiTheme.SUPERFICIE);
        norte.add(alta, BorderLayout.NORTH);

        centro.add(norte, BorderLayout.NORTH);
        centro.add(scroll, BorderLayout.CENTER);
        centro.add(accionesTabla, BorderLayout.SOUTH);
        return centro;
    }

    /** Pie: descuento global, total y botones. */
    private JComponent crearPie() {
        JPanel pie = new JPanel(new BorderLayout());
        pie.setBackground(UiTheme.SUPERFICIE);
        pie.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDE));

        JPanel totales = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, UiTheme.ESPACIO));
        totales.setBackground(UiTheme.SUPERFICIE);
        totales.add(etiqueta("Descuento venta:"));
        txtDescuento.setPreferredSize(new Dimension(100, 28));
        totales.add(txtDescuento);
        JLabel lt = new JLabel("Total:");
        lt.setFont(UiTheme.fuenteSubtitulo());
        lt.setForeground(UiTheme.TEXTO);
        totales.add(lt);
        lblTotal.setFont(UiTheme.fuenteTitulo());
        lblTotal.setForeground(UiTheme.PRIMARIO);
        totales.add(lblTotal);

        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        btnCancelar.addActionListener(e -> dispose());
        JButton btnGuardar = UiTheme.botonPrimario(new JButton("Guardar venta"));
        btnGuardar.addActionListener(e -> guardar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, UiTheme.ESPACIO));
        botones.setBackground(UiTheme.SUPERFICIE);
        botones.add(btnCancelar);
        botones.add(btnGuardar);

        pie.add(totales, BorderLayout.WEST);
        pie.add(botones, BorderLayout.EAST);
        return pie;
    }

    private JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        return l;
    }

    private void cargarDatos(TerceroController terceroController, ItemController itemController,
                             BodegaController bodegaController) {
        try {
            Bodega sin = new Bodega();
            sin.setIdBodega(0);
            sin.setNombre("(Sin bodega)");
            cmbBodega.addItem(sin);
            for (Bodega b : bodegaController.listar()) {
                cmbBodega.addItem(b);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar los datos: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        // Precio inicial del primer item.
        actualizarPrecioDesdeItem();
    }

    private void configurarEventos() {
        rbContado.addActionListener(e -> actualizarTipoPago());
        rbCredito.addActionListener(e -> actualizarTipoPago());
        // Al editar el descuento global, recalcular el total mostrado.
        txtDescuento.addCaretListener(e -> recalcularTotal());
    }

    /** Habilita medio (contado) o fecha de vencimiento (credito). */
    private void actualizarTipoPago() {
        boolean contado = rbContado.isSelected();
        cmbMedio.setEnabled(contado);
        fechaVenc.setEnabled(!contado);
    }

    /**
     * Coloca el precio sugerido del item seleccionado en el campo de precio.
     * Para un servicio con modo PORCENTAJE_REPUESTOS, sugiere el porcentaje sobre el
     * subtotal de los bienes fisicos ya agregados a la venta (valor editable por el usuario).
     */
    private void actualizarPrecioDesdeItem() {
        Item item = lkpItem.getSeleccionado();
        if (item == null) {
            return;
        }
        if (item.getModoCalculoServicio() == ModoCalculoServicio.PORCENTAJE_REPUESTOS) {
            BigDecimal sugerido = ManoObraCalculator.valorSugerido(item, lineas);
            txtPrecio.setText(sugerido.signum() == 0 ? "0" : sugerido.stripTrailingZeros().toPlainString());
        } else if (item.getPrecioVenta() != null) {
            txtPrecio.setText(item.getPrecioVenta().stripTrailingZeros().toPlainString());
        }
    }

    /** Agrega una linea a la venta a partir de los campos de alta. */
    private void agregarLinea() {
        Item item = lkpItem.getSeleccionado();
        if (item == null) {
            aviso("Seleccione un item.");
            return;
        }
        BigDecimal cantidad;
        BigDecimal precio;
        BigDecimal descLinea;
        try {
            cantidad = parseMonto(txtCantidad.getText());
            precio = parseMonto(txtPrecio.getText());
            descLinea = parseMonto(txtDescLinea.getText());
        } catch (NumberFormatException e) {
            aviso("Cantidad, precio y descuento deben ser numeros validos.");
            return;
        }
        if (cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            aviso("La cantidad debe ser mayor a 0.");
            return;
        }
        BigDecimal subtotal = cantidad.multiply(precio).subtract(descLinea);
        if (subtotal.compareTo(BigDecimal.ZERO) < 0) {
            aviso("El descuento de la linea no puede superar su importe.");
            return;
        }

        VentaDetalle d = new VentaDetalle();
        d.setIdItem(item.getIdItem());
        d.setNombreItem(item.getNombre());
        d.setControlaInventario(item.isControlaInventario());
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precio);
        d.setDescuentoLinea(descLinea);
        d.setSubtotalLinea(subtotal);

        // Paso I: resolver la bodega de salida de la linea (preferida si alcanza; si no, otra bodega).
        // Los servicios no requieren bodega ("No aplica"). Si no hay stock suficiente, se informa y no se agrega.
        String bodegaTexto;
        String loteTexto;
        if (item.isControlaInventario()) {
            Bodega pref = (Bodega) cmbBodega.getSelectedItem();
            int idPref = (pref == null || pref.getIdBodega() == 0) ? 0 : pref.getIdBodega();
            int idBodegaLinea;
            try {
                DisponibilidadBodegaItem elegida =
                        ventaController.resolverBodegaSalida(item.getIdItem(), cantidad, idPref);
                d.setIdBodegaSalida(elegida.getIdBodega());
                d.setNombreBodegaSalida(elegida.getNombreBodega());
                bodegaTexto = elegida.getNombreBodega();
                idBodegaLinea = elegida.getIdBodega();
            } catch (ValidacionException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Sin stock suficiente",
                        JOptionPane.WARNING_MESSAGE);
                return;
            } catch (SQLException ex) {
                aviso("No se pudo verificar la disponibilidad: " + ex.getMessage());
                return;
            }
            // Paso J: previsualizar el plan FEFO de lotes dentro de la bodega resuelta.
            // Si el item maneja lotes pero no cubren la cantidad, se informa y no se agrega (igual que al guardar).
            try {
                List<VentaDetalleLote> plan = ventaController.planificarLotes(item.getIdItem(), idBodegaLinea, cantidad);
                loteTexto = plan.isEmpty() ? "Sin lote" : formatearLotes(plan);
            } catch (ValidacionException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Lotes insuficientes",
                        JOptionPane.WARNING_MESSAGE);
                return;
            } catch (SQLException ex) {
                aviso("No se pudo verificar los lotes: " + ex.getMessage());
                return;
            }
        } else {
            d.setIdBodegaSalida(null);
            d.setNombreBodegaSalida(null);
            bodegaTexto = "No aplica";
            loteTexto = "No aplica";
        }

        lineas.add(d);
        modeloLineas.addRow(new Object[]{
                item.getNombre(), bodegaTexto, loteTexto, MoneyFormatter.cantidad(cantidad), MoneyFormatter.cop(precio),
                MoneyFormatter.cop(descLinea), MoneyFormatter.cop(subtotal)});
        // Reiniciar campos de alta para la siguiente linea.
        txtCantidad.setText("1");
        txtDescLinea.setText("0");
        recalcularTotal();
    }

    /** Formatea el plan FEFO para la tabla de líneas: "L-001 (4), L-002 (6)". */
    private String formatearLotes(List<VentaDetalleLote> plan) {
        StringBuilder sb = new StringBuilder();
        for (VentaDetalleLote c : plan) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(c.getNumeroLote()).append(" (")
              .append(c.getCantidad().stripTrailingZeros().toPlainString()).append(")");
        }
        return sb.toString();
    }

    private void quitarLinea() {
        int fila = tablaLineas.getSelectedRow();
        if (fila < 0) {
            aviso("Seleccione una linea para quitar.");
            return;
        }
        lineas.remove(fila);
        modeloLineas.removeRow(fila);
        recalcularTotal();
    }

    /** Recalcula y muestra el total (subtotal de lineas - descuento global). */
    private void recalcularTotal() {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (VentaDetalle d : lineas) {
            subtotal = subtotal.add(d.getSubtotalLinea());
        }
        BigDecimal descuento;
        try {
            descuento = parseMonto(txtDescuento.getText());
        } catch (NumberFormatException e) {
            descuento = BigDecimal.ZERO;
        }
        BigDecimal total = subtotal.subtract(descuento);
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }
        lblTotal.setText(MoneyFormatter.cop(total));
    }

    /** Construye la venta con los datos del formulario y la registra. */
    private void guardar() {
        Tercero cliente = lkpCliente.getSeleccionado();
        if (cliente == null) {
            aviso("Seleccione un cliente. Registre uno en 'Clientes / Prospectos' si no existe.");
            return;
        }
        if (lineas.isEmpty()) {
            aviso("Agregue al menos una linea a la venta.");
            return;
        }

        Bodega bodega = (Bodega) cmbBodega.getSelectedItem();
        int idBodega = (bodega == null || bodega.getIdBodega() == 0) ? 0 : bodega.getIdBodega();
        boolean contado = rbContado.isSelected();
        MedioPago medio = contado ? (MedioPago) cmbMedio.getSelectedItem() : null;

        Venta venta = new Venta();
        venta.setIdTercero(cliente.getIdTercero());
        venta.setIdUsuario(idUsuario);
        try {
            venta.setDescuento(parseMonto(txtDescuento.getText()));
        } catch (NumberFormatException e) {
            aviso("El descuento de la venta debe ser un numero valido.");
            return;
        }

        try {
            String fechaVencIso = contado ? null : fechaVenc.getTextoIso();
            String numero = ventaController.registrarVenta(venta, new ArrayList<>(lineas),
                    idBodega, contado, fechaVencIso, medio);
            guardado = true;
            JOptionPane.showMessageDialog(this, "Venta " + numero + " registrada correctamente.",
                    "Ventas", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IllegalArgumentException | ValidacionException e) {
            aviso(e.getMessage());
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo registrar la venta:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Convierte texto a BigDecimal aceptando coma o punto; vacio = 0. */
    private BigDecimal parseMonto(String texto) {
        String limpio = texto == null ? "" : texto.trim().replace(",", ".");
        if (limpio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(limpio);
    }

    private void aviso(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Datos incompletos", JOptionPane.WARNING_MESSAGE);
    }

    public boolean isGuardado() {
        return guardado;
    }
}
