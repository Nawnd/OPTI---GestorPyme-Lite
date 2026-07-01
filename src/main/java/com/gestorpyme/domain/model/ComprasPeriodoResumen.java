package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Resumen de compras de un periodo (Paso G), para el Dashboard.
 *
 * Capa: domain.model (POJO de solo lectura para presentar indicadores).
 * Responsabilidad: transportar los indicadores de compras calculados por periodo (mes/año).
 *
 * Aclaracion conceptual: en esta version NO existe modulo de pagos a proveedores, por lo que
 * no se reporta "valor pagado". Se reportan dos magnitudes distintas y bien definidas:
 *  - "ordenado": total de ordenes de compra efectivamente emitidas en el periodo (por fecha de orden).
 *  - "recibido": valor de la mercancia recibida en el periodo (por fecha de recepcion).
 */
public class ComprasPeriodoResumen {

    /** Valor total de compras ordenadas en el periodo (COP). */
    private BigDecimal totalOrdenado = BigDecimal.ZERO;
    /** Valor de la mercancia recibida en el periodo (COP). */
    private BigDecimal totalRecibido = BigDecimal.ZERO;
    /** Cantidad de ordenes emitidas en el periodo (pendientes + recibidas). */
    private long cantidadOrdenes;
    /** Ordenes pendientes (EMITIDA o PARCIALMENTE_RECIBIDA) del periodo. */
    private long ordenesPendientes;
    /** Ordenes totalmente recibidas (RECIBIDA) del periodo. */
    private long ordenesRecibidas;
    /** Valor promedio por orden del periodo (COP). */
    private BigDecimal promedioPorOrden = BigDecimal.ZERO;
    /** Proveedor con mayor valor comprado en el periodo (o null si no hay compras). */
    private String proveedorTopNombre;
    /** Valor comprado al proveedor top (COP). */
    private BigDecimal proveedorTopValor = BigDecimal.ZERO;

    public BigDecimal getTotalOrdenado() {
        return totalOrdenado;
    }

    public void setTotalOrdenado(BigDecimal totalOrdenado) {
        this.totalOrdenado = totalOrdenado;
    }

    public BigDecimal getTotalRecibido() {
        return totalRecibido;
    }

    public void setTotalRecibido(BigDecimal totalRecibido) {
        this.totalRecibido = totalRecibido;
    }

    public long getCantidadOrdenes() {
        return cantidadOrdenes;
    }

    public void setCantidadOrdenes(long cantidadOrdenes) {
        this.cantidadOrdenes = cantidadOrdenes;
    }

    public long getOrdenesPendientes() {
        return ordenesPendientes;
    }

    public void setOrdenesPendientes(long ordenesPendientes) {
        this.ordenesPendientes = ordenesPendientes;
    }

    public long getOrdenesRecibidas() {
        return ordenesRecibidas;
    }

    public void setOrdenesRecibidas(long ordenesRecibidas) {
        this.ordenesRecibidas = ordenesRecibidas;
    }

    public BigDecimal getPromedioPorOrden() {
        return promedioPorOrden;
    }

    public void setPromedioPorOrden(BigDecimal promedioPorOrden) {
        this.promedioPorOrden = promedioPorOrden;
    }

    public String getProveedorTopNombre() {
        return proveedorTopNombre;
    }

    public void setProveedorTopNombre(String proveedorTopNombre) {
        this.proveedorTopNombre = proveedorTopNombre;
    }

    public BigDecimal getProveedorTopValor() {
        return proveedorTopValor;
    }

    public void setProveedorTopValor(BigDecimal proveedorTopValor) {
        this.proveedorTopValor = proveedorTopValor;
    }
}
