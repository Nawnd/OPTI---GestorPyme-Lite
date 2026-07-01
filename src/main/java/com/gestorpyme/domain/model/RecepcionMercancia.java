package com.gestorpyme.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Cabecera de una recepcion de mercancia (tabla recepciones_mercancia).
 * Modelo de dominio puro. Una recepcion siempre pertenece a una orden de compra.
 */
public class RecepcionMercancia {

    private int idRecepcion;
    private String numeroRecepcion;   // RC-000001
    private int idOrden;
    private String numeroOrden;       // solo para mostrar
    private String fecha;             // ISO o datetime
    private String observaciones;
    private Integer idUsuario;
    private final List<RecepcionDetalle> detalles = new ArrayList<>();

    public int getIdRecepcion() { return idRecepcion; }
    public void setIdRecepcion(int idRecepcion) { this.idRecepcion = idRecepcion; }

    public String getNumeroRecepcion() { return numeroRecepcion; }
    public void setNumeroRecepcion(String numeroRecepcion) { this.numeroRecepcion = numeroRecepcion; }

    public int getIdOrden() { return idOrden; }
    public void setIdOrden(int idOrden) { this.idOrden = idOrden; }

    public String getNumeroOrden() { return numeroOrden; }
    public void setNumeroOrden(String numeroOrden) { this.numeroOrden = numeroOrden; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public List<RecepcionDetalle> getDetalles() { return detalles; }
}
