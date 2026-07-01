package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.MedioPago;

import java.math.BigDecimal;

/**
 * Abono a una cuenta por cobrar (tabla abonos_cuenta). No depende de Swing.
 * Solo admite medios EFECTIVO/TRANSFERENCIA/TARJETA/OTRO (subconjunto, ver BD).
 */
public class Abono {

    private int idAbono;
    private int idCuenta;
    private BigDecimal valor = BigDecimal.ZERO;
    private String fecha;       // ISO
    private MedioPago medioPago;
    private String observaciones;

    public int getIdAbono() { return idAbono; }
    public void setIdAbono(int idAbono) { this.idAbono = idAbono; }

    public int getIdCuenta() { return idCuenta; }
    public void setIdCuenta(int idCuenta) { this.idCuenta = idCuenta; }

    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public MedioPago getMedioPago() { return medioPago; }
    public void setMedioPago(MedioPago medioPago) { this.medioPago = medioPago; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
}
