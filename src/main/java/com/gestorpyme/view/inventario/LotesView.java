package com.gestorpyme.view.inventario;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.LoteController;
import com.gestorpyme.view.components.SearchSpecs;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.*;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.table.DefaultTableModel;
import java.awt.Dimension;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista del modulo Lotes y vencimientos.
 *
 * Muestra los lotes en una tabla estilizada y permite: registrar uno nuevo,
 * filtrar los proximos a vencer, marcar un lote como INACTIVO y refrescar.
 *
 * La vista NO ejecuta SQL: delega en los controladores.
 */
public class LotesView extends JPanel {

    private static final int DIAS_PROXIMOS = 30;

    private final LoteController loteController = new LoteController();
    private final ItemController itemController = new ItemController();
    private final BodegaController bodegaController = new BodegaController();

    private final DefaultTableModel modeloTabla;
    private final JTable tabla;

    // Lista en memoria: cada fila de la tabla corresponde al lote del mismo indice (ya filtrado).
    private List<Lote> lotes = new ArrayList<>();
    // Lista base sin filtrar (resultado de la ultima carga). Paso H.
    private List<Lote> lotesBase = new ArrayList<>();
    // Filtros (Paso H).
    private final EntityLookupField<Item> lkpItemFiltro =
            new EntityLookupField<>(SearchSpecs.items(new ItemController()));
    private final JComboBox<Object> cmbBodegaFiltro = new JComboBox<>();
    private final JComboBox<String> cmbVencimientoFiltro =
            new JComboBox<>(new String[]{"Todos", "VIGENTE", "POR VENCER", "VENCIDO", "SIN FECHA"});

    public LotesView() {
        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        modeloTabla = new DefaultTableModel(
                new Object[]{"ID", "Codigo item", "Item", "Bodega", "Lote", "Ingreso", "Vencimiento",
                        "Dias", "Cantidad", "Estado", "Estado venc."}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modeloTabla);
        TableUtils.estilizar(tabla);
        TableUtils.anchos(tabla, 40, 90, 170, 130, 100, 95, 100, 60, 80, 80, 100);
        TableUtils.alinearDerecha(tabla, 8); // Cantidad

        JPanel norte = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        norte.setOpaque(false);
        norte.add(crearTitulo(), BorderLayout.NORTH);
        norte.add(crearBarraBotones(), BorderLayout.CENTER);
        norte.add(crearBarraFiltros(), BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        cargarFiltroBodegas();
        lkpItemFiltro.setAlCambiar(this::aplicarFiltros);
        cmbBodegaFiltro.addActionListener(e -> aplicarFiltros());
        cmbVencimientoFiltro.addActionListener(e -> aplicarFiltros());

        cargarTodos();
    }

    private JComponent crearTitulo() {
        JLabel titulo = new JLabel("Lotes y vencimientos");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        return titulo;
    }

    private JComponent crearBarraBotones() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        barra.setOpaque(false);

        JButton btnNuevo = UiTheme.botonPrimario(new JButton("Nuevo lote"));
        btnNuevo.addActionListener(e -> abrirFormularioNuevo());

        JButton btnProximos = UiTheme.botonSecundario(new JButton("Proximos a vencer (30 dias)"));
        btnProximos.addActionListener(e -> cargarProximosAVencer());

        JButton btnInactivar = UiTheme.botonSecundario(new JButton("Marcar inactivo"));
        btnInactivar.addActionListener(e -> marcarInactivo());

        JButton btnRefrescar = UiTheme.botonSecundario(new JButton("Refrescar"));
        btnRefrescar.addActionListener(e -> cargarTodos());

        barra.add(btnNuevo);
        barra.add(btnProximos);
        barra.add(btnInactivar);
        barra.add(btnRefrescar);
        return barra;
    }

    /** Carga todos los lotes en la tabla. */
    private void cargarTodos() {
        try {
            lotesBase = loteController.listar();
            aplicarFiltros();
        } catch (Exception ex) {
            error("No se pudieron cargar los lotes: " + ex.getMessage());
        }
    }

