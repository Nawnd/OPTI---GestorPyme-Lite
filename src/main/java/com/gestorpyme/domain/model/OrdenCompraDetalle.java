package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Detalle (linea) de una orden de compra (tabla ordenes_compra_detalles).
 * Modelo de dominio puro. Montos y cantidades en BigDecimal (DEC-020).
 */
public class OrdenCompraDetalle {

    private int idDetalle;
    private int idOrden;
    private int idItem;
    private String codigoItem;   // solo para mostrar
    private String nombreItem;   // solo para mostrar
    private Integer idBodegaDestino;
    private String nombreBodega; // solo para mostrar
    private BigDecimal cantidadSolicitada = BigDecimal.ZERO;
    private BigDecimal cantidadRecibida = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal subtotal = BigDecimal.ZERO;

    public int getIdDetalle() { return idDetalle; }
    public void setIdDetalle(int idDetalle) { this.idDetalle = idDetalle; }

    public int getIdOrden() { return idOrden; }
    public void setIdOrden(int idOrden) { this.idOrden = idOrden; }

    public int getIdItem() { return idItem; }
    public void setIdItem(int idItem) { this.idItem = idItem; }

    public String getCodigoItem() { return codigoItem; }
    public void setCodigoItem(String codigoItem) { this.codigoItem = codigoItem; }

    public String getNombreItem() { return nombreItem; }
    public void setNombreItem(String nombreItem) { this.nombreItem = nombreItem; }

    public Integer getIdBodegaDestino() { return idBodegaDestino; }
    public void setIdBodegaDestino(Integer idBodegaDestino) { this.idBodegaDestino = idBodegaDestino; }

    public String getNombreBodega() { return nombreBodega; }
    public void setNombreBodega(String nombreBodega) { this.nombreBodega = nombreBodega; }

    public BigDecimal getCantidadSolicitada() { return cantidadSolicitada; }
    public void setCantidadSolicitada(BigDecimal cantidadSolicitada) { this.cantidadSolicitada = cantidadSolicitada; }

    public BigDecimal getCantidadRecibida() { return cantidadRecibida; }
    public void setCantidadRecibida(BigDecimal cantidadRecibida) { this.cantidadRecibida = cantidadRecibida; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    /** @return cantidad pendiente por recibir (solicitada - recibida), nunca negativa. */
    public BigDecimal getPendiente() {
        BigDecimal sol = cantidadSolicitada == null ? BigDecimal.ZERO : cantidadSolicitada;
        BigDecimal rec = cantidadRecibida == null ? BigDecimal.ZERO : cantidadRecibida;
        BigDecimal p = sol.subtract(rec);
        return p.signum() < 0 ? BigDecimal.ZERO : p;
    }
}
