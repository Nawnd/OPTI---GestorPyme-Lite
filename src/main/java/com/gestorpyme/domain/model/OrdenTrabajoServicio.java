package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Linea de servicio / mano de obra de una Orden de Trabajo (Paso U.2).
 *
 * Capa: domain.model (POJO). El item debe ser de tipo SERVICIO o MANO_OBRA (lo valida el servicio).
 * {@code nombreItem} es de solo lectura (JOIN al listar). Montos en BigDecimal.
 */
public class OrdenTrabajoServicio {

    private int idOtServicio;
    private int idOrdenTrabajo;
    private int idItem;
    private String nombreItem; // solo lectura (JOIN)
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal subtotal = BigDecimal.ZERO;

    public OrdenTrabajoServicio() {
    }

    public int getIdOtServicio() {
        return idOtServicio;
    }

    public void setIdOtServicio(int idOtServicio) {
        this.idOtServicio = idOtServicio;
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
