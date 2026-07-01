package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.MedioPago;

import java.math.BigDecimal;

/**
 * Fila de "ultimos pagos" para el Dashboard (datos para mostrar, con el numero de
 * venta y el nombre del cliente ya resueltos por JOIN). Sin Swing.
 */
public class PagoReciente {

    private String fecha;
    private String numeroVenta;
    private String nombreTercero;
    private MedioPago medioPago;
    private BigDecimal valor = BigDecimal.ZERO;

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getNumeroVenta() { return numeroVenta; }
    public void setNumeroVenta(String numeroVenta) { this.numeroVenta = numeroVenta; }

    public String getNombreTercero() { return nombreTercero; }
    public void setNombreTercero(String nombreTercero) { this.nombreTercero = nombreTercero; }

    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
}
