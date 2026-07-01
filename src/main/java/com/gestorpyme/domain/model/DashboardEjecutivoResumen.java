package com.gestorpyme.domain.model;

/**
 * Resumen ejecutivo 360 de un período (Paso K). Capa: domain.model (POJO de solo lectura).
 *
 * Agrega los bloques de KPIs (financiero, inventario, compras, lotes, operativo) y el comparativo
 * real vs meta, junto con la etiqueta del período. Es el objeto que la vista consume en una sola llamada.
 */
public class DashboardEjecutivoResumen {
    private String etiquetaPeriodo;
    private String fechaInicio;
    private String fechaFin;
    private DashboardFinancieroResumen financiero;
    private DashboardInventarioResumen inventario;
    private DashboardComprasResumen compras;
    private DashboardLotesResumen lotes;
    private DashboardOperativoResumen operativo;
    private DashboardComparativoMeta comparativoMeta;

    public String getEtiquetaPeriodo() { return etiquetaPeriodo; }
    public void setEtiquetaPeriodo(String v) { this.etiquetaPeriodo = v; }
    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String v) { this.fechaInicio = v; }
    public String getFechaFin() { return fechaFin; }
    public void setFechaFin(String v) { this.fechaFin = v; }
    public DashboardFinancieroResumen getFinanciero() { return financiero; }
    public void setFinanciero(DashboardFinancieroResumen v) { this.financiero = v; }
    public DashboardInventarioResumen getInventario() { return inventario; }
    public void setInventario(DashboardInventarioResumen v) { this.inventario = v; }
    public DashboardComprasResumen getCompras() { return compras; }
    public void setCompras(DashboardComprasResumen v) { this.compras = v; }
    public DashboardLotesResumen getLotes() { return lotes; }
    public void setLotes(DashboardLotesResumen v) { this.lotes = v; }
    public DashboardOperativoResumen getOperativo() { return operativo; }
    public void setOperativo(DashboardOperativoResumen v) { this.operativo = v; }
    public DashboardComparativoMeta getComparativoMeta() { return comparativoMeta; }
    public void setComparativoMeta(DashboardComparativoMeta v) { this.comparativoMeta = v; }
}
