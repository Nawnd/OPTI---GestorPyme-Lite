package com.gestorpyme.service;

import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.ComprasPeriodoResumen;
import com.gestorpyme.domain.model.DashboardComparativoMeta;
import com.gestorpyme.domain.model.DashboardEjecutivoResumen;
import com.gestorpyme.domain.model.DashboardFinancieroResumen;
import com.gestorpyme.domain.model.DashboardMeta;
import com.gestorpyme.domain.model.DashboardResumen;
import com.gestorpyme.domain.model.LoteVencimientoItem;
import com.gestorpyme.domain.model.PagoReciente;
import com.gestorpyme.domain.model.StockBajoItem;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDiaItem;
import com.gestorpyme.repository.DashboardRepository;
import com.gestorpyme.util.PeriodoDashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logica del Dashboard: obtiene los KPIs, calcula el porcentaje de cartera
 * recuperada y arma la serie de ventas por dia. Capa: service (sin SQL ni Swing).
 */
public class DashboardService {

    private final DashboardRepository dashboardRepository;

    public DashboardService() {
        this(new DashboardRepository());
    }

    public DashboardService(DashboardRepository dashboardRepository) {
        this.dashboardRepository = dashboardRepository;
    }

    /** Obtiene el resumen de KPIs y completa el porcentaje de cartera recuperada. */
    public DashboardResumen obtenerResumen() throws SQLException {
        DashboardResumen r = dashboardRepository.obtenerResumen();
        r.setPorcentajeCartera(porcentaje(r.getCreditoPagado(), r.getCreditoTotal()));
        return r;
    }

