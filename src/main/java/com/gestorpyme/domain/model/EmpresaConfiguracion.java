package com.gestorpyme.domain.model;

/**
 * Modelo de dominio de la configuracion de la empresa (tabla 'empresa_configuracion').
 * Es un registro unico. POJO: NO depende de Swing ni de JDBC.
 *
 * Campos: nombreEmpresa, documento, direccion, telefono, correo, moneda,
 * mensajeRecibo (texto al pie del recibo), rutaLogo (ruta del archivo de logo).
 */
public class EmpresaConfiguracion {

    private int idEmpresa;
    private String nombreEmpresa;
    private String documento;
    private String direccion;
    private String telefono;
    private String correo;
    private String moneda = "COP";
    private String mensajeRecibo;
    private String rutaLogo;
    private String fechaActualizacion;

    public EmpresaConfiguracion() {
    }

    public int getIdEmpresa() {
        return idEmpresa;
    }

    public void setIdEmpresa(int idEmpresa) {
        this.idEmpresa = idEmpresa;
    }

    public String getNombreEmpresa() {
        return nombreEmpresa;
    }

    public void setNombreEmpresa(String nombreEmpresa) {
        this.nombreEmpresa = nombreEmpresa;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
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

    public String getMoneda() {
        return moneda;
    }

    public void setMoneda(String moneda) {
        this.moneda = moneda;
    }

    public String getMensajeRecibo() {
        return mensajeRecibo;
    }

    public void setMensajeRecibo(String mensajeRecibo) {
        this.mensajeRecibo = mensajeRecibo;
    }

    public String getRutaLogo() {
        return rutaLogo;
    }

    public void setRutaLogo(String rutaLogo) {
        this.rutaLogo = rutaLogo;
    }

    public String getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(String fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
