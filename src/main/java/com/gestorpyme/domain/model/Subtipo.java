package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;

/**
 * Modelo de dominio que representa un subtipo de item (tabla 'subtipos').
 * Es un POJO: NO depende de Swing ni de JDBC.
 *
 * <p>Un subtipo pertenece a una categoria (relacion id_categoria) y ofrece una
 * clasificacion mas fina que la categoria (p. ej. categoria "Repuestos" con
 * subtipos "Electrico", "Frenos", ...). La naturaleza operativa del item sigue
 * estando en {@code tipo_item} (enum), no aqui.</p>
 *
 * Correspondencia con las columnas de 'subtipos':
 *  idSubtipo    &lt;-&gt; id_subtipo   (clave primaria)
 *  idCategoria  &lt;-&gt; id_categoria (categoria a la que pertenece)
 *  nombre       &lt;-&gt; nombre       (obligatorio; unico dentro de la categoria)
 *  estado       &lt;-&gt; estado       (ver {@link EstadoRegistro})
 */
public class Subtipo {

    private int idSubtipo;
    private int idCategoria;
    private String nombre;
    private EstadoRegistro estado;
    /** Solo lectura: nombre de la categoria (para mostrar; no se persiste desde aqui). */
    private String nombreCategoria;

    public Subtipo() {
    }

    /** Constructor breve, util para el item "(Sin subtipo)" de los combos (id 0). */
    public Subtipo(int idSubtipo, String nombre) {
        this.idSubtipo = idSubtipo;
        this.nombre = nombre;
    }

    public int getIdSubtipo() {
        return idSubtipo;
    }

    public void setIdSubtipo(int idSubtipo) {
        this.idSubtipo = idSubtipo;
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

    public EstadoRegistro getEstado() {
        return estado;
    }

    public void setEstado(EstadoRegistro estado) {
        this.estado = estado;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public void setNombreCategoria(String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    /** Devuelve el nombre (util para mostrar el subtipo en un combo). */
    @Override
    public String toString() {
        return nombre;
    }
}
