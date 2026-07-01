package com.gestorpyme.controller;

import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.ComprasPeriodoResumen;
import com.gestorpyme.domain.model.DashboardResumen;
import com.gestorpyme.domain.model.LoteVencimientoItem;
import com.gestorpyme.domain.model.PagoReciente;
import com.gestorpyme.domain.model.StockBajoItem;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDiaItem;
import com.gestorpyme.service.DashboardService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del Dashboard. Coordina la vista con {@link DashboardService}.
 * Capa: controller (no contiene reglas de negocio ni SQL).
 */
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController() {
        this(new DashboardService());
    }

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public DashboardResumen obtenerResumen() throws SQLException {
        return dashboardService.obtenerResumen();
    }

    public List<Venta> ultimasVentas(int limite) throws SQLException {
        return dashboardService.ultimasVentas(limite);
    }

    public List<PagoReciente> ultimosPagos(int limite) throws SQLException {
        return dashboardService.ultimosPagos(limite);
    }

    public List<StockBajoItem> stockBajo(int limite) throws SQLException {
        return dashboardService.stockBajo(limite);
    }

    public List<CuentaPorCobrar> cuentasPendientes(int limite) throws SQLException {
        return dashboardService.cuentasPendientes(limite);
    }

    public List<LoteVencimientoItem> lotesPorVencer(int dias, int limite) throws SQLException {
        return dashboardService.lotesPorVencer(dias, limite);
    }

    public List<VentaDiaItem> ventasUltimosDias(int dias) throws SQLException {
        return dashboardService.ventasUltimosDias(dias);
    }

    /** Indicadores de compras del periodo (año + mes; mes null = todos). Solo lectura. */
    public ComprasPeriodoResumen comprasPeriodo(int anio, Integer mes) throws SQLException {
        return dashboardService.comprasPeriodo(anio, mes);
    }

    /** Resumen ejecutivo 360 del período (año/mes/semana). Solo lectura. Paso K. */
    public com.gestorpyme.domain.model.DashboardEjecutivoResumen resumenEjecutivo(
            int anio, Integer mes, Integer semana) throws SQLException {
        return dashboardService.resumenEjecutivo(anio, mes, semana);
    }

    /** Registra o actualiza una meta gerencial (separada de datos reales). Paso K. */
    public com.gestorpyme.domain.model.DashboardMeta guardarMeta(
            com.gestorpyme.domain.model.DashboardMeta meta) throws SQLException {
        return dashboardService.guardarMeta(meta);
    }

    public com.gestorpyme.domain.model.DashboardMeta buscarMeta(int anio, Integer mes, Integer semana)
            throws SQLException {
        return dashboardService.buscarMeta(anio, mes, semana);
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardMeta> listarMetas() throws SQLException {
        return dashboardService.listarMetas();
    }

    // ======================= Paso L: gráficas clicables y desglose (solo lectura) =======================

    public java.util.List<com.gestorpyme.domain.model.DashboardDrillDownItem> cuentasPorEstado(String estado, int limite) throws SQLException {
        return dashboardService.cuentasPorEstado(estado, limite);
    }

    public java.math.BigDecimal pagosDelDia() throws SQLException {
        return dashboardService.pagosDelDia();
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardChartSegment> stockPorBodega() throws SQLException {
        return dashboardService.stockPorBodega();
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardDrillDownItem> itemsPorBodega(int idBodega, int limite)
            throws SQLException {
        return dashboardService.itemsPorBodega(idBodega, limite);
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardChartSegment> carteraPorEstado() throws SQLException {
        return dashboardService.carteraPorEstado();
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardDrillDownItem> ventasPorRango(
            String iniISO, String finISO, int limite) throws SQLException {
        return dashboardService.ventasPorRango(iniISO, finISO, limite);
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardChartSegment> rotacionProductos(
            String iniISO, String finISO, int limite) throws SQLException {
        return dashboardService.rotacionProductos(iniISO, finISO, limite);
    }

    public java.util.List<com.gestorpyme.domain.model.DashboardDrillDownItem> ventasPorItem(
            int idItem, String iniISO, String finISO, int limite) throws SQLException {
        return dashboardService.ventasPorItem(idItem, iniISO, finISO, limite);
    }
}
