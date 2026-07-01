package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * KPIs de compras del período (Paso K). Capa: domain.model (POJO de solo lectura). El total ordenado
 * se mide por fecha de la orden; el recibido por fecha de recepción. Excluye órdenes BORRADOR/CANCELADA.
 */
public class DashboardComprasResumen {
    private BigDecimal totalOrdenado = BigDecimal.ZERO;
    private BigDecimal totalRecibido = BigDecimal.ZERO;
    private long ordenesPendientes;
    private long ordenesRecibidas;
    private String proveedorTopNombre;
    private BigDecimal proveedorTopValor = BigDecimal.ZERO;

    public BigDecimal getTotalOrdenado() { return totalOrdenado; }
    public void setTotalOrdenado(BigDecimal v) { this.totalOrdenado = v; }
    public BigDecimal getTotalRecibido() { return totalRecibido; }
    public void setTotalRecibido(BigDecimal v) { this.totalRecibido = v; }
    public long getOrdenesPendientes() { return ordenesPendientes; }
    public void setOrdenesPendientes(long v) { this.ordenesPendientes = v; }
    public long getOrdenesRecibidas() { return ordenesRecibidas; }
    public void setOrdenesRecibidas(long v) { this.ordenesRecibidas = v; }
    public String getProveedorTopNombre() { return proveedorTopNombre; }
    public void setProveedorTopNombre(String v) { this.proveedorTopNombre = v; }
    public BigDecimal getProveedorTopValor() { return proveedorTopValor; }
    public void setProveedorTopValor(BigDecimal v) { this.proveedorTopValor = v; }
}
