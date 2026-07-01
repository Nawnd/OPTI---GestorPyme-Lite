package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoExportacion;
import com.gestorpyme.domain.enums.TipoExportacion;

/**
 * Registro de una exportacion realizada (tabla exportaciones_log). No depende de Swing.
 */
public class ExportacionLog {

    private int idExportacion;
    private TipoExportacion tipo;
    private String rutaArchivo;
    private String fecha;       // ISO
    private Integer idUsuario;
    private EstadoExportacion estado;

    public int getIdExportacion() { return idExportacion; }
    public void setIdExportacion(int idExportacion) { this.idExportacion = idExportacion; }

    public TipoExportacion getTipo() { return tipo; }
    public void setTipo(TipoExportacion tipo) { this.tipo = tipo; }

    public String getRutaArchivo() { return rutaArchivo; }
    public void setRutaArchivo(String rutaArchivo) { this.rutaArchivo = rutaArchivo; }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public EstadoExportacion getEstado() { return estado; }
    public void setEstado(EstadoExportacion estado) { this.estado = estado; }
}
