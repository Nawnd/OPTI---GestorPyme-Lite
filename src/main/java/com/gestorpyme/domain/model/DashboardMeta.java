package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Meta gerencial de un período (Paso K). Capa: domain.model (POJO). Fila de la tabla dashboard_metas.
 *
 * Es un OBJETIVO de referencia, totalmente separado de los datos reales: no modifica ventas, utilidad
 * ni contabilidad. Granularidad: mes null = año completo; semana null = mes completo (la edición
 * semanal se difiere a un paso futuro, pero el campo existe por compatibilidad).
 */
public class DashboardMeta {
    private int idMeta;
    private int anio;
    private Integer mes;     // null = año completo
    private Integer semana;  // null = mes completo
    private BigDecimal metaVentas;
    private BigDecimal metaUtilidad;
    private BigDecimal metaMargen;   // fracción 0..1
    private String comentario;
    private String fechaActualizacion;

    public int getIdMeta() { return idMeta; }
    public void setIdMeta(int v) { this.idMeta = v; }
    public int getAnio() { return anio; }
    public void setAnio(int v) { this.anio = v; }
    public Integer getMes() { return mes; }
    public void setMes(Integer v) { this.mes = v; }
    public Integer getSemana() { return semana; }
    public void setSemana(Integer v) { this.semana = v; }
    public BigDecimal getMetaVentas() { return metaVentas; }
    public void setMetaVentas(BigDecimal v) { this.metaVentas = v; }
    public BigDecimal getMetaUtilidad() { return metaUtilidad; }
    public void setMetaUtilidad(BigDecimal v) { this.metaUtilidad = v; }
    public BigDecimal getMetaMargen() { return metaMargen; }
    public void setMetaMargen(BigDecimal v) { this.metaMargen = v; }
    public String getComentario() { return comentario; }
    public void setComentario(String v) { this.comentario = v; }
    public String getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(String v) { this.fechaActualizacion = v; }
}
