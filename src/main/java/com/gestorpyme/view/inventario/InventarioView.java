package com.gestorpyme.view.inventario;
import com.gestorpyme.view.components.TableUtils;

import com.gestorpyme.controller.InventarioController;
import com.gestorpyme.controller.InventarioLogisticoController;
import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.ItemLogistico;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.domain.model.ConciliacionLoteStockItem;
import com.gestorpyme.view.compras.NuevaOrdenDialog;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.util.MoneyFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Modulo "Bodegas e inventario". Presenta dos pestanas:
 *  - Existencias: stock actual por item y bodega, con boton para registrar movimientos.
 *  - Bodegas: CRUD de bodegas ({@link BodegasView}).
 * Solo presenta datos y delega en {@link InventarioController}; NO contiene reglas ni SQL.
 * Capa: view.
 */
public class InventarioView extends JPanel {

    private static final String FUENTE = "Segoe UI";
    private static final Color COLOR_PRIMARIO = new Color(0x05, 0x1E, 0x22);
    private static final String[] COLUMNAS = {"Codigo", "Item", "Categoría", "Subtipo",
            "Bodega", "Ubicación", "Cantidad", "Stock mínimo", "Estado de stock"};

    private final InventarioController controller = new InventarioController();
    private final ItemController itemController = new ItemController();
    private final BodegaController bodegaController = new BodegaController();
    /** Filtros de busqueda inteligente (vacio = todos). */
    private final EntityLookupField<Item> lkpItemFiltro = new EntityLookupField<>(SearchSpecs.items(itemController));
    private final EntityLookupField<Bodega> lkpBodegaFiltro = new EntityLookupField<>(SearchSpecs.bodegas(bodegaController));
    // Filtros adicionales (Paso B): combinables con los anteriores; en memoria.
    private final JComboBox<String> cmbCategoria = new JComboBox<>();
    private final JComboBox<String> cmbSubtipo = new JComboBox<>();
    private final JComboBox<String> cmbEstado = new JComboBox<>(
            new String[]{"Todos", "SIN STOCK", "BAJO", "NORMAL"});
    private final JTextField txtUbicacionFiltro = new JTextField(12);
    /** Existencias completas en memoria (se filtran sin volver a consultar la base). */
    private final List<ExistenciaStock> todasExistencias = new ArrayList<>();
    /** Las opciones de categoria/subtipo se pueblan una sola vez. */
    private boolean filtrosCategoriaSubtipoPoblados = false;
    private static final String TODAS = "Todas";
    private static final String TODOS = "Todos";
    /** Usuario que registra los movimientos (queda en el Kardex). */
    private final Integer idUsuario;

    private final DefaultTableModel modeloExistencias;
    /** Existencias visibles en la tabla, en el mismo orden que las filas (para acciones por fila). */
    private final List<ExistenciaStock> existenciasMostradas = new ArrayList<>();
    private final JTable tablaExistencias;

    /** Inventario logistico (reabastecimiento): por item, con sugerido y estado. */
    private final InventarioLogisticoController logisticoController = new InventarioLogisticoController();
    private final DefaultTableModel modeloLogistico = new DefaultTableModel(
            new String[]{"Codigo", "Producto", "Stock actual", "Stock minimo", "Stock maximo", "En pedido", "Sugerido", "Proveedor pref.", "Estado"}, 0) {
        private static final long serialVersionUID = 2L;
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaLogistico = new JTable(modeloLogistico);
    /** Copia en memoria de las filas logisticas (fila N = filasLogistico.get(N)). */
    private final List<ItemLogistico> filasLogistico = new ArrayList<>();

    /** Conciliación lote vs stock (Paso Q): solo lectura, sin acciones de modificación. */
    private final DefaultTableModel modeloConciliacion = new DefaultTableModel(
            new String[]{"Codigo", "Item", "Bodega", "Stock actual", "Stock loteado", "Diferencia", "Estado"}, 0) {
        private static final long serialVersionUID = 3L;
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;
        }
    };
    private final JTable tablaConciliacion = new JTable(modeloConciliacion);

