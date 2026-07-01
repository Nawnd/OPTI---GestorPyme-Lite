package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * KPIs de lotes (Paso K). Capa: domain.model (POJO de solo lectura). Foto ACTUAL de vencimientos. El
 * valor en riesgo es estimado (saldo del lote x precio de compra actual) sobre lotes por vencer o vencidos.
 */
public class DashboardLotesResumen {
    private long proximosAVencer;   // ACTIVO, vence dentro de 30 días
    private long vencidos;          // ACTIVO con fecha < hoy
    private long agotados;          // estado AGOTADO
    private long sinFecha;          // ACTIVO sin fecha de vencimiento
    private BigDecimal valorEnRiesgoEstimado = BigDecimal.ZERO;

    public long getProximosAVencer() { return proximosAVencer; }
    public void setProximosAVencer(long v) { this.proximosAVencer = v; }
    public long getVencidos() { return vencidos; }
    public void setVencidos(long v) { this.vencidos = v; }
    public long getAgotados() { return agotados; }
    public void setAgotados(long v) { this.agotados = v; }
    public long getSinFecha() { return sinFecha; }
    public void setSinFecha(long v) { this.sinFecha = v; }
    public BigDecimal getValorEnRiesgoEstimado() { return valorEnRiesgoEstimado; }
    public void setValorEnRiesgoEstimado(BigDecimal v) { this.valorEnRiesgoEstimado = v; }
}
