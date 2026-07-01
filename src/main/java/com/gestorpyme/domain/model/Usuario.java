package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.Rol;

/**
 * Modelo de dominio que representa un usuario del sistema (tabla 'usuarios').
 * Es un POJO: NO depende de Swing ni de JDBC. El mapeo a/desde la base de datos
 * lo realiza la capa repository.
 *
 * Correspondencia con las columnas de la tabla 'usuarios':
 *  idUsuario      &lt;-&gt; id_usuario      (clave primaria; 0 = aun no persistido)
 *  nombreUsuario  &lt;-&gt; nombre_usuario  (unico, obligatorio)
 *  nombreCompleto &lt;-&gt; nombre_completo (opcional)
 *  passwordHash   &lt;-&gt; password_hash   (hash BCrypt; NUNCA texto plano)
 *  rol            &lt;-&gt; rol             (ver {@link Rol})
 *  estado         &lt;-&gt; estado          (ver {@link EstadoRegistro})
 *  fechaCreacion  &lt;-&gt; fecha_creacion  (texto fecha-hora generado por la BD)
 */
public class Usuario {

    private int idUsuario;
    private String nombreUsuario;
    private String nombreCompleto;
    private String passwordHash;
    private Rol rol;
    private EstadoRegistro estado;
    private String fechaCreacion;

    /** Constructor vacio (util para construir el objeto por pasos con setters). */
    public Usuario() {
    }

    /**
     * Constructor completo (util al leer un registro existente desde la base de datos).
     */
    public Usuario(int idUsuario, String nombreUsuario, String nombreCompleto,
                   String passwordHash, Rol rol, EstadoRegistro estado, String fechaCreacion) {
        this.idUsuario = idUsuario;
        this.nombreUsuario = nombreUsuario;
        this.nombreCompleto = nombreCompleto;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    // --- Getters y setters ---

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
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

    // --- Utilidades de dominio ---

    /** @return true si el usuario esta ACTIVO (condicion necesaria para iniciar sesion). */
    public boolean estaActivo() {
        return estado == EstadoRegistro.ACTIVO;
    }

    /**
     * Representacion textual para depuracion.
     * Importante: NO incluye passwordHash para no exponer credenciales en logs.
     */
    @Override
    public String toString() {
        return "Usuario{"
                + "idUsuario=" + idUsuario
                + ", nombreUsuario='" + nombreUsuario + '\''
                + ", nombreCompleto='" + nombreCompleto + '\''
                + ", rol=" + rol
                + ", estado=" + estado
                + ", fechaCreacion='" + fechaCreacion + '\''
                + '}';
    }
}