    /**
     * @param idUsuario id del usuario autenticado (se guarda en cada movimiento).
     */
    public InventarioView(Integer idUsuario) {
        this.idUsuario = idUsuario;
        setLayout(new BorderLayout());
        setOpaque(false);

        modeloExistencias = new DefaultTableModel(COLUMNAS, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        tablaExistencias = new JTable(modeloExistencias);
        TableUtils.estilizar(tablaExistencias);
        tablaExistencias.setRowHeight(26);
        tablaExistencias.getTableHeader().setReorderingAllowed(false);
        tablaExistencias.setFont(new Font(FUENTE, Font.PLAIN, 13));

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.addTab("Existencias", crearPanelExistencias());
        pestanas.addTab("Reabastecimiento", crearPanelReabastecimiento());
        pestanas.addTab("Conciliación lote vs stock", crearPanelConciliacion());
        pestanas.addTab("Bodegas", new BodegasView());
        add(pestanas, BorderLayout.CENTER);

        // Al elegir un filtro, la tabla se actualiza automaticamente.
        lkpItemFiltro.setAlCambiar(this::aplicarFiltros);
        lkpBodegaFiltro.setAlCambiar(this::aplicarFiltros);

        recargarExistencias();
        recargarReabastecimiento();
        recargarConciliacion();
    }

    /** Pestana de inventario logistico: stock actual, minimo, en pedido, sugerido y estado. */
    private JComponent crearPanelReabastecimiento() {
        TableUtils.estilizar(tablaLogistico);
        tablaLogistico.setRowHeight(26);
        tablaLogistico.getTableHeader().setReorderingAllowed(false);
        tablaLogistico.setFont(new Font(FUENTE, Font.PLAIN, 13));
        tablaLogistico.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barra.setOpaque(false);
        JButton btnGenerar = crearBoton("Generar orden de compra", true, false);
        btnGenerar.addActionListener(e -> generarOrdenDesdeSeleccion());
        barra.add(btnGenerar);
        JButton btnRefrescar = crearBoton("Refrescar", false, false);
        btnRefrescar.addActionListener(e -> recargarReabastecimiento());
        barra.add(btnRefrescar);
        barra.add(new JLabel("Seleccione productos y genere una OC. Sugerido: si hay stock máximo, repone hasta el máximo cuando falta al mínimo; si no, hasta el mínimo (0 si en pedido cubre el mínimo)."));

        panel.add(barra, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaLogistico), BorderLayout.CENTER);
        return panel;
    }

