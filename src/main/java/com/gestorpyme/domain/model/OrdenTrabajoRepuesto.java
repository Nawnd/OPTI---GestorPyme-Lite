package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Linea de repuesto (producto inventariable) de una Orden de Trabajo (Paso U.2).
 *
 * Capa: domain.model (POJO). En U.2 es solo intencion/documento: NO descuenta inventario (eso ocurre
 * en U.3, al cerrar a venta, delegando en VentaService). {@code nombreItem} y {@code nombreBodega}
 * son de solo lectura (JOIN). Montos en BigDecimal. La bodega de salida es la preferida; la real la
 * resolvera la venta en U.3.
 */
public class OrdenTrabajoRepuesto {

    private int idOtRepuesto;
    private int idOrdenTrabajo;
    private int idItem;
    private String nombreItem;     // solo lectura (JOIN)
    private Integer idBodegaSalida; // bodega preferida (puede ser null si el producto no controla inventario)
    private String nombreBodega;   // solo lectura (JOIN)
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal subtotal = BigDecimal.ZERO;

    public OrdenTrabajoRepuesto() {
    }

    public int getIdOtRepuesto() {
        return idOtRepuesto;
    }

    public void setIdOtRepuesto(int idOtRepuesto) {
        this.idOtRepuesto = idOtRepuesto;
    }

    public int getIdOrdenTrabajo() {
        return idOrdenTrabajo;
    }

    public void setIdOrdenTrabajo(int idOrdenTrabajo) {
        this.idOrdenTrabajo = idOrdenTrabajo;
    }

    public int getIdItem() {
        return idItem;
    }

    public void setIdItem(int idItem) {
        this.idItem = idItem;
    }

    public String getNombreItem() {
        return nombreItem;
    }

    public void setNombreItem(String nombreItem) {
        this.nombreItem = nombreItem;
    }

    public Integer getIdBodegaSalida() {
        return idBodegaSalida;
    }

    public void setIdBodegaSalida(Integer idBodegaSalida) {
        this.idBodegaSalida = idBodegaSalida;
    }

    public String getNombreBodega() {
        return nombreBodega;
    }

    public void setNombreBodega(String nombreBodega) {
        this.nombreBodega = nombreBodega;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = precioUnitario;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }
}
