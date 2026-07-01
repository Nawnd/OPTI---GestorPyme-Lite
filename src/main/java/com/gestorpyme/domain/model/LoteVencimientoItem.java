package com.gestorpyme.domain.model;

/**
 * Fila de "lotes proximos a vencer" para el Dashboard. Sin Swing.
 */
public class LoteVencimientoItem {

    private String nombreItem;
    private String numeroLote;
    private String fechaVencimiento;

    public String getNombreItem() { return nombreItem; }
    public void setNombreItem(String nombreItem) { this.nombreItem = nombreItem; }

    public String getNumeroLote() { return numeroLote; }
    public void setNumeroLote(String numeroLote) { this.numeroLote = numeroLote; }

    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
}