    /** Carga solo los lotes proximos a vencer (30 dias por defecto). */
    private void cargarProximosAVencer() {
        try {
            lotesBase = loteController.listarProximosAVencer(DIAS_PROXIMOS);
            aplicarFiltros();
            if (lotes.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No hay lotes proximos a vencer en " + DIAS_PROXIMOS + " dias.",
                        "Lotes", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            error("No se pudo filtrar: " + ex.getMessage());
        }
    }

    /** Aplica los filtros (producto, bodega, estado de vencimiento) sobre la lista base. Paso H. */
    private void aplicarFiltros() {
        Item item = lkpItemFiltro.getSeleccionado();
        Integer idItem = item == null ? null : item.getIdItem();
        Object selB = cmbBodegaFiltro.getSelectedItem();
        Integer idBodega = (selB instanceof Bodega) ? ((Bodega) selB).getIdBodega() : null;
        Object selV = cmbVencimientoFiltro.getSelectedItem();
        String estadoVenc = (selV == null || "Todos".equals(selV)) ? null : selV.toString();
        lotes = loteController.filtrar(lotesBase, idItem, idBodega, estadoVenc);
        pintarTabla();
    }

    /** Carga el combo de bodegas del filtro: "(Todas)" + bodegas. */
    private void cargarFiltroBodegas() {
        cmbBodegaFiltro.addItem("Todas");
        try {
            for (Bodega b : bodegaController.listar()) {
                cmbBodegaFiltro.addItem(b);
            }
        } catch (Exception ex) {
            // Si falla la carga de bodegas, el filtro queda solo con "Todas".
        }
    }

    /** Barra de filtros (Paso H): producto, bodega y estado de vencimiento. */
    private JComponent crearBarraFiltros() {
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        barra.setOpaque(false);
        lkpItemFiltro.setPreferredSize(new Dimension(240, 30));
        barra.add(new JLabel("Producto:"));
        barra.add(lkpItemFiltro);
        barra.add(new JLabel("Bodega:"));
        barra.add(cmbBodegaFiltro);
        barra.add(new JLabel("Vencimiento:"));
        barra.add(cmbVencimientoFiltro);
        JButton limpiar = UiTheme.botonSecundario(new JButton("Limpiar filtros"));
        limpiar.addActionListener(e -> {
            lkpItemFiltro.limpiar();
            cmbBodegaFiltro.setSelectedIndex(0);
            cmbVencimientoFiltro.setSelectedIndex(0);
            aplicarFiltros();
        });
        barra.add(limpiar);
        return barra;
    }

    /** Vuelca la lista en memoria a las filas de la tabla. */
    private void pintarTabla() {
        modeloTabla.setRowCount(0);
        for (Lote l : lotes) {
            Long dias = l.diasParaVencer();
            modeloTabla.addRow(new Object[]{
                    l.getIdLote(),
                    l.getCodigoItem() == null ? "" : l.getCodigoItem(),
                    l.getNombreItem(),
                    l.getNombreBodega() == null ? "(Sin bodega)" : l.getNombreBodega(),
                    l.getNumeroLote(),
                    l.getFechaIngreso() == null ? "" : l.getFechaIngreso(),
                    l.getFechaVencimiento() == null ? "" : l.getFechaVencimiento(),
                    dias == null ? "" : dias,
                    l.getCantidadInicial() == null ? "" : l.getCantidadInicial().toPlainString(),
                    l.getEstado(),
                    l.estadoVencimiento()
            });
        }
    }

    private void abrirFormularioNuevo() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        LoteFormDialog dialog = new LoteFormDialog(owner, loteController, itemController, bodegaController);
        dialog.setVisible(true);
        if (dialog.isGuardado()) {
            cargarTodos();
        }
    }

    /** Marca el lote seleccionado como INACTIVO. */
    private void marcarInactivo() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione un lote en la tabla.");
            return;
        }
        Lote lote = lotes.get(fila);
        try {
            loteController.cambiarEstado(lote.getIdLote(), EstadoLote.INACTIVO);
            cargarTodos();
        } catch (Exception ex) {
            error("No se pudo cambiar el estado: " + ex.getMessage());
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Lotes", JOptionPane.WARNING_MESSAGE);
    }
}
