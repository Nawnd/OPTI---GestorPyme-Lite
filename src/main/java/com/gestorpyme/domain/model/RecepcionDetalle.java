package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Detalle (linea) de una recepcion de mercancia (tabla recepciones_detalles).
 * Cada linea referencia la linea de la orden de compra que satisface y la bodega
 * donde ingresa fisicamente la mercancia. Cantidad en BigDecimal (DEC-020).
 *
 * Paso M: incorpora dos campos de ENTRADA transitorios ({@link #numeroLote} y
 * {@link #fechaVencimiento}) que NO son columnas de la tabla; son lo que el usuario captura por linea
 * para que el servicio/repositorio cree o engrose el lote y fije {@link #idLote}. Lo persistido sigue
 * siendo {@code id_lote}.
 */
public class RecepcionDetalle {

    private int idDetalleRec;
    private int idRecepcion;
    private int idDetalleOc;
    private int idItem;
    private int idBodega;
    private BigDecimal cantidadRecibida = BigDecimal.ZERO;
    private Integer idLote;

    /** Entrada transitoria (Paso M): numero de lote informado por linea; null/vacio = sin lote. */
    private String numeroLote;
    /** Entrada transitoria (Paso M): fecha de vencimiento ISO (AAAA-MM-DD) opcional; null = sin vencimiento. */
    private String fechaVencimiento;

    public int getIdDetalleRec() { return idDetalleRec; }
    public void setIdDetalleRec(int idDetalleRec) { this.idDetalleRec = idDetalleRec; }

    public int getIdRecepcion() { return idRecepcion; }
    public void setIdRecepcion(int idRecepcion) { this.idRecepcion = idRecepcion; }

    public int getIdDetalleOc() { return idDetalleOc; }
    public void setIdDetalleOc(int idDetalleOc) { this.idDetalleOc = idDetalleOc; }

    public int getIdItem() { return idItem; }
    public void setIdItem(int idItem) { this.idItem = idItem; }

    public int getIdBodega() { return idBodega; }
    public void setIdBodega(int idBodega) { this.idBodega = idBodega; }

    public BigDecimal getCantidadRecibida() { return cantidadRecibida; }
    public void setCantidadRecibida(BigDecimal cantidadRecibida) { this.cantidadRecibida = cantidadRecibida; }

    public Integer getIdLote() { return idLote; }
    public void setIdLote(Integer idLote) { this.idLote = idLote; }

    public String getNumeroLote() { return numeroLote; }
    public void setNumeroLote(String numeroLote) { this.numeroLote = numeroLote; }

    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
}
