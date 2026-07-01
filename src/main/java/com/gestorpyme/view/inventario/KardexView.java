package com.gestorpyme.view.inventario;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.InventarioController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.MovimientoInventario;
import com.gestorpyme.util.DateFormatter;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.ComboBoxRenderers;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;
import com.gestorpyme.view.components.DatePickerField;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * Vista del modulo Kardex: consulta historica de movimientos de inventario.
 *
 * Filtros: item, bodega, tipo de movimiento y rango de fechas (con
 * {@link DatePickerField}: manual dd/MM/yyyy o selector visual). Es de SOLO LECTURA.
 *
 * Diseno responsive: titulo + tarjeta de filtros (GridBagLayout) + tabla estilizada
 * con scroll horizontal y anchos minimos, de modo que ninguna columna se corte
 * (y, si la ventana es pequena, aparece la barra de desplazamiento). La vista NO
 * ejecuta SQL: delega en los controladores.
 */
public class KardexView extends JPanel {

    private static final Dimension TAM_COMBO = new Dimension(220, 28);

    private final InventarioController inventarioController = new InventarioController();
    private final ItemController itemController = new ItemController();
    private final BodegaController bodegaController = new BodegaController();

    private EntityLookupField<Item> lkpItem;
    private EntityLookupField<Bodega> lkpBodega;
    private final JComboBox<Object> cmbTipo = new JComboBox<>();
    private final DatePickerField fechaDesde = new DatePickerField();
    private final DatePickerField fechaHasta = new DatePickerField();

    private final DefaultTableModel modeloTabla;
    private final JTable tabla;

