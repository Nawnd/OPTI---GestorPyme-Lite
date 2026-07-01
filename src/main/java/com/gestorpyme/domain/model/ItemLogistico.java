package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Fila del inventario logistico (vista de reabastecimiento). DTO de solo lectura que
 * combina stock actual, minimo, en pedido y la cantidad sugerida de compra, junto con
 * un estado logistico calculado (NORMAL / BAJO / SIN_STOCK / EN_PEDIDO y combinaciones).
 * No se persiste: lo arma el servicio a partir de varias consultas.
 */
public class ItemLogistico {

    private int idItem;
    private String codigo;
    private String nombre;
    private BigDecimal stockActual = BigDecimal.ZERO;
    private BigDecimal stockMinimo = BigDecimal.ZERO;
    private BigDecimal enPedido = BigDecimal.ZERO;
    private BigDecimal sugerido = BigDecimal.ZERO;
    private BigDecimal precioCompra = BigDecimal.ZERO;
    private String estado;
    // Paso F: techo de reposicion (nullable) y proveedor preferido (id + nombre, solo lectura).
    private BigDecimal stockMaximo;
    private Integer idProveedorPreferido;
    private String nombreProveedorPreferido;

    public int getIdItem() { return idItem; }
    public void setIdItem(int idItem) { this.idItem = idItem; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public BigDecimal getStockActual() { return stockActual; }
    public void setStockActual(BigDecimal stockActual) { this.stockActual = stockActual; }

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }

    public BigDecimal getEnPedido() { return enPedido; }
    public void setEnPedido(BigDecimal enPedido) { this.enPedido = enPedido; }

    public BigDecimal getSugerido() { return sugerido; }
    public void setSugerido(BigDecimal sugerido) { this.sugerido = sugerido; }

    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    /** Stock maximo (techo de reposicion). Null si no esta definido. */
    public BigDecimal getStockMaximo() { return stockMaximo; }
    public void setStockMaximo(BigDecimal stockMaximo) { this.stockMaximo = stockMaximo; }

    public Integer getIdProveedorPreferido() { return idProveedorPreferido; }
    public void setIdProveedorPreferido(Integer idProveedorPreferido) { this.idProveedorPreferido = idProveedorPreferido; }

    /** Nombre del proveedor preferido (solo lectura, via JOIN). */
    public String getNombreProveedorPreferido() { return nombreProveedorPreferido; }
    public void setNombreProveedorPreferido(String nombreProveedorPreferido) { this.nombreProveedorPreferido = nombreProveedorPreferido; }
}