    /** Carga la tabla de reabastecimiento desde el controlador (sin SQL en la vista). */
    private void recargarReabastecimiento() {
        try {
            List<ItemLogistico> filas = logisticoController.listar();
            filasLogistico.clear();
            filasLogistico.addAll(filas);
            modeloLogistico.setRowCount(0);
            for (ItemLogistico it : filas) {
                modeloLogistico.addRow(new Object[]{
                        it.getCodigo(), it.getNombre(), num(it.getStockActual()),
                        num(it.getStockMinimo()),
                        it.getStockMaximo() == null ? "" : num(it.getStockMaximo()),
                        num(it.getEnPedido()), num(it.getSugerido()),
                        it.getNombreProveedorPreferido() == null ? "" : it.getNombreProveedorPreferido(),
                        it.getEstado()
                });
            }
            TableUtils.alinearDerecha(tablaLogistico, 2, 3, 4, 5);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar el reabastecimiento:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Genera una orden de compra a partir de los productos seleccionados (con sugerido &gt; 0).
     * Pide una bodega destino, propone las cantidades sugeridas y abre el dialogo de orden
     * precargado. La orden NO aumenta stock; solo la recepcion lo hace.
     */
    private void generarOrdenDesdeSeleccion() {
        int[] filas = tablaLogistico.getSelectedRows();
        if (filas.length == 0) {
            JOptionPane.showMessageDialog(this, "Seleccione uno o varios productos en la tabla.",
                    "Generar orden", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Bodega destino para las lineas sugeridas.
        Bodega bodega = pedirBodegaDestino();
        if (bodega == null) {
            return;
        }

        List<OrdenCompraDetalle> sugeridas = new ArrayList<>();
        // Paso F: proveedores preferidos de los items efectivamente incluidos (sugerido > 0).
        java.util.Set<Integer> provsPreferidos = new java.util.LinkedHashSet<>();
        boolean algunSinProveedor = false;
        Integer idProvComun = null;
        String nombreProvComun = null;
        for (int fila : filas) {
            ItemLogistico it = filasLogistico.get(fila);
            BigDecimal sugerido = it.getSugerido() == null ? BigDecimal.ZERO : it.getSugerido();
            if (sugerido.signum() <= 0) {
                continue; // sin faltante: no se incluye
            }
            if (it.getIdProveedorPreferido() == null) {
                algunSinProveedor = true;
            } else {
                provsPreferidos.add(it.getIdProveedorPreferido());
                idProvComun = it.getIdProveedorPreferido();
                nombreProvComun = it.getNombreProveedorPreferido();
            }
            OrdenCompraDetalle d = new OrdenCompraDetalle();
            d.setIdItem(it.getIdItem());
            d.setNombreItem(it.getNombre());
            d.setIdBodegaDestino(bodega.getIdBodega());
            d.setNombreBodega(bodega.getNombre());
            d.setCantidadSolicitada(sugerido);
            d.setPrecioUnitario(it.getPrecioCompra() == null ? BigDecimal.ZERO : it.getPrecioCompra());
            sugeridas.add(d);
        }

        if (sugeridas.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Los productos seleccionados no tienen cantidad sugerida (stock por encima del minimo).",
                    "Generar orden", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Window ventana = SwingUtilities.getWindowAncestor(this);
        // Paso F: Caso A (todos el mismo proveedor preferido) -> preseleccionar;
        // Caso B (proveedores distintos o mezcla con productos sin proveedor) -> avisar y elegir manual;
        // Caso C (ninguno tiene proveedor preferido) -> flujo actual.
        Tercero proveedorPreferido = null;
        boolean todosMismoProveedor = provsPreferidos.size() == 1 && !algunSinProveedor;
        if (todosMismoProveedor) {
            proveedorPreferido = new Tercero();
            proveedorPreferido.setIdTercero(idProvComun);
            proveedorPreferido.setNombre(nombreProvComun);
        } else if (!provsPreferidos.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Los productos seleccionados tienen proveedores preferidos diferentes. "
                            + "Seleccione el proveedor manualmente.",
                    "Proveedor preferido", JOptionPane.INFORMATION_MESSAGE);
        }
        NuevaOrdenDialog dlg = new NuevaOrdenDialog(ventana, sugeridas, proveedorPreferido);
        dlg.setVisible(true);
        if (dlg.fueGuardada()) {
            recargarReabastecimiento();
        }
    }

    /** Muestra un combo con las bodegas activas y devuelve la elegida (o null si cancela). */
    private Bodega pedirBodegaDestino() {
        try {
            List<Bodega> activas = new ArrayList<>();
            for (Bodega b : bodegaController.listar()) {
                if (b.getEstado() == com.gestorpyme.domain.enums.EstadoRegistro.ACTIVO) {
                    activas.add(b);
                }
            }
            if (activas.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No hay bodegas activas. Cree una primero.",
                        "Generar orden", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            JComboBox<Bodega> combo = new JComboBox<>(activas.toArray(new Bodega[0]));
            int op = JOptionPane.showConfirmDialog(this, combo, "Bodega destino de la orden",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (op != JOptionPane.OK_OPTION) {
                return null;
            }
            return (Bodega) combo.getSelectedItem();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar las bodegas:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    /** Formatea una cantidad BigDecimal sin ceros sobrantes (p. ej. 5 en vez de 5.0). */
    private static String num(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    /**
     * Pestaña "Conciliación lote vs stock" (Paso Q): tabla de solo lectura que compara el stock
     * autoritativo con el stock loteado por item + bodega. No tiene acciones de modificación.
     */
    private JComponent crearPanelConciliacion() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barra.setOpaque(false);
        JButton btnRefrescar = crearBoton("Refrescar", false, false);
        btnRefrescar.addActionListener(e -> recargarConciliacion());
        barra.add(btnRefrescar);
        barra.add(new JLabel("Solo lectura. Compara inventario (autoritativo) con la suma de lotes "
                + "por item y bodega. Una diferencia no siempre es error (puede ser una entrada sin lote)."));

        TableUtils.estilizar(tablaConciliacion);
        tablaConciliacion.setRowHeight(26);
        tablaConciliacion.getTableHeader().setReorderingAllowed(false);
        tablaConciliacion.setFont(new Font(FUENTE, Font.PLAIN, 13));
        TableUtils.alinearDerecha(tablaConciliacion, 3, 4, 5); // stock actual, loteado, diferencia
        // La columna Estado se colorea segun su valor (verde/ambar/rojo).
        tablaConciliacion.getColumnModel().getColumn(6).setCellRenderer(new EstadoConciliacionRenderer());

        panel.add(barra, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaConciliacion), BorderLayout.CENTER);
        return panel;
    }

    /** Carga (o recarga) la conciliación desde el controlador. Solo lectura. */
    private void recargarConciliacion() {
        try {
            List<ConciliacionLoteStockItem> filas = controller.conciliacionLoteStock();
            modeloConciliacion.setRowCount(0);
            for (ConciliacionLoteStockItem c : filas) {
                modeloConciliacion.addRow(new Object[]{
                        c.getCodigoItem() == null ? "" : c.getCodigoItem(),
                        c.getNombreItem(),
                        c.getNombreBodega(),
                        MoneyFormatter.cantidad(c.getStockActual()),
                        MoneyFormatter.cantidad(c.getStockLoteado()),
                        MoneyFormatter.cantidad(c.getDiferencia()),
                        c.getEstado()
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar la conciliación:\n" + ex.getMessage(),
                    "Conciliación lote vs stock", JOptionPane.WARNING_MESSAGE);
        }
    }

    /** Renderer que colorea la celda de Estado con semántica: verde OK, ámbar faltante, rojo exceso. */
    private static final class EstadoConciliacionRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionada,
                boolean foco, int fila, int columna) {
            Component c = super.getTableCellRendererComponent(tabla, valor, seleccionada, foco, fila, columna);
            if (!seleccionada) {
                String estado = valor == null ? "" : valor.toString();
                Color color;
                if (ConciliacionLoteStockItem.ESTADO_OK.equals(estado)) {
                    color = AppPalette.EXITO;
                } else if (ConciliacionLoteStockItem.ESTADO_EXCESO_LOTEADO.equals(estado)) {
                    color = AppPalette.PELIGRO;
                } else {
                    color = AppPalette.ADVERTENCIA; // FALTA_LOTEAR / SIN_LOTES
                }
                c.setForeground(color);
            }
            return c;
        }
    }

    private JComponent crearPanelExistencias() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barra.setOpaque(false);
        barra.add(crearBoton("Registrar movimiento", true, true));
        JButton btnUbicacion = new JButton("Editar ubicación");
        btnUbicacion.setFocusPainted(false);
        btnUbicacion.setFont(new Font(FUENTE, Font.PLAIN, 13));
        btnUbicacion.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnUbicacion.addActionListener(e -> editarUbicacionSeleccionada());
        barra.add(btnUbicacion);
        barra.add(crearBoton("Refrescar", false, false));
        // Filtros de busqueda inteligente.
        lkpItemFiltro.setPreferredSize(new Dimension(220, 32));
        lkpBodegaFiltro.setPreferredSize(new Dimension(220, 32));
        barra.add(new JLabel("Item:"));
        barra.add(lkpItemFiltro);
        barra.add(new JLabel("Bodega:"));
        barra.add(lkpBodegaFiltro);
        JButton btnLimpiar = crearBoton("Limpiar filtro", false, false);
        btnLimpiar.addActionListener(e -> limpiarFiltros());
        barra.add(btnLimpiar);

        // Segunda fila de filtros (Paso B): categoria, subtipo, estado de stock y ubicacion.
        JPanel barra2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        barra2.setOpaque(false);
        barra2.add(new JLabel("Categoría:"));
        barra2.add(cmbCategoria);
        barra2.add(new JLabel("Subtipo:"));
        barra2.add(cmbSubtipo);
        barra2.add(new JLabel("Estado:"));
        barra2.add(cmbEstado);
        barra2.add(new JLabel("Ubicación:"));
        barra2.add(txtUbicacionFiltro);
        // Al cambiar cualquier filtro nuevo, se reaplica en memoria.
        cmbCategoria.addActionListener(e -> aplicarFiltros());
        cmbSubtipo.addActionListener(e -> aplicarFiltros());
        cmbEstado.addActionListener(e -> aplicarFiltros());
        txtUbicacionFiltro.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { aplicarFiltros(); }
            public void removeUpdate(DocumentEvent e) { aplicarFiltros(); }
            public void changedUpdate(DocumentEvent e) { aplicarFiltros(); }
        });

        JPanel barras = new JPanel();
        barras.setOpaque(false);
        barras.setLayout(new javax.swing.BoxLayout(barras, javax.swing.BoxLayout.Y_AXIS));
        barras.add(barra);
        barras.add(barra2);

        panel.add(barras, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaExistencias), BorderLayout.CENTER);
        return panel;
    }

    private JButton crearBoton(String texto, boolean primario, boolean abreMovimiento) {
        JButton boton = new JButton(texto);
        boton.setFocusPainted(false);
        boton.setFont(new Font(FUENTE, primario ? Font.BOLD : Font.PLAIN, 13));
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if (primario) {
            boton.setBackground(COLOR_PRIMARIO);
            boton.setForeground(Color.WHITE);
        }
        if (abreMovimiento) {
            boton.addActionListener(e -> abrirMovimiento());
        } else {
            boton.addActionListener(e -> recargarExistencias());
        }
        return boton;
    }

    private void abrirMovimiento() {
        MovimientoFormDialog dialogo = new MovimientoFormDialog(
                SwingUtilities.getWindowAncestor(this), controller, idUsuario);
        dialogo.setVisible(true);
        if (dialogo.fueGuardado()) {
            recargarExistencias();
        }
    }

    /**
     * Edita la ubicacion interna de la existencia seleccionada (Paso A). Pide el texto en un
     * dialogo simple y lo guarda. NO modifica la cantidad ni genera movimiento de Kardex.
     */
    private void editarUbicacionSeleccionada() {
        int fila = tablaExistencias.getSelectedRow();
        if (fila < 0 || fila >= existenciasMostradas.size()) {
            JOptionPane.showMessageDialog(this, "Seleccione una existencia en la tabla.",
                    "Editar ubicación", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        ExistenciaStock e = existenciasMostradas.get(fila);
        String actual = e.getUbicacionInterna() == null ? "" : e.getUbicacionInterna();
        String nueva = (String) JOptionPane.showInputDialog(this,
                "Ubicación interna de " + e.getNombreItem() + " en " + e.getNombreBodega()
                        + "\n(ej. Estante A1; deje vacío para quitarla):",
                "Editar ubicación", JOptionPane.PLAIN_MESSAGE, null, null, actual);
        if (nueva == null) {
            return; // cancelado
        }
        try {
            controller.actualizarUbicacion(e.getIdItem(), e.getIdBodega(), nueva);
            recargarExistencias();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudo actualizar la ubicación:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Recarga la tabla de existencias desde la base de datos. */
    /** Recarga las existencias desde la base de datos y reaplica los filtros. */
    private void recargarExistencias() {
        try {
            todasExistencias.clear();
            todasExistencias.addAll(controller.listarExistencias());
            if (!filtrosCategoriaSubtipoPoblados) {
                poblarFiltrosCategoriaSubtipo();
                filtrosCategoriaSubtipoPoblados = true;
            }
            aplicarFiltros();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar las existencias:\n" + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Aplica los filtros actuales (item, bodega, categoria, subtipo, estado, ubicacion) sobre las
     * existencias ya cargadas en memoria y reconstruye la tabla. No consulta la base de datos.
     */
    private void aplicarFiltros() {
        String codigoItem = lkpItemFiltro.getSeleccionado() == null ? null : lkpItemFiltro.getSeleccionado().getCodigo();
        String bodega = lkpBodegaFiltro.getSeleccionado() == null ? null : lkpBodegaFiltro.getSeleccionado().getNombre();
        String categoria = valorFiltro(cmbCategoria, TODAS);
        String subtipo = valorFiltro(cmbSubtipo, TODOS);
        String estado = valorFiltro(cmbEstado, TODOS);
        String ubicacion = txtUbicacionFiltro.getText();

        List<ExistenciaStock> filtradas = controller.filtrarExistencias(
                todasExistencias, codigoItem, bodega, categoria, subtipo, estado, ubicacion);

        modeloExistencias.setRowCount(0);
        existenciasMostradas.clear();
        for (ExistenciaStock e : filtradas) {
            existenciasMostradas.add(e);
            modeloExistencias.addRow(new Object[]{
                textoSeguro(e.getCodigoItem()),
                e.getNombreItem(),
                textoSeguro(e.getNombreCategoria()),
                textoSeguro(e.getNombreSubtipo()),
                e.getNombreBodega(),
                textoSeguro(e.getUbicacionInterna()),
                formatoCantidad(e.getCantidad()),
                formatoCantidad(e.getStockMinimo()),
                e.getEstadoStock()
            });
        }
    }

    /** Puebla los combos de categoria y subtipo con los valores presentes en las existencias. */
    private void poblarFiltrosCategoriaSubtipo() {
        java.util.TreeSet<String> categorias = new java.util.TreeSet<>();
        java.util.TreeSet<String> subtipos = new java.util.TreeSet<>();
        for (ExistenciaStock e : todasExistencias) {
            if (e.getNombreCategoria() != null && !e.getNombreCategoria().isEmpty()) {
                categorias.add(e.getNombreCategoria());
            }
            if (e.getNombreSubtipo() != null && !e.getNombreSubtipo().isEmpty()) {
                subtipos.add(e.getNombreSubtipo());
            }
        }
        cmbCategoria.removeAllItems();
        cmbCategoria.addItem(TODAS);
        for (String c : categorias) {
            cmbCategoria.addItem(c);
        }
        cmbSubtipo.removeAllItems();
        cmbSubtipo.addItem(TODOS);
        for (String s : subtipos) {
            cmbSubtipo.addItem(s);
        }
    }

    /** Devuelve el valor del combo, o null si esta en su opcion "todas/todos". */
    private static String valorFiltro(JComboBox<String> combo, String sentinela) {
        Object sel = combo.getSelectedItem();
        if (sel == null || sentinela.equals(sel)) {
            return null;
        }
        return sel.toString();
    }

    /** Limpia todos los filtros y reaplica (muestra todas las existencias). */
    private void limpiarFiltros() {
        lkpItemFiltro.limpiar();
        lkpBodegaFiltro.limpiar();
        if (cmbCategoria.getItemCount() > 0) {
            cmbCategoria.setSelectedIndex(0);
        }
        if (cmbSubtipo.getItemCount() > 0) {
            cmbSubtipo.setSelectedIndex(0);
        }
        cmbEstado.setSelectedIndex(0);
        txtUbicacionFiltro.setText("");
        aplicarFiltros();
    }

    private String formatoCantidad(BigDecimal valor) {
        BigDecimal v = (valor != null) ? valor : BigDecimal.ZERO;
        return v.stripTrailingZeros().toPlainString();
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }
}
