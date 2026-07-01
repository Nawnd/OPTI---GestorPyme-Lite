package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Comparación real vs meta de un período (Paso K). Capa: domain.model (POJO de solo lectura).
 *
 * Calculado por el servicio a partir de los KPIs reales y la meta registrada. NUNCA modifica datos
 * reales: es solo una referencia de desempeño. El cumplimiento es real/meta (0..1+), o null si no hay meta.
 */
public class DashboardComparativoMeta {
    private boolean hayMeta;
    private BigDecimal metaVentas;
    private BigDecimal ventasReales = BigDecimal.ZERO;
    private Double cumplimientoVentas;   // ventasReales / metaVentas (null si no hay meta)
    private BigDecimal metaUtilidad;
    private BigDecimal utilidadReal = BigDecimal.ZERO;
    private Double cumplimientoUtilidad;
    private BigDecimal metaMargen;
    private BigDecimal margenReal = BigDecimal.ZERO;
    private String comentario;

    public boolean isHayMeta() { return hayMeta; }
    public void setHayMeta(boolean v) { this.hayMeta = v; }
    public BigDecimal getMetaVentas() { return metaVentas; }
    public void setMetaVentas(BigDecimal v) { this.metaVentas = v; }
    public BigDecimal getVentasReales() { return ventasReales; }
    public void setVentasReales(BigDecimal v) { this.ventasReales = v; }
    public Double getCumplimientoVentas() { return cumplimientoVentas; }
    public void setCumplimientoVentas(Double v) { this.cumplimientoVentas = v; }
    public BigDecimal getMetaUtilidad() { return metaUtilidad; }
    public void setMetaUtilidad(BigDecimal v) { this.metaUtilidad = v; }
    public BigDecimal getUtilidadReal() { return utilidadReal; }
    public void setUtilidadReal(BigDecimal v) { this.utilidadReal = v; }
    public Double getCumplimientoUtilidad() { return cumplimientoUtilidad; }
    public void setCumplimientoUtilidad(Double v) { this.cumplimientoUtilidad = v; }
    public BigDecimal getMetaMargen() { return metaMargen; }
    public void setMetaMargen(BigDecimal v) { this.metaMargen = v; }
    public BigDecimal getMargenReal() { return margenReal; }
    public void setMargenReal(BigDecimal v) { this.margenReal = v; }
    public String getComentario() { return comentario; }
    public void setComentario(String v) { this.comentario = v; }
}
