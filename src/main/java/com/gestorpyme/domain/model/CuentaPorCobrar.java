package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoCuenta;

import java.math.BigDecimal;

/**
 * Cuenta por cobrar generada por una venta a credito (tabla cuentas_por_cobrar).
 * No depende de Swing. El saldo lo calcula el servicio (valor_total - valor_pagado).
 * {@code numeroVenta} y {@code nombreTercero} son solo display (JOIN).
 */
public class CuentaPorCobrar {

    private int idCuenta;
    private int idVenta;
    private String numeroVenta;   // solo display
    private int idTercero;
    private String nombreTercero; // solo display
    private BigDecimal valorTotal = BigDecimal.ZERO;
    private BigDecimal valorPagado = BigDecimal.ZERO;
    private BigDecimal saldoPendiente = BigDecimal.ZERO;
    private String fechaVencimiento;
    private EstadoCuenta estado;

    public int getIdCuenta() { return idCuenta; }
    public void setIdCuenta(int idCuenta) { this.idCuenta = idCuenta; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public String getNumeroVenta() { return numeroVenta; }
    public void setNumeroVenta(String numeroVenta) { this.numeroVenta = numeroVenta; }

    public int getIdTercero() { return idTercero; }
    public void setIdTercero(int idTercero) { this.idTercero = idTercero; }

    public String getNombreTercero() { return nombreTercero; }
    public void setNombreTercero(String nombreTercero) { this.nombreTercero = nombreTercero; }

    public BigDecimal getValorTotal() { return valorTotal; }
    public void setValorTotal(BigDecimal valorTotal) { this.valorTotal = valorTotal; }

    public BigDecimal getValorPagado() { return valorPagado; }
    public void setValorPagado(BigDecimal valorPagado) { this.valorPagado = valorPagado; }

    public BigDecimal getSaldoPendiente() { return saldoPendiente; }
    public void setSaldoPendiente(BigDecimal saldoPendiente) { this.saldoPendiente = saldoPendiente; }

    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public EstadoCuenta getEstado() { return estado; }
    public void setEstado(EstadoCuenta estado) { this.estado = estado; }
}
