package com.gestorpyme.view.dashboard;

import com.gestorpyme.controller.DashboardController;
import com.gestorpyme.domain.enums.TipoDrillDown;
import com.gestorpyme.domain.model.DashboardChartData;
import com.gestorpyme.domain.model.DashboardChartSegment;
import com.gestorpyme.domain.model.DashboardResumen;
import com.gestorpyme.domain.model.LoteVencimientoItem;
import com.gestorpyme.domain.model.PagoReciente;
import com.gestorpyme.domain.model.StockBajoItem;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDiaItem;
import com.gestorpyme.util.DateFormatter;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.components.DashboardBigKpi;
import com.gestorpyme.view.components.DashboardListaEjecutiva;
import com.gestorpyme.view.components.DashboardSectionPanel;
import com.gestorpyme.view.components.InteractiveBarChartPanel;
import com.gestorpyme.view.components.MetricSelectionListener;
import com.gestorpyme.view.components.ResponsiveGridPanel;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pestaña "Dashboard General" (Paso L). Capa: view.dashboard. Orientada a la operación diaria.
 *
 * Reúne cuatro KPIs (ventas y pagos del día, cuentas pendientes y alertas), las ventas y pagos recientes como
 * listas ejecutivas (no tablas pesadas), cuatro gráficas de barras clicables (ventas por día, stock por
 * bodega, cartera por estado y rotación Top 5) y un panel de desglose que aparece al hacer clic. La carga es
 * perezosa ({@link #cargar()} se invoca al mostrarse la pestaña) y un selector de rango ajusta la rotación y
 * el desglose por ítem. No contiene lógica de negocio: solo consulta de lectura y presentación.
 */
public class GeneralDashboardPanel extends JPanel {

    private static final int LIMITE_LISTA = 8;
    private static final int LIMITE_DRILL = 50;
    /** Alertas: cuántos registros se consultan, cuántos se muestran y el horizonte de vencimiento (días). */
    private static final int LIMITE_ALERTAS = 100;
    private static final int MAX_VISIBLES = 5;
    private static final int DIAS_HORIZONTE = 30;
    /** Anchos mínimos de columna para la grilla responsive (KPIs/alertas/gráficas). */
    private static final int ANCHO_MIN_KPI = 210;
    private static final int ANCHO_MIN_ALERTA = 320;
    private static final int ANCHO_MIN_GRAFICA = 300;

    private final DashboardController controller;
    private final JComboBox<String> cmbRango = new JComboBox<>(
            new String[]{"Hoy", "Últimos 7 días", "Mes actual"});
    private final JPanel contenido = new JPanel();
    private final DrillDownPanel drillDownPanel = new DrillDownPanel();
    private boolean cargado = false;

    public GeneralDashboardPanel(DashboardController controller) {
        this.controller = controller;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(UiTheme.margen());

        add(crearBarraRango());
        add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
        contenido.setOpaque(false);
        contenido.setLayout(new BoxLayout(contenido, BoxLayout.Y_AXIS));
        contenido.setAlignmentX(LEFT_ALIGNMENT);
        add(contenido);
        add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
        drillDownPanel.setAlignmentX(LEFT_ALIGNMENT);
        add(drillDownPanel);
    }

    /** Carga (perezosa) el contenido la primera vez que se muestra la pestaña. */
    public void cargar() {
        if (cargado) {
            return;
        }
        cargado = true;
        refrescar();
    }

    private JComponent crearBarraRango() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        barra.setAlignmentX(LEFT_ALIGNMENT);
        barra.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JLabel titulo = new JLabel("Operación diaria");
        titulo.setFont(UiTheme.fuente(Font.BOLD, 16));
        titulo.setForeground(AppPalette.PRIMARIO);

        JPanel der = new JPanel();
        der.setOpaque(false);
        JLabel l = new JLabel("Rango: ");
        l.setForeground(AppPalette.TEXTO_SECUNDARIO);
        cmbRango.setSelectedIndex(1); // por defecto, últimos 7 días
        cmbRango.addActionListener(e -> {
            if (cargado) {
                refrescar();
            }
        });
        javax.swing.JButton btn = new javax.swing.JButton("Refrescar");
        btn.addActionListener(e -> refrescar());
        der.add(l);
        der.add(cmbRango);
        der.add(btn);

        barra.add(titulo, BorderLayout.WEST);
        barra.add(der, BorderLayout.EAST);
        return barra;
    }

    /** Reconstruye KPIs, gráficas y listas con una sola pasada de consultas de lectura. */
    private void refrescar() {
        contenido.removeAll();
        drillDownPanel.ocultar();
        try {
            DashboardResumen r = controller.obtenerResumen();
            java.math.BigDecimal pagosHoy = controller.pagosDelDia();
            String[] rango = rangoActual();

            contenido.add(crearKpis(r, pagosHoy));
            contenido.add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
            contenido.add(crearAlertas());
            contenido.add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
            contenido.add(crearGraficas(rango));
            contenido.add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
            contenido.add(crearListas());
        } catch (SQLException ex) {
            JLabel error = new JLabel("No se pudo cargar el panel: " + ex.getMessage());
            error.setForeground(AppPalette.PELIGRO);
            error.setAlignmentX(LEFT_ALIGNMENT);
            contenido.add(error);
        }
        contenido.revalidate();
        contenido.repaint();
    }

    private JComponent crearKpis(DashboardResumen r, java.math.BigDecimal pagosHoy) {
        ResponsiveGridPanel grid = new ResponsiveGridPanel(4, ANCHO_MIN_KPI, UiTheme.ESPACIO, UiTheme.ESPACIO);

        grid.add(new DashboardBigKpi("Ventas del día",
                MoneyFormatter.cop(r.getTotalVentasHoy()),
                r.getCantidadVentasHoy() + " ventas", AppPalette.PRIMARIO));
        grid.add(new DashboardBigKpi("Pagos del día",
                MoneyFormatter.cop(pagosHoy), "recibido hoy", AppPalette.EXITO));
        grid.add(new DashboardBigKpi("Cuentas pendientes",
                MoneyFormatter.entero(r.getCuentasPendientes()),
                MoneyFormatter.cop(r.getCarteraPendiente()), AppPalette.ADVERTENCIA));
        grid.add(new DashboardBigKpi("Alertas críticas",
                MoneyFormatter.entero(r.getProductosStockBajo()), "productos en alerta",
                r.getProductosStockBajo() > 0 ? AppPalette.PELIGRO : AppPalette.EXITO));
        return grid;
    }

    /**
     * Panel "Alertas operativas": dos listas compactas y accionables (stock crítico y lotes críticos),
     * con colores semánticos (rojo = sin stock / vencido; ámbar = bajo stock / por vencer) y un máximo de
     * cinco filas por grupo (si hay más, se indica "+N adicionales"). Reutiliza las lecturas existentes
     * {@code stockBajo} y {@code lotesPorVencer}; la clasificación se calcula aquí (presentación).
     */
    private JComponent crearAlertas() throws SQLException {
        JPanel cont = new JPanel();
        cont.setOpaque(false);
        cont.setLayout(new BoxLayout(cont, BoxLayout.Y_AXIS));
        cont.setAlignmentX(LEFT_ALIGNMENT);

        JLabel titulo = new JLabel("Alertas operativas");
        titulo.setFont(UiTheme.fuente(Font.BOLD, 16));
        titulo.setForeground(AppPalette.PRIMARIO);
        titulo.setAlignmentX(LEFT_ALIGNMENT);

        ResponsiveGridPanel grid = new ResponsiveGridPanel(2, ANCHO_MIN_ALERTA, UiTheme.ESPACIO, UiTheme.ESPACIO);
        grid.add(new DashboardSectionPanel("Stock crítico", componenteStockCritico()));
        grid.add(new DashboardSectionPanel("Lotes críticos", componenteLotesCriticos()));

        cont.add(titulo);
        cont.add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));
        cont.add(grid);
        return cont;
    }

    /** Lista de stock crítico: clasifica cada fila como SIN STOCK (cantidad ≤ 0) o BAJO. */
    private JComponent componenteStockCritico() throws SQLException {
        List<StockBajoItem> items = controller.stockBajo(LIMITE_ALERTAS);
        if (items.isEmpty()) {
            return DashboardListaEjecutiva.vacia("Sin alertas de stock. Todo en nivel normal.");
        }
        DashboardListaEjecutiva lista = new DashboardListaEjecutiva(
                new String[]{"Item", "Bodega", "Actual", "Mínimo", "Estado"});
        lista.alinearDerecha(2, 3);
        int mostrados = Math.min(MAX_VISIBLES, items.size());
        for (int i = 0; i < mostrados; i++) {
            StockBajoItem s = items.get(i);
            boolean sinStock = s.getCantidad() == null || s.getCantidad().signum() <= 0;
            String estado = sinStock ? "SIN STOCK" : "BAJO";
            String hint = sinStock ? "PELIGRO" : "ADVERTENCIA";
            lista.agregarFilaConColor(hint,
                    s.getNombreItem(), textoOVacio(s.getNombreBodega()),
                    MoneyFormatter.cantidad(s.getCantidad()),
                    MoneyFormatter.cantidad(s.getStockMinimo()), estado);
        }
        if (items.size() > mostrados) {
            lista.agregarFila("+" + (items.size() - mostrados) + " adicionales");
        }
        return lista;
    }

    /** Lista de lotes críticos: clasifica por días al vencimiento (VENCIDO si negativos, si no POR VENCER). */
    private JComponent componenteLotesCriticos() throws SQLException {
        List<LoteVencimientoItem> lotes = controller.lotesPorVencer(DIAS_HORIZONTE, LIMITE_ALERTAS);
        if (lotes.isEmpty()) {
            return DashboardListaEjecutiva.vacia("Sin lotes por vencer ni vencidos.");
        }
        DashboardListaEjecutiva lista = new DashboardListaEjecutiva(
                new String[]{"Item", "Lote", "Vence", "Días", "Estado"});
        lista.alinearDerecha(3);
        int mostrados = Math.min(MAX_VISIBLES, lotes.size());
        for (int i = 0; i < mostrados; i++) {
            LoteVencimientoItem l = lotes.get(i);
            Long dias = diasHasta(l.getFechaVencimiento());
            boolean vencido = dias != null && dias < 0;
            String estado = vencido ? "VENCIDO" : "POR VENCER";
            String hint = vencido ? "PELIGRO" : "ADVERTENCIA";
            String diasTxt = dias == null ? "" : String.valueOf(dias);
            lista.agregarFilaConColor(hint,
                    l.getNombreItem(), textoOVacio(l.getNumeroLote()),
                    fechaVencVista(l.getFechaVencimiento()), diasTxt, estado);
        }
        if (lotes.size() > mostrados) {
            lista.agregarFila("+" + (lotes.size() - mostrados) + " adicionales");
        }
        return lista;
    }

    /** Días desde hoy hasta el vencimiento (negativo si ya venció); null si la fecha no es parseable. */
    private static Long diasHasta(String fechaIso) {
        if (fechaIso == null || fechaIso.length() < 10) {
            return null;
        }
        try {
            LocalDate venc = DateFormatter.desdeIso(fechaIso.substring(0, 10));
            return ChronoUnit.DAYS.between(LocalDate.now(), venc);
        } catch (RuntimeException ex) {
            return null; // fecha con formato inesperado: se omite el cálculo
        }
    }

    /** Formatea el vencimiento a dd/MM/yyyy; si no es parseable, devuelve el texto original. */
    private static String fechaVencVista(String fechaIso) {
        if (fechaIso == null || fechaIso.length() < 10) {
            return textoOVacio(fechaIso);
        }
        try {
            return DateFormatter.isoAVista(fechaIso.substring(0, 10));
        } catch (RuntimeException ex) {
            return fechaIso;
        }
    }

    private static String textoOVacio(String v) {
        return (v == null || v.trim().isEmpty()) ? "" : v.trim();
    }

    private JComponent crearGraficas(String[] rango) throws SQLException {
        ResponsiveGridPanel grid = new ResponsiveGridPanel(2, ANCHO_MIN_GRAFICA, UiTheme.ESPACIO, UiTheme.ESPACIO);

        grid.add(new DashboardSectionPanel("Ventas por día (7 días)", grafica(ventasPorDiaData())));
        grid.add(new DashboardSectionPanel("Stock por bodega",
                grafica(new DashboardChartData("Stock por bodega", controller.stockPorBodega()))));
        grid.add(new DashboardSectionPanel("Cartera por estado",
                grafica(new DashboardChartData("Cartera por estado", controller.carteraPorEstado()))));
        grid.add(new DashboardSectionPanel("Rotación Top 5",
                grafica(new DashboardChartData("Rotación Top 5",
                        controller.rotacionProductos(rango[0], rango[1], 5)))));
        return grid;
    }

    /** Crea la gráfica y la conecta al manejador de desglose. */
    private InteractiveBarChartPanel grafica(DashboardChartData data) {
        InteractiveBarChartPanel chart = new InteractiveBarChartPanel(data);
        chart.setMetricSelectionListener(manejadorDrill());
        return chart;
    }

    /** Construye la serie de ventas de los últimos 7 días con su fecha ISO por barra (para el desglose). */
    private DashboardChartData ventasPorDiaData() throws SQLException {
        List<VentaDiaItem> serie = controller.ventasUltimosDias(7);
        List<DashboardChartSegment> segs = new ArrayList<>();
        int n = serie.size();
        for (int k = 0; k < n; k++) {
            VentaDiaItem it = serie.get(k);
            String iso = LocalDate.now().minusDays((long) (n - 1) - k).toString();
            segs.add(new DashboardChartSegment(it.getEtiqueta(), it.getTotal().doubleValue(),
                    TipoDrillDown.VENTAS, iso, "PRIMARIO"));
        }
        return new DashboardChartData("Ventas por día", segs);
    }

    private JComponent crearListas() throws SQLException {
        JPanel cont = new JPanel();
        cont.setOpaque(false);
        cont.setLayout(new BoxLayout(cont, BoxLayout.Y_AXIS));
        cont.setAlignmentX(LEFT_ALIGNMENT);

        // Ventas recientes.
        List<Venta> ventas = controller.ultimasVentas(LIMITE_LISTA);
        JComponent ventasComp;
        if (ventas.isEmpty()) {
            ventasComp = DashboardListaEjecutiva.vacia("Aún no hay ventas registradas.");
        } else {
            DashboardListaEjecutiva lista = new DashboardListaEjecutiva(
                    new String[]{"N°", "Cliente", "Fecha", "Total", "Estado"});
            lista.alinearDerecha(3);
            for (Venta v : ventas) {
                lista.agregarFilaConColor(estadoVentaHint(v.getEstado() == null ? "" : v.getEstado().name()),
                        v.getNumeroVenta(),
                        v.getNombreTercero() == null ? "Consumidor final" : v.getNombreTercero(),
                        fechaVista(v.getFecha()),
                        MoneyFormatter.cop(v.getTotal()),
                        v.getEstado() == null ? "" : v.getEstado().toString());
            }
            ventasComp = lista;
        }
        cont.add(new DashboardSectionPanel("Ventas recientes", ventasComp));
        cont.add(Box.createRigidArea(new Dimension(0, UiTheme.ESPACIO)));

        // Pagos recientes.
        List<PagoReciente> pagos = controller.ultimosPagos(LIMITE_LISTA);
        JComponent pagosComp;
        if (pagos.isEmpty()) {
            pagosComp = DashboardListaEjecutiva.vacia("Aún no hay pagos registrados.");
        } else {
            DashboardListaEjecutiva lista = new DashboardListaEjecutiva(
                    new String[]{"Fecha", "Venta", "Cliente", "Medio", "Valor"});
            lista.alinearDerecha(4);
            for (PagoReciente p : pagos) {
                lista.agregarFila(
                        fechaVista(p.getFecha()),
                        p.getNumeroVenta(),
                        p.getNombreTercero() == null ? "Consumidor final" : p.getNombreTercero(),
                        p.getMedioPago() == null ? "" : p.getMedioPago().toString(),
                        MoneyFormatter.cop(p.getValor()));
            }
            pagosComp = lista;
        }
        cont.add(new DashboardSectionPanel("Últimos pagos", pagosComp));
        return cont;
    }

    /** Manejador único de clic en barras: consulta el desglose y lo muestra en el panel inferior. */
    private MetricSelectionListener manejadorDrill() {
        return (tipo, referencia, etiqueta) -> {
            try {
                String[] rango = rangoActual();
                switch (tipo) {
                    case VENTAS:
                        drillDownPanel.mostrar("Ventas del " + fechaVista(referencia),
                                new String[]{"N°", "Cliente", "Total", "Estado"},
                                controller.ventasPorRango(referencia, referencia, LIMITE_DRILL), 2);
                        break;
                    case BODEGA:
                        drillDownPanel.mostrar("Ítems en " + etiqueta,
                                new String[]{"Producto", "Código", "Cantidad"},
                                controller.itemsPorBodega(parseInt(referencia), LIMITE_DRILL), 2);
                        break;
                    case CARTERA:
                        drillDownPanel.mostrar("Cartera: " + etiqueta,
                                new String[]{"N° venta", "Cliente", "Saldo", "Estado"},
                                controller.cuentasPorEstado(referencia, LIMITE_DRILL), 2);
                        break;
                    case ITEM:
                        drillDownPanel.mostrar("Ventas de " + etiqueta,
                                new String[]{"N° venta", "Cantidad", "Subtotal", "Estado"},
                                controller.ventasPorItem(parseInt(referencia), rango[0], rango[1], LIMITE_DRILL), 2);
                        break;
                    default:
                        // Otros tipos no se desglosan en esta pestaña.
                }
            } catch (SQLException ex) {
                drillDownPanel.mostrar("No se pudo cargar el detalle",
                        new String[]{"Detalle"}, new ArrayList<>());
            }
        };
    }

    /** Devuelve [inicioISO, finISO] según el rango seleccionado. */
    private String[] rangoActual() {
        LocalDate hoy = LocalDate.now();
        int idx = cmbRango.getSelectedIndex();
        LocalDate ini;
        if (idx == 0) {
            ini = hoy;                       // Hoy
        } else if (idx == 2) {
            ini = hoy.withDayOfMonth(1);     // Mes actual
        } else {
            ini = hoy.minusDays(6);          // Últimos 7 días
        }
        return new String[]{ini.toString(), hoy.toString()};
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    /** Convierte una fecha ISO (con o sin hora) a dd/MM/yyyy para mostrar; si falla, devuelve el original. */
    private static String fechaVista(String iso) {
        if (iso == null || iso.length() < 10) {
            return iso == null ? "" : iso;
        }
        try {
            return DateFormatter.isoAVista(iso.substring(0, 10));
        } catch (Exception e) {
            return iso;
        }
    }

    private static String estadoVentaHint(String estado) {
        if (estado == null) {
            return "NEUTRO";
        }
        switch (estado) {
            case "PAGADA": return "EXITO";
            case "ANULADA": return "PELIGRO";
            case "PENDIENTE_PAGO": return "ADVERTENCIA";
            case "CONFIRMADA": return "SECUNDARIO";
            default: return "NEUTRO";
        }
    }
}
