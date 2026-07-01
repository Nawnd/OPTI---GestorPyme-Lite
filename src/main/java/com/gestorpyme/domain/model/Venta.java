package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoVenta;

import java.math.BigDecimal;

/**
 * Encabezado de una venta (tabla ventas). No depende de Swing.
 * Los montos se manejan con {@link BigDecimal} (DEC-020).
 * {@code nombreTercero} es solo para mostrar (proviene de un JOIN).
 */
public class Venta {

    private int idVenta;
    private String numeroVenta;
    private Integer idTercero;
    private String nombreTercero; // solo display
    private String fecha;         // ISO (yyyy-MM-dd HH:mm:ss)
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal descuento = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private EstadoVenta estado;
    private Integer idUsuario;
    private String observaciones;

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public String getNumeroVenta() { return numeroVenta; }
    public void setNumeroVenta(String numeroVenta) { this.numeroVenta = numeroVenta; }

    public Integer getIdTercero() { return idTercero; }
    public void setIdTercero(Integer idTercero) { this.idTercero = idTercero; }

    public String getNombreTercero() { return nombreTercero; }
    public void setNombreTercero(String nombreTercero) { this.nombreTercero = nombreTercero; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getDescuento() { return descuento; }
    public void setDescuento(BigDecimal descuento) { this.descuento = descuento; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public EstadoVenta getEstado() { return estado; }
    public void setEstado(EstadoVenta estado) { this.estado = estado; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
