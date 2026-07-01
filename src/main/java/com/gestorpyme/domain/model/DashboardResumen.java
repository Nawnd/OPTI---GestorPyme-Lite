package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Resumen de indicadores (KPIs) del Dashboard. Objeto de transporte sin Swing.
 * Los montos se manejan con {@link BigDecimal} (DEC-020). El porcentaje de cartera
 * lo calcula el servicio a partir de creditoTotal/creditoPagado.
 */
public class DashboardResumen {

    private BigDecimal totalVentasHoy = BigDecimal.ZERO;
    private long cantidadVentasHoy;
    private BigDecimal totalVentasMes = BigDecimal.ZERO;
    private BigDecimal carteraPendiente = BigDecimal.ZERO;
    private long clientesProspectos;
    private long productosStockBajo;
    private long cuentasPendientes;
    private long comprasPendientes;
    private BigDecimal creditoTotal = BigDecimal.ZERO;
    private BigDecimal creditoPagado = BigDecimal.ZERO;
    private double porcentajeCartera; // 0..1

    public BigDecimal getTotalVentasHoy() { return totalVentasHoy; }
    public void setTotalVentasHoy(BigDecimal v) { this.totalVentasHoy = v; }

    public long getCantidadVentasHoy() { return cantidadVentasHoy; }
    public void setCantidadVentasHoy(long v) { this.cantidadVentasHoy = v; }

    public BigDecimal getTotalVentasMes() { return totalVentasMes; }
    public void setTotalVentasMes(BigDecimal v) { this.totalVentasMes = v; }

    public BigDecimal getCarteraPendiente() { return carteraPendiente; }
    public void setCarteraPendiente(BigDecimal v) { this.carteraPendiente = v; }

    public long getClientesProspectos() { return clientesProspectos; }
    public void setClientesProspectos(long v) { this.clientesProspectos = v; }

    public long getProductosStockBajo() { return productosStockBajo; }
    public void setProductosStockBajo(long v) { this.productosStockBajo = v; }

    public long getCuentasPendientes() { return cuentasPendientes; }
    public void setCuentasPendientes(long v) { this.cuentasPendientes = v; }

    public long getComprasPendientes() { return comprasPendientes; }
    public void setComprasPendientes(long v) { this.comprasPendientes = v; }

    public BigDecimal getCreditoTotal() { return creditoTotal; }
    public void setCreditoTotal(BigDecimal v) { this.creditoTotal = v; }

    public BigDecimal getCreditoPagado() { return creditoPagado; }
    public void setCreditoPagado(BigDecimal v) { this.creditoPagado = v; }

    public double getPorcentajeCartera() { return porcentajeCartera; }
    public void setPorcentajeCartera(double v) { this.porcentajeCartera = v; }
}
