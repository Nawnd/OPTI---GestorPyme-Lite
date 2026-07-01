package com.gestorpyme.domain.model;

/**
 * KPIs operativos del período (Paso K). Capa: domain.model (POJO de solo lectura). Cuenta movimientos
 * del Kardex y resalta el producto más vendido y la bodega con mayor movimiento dentro del período.
 */
public class DashboardOperativoResumen {
    private long movimientosKardex;
    private long salidasVenta;
    private long entradasCompra;
    private long ajustesManuales;
    private String topProductoNombre;
    private java.math.BigDecimal topProductoCantidad = java.math.BigDecimal.ZERO;
    private String bodegaMayorMovimientoNombre;
    private long bodegaMayorMovimientoCantidad;

    public long getMovimientosKardex() { return movimientosKardex; }
    public void setMovimientosKardex(long v) { this.movimientosKardex = v; }
    public long getSalidasVenta() { return salidasVenta; }
    public void setSalidasVenta(long v) { this.salidasVenta = v; }
    public long getEntradasCompra() { return entradasCompra; }
    public void setEntradasCompra(long v) { this.entradasCompra = v; }
    public long getAjustesManuales() { return ajustesManuales; }
    public void setAjustesManuales(long v) { this.ajustesManuales = v; }
    public String getTopProductoNombre() { return topProductoNombre; }
    public void setTopProductoNombre(String v) { this.topProductoNombre = v; }
    public java.math.BigDecimal getTopProductoCantidad() { return topProductoCantidad; }
    public void setTopProductoCantidad(java.math.BigDecimal v) { this.topProductoCantidad = v; }
    public String getBodegaMayorMovimientoNombre() { return bodegaMayorMovimientoNombre; }
    public void setBodegaMayorMovimientoNombre(String v) { this.bodegaMayorMovimientoNombre = v; }
    public long getBodegaMayorMovimientoCantidad() { return bodegaMayorMovimientoCantidad; }
    public void setBodegaMayorMovimientoCantidad(long v) { this.bodegaMayorMovimientoCantidad = v; }
}
