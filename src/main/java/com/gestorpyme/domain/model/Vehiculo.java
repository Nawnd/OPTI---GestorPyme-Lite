package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;

/**
 * Vehiculo asociado a un cliente (base del modulo Taller / Orden de trabajo, Paso U.1).
 *
 * Capa: domain.model (POJO limpio, sin dependencia de Swing/JDBC).
 * Responsabilidad: representar un vehiculo de un cliente para su registro/consulta y, mas
 * adelante, para vincularlo a ordenes de trabajo. El campo {@code nombreCliente} es de solo
 * lectura (se resuelve por JOIN al listar) y no se persiste desde aqui.
 */
public class Vehiculo {

    private int idVehiculo;
    private int idTercero;
    /** Solo lectura: nombre del cliente, resuelto por JOIN al listar (no se persiste aqui). */
    private String nombreCliente;
    private String placa;
    private String marca;
    private String linea;
    private Integer anio;
    private String color;
    private double kilometraje;
    private String observaciones;
    private EstadoRegistro estado;
    private String fechaCreacion;

    public Vehiculo() {
    }

    public int getIdVehiculo() {
        return idVehiculo;
    }

    public void setIdVehiculo(int idVehiculo) {
        this.idVehiculo = idVehiculo;
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

    public String getPlaca() {
        return placa;
    }

    public void setPlaca(String placa) {
        this.placa = placa;
    }

    public String getMarca() {
        return marca;
    }

    public void setMarca(String marca) {
        this.marca = marca;
    }

    public String getLinea() {
        return linea;
    }

    public void setLinea(String linea) {
        this.linea = linea;
    }

    public Integer getAnio() {
        return anio;
    }

    public void setAnio(Integer anio) {
        this.anio = anio;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public double getKilometraje() {
        return kilometraje;
    }

    public void setKilometraje(double kilometraje) {
        this.kilometraje = kilometraje;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public EstadoRegistro getEstado() {
        return estado;
    }

    public void setEstado(EstadoRegistro estado) {
        this.estado = estado;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
