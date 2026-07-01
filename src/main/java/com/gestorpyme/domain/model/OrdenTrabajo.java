package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de una Orden de Trabajo de taller (Paso U.2), con su detalle de servicios y repuestos.
 *
 * Capa: domain.model (POJO). En U.2 la OT es un documento de trabajo: NO factura, NO descuenta
 * inventario y NO genera cartera/pago (eso es U.3). {@code nombreCliente} y {@code placaVehiculo}
 * son de solo lectura (JOIN). {@code idVenta} queda null hasta el cierre a venta (U.3). Montos en
 * BigDecimal; el servicio recalcula subtotales y total a partir del detalle.
 */
public class OrdenTrabajo {

    private int idOrdenTrabajo;
    private String numeroOt;
    private int idTercero;
    private String nombreCliente;   // solo lectura (JOIN)
    private int idVehiculo;
    private String placaVehiculo;   // solo lectura (JOIN)
    private String fechaIngreso;
    private String fechaEntregaEstimada;
    private double kilometrajeIngreso;
    private String motivoIngreso;
    private String diagnostico;
    private EstadoOrdenTrabajo estado;
    private String observaciones;
    private Integer idUsuario;
    private Integer idVenta;         // null hasta el cierre a venta (U.3)
    private BigDecimal subtotalServicios = BigDecimal.ZERO;
    private BigDecimal subtotalRepuestos = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String fechaCreacion;
    private String fechaActualizacion;

    private List<OrdenTrabajoServicio> servicios = new ArrayList<>();
    private List<OrdenTrabajoRepuesto> repuestos = new ArrayList<>();

    public OrdenTrabajo() {
    }

    public int getIdOrdenTrabajo() {
        return idOrdenTrabajo;
    }

    public void setIdOrdenTrabajo(int idOrdenTrabajo) {
        this.idOrdenTrabajo = idOrdenTrabajo;
    }

    public String getNumeroOt() {
        return numeroOt;
    }

    public void setNumeroOt(String numeroOt) {
        this.numeroOt = numeroOt;
    }

    public int getIdTercero() {
        return idTercero;
    }

    public void setIdTercero(int idTercero) {
        this.idTercero = idTercero;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public int getIdVehiculo() {
        return idVehiculo;
    }

    public void setIdVehiculo(int idVehiculo) {
        this.idVehiculo = idVehiculo;
    }

    public String getPlacaVehiculo() {
        return placaVehiculo;
    }

    public void setPlacaVehiculo(String placaVehiculo) {
        this.placaVehiculo = placaVehiculo;
    }

    public String getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(String fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public String getFechaEntregaEstimada() {
        return fechaEntregaEstimada;
    }

    public void setFechaEntregaEstimada(String fechaEntregaEstimada) {
        this.fechaEntregaEstimada = fechaEntregaEstimada;
    }

    public double getKilometrajeIngreso() {
        return kilometrajeIngreso;
    }

    public void setKilometrajeIngreso(double kilometrajeIngreso) {
        this.kilometrajeIngreso = kilometrajeIngreso;
    }

    public String getMotivoIngreso() {
        return motivoIngreso;
    }

    public void setMotivoIngreso(String motivoIngreso) {
        this.motivoIngreso = motivoIngreso;
    }

    public String getDiagnostico() {
        return diagnostico;
    }

    public void setDiagnostico(String diagnostico) {
        this.diagnostico = diagnostico;
    }

    public EstadoOrdenTrabajo getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrdenTrabajo estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public Integer getIdVenta() {
        return idVenta;
    }

    public void setIdVenta(Integer idVenta) {
        this.idVenta = idVenta;
    }

    public BigDecimal getSubtotalServicios() {
        return subtotalServicios;
    }

    public void setSubtotalServicios(BigDecimal subtotalServicios) {
        this.subtotalServicios = subtotalServicios;
    }

    public BigDecimal getSubtotalRepuestos() {
        return subtotalRepuestos;
    }

    public void setSubtotalRepuestos(BigDecimal subtotalRepuestos) {
        this.subtotalRepuestos = subtotalRepuestos;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public String getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(String fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }

    public List<OrdenTrabajoServicio> getServicios() {
        return servicios;
    }

    public void setServicios(List<OrdenTrabajoServicio> servicios) {
        this.servicios = (servicios != null) ? servicios : new ArrayList<>();
    }

    public List<OrdenTrabajoRepuesto> getRepuestos() {
        return repuestos;
    }

    public void setRepuestos(List<OrdenTrabajoRepuesto> repuestos) {
        this.repuestos = (repuestos != null) ? repuestos : new ArrayList<>();
    }
}
