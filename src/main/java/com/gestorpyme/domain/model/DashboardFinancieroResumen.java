package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * KPIs financieros/comerciales del período (Paso K). Capa: domain.model (POJO de solo lectura).
 *
 * La utilidad y el margen son ESTIMADOS (basados en el precio de compra actual del ítem) y excluyen
 * líneas sin costo válido; no equivalen a costeo contable. La cartera es un saldo actual (no del período).
 */
public class DashboardFinancieroResumen {

    private BigDecimal ventas = BigDecimal.ZERO;
    private long cantidadVentas;
    private BigDecimal ticketPromedio = BigDecimal.ZERO;
    private BigDecimal descuentos = BigDecimal.ZERO;
    private BigDecimal ventasContado = BigDecimal.ZERO;
    private BigDecimal ventasCredito = BigDecimal.ZERO;
    private BigDecimal pagosRecibidos = BigDecimal.ZERO;
    private BigDecimal carteraPendiente = BigDecimal.ZERO;
    private BigDecimal utilidadEstimada = BigDecimal.ZERO;
    private BigDecimal baseVentasConCosto = BigDecimal.ZERO;
    private BigDecimal margenEstimado = BigDecimal.ZERO; // fracción 0..1 (o más); la UI lo muestra en %
    private boolean margenDisponible;                    // false si no hubo base con costo
    private long lineasConCosto;
    private long lineasSinCosto;                          // excluidas del cálculo de utilidad/margen

    public BigDecimal getVentas() { return ventas; }
    public void setVentas(BigDecimal ventas) { this.ventas = ventas; }

    public long getCantidadVentas() { return cantidadVentas; }
    public void setCantidadVentas(long cantidadVentas) { this.cantidadVentas = cantidadVentas; }

    public BigDecimal getTicketPromedio() { return ticketPromedio; }
    public void setTicketPromedio(BigDecimal ticketPromedio) { this.ticketPromedio = ticketPromedio; }

    public BigDecimal getDescuentos() { return descuentos; }
    public void setDescuentos(BigDecimal descuentos) { this.descuentos = descuentos; }

    public BigDecimal getVentasContado() { return ventasContado; }
    public void setVentasContado(BigDecimal ventasContado) { this.ventasContado = ventasContado; }

    public BigDecimal getVentasCredito() { return ventasCredito; }
    public void setVentasCredito(BigDecimal ventasCredito) { this.ventasCredito = ventasCredito; }

    public BigDecimal getPagosRecibidos() { return pagosRecibidos; }
    public void setPagosRecibidos(BigDecimal pagosRecibidos) { this.pagosRecibidos = pagosRecibidos; }

    public BigDecimal getCarteraPendiente() { return carteraPendiente; }
    public void setCarteraPendiente(BigDecimal carteraPendiente) { this.carteraPendiente = carteraPendiente; }

    public BigDecimal getUtilidadEstimada() { return utilidadEstimada; }
    public void setUtilidadEstimada(BigDecimal utilidadEstimada) { this.utilidadEstimada = utilidadEstimada; }

    public BigDecimal getBaseVentasConCosto() { return baseVentasConCosto; }
    public void setBaseVentasConCosto(BigDecimal baseVentasConCosto) { this.baseVentasConCosto = baseVentasConCosto; }

    public BigDecimal getMargenEstimado() { return margenEstimado; }
    public void setMargenEstimado(BigDecimal margenEstimado) { this.margenEstimado = margenEstimado; }

    public boolean isMargenDisponible() { return margenDisponible; }
    public void setMargenDisponible(boolean margenDisponible) { this.margenDisponible = margenDisponible; }

    public long getLineasConCosto() { return lineasConCosto; }
    public void setLineasConCosto(long lineasConCosto) { this.lineasConCosto = lineasConCosto; }

    public long getLineasSinCosto() { return lineasSinCosto; }
    public void setLineasSinCosto(long lineasSinCosto) { this.lineasSinCosto = lineasSinCosto; }
}