    public KardexView() {
        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        modeloTabla = new DefaultTableModel(
                new Object[]{"Fecha", "Item", "Bodega", "Tipo", "Cantidad", "Motivo", "Usuario", "Lote"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modeloTabla);
        TableUtils.estilizar(tabla);
        TableUtils.conScrollHorizontal(tabla); // no truncar; scroll si no cabe
        TableUtils.anchos(tabla, 110, 220, 170, 140, 100, 280, 130);
        TableUtils.anchosMinimos(tabla, 100, 160, 130, 120, 80, 160, 100);
        TableUtils.alinearDerecha(tabla, 4); // Cantidad

        JPanel centro = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        centro.setOpaque(false);
        centro.add(crearPanelFiltros(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(tabla,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);
        centro.add(scroll, BorderLayout.CENTER);

        add(crearTitulo(), BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        poblarCombos();
        buscar();
    }

    private JComponent crearTitulo() {
        JLabel titulo = new JLabel("Kardex - Movimientos de inventario");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        return titulo;
    }

    private JComponent crearPanelFiltros() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(UiTheme.SUPERFICIE);
        panel.setBorder(UiTheme.tarjeta());

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 12);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.NONE;

        dimensionar(cmbTipo);
        // Busqueda inteligente como filtro de item y bodega (vacio = todos).
        lkpItem = new EntityLookupField<>(SearchSpecs.items(itemController));
        lkpBodega = new EntityLookupField<>(SearchSpecs.bodegas(bodegaController));
        lkpItem.setPreferredSize(new Dimension(240, 34));
        lkpBodega.setPreferredSize(new Dimension(240, 34));
        cmbTipo.setRenderer(ComboBoxRenderers.amigable());
        fechaDesde.setAnchoCampo(110);
        fechaHasta.setAnchoCampo(110);

        // Fila 1: Item, Bodega, Tipo
        g.gridy = 0;
        g.gridx = 0; panel.add(etiqueta("Item:"), g);
        g.gridx = 1; panel.add(lkpItem, g);
        g.gridx = 2; panel.add(etiqueta("Bodega:"), g);
        g.gridx = 3; panel.add(lkpBodega, g);
        g.gridx = 4; panel.add(etiqueta("Tipo:"), g);
        g.gridx = 5; panel.add(cmbTipo, g);

        // Fila 2: Desde, Hasta, botones
        g.gridy = 1;
        g.gridx = 0; panel.add(etiqueta("Desde:"), g);
        g.gridx = 1; panel.add(fechaDesde, g);
        g.gridx = 2; panel.add(etiqueta("Hasta:"), g);
        g.gridx = 3; panel.add(fechaHasta, g);

        JButton btnBuscar = UiTheme.botonPrimario(new JButton("Buscar"));
        btnBuscar.addActionListener(e -> buscar());
        JButton btnLimpiar = UiTheme.botonSecundario(new JButton("Limpiar"));
        btnLimpiar.addActionListener(e -> limpiarFiltros());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        botones.setOpaque(false);
        botones.add(btnBuscar);
        botones.add(btnLimpiar);
        g.gridx = 4; g.gridwidth = 2; panel.add(botones, g);
        g.gridwidth = 1;

        JLabel hint = new JLabel("Fechas en dd/MM/yyyy (use el calendario si prefiere). Deje vacios los filtros que no necesite.");
        hint.setFont(UiTheme.fuentePequena());
        hint.setForeground(UiTheme.TEXTO_TENUE);
        g.gridy = 2; g.gridx = 0; g.gridwidth = 6;
        panel.add(hint, g);

        return panel;
    }

    private void dimensionar(JComponent c) {
        c.setPreferredSize(TAM_COMBO);
        c.setMinimumSize(TAM_COMBO);
    }

    private JLabel etiqueta(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        return l;
    }

    private void poblarCombos() {
        try {
            cmbTipo.addItem("(Todos)");
            for (TipoMovimiento t : TipoMovimiento.values()) {
                cmbTipo.addItem(t);
            }
        } catch (Exception ex) {
            mostrarError("No se pudieron cargar los filtros: " + ex.getMessage());
        }
    }

    private void buscar() {
        Item item = lkpItem.getSeleccionado();
        Integer idItem = (item == null) ? null : item.getIdItem();

        Bodega bodega = lkpBodega.getSeleccionado();
        Integer idBodega = (bodega == null) ? null : bodega.getIdBodega();

        Object tipoSel = cmbTipo.getSelectedItem();
        TipoMovimiento tipo = (tipoSel instanceof TipoMovimiento) ? (TipoMovimiento) tipoSel : null;

        // Las fechas se convierten de dd/MM/yyyy (vista) a ISO (BD). Si son invalidas, se avisa.
        String desde;
        String hasta;
        try {
            desde = fechaDesde.getTextoIso();
            hasta = fechaHasta.getTextoIso();
        } catch (IllegalArgumentException ex) {
            mostrarError(ex.getMessage());
            return;
        }

        try {
            List<MovimientoInventario> movimientos = inventarioController.listarMovimientos(
                    idItem, idBodega, tipo, desde == null ? "" : desde, hasta == null ? "" : hasta);

            modeloTabla.setRowCount(0);
            for (MovimientoInventario m : movimientos) {
                modeloTabla.addRow(new Object[]{
                        DateFormatter.isoAVista(m.getFecha()), // muestra dd/MM/yyyy
                        m.getNombreItem(),
                        m.getNombreBodega(),
                        m.getTipo(),
                        m.getCantidad() == null ? "" : MoneyFormatter.cantidad(m.getCantidad()),
                        m.getMotivo() == null ? "" : m.getMotivo(),
                        m.getNombreUsuario() == null ? "" : m.getNombreUsuario(),
                        m.getNumeroLote() == null ? "" : m.getNumeroLote() // Paso J: lote (vacio si no aplica)
                });
            }
        } catch (Exception ex) {
            mostrarError("Error al consultar el Kardex: " + ex.getMessage());
        }
    }

    private void limpiarFiltros() {
        lkpItem.limpiar();
        lkpBodega.limpiar();
        cmbTipo.setSelectedIndex(0);
        fechaDesde.setFecha(null);
        fechaHasta.setFecha(null);
        buscar();
    }

    private void mostrarError(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Kardex", JOptionPane.ERROR_MESSAGE);
    }
}
