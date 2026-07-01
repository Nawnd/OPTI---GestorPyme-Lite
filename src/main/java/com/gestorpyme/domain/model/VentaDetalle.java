package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Linea de detalle de una venta (tabla venta_detalles). No depende de Swing.
 *
 * {@code nombreItem} es solo para mostrar. {@code controlaInventario} es un dato
 * de apoyo (tomado del item) para saber si la linea descuenta stock; la fuente
 * autoritativa sigue siendo la tabla items.
 */
public class VentaDetalle {

    private int idDetalle;
    private int idVenta;
    private int idItem;
    private String nombreItem;   // solo display
    private boolean controlaInventario; // apoyo para el descuento de stock
    private BigDecimal cantidad = BigDecimal.ZERO;
    private BigDecimal precioUnitario = BigDecimal.ZERO;
    private BigDecimal descuentoLinea = BigDecimal.ZERO;
    private BigDecimal subtotalLinea = BigDecimal.ZERO;
    // Paso I: bodega real desde la que sale la linea (null para servicios o ventas historicas).
    private Integer idBodegaSalida;
    private String nombreBodegaSalida; // solo display

    public int getIdDetalle() { return idDetalle; }
    public void setIdDetalle(int idDetalle) { this.idDetalle = idDetalle; }

    public int getIdVenta() { return idVenta; }
    public void setIdVenta(int idVenta) { this.idVenta = idVenta; }

    public int getIdItem() { return idItem; }
    public void setIdItem(int idItem) { this.idItem = idItem; }

    public String getNombreItem() { return nombreItem; }
    public void setNombreItem(String nombreItem) { this.nombreItem = nombreItem; }

    public boolean isControlaInventario() { return controlaInventario; }
    public void setControlaInventario(boolean controlaInventario) { this.controlaInventario = controlaInventario; }

    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }

    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public void setPrecioUnitario(BigDecimal precioUnitario) { this.precioUnitario = precioUnitario; }

    public BigDecimal getDescuentoLinea() { return descuentoLinea; }
    public void setDescuentoLinea(BigDecimal descuentoLinea) { this.descuentoLinea = descuentoLinea; }

    public BigDecimal getSubtotalLinea() { return subtotalLinea; }
    public void setSubtotalLinea(BigDecimal subtotalLinea) { this.subtotalLinea = subtotalLinea; }

    /** Bodega real de salida de la linea (Paso I). Null para servicios o ventas historicas. */
    public Integer getIdBodegaSalida() { return idBodegaSalida; }
    public void setIdBodegaSalida(Integer idBodegaSalida) { this.idBodegaSalida = idBodegaSalida; }

    public String getNombreBodegaSalida() { return nombreBodegaSalida; }
    public void setNombreBodegaSalida(String nombreBodegaSalida) { this.nombreBodegaSalida = nombreBodegaSalida; }
}
