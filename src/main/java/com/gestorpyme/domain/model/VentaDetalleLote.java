package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Consumo de un lote por una linea de venta (Paso J): fila de la tabla puente venta_detalle_lotes.
 * Capa: domain.model (POJO de solo lectura).
 *
 * Responsabilidad: registrar la trazabilidad "linea de venta -&gt; lote -&gt; cantidad consumida". Una
 * misma linea puede tener varios consumos (uno por lote) cuando un solo lote no cubre la cantidad.
 */
public class VentaDetalleLote {

    private int idVentaDetalleLote;
    private int idDetalle;
    private int idLote;
    private String numeroLote;   // solo display (JOIN a lotes)
    private BigDecimal cantidad;

    public VentaDetalleLote() {
    }

    public VentaDetalleLote(int idDetalle, int idLote, String numeroLote, BigDecimal cantidad) {
        this.idDetalle = idDetalle;
        this.idLote = idLote;
        this.numeroLote = numeroLote;
        this.cantidad = cantidad;
    }

    public int getIdVentaDetalleLote() { return idVentaDetalleLote; }
    public void setIdVentaDetalleLote(int idVentaDetalleLote) { this.idVentaDetalleLote = idVentaDetalleLote; }

    public int getIdDetalle() { return idDetalle; }
    public void setIdDetalle(int idDetalle) { this.idDetalle = idDetalle; }

    public int getIdLote() { return idLote; }
    public void setIdLote(int idLote) { this.idLote = idLote; }

    public String getNumeroLote() { return numeroLote; }
    public void setNumeroLote(String numeroLote) { this.numeroLote = numeroLote; }

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
}
