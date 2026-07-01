package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoSeguimiento;
import com.gestorpyme.domain.enums.TipoSeguimiento;

/**
 * Modelo de dominio de un seguimiento de CRM (tabla 'crm_seguimientos').
 *
 * Registra una interaccion con un tercero (cliente o prospecto): tipo, descripcion,
 * fecha y estado. POJO sin dependencias de Swing ni JDBC.
 *
 * El campo nombreTercero es de SOLO LECTURA (viene de JOIN) para mostrarlo en pantalla.
 */
public class Seguimiento {

    private int idSeguimiento;
    private int idTercero;
    private TipoSeguimiento tipo;
    private String descripcion;
    private String fecha;
    private EstadoSeguimiento estado;
    private Integer idUsuario;

    // Presentacion (no se guarda; viene de JOIN).
    private String nombreTercero;

    public Seguimiento() {
    }

    public int getIdSeguimiento() {
        return idSeguimiento;
    }

    public void setIdSeguimiento(int idSeguimiento) {
        this.idSeguimiento = idSeguimiento;
    }

    public int getIdTercero() {
        return idTercero;
    }

    public void setIdTercero(int idTercero) {
        this.idTercero = idTercero;
    }

    public TipoSeguimiento getTipo() {
        return tipo;
    }

    public void setTipo(TipoSeguimiento tipo) {
        this.tipo = tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
    }

    public EstadoSeguimiento getEstado() {
        return estado;
    }

    public void setEstado(EstadoSeguimiento estado) {
        this.estado = estado;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreTercero() {
        return nombreTercero;
    }

    public void setNombreTercero(String nombreTercero) {
        this.nombreTercero = nombreTercero;
    }
}
