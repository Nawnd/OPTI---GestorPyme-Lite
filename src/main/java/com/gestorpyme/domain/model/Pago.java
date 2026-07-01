package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.MedioPago;

import java.math.BigDecimal;

/**
 * Pago asociado a una venta (tabla pagos). No depende de Swing.
 * Los montos se manejan con {@link BigDecimal} (DEC-020).
 */
public class Pago {

    private int idPago;
    private int idVenta;
    private MedioPago medioPago;
    private BigDecimal valor = BigDecimal.ZERO;
    private String fecha;       // ISO
    private String referencia;
    private String observaciones;

    public int getIdPago() { return idPago; }
    public void setIdPago(int idPago) { this.idPago = idPago; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
