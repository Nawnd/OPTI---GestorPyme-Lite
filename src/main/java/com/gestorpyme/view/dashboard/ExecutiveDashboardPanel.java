package com.gestorpyme.view.dashboard;

import com.gestorpyme.controller.DashboardController;
import com.gestorpyme.domain.model.DashboardEjecutivoResumen;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.components.ResponsiveGridPanel;
import com.gestorpyme.view.components.DashboardBigKpi;
import com.gestorpyme.view.components.DashboardMetricListPanel;
import com.gestorpyme.view.components.DashboardSectionPanel;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.time.LocalDate;

/**
 * Pestaña "Dashboard Gerencial" (Paso L). Capa: view.dashboard. Orientada a dirección y análisis.
 *
 * Reúne cuatro KPIs del período (ventas, utilidad y margen estimados, cartera), el panel financiero/comercial
 * con su aviso de utilidad estimada, el panel operativo (Kardex), el panel de alertas y el de metas
 * gerenciales (delegado en {@link MetaGerencialPanel}, que separa resumen y editor sin cortarse), más
 * subpestañas de Inventario, Compras (Paso G integrado) y Lotes. La carga es perezosa y los filtros año/mes/
 * semana recomputan el período. No modifica cálculos: consume {@code resumenEjecutivo} y solo presenta.
 */
public class ExecutiveDashboardPanel extends JPanel {

    private static final int ANCHO_COL_DER = 340;
    private static final int ANCHO_MIN_KPI = 210;

    private final DashboardController controller;
    private final JComboBox<Integer> cmbAnio = new JComboBox<>();
    private final JComboBox<String> cmbMes = new JComboBox<>();
    private final JComboBox<String> cmbSemana = new JComboBox<>();
    private final JLabel lblSubtitulo = new JLabel();
    private final JPanel contenido = new JPanel();
    private final MetaGerencialPanel metaPanel;
    private boolean filtrosListos = false;
    private boolean cargado = false;

    public ExecutiveDashboardPanel(DashboardController controller) {
        this.controller = controller;
        this.metaPanel = new MetaGerencialPanel(controller, this::refrescar);
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(UiTheme.margen());

        add(crearEncabezado());
        add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
        contenido.setOpaque(false);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setAlignmentX(LEFT_ALIGNMENT);
        add(contenido);
    }

    /** Carga (perezosa) el contenido la primera vez que se muestra la pestaña. */
    public void cargar() {
        if (cargado) {
            return;
        }
        cargado = true;
        poblarFiltros();
        refrescar();
    }

    private JComponent crearEncabezado() {
        JPanel enc = new JPanel(new BorderLayout(0, 4));
        enc.setOpaque(false);
        enc.setAlignmentX(LEFT_ALIGNMENT);
        enc.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        JPanel fila = new JPanel(new BorderLayout());
        fila.setOpaque(false);
        JLabel titulo = new JLabel("Dirección y análisis");
        titulo.setFont(UiTheme.fuente(Font.BOLD, 16));
        titulo.setForeground(AppPalette.PRIMARIO);

        JPanel filtros = new JPanel();
        filtros.setOpaque(false);
        JLabel la = new JLabel("Año: ");
        la.setForeground(AppPalette.TEXTO_SECUNDARIO);
        JLabel lm = new JLabel("  Mes: ");
        lm.setForeground(AppPalette.TEXTO_SECUNDARIO);
        JLabel ls = new JLabel("  Semana: ");
        ls.setForeground(AppPalette.TEXTO_SECUNDARIO);
        JButton btn = new JButton("Refrescar");
        btn.addActionListener(e -> refrescar());
        filtros.add(la);
        filtros.add(cmbAnio);
        filtros.add(lm);
        filtros.add(cmbMes);
        filtros.add(ls);
        filtros.add(cmbSemana);
        filtros.add(btn);

        fila.add(titulo, BorderLayout.WEST);
        fila.add(filtros, BorderLayout.EAST);

        lblSubtitulo.setFont(UiTheme.fuente(Font.PLAIN, 12));
        lblSubtitulo.setForeground(AppPalette.TEXTO_SECUNDARIO);

        enc.add(fila, BorderLayout.NORTH);
        enc.add(lblSubtitulo, BorderLayout.SOUTH);
        return enc;
    }

