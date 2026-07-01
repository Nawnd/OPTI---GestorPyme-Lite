package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;

/**
 * Modelo de dominio que representa una categoria de items (tabla 'categorias').
 * Es un POJO: NO depende de Swing ni de JDBC. Se usa, sobre todo, para poblar el
 * selector de categoria del formulario de productos.
 *
 * Correspondencia con las columnas de 'categorias':
 *  idCategoria  &lt;-&gt; id_categoria  (clave primaria)
 *  nombre       &lt;-&gt; nombre        (unico, obligatorio)
 *  descripcion  &lt;-&gt; descripcion   (opcional)
 *  estado       &lt;-&gt; estado        (ver {@link EstadoRegistro})
 */
public class Categoria {

    private int idCategoria;
    private String nombre;
    private String descripcion;
    private EstadoRegistro estado;

    public Categoria() {
    }

    public Categoria(int idCategoria, String nombre) {
        this.idCategoria = idCategoria;
        this.nombre = nombre;
    }

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoRegistro getEstado() {
        return estado;
    }

    public void setEstado(EstadoRegistro estado) {
        this.estado = estado;
    }

    /** Devuelve el nombre (util para mostrar la categoria en un combo). */
    @Override
    public String toString() {
        return nombre;
    }
}
