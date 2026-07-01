package com.gestorpyme.view.taller;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.InventarioController;
import com.gestorpyme.controller.OrdenTrabajoController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.controller.VehiculoController;
import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.OrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajoRepuesto;
import com.gestorpyme.domain.model.OrdenTrabajoServicio;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Formulario de creacion/edicion de una Orden de Trabajo (Paso U.2).
 *
 * Capa: view (Swing). No contiene reglas de negocio: arma el {@link OrdenTrabajo} (cabecera + detalle
 * de servicios y repuestos) y delega en {@link OrdenTrabajoController} (que valida y recalcula
 * totales). El cliente se elige con busqueda inteligente; el vehiculo se filtra por cliente. El boton
 * "Cerrar y facturar" esta deshabilitado: el cierre a venta (descuento FEFO, cartera/pago) es U.3.
 */
public class OrdenTrabajoFormDialog extends JDialog {

    private static final String FUENTE = "Segoe UI";

    private final transient OrdenTrabajoController controller;
    private final transient OrdenTrabajo original; // null = creacion

    private final EntityLookupField<Tercero> lookupCliente;
    private final JComboBox<Vehiculo> comboVehiculo = new JComboBox<>();
    private final JTextField txtKilometraje = new JTextField();
    private final JTextField txtFechaEntrega = new JTextField();
    private final JTextField txtMotivo = new JTextField();
    private final JTextArea txtDiagnostico = new JTextArea(2, 20);
    private final JTextArea txtObservaciones = new JTextArea(2, 20);

    private final transient List<OrdenTrabajoServicio> servicios = new ArrayList<>();
    private final transient List<OrdenTrabajoRepuesto> repuestos = new ArrayList<>();
    private final DefaultTableModel modeloServicios =
            tablaNoEditable(new String[]{"Servicio", "Cantidad", "Precio", "Subtotal"});
    private final DefaultTableModel modeloRepuestos =
            tablaNoEditable(new String[]{"Repuesto", "Bodega", "Cantidad", "Precio", "Subtotal"});

    private final JLabel lblSubServicios = new JLabel();
    private final JLabel lblSubRepuestos = new JLabel();
    private final JLabel lblTotal = new JLabel();

    private final transient VehiculoController vehiculoController = new VehiculoController();
    private final transient ItemController itemController = new ItemController();
    private final transient BodegaController bodegaController = new BodegaController();
    private final transient InventarioController inventarioController = new InventarioController();

    private boolean guardado;

    public OrdenTrabajoFormDialog(Window propietario, OrdenTrabajoController controller, OrdenTrabajo ot) {
        super(propietario, ot == null ? "Nueva orden de trabajo" : "Orden de trabajo " + ot.getNumeroOt(),
                ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.original = ot;
        this.lookupCliente = new EntityLookupField<>(SearchSpecs.clientes(new TerceroController()));

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 8));
        add(crearCabecera(), BorderLayout.NORTH);
        add(crearDetalle(), BorderLayout.CENTER);
        add(crearPie(), BorderLayout.SOUTH);

        comboVehiculo.setRenderer(rendererVehiculo());
        // Al cambiar el cliente, se recargan sus vehiculos.
        lookupCliente.setAlCambiar(this::cargarVehiculosDelCliente);

        if (ot != null) {
            cargar(ot);
        }
        actualizarTotales();

