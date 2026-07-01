package com.gestorpyme.service;

import com.gestorpyme.domain.model.Usuario;

/**
 * Resultado de un intento de autenticacion. Indica el desenlace mediante {@link Estado}
 * y, en caso de exito, el {@link Usuario} autenticado. Incluye un mensaje listo para mostrar.
 * Se construye con los metodos de fabrica estaticos (exito, credencialesInvalidas, etc.).
 */
public class ResultadoAutenticacion {

    /** Posibles desenlaces de la autenticacion. */
    public enum Estado {
        /** Credenciales correctas y usuario activo. */
        EXITO,
        /** Usuario inexistente o contrasena incorrecta (mismo caso por seguridad). */
        CREDENCIALES_INVALIDAS,
        /** Credenciales correctas pero el usuario esta inactivo. */
        USUARIO_INACTIVO,
        /** Error tecnico (p. ej. fallo de base de datos). */
        ERROR
    }

    private final Estado estado;
    private final Usuario usuario;
    private final String mensaje;

    private ResultadoAutenticacion(Estado estado, Usuario usuario, String mensaje) {
        this.estado = estado;
        this.usuario = usuario;
        this.mensaje = mensaje;
    }

    public static ResultadoAutenticacion exito(Usuario usuario) {
        return new ResultadoAutenticacion(Estado.EXITO, usuario, "Inicio de sesion correcto");
    }

    public static ResultadoAutenticacion credencialesInvalidas() {
        return new ResultadoAutenticacion(Estado.CREDENCIALES_INVALIDAS, null,
                "Usuario o contrasena incorrectos");
    }

    public static ResultadoAutenticacion usuarioInactivo() {
        return new ResultadoAutenticacion(Estado.USUARIO_INACTIVO, null,
                "El usuario esta inactivo");
    }

    public static ResultadoAutenticacion error(String mensaje) {
        return new ResultadoAutenticacion(Estado.ERROR, null, mensaje);
    }

    public Estado getEstado() {
        return estado;
    }

    /** @return el usuario autenticado (solo en EXITO; null en los demas casos). */
    public Usuario getUsuario() {
        return usuario;
    }

    public String getMensaje() {
        return mensaje;
    }

    /** @return true si la autenticacion fue exitosa. */
    public boolean esExito() {
        return estado == Estado.EXITO;
    }
}
