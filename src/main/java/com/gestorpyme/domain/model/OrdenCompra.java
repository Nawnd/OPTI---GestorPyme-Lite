package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoOrdenCompra;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de una orden de compra (tabla ordenes_compra). Modelo de dominio puro:
 * no depende de Swing ni de JDBC. Los montos usan BigDecimal (DEC-020).
 */
public class OrdenCompra {

    private int idOrden;
    private String numeroOrden;          // OC-000001
    private int idProveedor;
    private String nombreProveedor;      // solo para mostrar (no se persiste aqui)
    private String fechaOrden;           // ISO yyyy-MM-dd
    private String fechaEstimada;        // ISO yyyy-MM-dd (opcional)
    private EstadoOrdenCompra estado = EstadoOrdenCompra.BORRADOR;
    private String observaciones;
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String fechaCreacion;
    private String fechaActualizacion;
    private final List<OrdenCompraDetalle> detalles = new ArrayList<>();

    public int getIdOrden() { return idOrden; }
    public void setIdOrden(int idOrden) { this.idOrden = idOrden; }

    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }

    public int getIdProveedor() { return idProveedor; }
    public void setIdProveedor(int idProveedor) { this.idProveedor = idProveedor; }

    public String getNombreProveedor() { return nombreProveedor; }
    public void setNombreProveedor(String nombreProveedor) { this.nombreProveedor = nombreProveedor; }

    public String getFechaOrden() { return fechaOrden; }
    public void setFechaOrden(String fechaOrden) { this.fechaOrden = fechaOrden; }

    public String getFechaEstimada() { return fechaEstimada; }
    public void setFechaEstimada(String fechaEstimada) { this.fechaEstimada = fechaEstimada; }

    public EstadoOrdenCompra getEstado() { return estado; }
    public void setEstado(EstadoOrdenCompra estado) { this.estado = estado; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(String fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getFechaActualizacion() { return fechaActualizacion; }
    public void setFechaActualizacion(String fechaActualizacion) { this.fechaActualizacion = fechaActualizacion; }

    public List<OrdenCompraDetalle> getDetalles() { return detalles; }
}
