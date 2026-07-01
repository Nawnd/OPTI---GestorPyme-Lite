package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoTercero;

/**
 * Modelo de dominio que representa un tercero (cliente, prospecto o proveedor),
 * mapeado a la tabla 'terceros'. Es un POJO: NO depende de Swing ni de JDBC.
 *
 * Correspondencia con las columnas de 'terceros':
 *  idTercero      &lt;-&gt; id_tercero      (clave primaria; 0 = aun no persistido)
 *  tipoTercero    &lt;-&gt; tipo_tercero    (ver {@link TipoTercero})
 *  nombre         &lt;-&gt; nombre          (obligatorio)
 *  documento      &lt;-&gt; documento       (opcional)
 *  telefono       &lt;-&gt; telefono        (opcional)
 *  correo         &lt;-&gt; correo          (opcional)
 *  direccion      &lt;-&gt; direccion       (opcional)
 *  estado         &lt;-&gt; estado          (ver {@link EstadoRegistro})
 *  observaciones  &lt;-&gt; observaciones   (opcional)
 *  fechaCreacion  &lt;-&gt; fecha_creacion  (texto fecha-hora generado por la BD)
 */
public class Tercero {

    private int idTercero;
    private TipoTercero tipoTercero;
    private String nombre;
    private String documento;
    private String telefono;
    private String correo;
    private String direccion;
    private EstadoRegistro estado;
    private String observaciones;
    private String fechaCreacion;

    /** Constructor vacio (util para construir el objeto por pasos con setters). */
    public Tercero() {
    }

    public int getIdTercero() {
        return idTercero;
    }

    public void setIdTercero(int idTercero) {
        this.idTercero = idTercero;
    }

    public TipoTercero getTipoTercero() {
        return tipoTercero;
    }

    public void setTipoTercero(TipoTercero tipoTercero) {
        this.tipoTercero = tipoTercero;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public EstadoRegistro getEstado() {
        return estado;
    }

    public void setEstado(EstadoRegistro estado) {
        this.estado = estado;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    /**
     * Representacion amigable para mostrar en listas y combos: el nombre del tercero.
     * No expone informacion tecnica interna.
     */
    @Override
    public String toString() {
        return nombre == null ? "" : nombre;
    }
}
