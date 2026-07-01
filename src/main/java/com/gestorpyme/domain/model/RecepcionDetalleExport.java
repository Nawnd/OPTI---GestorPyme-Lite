package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Fila de detalle de recepcion para exportacion CSV (Paso N). Capa: domain.model (POJO de solo lectura,
 * sin dependencia de Swing ni JDBC). Reune, por cada linea recibida, la cabecera (numero de recepcion,
 * fecha, numero de orden), el item, la bodega, la cantidad recibida y los datos de lote (numero,
 * vencimiento e id) cuando la linea tiene lote; si no lo tiene, esos campos quedan nulos.
 *
 * Permite reconstruir la trazabilidad: Recepcion -> Detalle -> Item -> Bodega -> Lote -> Vencimiento.
 * No todas las recepciones tienen lote (LEFT JOIN a lotes en el repositorio).
 */
public class RecepcionDetalleExport {

    private String numeroRecepcion;
    private String fecha;
    private String numeroOrden;
    private String nombreItem;
    private String nombreBodega;
    private BigDecimal cantidadRecibida;
    private Integer idLote;          // null si la linea no tiene lote
    private String numeroLote;       // null si la linea no tiene lote
    private String fechaVencimiento; // null si no aplica

    public String getNumeroRecepcion() { return numeroRecepcion; }
    public void setNumeroRecepcion(String numeroRecepcion) { this.numeroRecepcion = numeroRecepcion; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }

    public String getNombreItem() { return nombreItem; }
    public void setNombreItem(String nombreItem) { this.nombreItem = nombreItem; }

    public String getNombreBodega() { return nombreBodega; }
    public void setNombreBodega(String nombreBodega) { this.nombreBodega = nombreBodega; }

    public BigDecimal getCantidadRecibida() { return cantidadRecibida; }
    public void setCantidadRecibida(BigDecimal cantidadRecibida) { this.cantidadRecibida = cantidadRecibida; }

    public Integer getIdLote() { return idLote; }
    public void setIdLote(Integer idLote) { this.idLote = idLote; }

    public String getNumeroLote() { return numeroLote; }
    public void setNumeroLote(String numeroLote) { this.numeroLote = numeroLote; }

    public String getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(String fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }
}