    /**
     * Calcula la proporcion pagado/total (0..1) de forma segura.
     *
     * @param pagado total abonado.
     * @param total  total a credito.
     * @return proporcion entre 0 y 1; 0 si no hay credito.
     */
    public double porcentaje(BigDecimal pagado, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0 || pagado == null) {
            return 0.0;
        }
        BigDecimal ratio = pagado.divide(total, 4, RoundingMode.HALF_UP);
        double v = ratio.doubleValue();
        if (v < 0) {
            return 0.0;
        }
        return Math.min(1.0, v);
    }

    public List<Venta> ultimasVentas(int limite) throws SQLException {
        return dashboardRepository.ultimasVentas(limite);
    }

    public List<PagoReciente> ultimosPagos(int limite) throws SQLException {
        return dashboardRepository.ultimosPagos(limite);
    }

    public List<StockBajoItem> stockBajo(int limite) throws SQLException {
        return dashboardRepository.stockBajo(limite);
    }

    public List<CuentaPorCobrar> cuentasPendientes(int limite) throws SQLException {
        return dashboardRepository.cuentasPendientes(limite);
    }

    public List<LoteVencimientoItem> lotesPorVencer(int dias, int limite) throws SQLException {
        return dashboardRepository.lotesPorVencer(dias, limite);
    }

    /**
     * Serie de ventas de los ultimos {@code dias} dias, rellenando con 0 los dias
     * sin ventas y etiquetando cada uno como dd/MM (orden cronologico).
     *
     * @param dias numero de dias hacia atras (incluyendo hoy).
     * @return lista de {@link VentaDiaItem} en orden cronologico.
     */
    public List<VentaDiaItem> ventasUltimosDias(int dias) throws SQLException {
        Map<String, BigDecimal> mapa = dashboardRepository.ventasPorDia(dias);
        List<VentaDiaItem> serie = new ArrayList<>();
        LocalDate hoy = LocalDate.now();
        for (int i = dias - 1; i >= 0; i--) {
            LocalDate dia = hoy.minusDays(i);
            String iso = dia.toString(); // yyyy-MM-dd, igual que date() en SQLite
            BigDecimal total = mapa.getOrDefault(iso, BigDecimal.ZERO);
            String etiqueta = String.format("%02d/%02d", dia.getDayOfMonth(), dia.getMonthValue());
            serie.add(new VentaDiaItem(etiqueta, total));
        }
        return serie;
    }

    /**
     * Indicadores de compras del periodo (Paso G). Obtiene los agregados del repositorio y
     * calcula el promedio por orden. Solo lectura: no modifica ordenes, recepciones ni stock.
     *
     * @param anio año a consultar.
     * @param mes  mes 1..12, o {@code null} para "todos los meses".
     */
    public ComprasPeriodoResumen comprasPeriodo(int anio, Integer mes) throws SQLException {
        ComprasPeriodoResumen r = dashboardRepository.comprasPeriodo(anio, mes);
        r.setPromedioPorOrden(promedioPorOrden(r.getTotalOrdenado(), r.getCantidadOrdenes()));
        return r;
    }

    /**
     * Promedio por orden = total ordenado / cantidad de ordenes. Si no hay ordenes, devuelve 0.
     * Metodo puro y testeable (no accede a la base de datos).
     *
     * @param totalOrdenado valor total ordenado en el periodo.
     * @param cantidadOrdenes numero de ordenes del periodo.
     * @return promedio por orden (2 decimales) o 0 si la cantidad es 0.
     */
    public BigDecimal promedioPorOrden(BigDecimal totalOrdenado, long cantidadOrdenes) {
        if (cantidadOrdenes <= 0 || totalOrdenado == null) {
            return BigDecimal.ZERO;
        }
        return totalOrdenado.divide(BigDecimal.valueOf(cantidadOrdenes), 2, java.math.RoundingMode.HALF_UP);
    }

    // ======================= Paso K: Dashboard Ejecutivo 360 =======================

    /**
     * Arma el resumen ejecutivo 360 del período (año/mes/semana). Calcula ticket y margen con guardas de
     * división por cero, y la comparación real vs meta (solo lectura; nunca modifica datos reales).
     *
     * @param anio   año.
     * @param mes    1..12 o null (año completo).
     * @param semana 1..4 o null (mes completo).
     */
    public DashboardEjecutivoResumen resumenEjecutivo(int anio, Integer mes, Integer semana) throws SQLException {
        PeriodoDashboard periodo = PeriodoDashboard.de(anio, mes, semana);
        String ini = periodo.getFechaInicio();
        String fin = periodo.getFechaFin();

        DashboardFinancieroResumen fin1 = dashboardRepository.financiero(ini, fin);
        // Ticket promedio y margen estimado: se calculan aquí con guardas.
        fin1.setTicketPromedio(calcularTicket(fin1.getVentas(), fin1.getCantidadVentas()));
        boolean hayBase = fin1.getBaseVentasConCosto() != null && fin1.getBaseVentasConCosto().signum() > 0;
        fin1.setMargenDisponible(hayBase);
        fin1.setMargenEstimado(hayBase
                ? fin1.getUtilidadEstimada().divide(fin1.getBaseVentasConCosto(), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);

        DashboardEjecutivoResumen r = new DashboardEjecutivoResumen();
        r.setEtiquetaPeriodo(periodo.getEtiqueta());
        r.setFechaInicio(ini);
        r.setFechaFin(fin);
        r.setFinanciero(fin1);
        r.setInventario(dashboardRepository.inventario());
        r.setCompras(dashboardRepository.comprasRango(ini, fin));
        r.setLotes(dashboardRepository.lotes());
        r.setOperativo(dashboardRepository.operativo(ini, fin));
        r.setComparativoMeta(compararMeta(anio, mes, semana, fin1));
        return r;
    }

    /** Ticket promedio = ventas / cantidad, con guarda de división por cero (0 si no hay ventas). */
    public BigDecimal calcularTicket(BigDecimal ventas, long cantidad) {
        if (ventas == null || cantidad <= 0) {
            return BigDecimal.ZERO;
        }
        return ventas.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }

    /** Margen estimado = utilidad / base, con guarda de división por cero (0 si la base es 0). */
    public BigDecimal calcularMargen(BigDecimal utilidad, BigDecimal base) {
        if (utilidad == null || base == null || base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return utilidad.divide(base, 4, RoundingMode.HALF_UP);
    }

    /**
     * Compara los KPIs reales del período contra la meta registrada (si existe). No modifica datos reales:
     * solo calcula porcentajes de cumplimiento (real/meta), o null cuando no hay meta o la meta es 0.
     */
    public DashboardComparativoMeta compararMeta(int anio, Integer mes, Integer semana,
                                                 DashboardFinancieroResumen fin) throws SQLException {
        DashboardComparativoMeta c = new DashboardComparativoMeta();
        c.setVentasReales(fin.getVentas());
        c.setUtilidadReal(fin.getUtilidadEstimada());
        c.setMargenReal(fin.getMargenEstimado());
        DashboardMeta meta = dashboardRepository.buscarMeta(anio, mes, semana);
        if (meta == null) {
            c.setHayMeta(false);
            return c;
        }
        c.setHayMeta(true);
        c.setMetaVentas(meta.getMetaVentas());
        c.setMetaUtilidad(meta.getMetaUtilidad());
        c.setMetaMargen(meta.getMetaMargen());
        c.setComentario(meta.getComentario());
        c.setCumplimientoVentas(cumplimiento(fin.getVentas(), meta.getMetaVentas()));
        c.setCumplimientoUtilidad(cumplimiento(fin.getUtilidadEstimada(), meta.getMetaUtilidad()));
        return c;
    }

    /** Cumplimiento = real / meta como fracción (0..1+). null si no hay meta o la meta es 0. */
    private Double cumplimiento(BigDecimal real, BigDecimal meta) {
        if (meta == null || meta.signum() == 0 || real == null) {
            return null;
        }
        return real.divide(meta, 4, RoundingMode.HALF_UP).doubleValue();
    }

    /** Registra o actualiza una meta gerencial (separada de los datos reales). */
    public DashboardMeta guardarMeta(DashboardMeta meta) throws SQLException {
        if (meta.getAnio() <= 0) {
            throw new com.gestorpyme.domain.exception.ValidacionException("El año de la meta es obligatorio.");
        }
        if (meta.getMes() != null && (meta.getMes() < 1 || meta.getMes() > 12)) {
            throw new com.gestorpyme.domain.exception.ValidacionException("El mes de la meta debe estar entre 1 y 12.");
        }
        return dashboardRepository.guardarMeta(meta);
    }

    public DashboardMeta buscarMeta(int anio, Integer mes, Integer semana) throws SQLException {
        return dashboardRepository.buscarMeta(anio, mes, semana);
    }

    public List<DashboardMeta> listarMetas() throws SQLException {
        return dashboardRepository.listarMetas();
    }

    // ======================= Paso L: gráficas clicables y desglose (solo lectura) =======================

    /** Total de pagos+abonos recibidos hoy (KPI Dashboard General). Passthrough. */
    public java.math.BigDecimal pagosDelDia() throws SQLException {
        return dashboardRepository.pagosDelDia();
    }

    /** Cuentas por cobrar de un estado (desglose de cartera). Passthrough de lectura. */
    public java.util.List<com.gestorpyme.domain.model.DashboardDrillDownItem> cuentasPorEstado(String estado, int limite)
            throws SQLException {
        return dashboardRepository.cuentasPorEstado(estado, limite);
    }

    /** Stock por bodega (gráfica clicable). Passthrough de lectura. */
    public List<com.gestorpyme.domain.model.DashboardChartSegment> stockPorBodega() throws SQLException {
        return dashboardRepository.stockPorBodega();
    }

    /** Ítems y stock de una bodega (desglose). Passthrough de lectura. */
    public List<com.gestorpyme.domain.model.DashboardDrillDownItem> itemsPorBodega(int idBodega, int limite)
            throws SQLException {
        return dashboardRepository.itemsPorBodega(idBodega, limite);
    }

    /** Cartera por estado (gráfica clicable). Passthrough de lectura. */
    public List<com.gestorpyme.domain.model.DashboardChartSegment> carteraPorEstado() throws SQLException {
        return dashboardRepository.carteraPorEstado();
    }

    /** Ventas por rango ISO (desglose). Passthrough de lectura. */
    public List<com.gestorpyme.domain.model.DashboardDrillDownItem> ventasPorRango(
            String iniISO, String finISO, int limite) throws SQLException {
        return dashboardRepository.ventasPorRango(iniISO, finISO, limite);
    }

    /** Rotación: Top de productos por cantidad vendida en el rango (gráfica clicable). Passthrough. */
    public List<com.gestorpyme.domain.model.DashboardChartSegment> rotacionProductos(
            String iniISO, String finISO, int limite) throws SQLException {
        return dashboardRepository.rotacionProductos(iniISO, finISO, limite);
    }

    /** Ventas de un ítem en el rango (desglose de rotación). Passthrough de lectura. */
    public List<com.gestorpyme.domain.model.DashboardDrillDownItem> ventasPorItem(
            int idItem, String iniISO, String finISO, int limite) throws SQLException {
        return dashboardRepository.ventasPorItem(idItem, iniISO, finISO, limite);
    }
}