    private void poblarFiltros() {
        if (filtrosListos) {
            return;
        }
        int anioActual = LocalDate.now().getYear();
        for (int a = anioActual - 4; a <= anioActual; a++) {
            cmbAnio.addItem(a);
        }
        cmbAnio.setSelectedItem(anioActual);
        cmbMes.addItem("Todos");
        String[] meses = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
                "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        for (String m : meses) {
            cmbMes.addItem(m);
        }
        cmbMes.setSelectedIndex(LocalDate.now().getMonthValue());
        cmbSemana.addItem("Todas");
        for (int s = 1; s <= 4; s++) {
            cmbSemana.addItem("Semana " + s);
        }
        cmbSemana.setSelectedIndex(0);

        cmbAnio.addActionListener(e -> refrescar());
        cmbMes.addActionListener(e -> refrescar());
        cmbSemana.addActionListener(e -> refrescar());
        filtrosListos = true;
    }

    private Integer mesSeleccionado() {
        int idx = cmbMes.getSelectedIndex();
        return idx <= 0 ? null : idx;
    }

    private Integer semanaSeleccionada() {
        int idx = cmbSemana.getSelectedIndex();
        return idx <= 0 ? null : idx;
    }

    /** Reconstruye KPIs, paneles y subpestañas a partir del resumen ejecutivo del período. */
    private void refrescar() {
        if (!cargado) {
            return;
        }
        contenido.removeAll();
        try {
            Integer anio = (Integer) cmbAnio.getSelectedItem();
            if (anio == null) {
                anio = LocalDate.now().getYear();
            }
            DashboardEjecutivoResumen r = controller.resumenEjecutivo(anio, mesSeleccionado(), semanaSeleccionada());
            var f = r.getFinanciero();
            var inv = r.getInventario();
            var com = r.getCompras();
            var lot = r.getLotes();
            var op = r.getOperativo();

            lblSubtitulo.setText("Período: " + r.getEtiquetaPeriodo() + "   ·   "
                    + r.getFechaInicio() + " a " + r.getFechaFin());

            // ---- Fila superior: 4 KPIs principales (responsive: 4/2/1 columnas según ancho) ----
            ResponsiveGridPanel top = new ResponsiveGridPanel(4, ANCHO_MIN_KPI, UiTheme.MARGEN, UiTheme.MARGEN);
            top.add(new DashboardBigKpi("Ventas del período", MoneyFormatter.cop(f.getVentas()),
                    f.getCantidadVentas() + (f.getCantidadVentas() == 1 ? " venta" : " ventas"), AppPalette.PRIMARIO));
            top.add(new DashboardBigKpi("Utilidad estimada", MoneyFormatter.cop(f.getUtilidadEstimada()),
                    "Base: " + MoneyFormatter.cop(f.getBaseVentasConCosto()), AppPalette.EXITO));
            top.add(new DashboardBigKpi("Margen estimado",
                    f.isMargenDisponible() ? MoneyFormatter.porcentaje(f.getMargenEstimado().doubleValue()) : "No disp.",
                    f.isMargenDisponible() ? "Bruto estimado" : "Sin base con costo", AppPalette.EXITO));
            top.add(new DashboardBigKpi("Cartera pendiente", MoneyFormatter.cop(f.getCarteraPendiente()),
                    "Saldo actual", AppPalette.ADVERTENCIA));

            // ---- Cuerpo: dos columnas (izquierda Financiero+Operación, derecha Alertas+Metas) ----
            DashboardMetricListPanel finList = new DashboardMetricListPanel();
            finList.agregar("Ticket promedio", MoneyFormatter.cop(f.getTicketPromedio()));
            finList.agregar("Descuentos", MoneyFormatter.cop(f.getDescuentos()), AppPalette.ADVERTENCIA);
            finList.agregar("Ventas contado", MoneyFormatter.cop(f.getVentasContado()));
            finList.agregar("Ventas crédito", MoneyFormatter.cop(f.getVentasCredito()));
            finList.agregar("Pagos recibidos", MoneyFormatter.cop(f.getPagosRecibidos()), AppPalette.EXITO);
            JLabel caveat = new JLabel("<html><i>Utilidad estimada con precio de compra actual; no es costeo "
                    + "contable. " + f.getLineasSinCosto() + " líneas sin costo válido no incluidas.</i></html>");
            caveat.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            caveat.setForeground(AppPalette.TEXTO_SECUNDARIO);
            JPanel finContenido = new JPanel(new BorderLayout(0, 6));
            finContenido.setOpaque(false);
            finContenido.add(finList, BorderLayout.CENTER);
            finContenido.add(caveat, BorderLayout.SOUTH);
            DashboardSectionPanel panelFin = new DashboardSectionPanel("Financiero / Comercial", finContenido);
            panelFin.setAlignmentX(LEFT_ALIGNMENT);

            DashboardMetricListPanel opList = new DashboardMetricListPanel();
            opList.agregar("Movimientos Kardex", MoneyFormatter.entero(op.getMovimientosKardex()));
            opList.agregar("Salidas por venta", MoneyFormatter.entero(op.getSalidasVenta()));
            opList.agregar("Entradas por compra", MoneyFormatter.entero(op.getEntradasCompra()));
            opList.agregar("Ajustes manuales", MoneyFormatter.entero(op.getAjustesManuales()),
                    op.getAjustesManuales() > 0 ? AppPalette.ADVERTENCIA : AppPalette.PRIMARIO);
            opList.agregar("Producto top", op.getTopProductoNombre() == null ? "—"
                    : op.getTopProductoNombre() + " (" + MoneyFormatter.cantidad(op.getTopProductoCantidad()) + ")");
            opList.agregar("Bodega más activa", op.getBodegaMayorMovimientoNombre() == null ? "—"
                    : op.getBodegaMayorMovimientoNombre() + " (" + op.getBodegaMayorMovimientoCantidad() + ")");
            DashboardSectionPanel panelOp = new DashboardSectionPanel("Operación", opList);
            panelOp.setAlignmentX(LEFT_ALIGNMENT);

            JPanel colIzq = new JPanel();
            colIzq.setOpaque(false);
            colIzq.setLayout(new BoxLayout(colIzq, BoxLayout.Y_AXIS));
            colIzq.add(panelFin);
            colIzq.add(Box.createRigidArea(new Dimension(0, UiTheme.MARGEN)));
            colIzq.add(panelOp);

            DashboardMetricListPanel alertList = new DashboardMetricListPanel();
            alertList.agregar("Bajo stock", MoneyFormatter.entero(inv.getProductosBajoStock()),
                    inv.getProductosBajoStock() > 0 ? AppPalette.ADVERTENCIA : AppPalette.EXITO);
            alertList.agregar("Sin stock", MoneyFormatter.entero(inv.getProductosSinStock()),
                    inv.getProductosSinStock() > 0 ? AppPalette.PELIGRO : AppPalette.EXITO);
            alertList.agregar("Lotes por vencer", MoneyFormatter.entero(lot.getProximosAVencer()),
                    lot.getProximosAVencer() > 0 ? AppPalette.ADVERTENCIA : AppPalette.EXITO);
            alertList.agregar("Lotes vencidos", MoneyFormatter.entero(lot.getVencidos()),
                    lot.getVencidos() > 0 ? AppPalette.PELIGRO : AppPalette.EXITO);
            alertList.agregar("Valor en riesgo", MoneyFormatter.cop(lot.getValorEnRiesgoEstimado()),
                    lot.getValorEnRiesgoEstimado().signum() > 0 ? AppPalette.ADVERTENCIA : AppPalette.PRIMARIO);
            DashboardSectionPanel panelAlertas = new DashboardSectionPanel("Alertas", alertList);
            panelAlertas.setAlignmentX(LEFT_ALIGNMENT);

            metaPanel.actualizar(r.getComparativoMeta(), anio, mesSeleccionado());
            DashboardSectionPanel panelMetas = new DashboardSectionPanel("Metas gerenciales", metaPanel);
            panelMetas.setAlignmentX(LEFT_ALIGNMENT);

            JPanel colDer = new JPanel();
            colDer.setOpaque(false);
            colDer.setLayout(new BoxLayout(colDer, BoxLayout.Y_AXIS));
            colDer.add(panelAlertas);
            colDer.add(Box.createRigidArea(new Dimension(0, UiTheme.MARGEN)));
            colDer.add(panelMetas);
            // Ancho fijo de columna, pero altura REAL (no clamp): así el BoxLayout vertical superior reserva
            // el alto necesario y Alertas + Metas nunca se solapan.
            colDer.setMaximumSize(new Dimension(ANCHO_COL_DER, Integer.MAX_VALUE));
            colDer.setPreferredSize(new Dimension(ANCHO_COL_DER, colDer.getPreferredSize().height));

            JPanel medio = new JPanel(new BorderLayout(UiTheme.MARGEN, 0));
            medio.setOpaque(false);
            medio.setAlignmentX(LEFT_ALIGNMENT);
            medio.add(colIzq, BorderLayout.CENTER);
            medio.add(colDer, BorderLayout.EAST);

            // ---- Subpestañas: Inventario / Compras / Lotes ----
            JTabbedPane tabs = new JTabbedPane();
            tabs.setAlignmentX(LEFT_ALIGNMENT);
            tabs.setBackground(AppPalette.FONDO);

            DashboardMetricListPanel invList = new DashboardMetricListPanel();
            invList.agregar("Productos bajo stock", MoneyFormatter.entero(inv.getProductosBajoStock()),
                    inv.getProductosBajoStock() > 0 ? AppPalette.ADVERTENCIA : AppPalette.EXITO);
            invList.agregar("Productos sin stock", MoneyFormatter.entero(inv.getProductosSinStock()),
                    inv.getProductosSinStock() > 0 ? AppPalette.PELIGRO : AppPalette.EXITO);
            invList.agregar("Ítems con stock máximo", MoneyFormatter.entero(inv.getItemsStockMaximo()));
            invList.agregar("Ítems con proveedor preferido", MoneyFormatter.entero(inv.getItemsProveedorPreferido()));
            invList.agregar("Reabastecimiento sugerido", MoneyFormatter.cantidad(inv.getSugeridoTotal()));
            invList.agregar("Valor de inventario (est.)", MoneyFormatter.cop(inv.getValorInventarioEstimado()));
            tabs.addTab("Inventario", envolverTab(invList));

            DashboardMetricListPanel compList = new DashboardMetricListPanel();
            compList.agregar("Compras ordenadas", MoneyFormatter.cop(com.getTotalOrdenado()));
            compList.agregar("Compras recibidas", MoneyFormatter.cop(com.getTotalRecibido()), AppPalette.EXITO);
            compList.agregar("Órdenes pendientes", MoneyFormatter.entero(com.getOrdenesPendientes()),
                    com.getOrdenesPendientes() > 0 ? AppPalette.ADVERTENCIA : AppPalette.EXITO);
            compList.agregar("Órdenes recibidas", MoneyFormatter.entero(com.getOrdenesRecibidas()));
            compList.agregar("Proveedor top", com.getProveedorTopNombre() == null ? "—"
                    : com.getProveedorTopNombre() + " (" + MoneyFormatter.cop(com.getProveedorTopValor()) + ")");
            tabs.addTab("Compras", envolverTab(compList));

            DashboardMetricListPanel lotList = new DashboardMetricListPanel();
            lotList.agregar("Lotes por vencer (≤30 d)", MoneyFormatter.entero(lot.getProximosAVencer()),
                    lot.getProximosAVencer() > 0 ? AppPalette.ADVERTENCIA : AppPalette.EXITO);
            lotList.agregar("Lotes vencidos", MoneyFormatter.entero(lot.getVencidos()),
                    lot.getVencidos() > 0 ? AppPalette.PELIGRO : AppPalette.EXITO);
            lotList.agregar("Lotes agotados", MoneyFormatter.entero(lot.getAgotados()));
            lotList.agregar("Lotes sin fecha", MoneyFormatter.entero(lot.getSinFecha()));
            lotList.agregar("Valor en riesgo (est.)", MoneyFormatter.cop(lot.getValorEnRiesgoEstimado()),
                    lot.getValorEnRiesgoEstimado().signum() > 0 ? AppPalette.ADVERTENCIA : AppPalette.PRIMARIO);
            tabs.addTab("Lotes", envolverTab(lotList));
            tabs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));

            contenido.add(top);
            contenido.add(Box.createRigidArea(new Dimension(0, UiTheme.MARGEN)));
            contenido.add(medio);
            contenido.add(Box.createRigidArea(new Dimension(0, UiTheme.MARGEN)));
            contenido.add(tabs);
        } catch (Exception e) {
            JLabel err = new JLabel("No se pudo cargar el dashboard ejecutivo: " + e.getMessage());
            err.setFont(UiTheme.fuenteNormal());
            err.setForeground(AppPalette.PELIGRO);
            contenido.add(err);
        }
        contenido.revalidate();
        contenido.repaint();
    }

    /** Envuelve el contenido de una subpestaña con padding y fondo blanco. */
    private JComponent envolverTab(JComponent c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(UiTheme.SUPERFICIE);
        p.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        p.add(c, BorderLayout.NORTH);
        return p;
    }
}
