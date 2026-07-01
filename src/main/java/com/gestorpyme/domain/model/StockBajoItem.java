package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Fila de "alertas de inventario" (stock bajo) para el Dashboard. Sin Swing.
 */
public class StockBajoItem {

    private String nombreItem;
    private String nombreBodega;
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    public String getNombreItem() { return nombreItem; }
    public void setNombreItem(String nombreItem) { this.nombreItem = nombreItem; }

    public String getNombreBodega() { return nombreBodega; }
    public void setNombreBodega(String nombreBodega) { this.nombreBodega = nombreBodega; }

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
}
