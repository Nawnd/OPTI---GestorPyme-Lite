package com.gestorpyme.domain.exception;

/**
 * Excepcion no comprobada (runtime) que indica que un dato no cumple una regla de
 * validacion de negocio. Su mensaje esta pensado para mostrarse directamente al usuario.
 */
public class ValidacionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}
