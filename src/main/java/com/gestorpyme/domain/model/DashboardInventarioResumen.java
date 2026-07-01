package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * KPIs de inventario (Paso K). Capa: domain.model (POJO de solo lectura). Es una foto ACTUAL del
 * inventario (no del período), pues las existencias son un estado puntual. El valor de inventario es
 * estimado (stock x precio de compra actual) sobre ítems con costo &gt; 0.
 */
public class DashboardInventarioResumen {
    private long productosBajoStock;
    private long productosSinStock;
    private long itemsStockMaximo;
    private long itemsProveedorPreferido;
    private BigDecimal sugeridoTotal = BigDecimal.ZERO;
    private BigDecimal valorInventarioEstimado = BigDecimal.ZERO;

    public long getProductosBajoStock() { return productosBajoStock; }
    public void setProductosBajoStock(long v) { this.productosBajoStock = v; }
    public long getProductosSinStock() { return productosSinStock; }
    public void setProductosSinStock(long v) { this.productosSinStock = v; }
    public long getItemsStockMaximo() { return itemsStockMaximo; }
    public void setItemsStockMaximo(long v) { this.itemsStockMaximo = v; }
    public long getItemsProveedorPreferido() { return itemsProveedorPreferido; }
    public void setItemsProveedorPreferido(long v) { this.itemsProveedorPreferido = v; }
    public BigDecimal getSugeridoTotal() { return sugeridoTotal; }
    public void setSugeridoTotal(BigDecimal v) { this.sugeridoTotal = v; }
    public BigDecimal getValorInventarioEstimado() { return valorInventarioEstimado; }
    public void setValorInventarioEstimado(BigDecimal v) { this.valorInventarioEstimado = v; }
}