        setMinimumSize(new Dimension(760, 560));
        pack();
        setLocationRelativeTo(propietario);
    }

    /** @return true si la OT fue guardada correctamente. */
    public boolean fueGuardado() {
        return guardado;
    }

    // ----------------------------------------------------------------- cabecera

    private JComponent crearCabecera() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 4, 12));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 6, 4, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        g.gridx = 0; g.gridy = 0; g.weightx = 0; p.add(etiqueta("Cliente:"), g);
        g.gridx = 1; g.weightx = 1; p.add(lookupCliente, g);
        g.gridx = 2; g.weightx = 0; p.add(etiqueta("Vehiculo:"), g);
        g.gridx = 3; g.weightx = 1; comboVehiculo.setPreferredSize(new Dimension(220, 28)); p.add(comboVehiculo, g);

        g.gridx = 0; g.gridy = 1; g.weightx = 0; p.add(etiqueta("Kilometraje:"), g);
        g.gridx = 1; g.weightx = 1; campo(txtKilometraje); p.add(txtKilometraje, g);
        g.gridx = 2; g.weightx = 0; p.add(etiqueta("Entrega estimada (ISO):"), g);
        g.gridx = 3; g.weightx = 1; campo(txtFechaEntrega); p.add(txtFechaEntrega, g);

        g.gridx = 0; g.gridy = 2; g.weightx = 0; p.add(etiqueta("Motivo de ingreso:"), g);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1; campo(txtMotivo); p.add(txtMotivo, g);
        g.gridwidth = 1;

        g.gridx = 0; g.gridy = 3; g.weightx = 0; g.anchor = GridBagConstraints.NORTHWEST;
        p.add(etiqueta("Diagnostico:"), g);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1; g.anchor = GridBagConstraints.WEST;
        p.add(areaScroll(txtDiagnostico), g);
        g.gridwidth = 1;

        g.gridx = 0; g.gridy = 4; g.weightx = 0; g.anchor = GridBagConstraints.NORTHWEST;
        p.add(etiqueta("Observaciones:"), g);
        g.gridx = 1; g.gridwidth = 3; g.weightx = 1; g.anchor = GridBagConstraints.WEST;
        p.add(areaScroll(txtObservaciones), g);
        return p;
    }

    // ----------------------------------------------------------------- detalle

    private JComponent crearDetalle() {
        javax.swing.JTabbedPane tabs = new javax.swing.JTabbedPane();
        tabs.addTab("Servicios", crearPanelServicios());
        tabs.addTab("Repuestos", crearPanelRepuestos());

        JPanel cont = new JPanel(new BorderLayout(0, 6));
        cont.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        cont.add(tabs, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.add(crearImpactoOperativo(), BorderLayout.NORTH);
        sur.add(crearTotales(), BorderLayout.SOUTH);
        cont.add(sur, BorderLayout.SOUTH);
        return cont;
    }

    /**
     * Panel informativo de impacto operativo (Paso U.2.1). Comunica como esta OT afectara a los
     * modulos conectados cuando se cierre y facture (U.3). Es SOLO informativo: no ejecuta ninguna
     * accion sobre inventario, ventas, compras ni cartera.
     */
    private JComponent crearImpactoOperativo() {
        JPanel p = new JPanel(new GridLayout(0, 1, 0, 1));
        p.setBorder(BorderFactory.createTitledBorder("Impacto operativo"));
        p.add(lineaImpacto("Esta OT no descuenta inventario ni genera venta hasta cerrar y facturar.", true));
        p.add(lineaImpacto("Inventario: no se descuenta hasta cerrar y facturar (Paso U.3).", false));
        p.add(lineaImpacto("Venta: se generara al cerrar la OT (Paso U.3).", false));
        p.add(lineaImpacto("Compras: si no hay stock suficiente, revisar reabastecimiento / compras.", false));
        p.add(lineaImpacto("Pagos / Cartera: se registraran al facturar segun contado o credito.", false));
        return p;
    }

    /** Etiqueta de una linea del panel de impacto operativo (la primera linea va resaltada). */
    private JLabel lineaImpacto(String texto, boolean resaltado) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font(FUENTE, resaltado ? Font.BOLD : Font.PLAIN, 12));
        return l;
    }

    private JComponent crearPanelServicios() {
        JTable t = new JTable(modeloServicios);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        barra.add(boton("Agregar servicio", e -> agregarServicio()));
        barra.add(boton("Quitar servicio", e -> quitarFila(t, servicios, modeloServicios)));
        JPanel p = new JPanel(new BorderLayout());
        p.add(barra, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JComponent crearPanelRepuestos() {
        JTable t = new JTable(modeloRepuestos);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        barra.add(boton("Agregar repuesto", e -> agregarRepuesto()));
        barra.add(boton("Quitar repuesto", e -> quitarFila(t, repuestos, modeloRepuestos)));
        JPanel p = new JPanel(new BorderLayout());
        p.add(barra, BorderLayout.NORTH);
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    private JComponent crearTotales() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 4));
        lblSubServicios.setFont(new Font(FUENTE, Font.PLAIN, 13));
        lblSubRepuestos.setFont(new Font(FUENTE, Font.PLAIN, 13));
        lblTotal.setFont(new Font(FUENTE, Font.BOLD, 14));
        p.add(lblSubServicios);
        p.add(lblSubRepuestos);
        p.add(lblTotal);
        return p;
    }

    private JComponent crearPie() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        JButton btnCerrar = new JButton("Cerrar y facturar");
        // Solo se puede cerrar una OT ya guardada, no bloqueada (CANCELADA/ENTREGADA) y sin venta previa.
        boolean puedeCerrar = original != null
                && original.getEstado() != null && !original.getEstado().esBloqueado()
                && original.getIdVenta() == null;
        btnCerrar.setEnabled(puedeCerrar);
        btnCerrar.setToolTipText(puedeCerrar
                ? "Genera la venta, descuenta inventario y cierra la OT (Paso U.3)"
                : "Guarde la OT (y que no este cancelada/entregada/facturada) para cerrar y facturar");
        btnCerrar.addActionListener(e -> cerrarYFacturar());
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.addActionListener(e -> dispose());
        JButton btnGuardar = new JButton("Guardar");
        btnGuardar.setFont(new Font(FUENTE, Font.BOLD, 13));
        btnGuardar.addActionListener(e -> guardar());
        barra.add(btnCerrar);
        barra.add(btnCancelar);
        barra.add(btnGuardar);
        return barra;
    }

    /**
     * Abre el dialogo de cierre (tipo de venta, medio de pago, vencimiento y observaciones) y, tras
     * la confirmacion explicita, delega el cierre en el controlador (que delega en VentaService).
     * Muestra el numero de venta generado y cierra el formulario: la OT queda ENTREGADA y enlazada a
     * la venta. No ejecuta logica de venta/inventario aqui.
     */
    private void cerrarYFacturar() {
        if (original == null) {
            JOptionPane.showMessageDialog(this, "Primero guarde la OT antes de cerrar y facturar.",
                    "Cerrar y facturar", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JRadioButton rbContado = new JRadioButton("Contado", true);
        JRadioButton rbCredito = new JRadioButton("Credito");
        ButtonGroup grupo = new ButtonGroup();
        grupo.add(rbContado);
        grupo.add(rbCredito);
        JComboBox<MedioPago> cmbMedio = new JComboBox<>(MedioPago.comunes());
        JTextField txtVenc = new JTextField();
        txtVenc.setEnabled(false);
        JTextField txtObs = new JTextField();
        // Contado -> medio de pago habilitado; Credito -> fecha de vencimiento habilitada.
        Runnable sync = () -> {
            boolean contado = rbContado.isSelected();
            cmbMedio.setEnabled(contado);
            txtVenc.setEnabled(!contado);
        };
        rbContado.addActionListener(e -> sync.run());
        rbCredito.addActionListener(e -> sync.run());

        JPanel tipo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tipo.add(rbContado);
        tipo.add(rbCredito);
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(etiqueta("Tipo de venta:"));
        panel.add(tipo);
        panel.add(filaCampo("Medio de pago (contado):", cmbMedio));
        panel.add(filaCampo("Vencimiento credito (YYYY-MM-DD, opcional):", txtVenc));
        panel.add(filaCampo("Observaciones (opcional):", txtObs));
        JLabel nota = new JLabel("Esto generara una venta, descontara inventario y cerrara la OT.");
        nota.setFont(new Font(FUENTE, Font.BOLD, 12));
        panel.add(nota);

        if (!confirmar(panel, "Cerrar y facturar OT " + original.getNumeroOt())) {
            return;
        }
        boolean contado = rbContado.isSelected();
        MedioPago medio = contado ? (MedioPago) cmbMedio.getSelectedItem() : null;
        String venc = (contado || txtVenc.getText() == null || txtVenc.getText().trim().isEmpty())
                ? null : txtVenc.getText().trim();
        try {
            String numero = controller.cerrarYFacturar(original.getIdOrdenTrabajo(), contado, medio,
                    venc, txtObs.getText());
            guardado = true;
            JOptionPane.showMessageDialog(this,
                    "OT cerrada y facturada.\nVenta generada: " + numero,
                    "Cerrar y facturar", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (ValidacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cerrar y facturar:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ----------------------------------------------------------------- acciones detalle

    private void agregarServicio() {
        EntityLookupField<Item> lookup = new EntityLookupField<>(SearchSpecs.serviciosParaOT(itemController));
        JTextField cant = new JTextField("1");
        JTextField precio = new JTextField("0");
        lookup.setAlCambiar(() -> prefijarPrecio(lookup, precio));
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(etiqueta("Servicio / mano de obra:"));
        panel.add(lookup);
        panel.add(filaCampo("Cantidad:", cant));
        panel.add(filaCampo("Precio unitario:", precio));
        if (!confirmar(panel, "Agregar servicio")) {
            return;
        }
        Item item = lookup.getSeleccionado();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un servicio.", "Validacion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        BigDecimal cantidad = parsear(cant.getText());
        BigDecimal precioU = parsear(precio.getText());
        if (cantidad == null || precioU == null) {
            return;
        }
        OrdenTrabajoServicio s = new OrdenTrabajoServicio();
        s.setIdItem(item.getIdItem());
        s.setNombreItem(item.getNombre());
        s.setCantidad(cantidad);
        s.setPrecioUnitario(precioU);
        s.setSubtotal(cantidad.multiply(precioU).setScale(2, RoundingMode.HALF_UP));
        servicios.add(s);
        modeloServicios.addRow(new Object[]{s.getNombreItem(), texto(s.getCantidad()),
                MoneyFormatter.cop(s.getPrecioUnitario()), MoneyFormatter.cop(s.getSubtotal())});
        actualizarTotales();
    }

    private void agregarRepuesto() {
        EntityLookupField<Item> lookup = new EntityLookupField<>(SearchSpecs.repuestosParaOT(itemController));
        JComboBox<Bodega> comboBodega = new JComboBox<>();
        cargarBodegas(comboBodega);
        comboBodega.setRenderer(rendererBodega());
        JTextField cant = new JTextField("1");
        JTextField precio = new JTextField("0");
        lookup.setAlCambiar(() -> prefijarPrecio(lookup, precio));
        JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));
        panel.add(etiqueta("Repuesto / insumo inventariable:"));
        panel.add(lookup);
        panel.add(filaCampo("Bodega de salida:", comboBodega));
        panel.add(filaCampo("Cantidad:", cant));
        panel.add(filaCampo("Precio unitario:", precio));
        if (!confirmar(panel, "Agregar repuesto")) {
            return;
        }
        Item item = lookup.getSeleccionado();
        Bodega bodega = (Bodega) comboBodega.getSelectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un repuesto.", "Validacion",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        BigDecimal cantidad = parsear(cant.getText());
        BigDecimal precioU = parsear(precio.getText());
        if (cantidad == null || precioU == null) {
            return;
        }
        // Aviso de disponibilidad (Paso U.2.1): lectura de solo lectura sobre el stock por bodega.
        // NO bloquea ni descuenta inventario; la OT es un documento de trabajo. Si la cantidad
        // planeada supera el stock disponible, se informa para revisar compras/reabastecimiento,
        // pero la linea se agrega igual (el descuento real ocurrira al cerrar y facturar en U.3).
        if (bodega != null) {
            avisarDisponibilidad(item, bodega, cantidad);
        }

        OrdenTrabajoRepuesto r = new OrdenTrabajoRepuesto();
        r.setIdItem(item.getIdItem());
        r.setNombreItem(item.getNombre());
        if (bodega != null) {
            r.setIdBodegaSalida(bodega.getIdBodega());
            r.setNombreBodega(bodega.getNombre());
        }
        r.setCantidad(cantidad);
        r.setPrecioUnitario(precioU);
        r.setSubtotal(cantidad.multiply(precioU).setScale(2, RoundingMode.HALF_UP));
        repuestos.add(r);
        modeloRepuestos.addRow(new Object[]{r.getNombreItem(), textoSeguro(r.getNombreBodega()),
                texto(r.getCantidad()), MoneyFormatter.cop(r.getPrecioUnitario()),
                MoneyFormatter.cop(r.getSubtotal())});
        actualizarTotales();
    }

    /**
     * Calcula el stock disponible del repuesto en la bodega elegida (lectura de solo lectura sobre el
     * inventario) y, si la cantidad planeada lo supera, muestra un aviso NO bloqueante. Nunca descuenta
     * inventario: la OT es un documento de trabajo y el descuento real ocurrira al cerrar y facturar (U.3).
     */
    private void avisarDisponibilidad(Item item, Bodega bodega, BigDecimal cantidad) {
        try {
            BigDecimal disponible = BigDecimal.ZERO;
            for (ExistenciaStock e : inventarioController.listarExistencias()) {
                if (e.getIdItem() == item.getIdItem() && e.getIdBodega() == bodega.getIdBodega()) {
                    disponible = e.getCantidad() == null ? BigDecimal.ZERO : e.getCantidad();
                    break;
                }
            }
            if (cantidad.compareTo(disponible) > 0) {
                JOptionPane.showMessageDialog(this,
                        "Stock insuficiente en la bodega '" + bodega.getNombre() + "'.\n"
                        + "Disponible en bodega: " + texto(disponible)
                        + "    Solicitado: " + texto(cantidad) + "\n\n"
                        + "La orden de trabajo no descuenta inventario: la linea se agrega de todas formas.\n"
                        + "Revisar compras / reabastecimiento antes de cerrar y facturar (Paso U.3).",
                        "Disponibilidad", JOptionPane.WARNING_MESSAGE);
            }
        } catch (SQLException ex) {
            // Si falla la lectura de existencias no se bloquea el registro de la OT (documento de trabajo).
        }
    }

    private <T> void quitarFila(JTable tabla, List<T> lista, DefaultTableModel modelo) {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            return;
        }
        lista.remove(fila);
        modelo.removeRow(fila);
        actualizarTotales();
    }

    // ----------------------------------------------------------------- guardar

    private void guardar() {
        BigDecimal kilometraje = parsear(txtKilometraje.getText().isEmpty() ? "0" : txtKilometraje.getText());
        if (kilometraje == null) {
            return;
        }
        OrdenTrabajo ot = (original == null) ? new OrdenTrabajo() : original;
        Tercero cliente = lookupCliente.getSeleccionado();
        Vehiculo vehiculo = (Vehiculo) comboVehiculo.getSelectedItem();
        ot.setIdTercero(cliente != null ? cliente.getIdTercero() : 0);
        ot.setIdVehiculo(vehiculo != null ? vehiculo.getIdVehiculo() : 0);
        ot.setKilometrajeIngreso(kilometraje.doubleValue());
        ot.setFechaEntregaEstimada(textoONull(txtFechaEntrega.getText()));
        ot.setMotivoIngreso(textoONull(txtMotivo.getText()));
        ot.setDiagnostico(textoONull(txtDiagnostico.getText()));
        ot.setObservaciones(textoONull(txtObservaciones.getText()));
        if (original == null) {
            ot.setEstado(EstadoOrdenTrabajo.ABIERTA);
        }
        ot.setServicios(new ArrayList<>(servicios));
        ot.setRepuestos(new ArrayList<>(repuestos));

        try {
            controller.guardar(ot);
            guardado = true;
            dispose();
        } catch (ValidacionException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Validacion", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Ocurrio un error al guardar: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ----------------------------------------------------------------- carga (edicion)

    private void cargar(OrdenTrabajo ot) {
        if (ot.getIdTercero() > 0) {
            Tercero cliente = new Tercero();
            cliente.setIdTercero(ot.getIdTercero());
            cliente.setNombre(ot.getNombreCliente());
            lookupCliente.setSeleccionado(cliente);
        }
        cargarVehiculosDelCliente();
        seleccionarVehiculo(ot.getIdVehiculo());
        txtKilometraje.setText(String.format("%.0f", ot.getKilometrajeIngreso()));
        txtFechaEntrega.setText(textoSeguro(ot.getFechaEntregaEstimada()));
        txtMotivo.setText(textoSeguro(ot.getMotivoIngreso()));
        txtDiagnostico.setText(textoSeguro(ot.getDiagnostico()));
        txtObservaciones.setText(textoSeguro(ot.getObservaciones()));

        for (OrdenTrabajoServicio s : ot.getServicios()) {
            servicios.add(s);
            modeloServicios.addRow(new Object[]{s.getNombreItem(), texto(s.getCantidad()),
                    MoneyFormatter.cop(s.getPrecioUnitario()), MoneyFormatter.cop(s.getSubtotal())});
        }
        for (OrdenTrabajoRepuesto r : ot.getRepuestos()) {
            repuestos.add(r);
            modeloRepuestos.addRow(new Object[]{r.getNombreItem(), textoSeguro(r.getNombreBodega()),
                    texto(r.getCantidad()), MoneyFormatter.cop(r.getPrecioUnitario()),
                    MoneyFormatter.cop(r.getSubtotal())});
        }
    }

    private void cargarVehiculosDelCliente() {
        comboVehiculo.setModel(new DefaultComboBoxModel<>());
        Tercero cliente = lookupCliente.getSeleccionado();
        if (cliente == null) {
            return;
        }
        try {
            for (Vehiculo v : vehiculoController.listarPorCliente(cliente.getIdTercero())) {
                comboVehiculo.addItem(v);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar los vehiculos del cliente:\n"
                    + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void seleccionarVehiculo(int idVehiculo) {
        for (int i = 0; i < comboVehiculo.getItemCount(); i++) {
            if (comboVehiculo.getItemAt(i).getIdVehiculo() == idVehiculo) {
                comboVehiculo.setSelectedIndex(i);
                return;
            }
        }
    }

    private void cargarBodegas(JComboBox<Bodega> combo) {
        try {
            for (Bodega b : bodegaController.listar()) {
                if (b.getEstado() == EstadoRegistro.ACTIVO) {
                    combo.addItem(b);
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar las bodegas:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ----------------------------------------------------------------- utilidades

    private void actualizarTotales() {
        BigDecimal subS = sumar(servicios.stream().map(OrdenTrabajoServicio::getSubtotal));
        BigDecimal subR = sumar(repuestos.stream().map(OrdenTrabajoRepuesto::getSubtotal));
        lblSubServicios.setText("Servicios: " + MoneyFormatter.cop(subS));
        lblSubRepuestos.setText("Repuestos: " + MoneyFormatter.cop(subR));
        lblTotal.setText("Total: " + MoneyFormatter.cop(subS.add(subR)));
    }

    private BigDecimal sumar(java.util.stream.Stream<BigDecimal> valores) {
        return valores.filter(x -> x != null).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void prefijarPrecio(EntityLookupField<Item> lookup, JTextField precio) {
        Item it = lookup.getSeleccionado();
        if (it != null && it.getPrecioVenta() != null) {
            precio.setText(it.getPrecioVenta().toPlainString());
        }
    }

    private BigDecimal parsear(String texto) {
        try {
            return new BigDecimal(texto.trim().replace(",", "."));
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Valor numerico invalido: " + texto,
                    "Validacion", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private boolean confirmar(JComponent panel, String titulo) {
        int r = JOptionPane.showConfirmDialog(this, panel, titulo,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        return r == JOptionPane.OK_OPTION;
    }

    private JPanel filaCampo(String etiqueta, JComponent campo) {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.add(etiqueta(etiqueta), BorderLayout.WEST);
        p.add(campo, BorderLayout.CENTER);
        return p;
    }

    private DefaultListCellRenderer rendererVehiculo() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Vehiculo) {
                    Vehiculo v = (Vehiculo) value;
                    String extra = v.getMarca() != null ? " - " + v.getMarca() : "";
                    setText(v.getPlaca() + extra);
                }
                return this;
            }
        };
    }

    private DefaultListCellRenderer rendererBodega() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Bodega) {
                    setText(((Bodega) value).getNombre());
                }
                return this;
            }
        };
    }

    private static DefaultTableModel tablaNoEditable(String[] columnas) {
        return new DefaultTableModel(columnas, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
    }

    private JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font(FUENTE, Font.BOLD, 13));
        return l;
    }

    /** Crea un boton sencillo con su accion. */
    private JButton boton(String texto, java.awt.event.ActionListener accion) {
        JButton b = new JButton(texto);
        b.setFont(new Font(FUENTE, Font.PLAIN, 13));
        b.addActionListener(accion);
        return b;
    }

    private void campo(JTextField campo) {
        campo.setFont(new Font(FUENTE, Font.PLAIN, 13));
        campo.setPreferredSize(new Dimension(220, 28));
    }

    private JComponent areaScroll(JTextArea area) {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(FUENTE, Font.PLAIN, 13));
        return new JScrollPane(area);
    }

    private String texto(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    private String textoSeguro(String v) {
        return v != null ? v : "";
    }

    private String textoONull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
