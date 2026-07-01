package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;

/**
 * Modelo de dominio que representa una bodega / ubicacion de almacenamiento
 * (tabla 'bodegas'). Es un POJO: NO depende de Swing ni de JDBC.
 *
 * Correspondencia con las columnas de 'bodegas':
 *  idBodega      &lt;-&gt; id_bodega      (clave primaria; 0 = aun no persistida)
 *  nombre        &lt;-&gt; nombre         (unico, obligatorio)
 *  ubicacion     &lt;-&gt; ubicacion      (opcional)
 *  estado        &lt;-&gt; estado         (ver {@link EstadoRegistro})
 *  fechaCreacion &lt;-&gt; fecha_creacion (texto fecha-hora generado por la BD)
 */
public class Bodega {

    private int idBodega;
    private String nombre;
    private String ubicacion;
    private EstadoRegistro estado;
    private String fechaCreacion;

    public Bodega() {
    }

    public int getIdBodega() {
        return idBodega;
    }

    public void setIdBodega(int idBodega) {
        this.idBodega = idBodega;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
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

    /** Devuelve el nombre (util para mostrar la bodega en un combo). */
    @Override
    public String toString() {
        return nombre;
    }
}
