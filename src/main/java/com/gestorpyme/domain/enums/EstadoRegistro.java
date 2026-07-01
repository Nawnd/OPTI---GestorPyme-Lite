package com.gestorpyme.domain.enums;

/**
 * Estado de un registro. El valor se almacena como TEXT (p. ej. en usuarios.estado);
 * el nombre de cada constante coincide con el valor guardado en la base de datos.
 * Se usa para la "baja logica": un registro inactivo no se elimina, solo se deshabilita.
 */
public enum EstadoRegistro {

    /** Registro habilitado / vigente. */
    ACTIVO,

    /** Registro deshabilitado (baja logica; no se elimina fisicamente). */
    INACTIVO;

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del estado (p. ej. "ACTIVO"); se ignoran espacios alrededor.
     * @return el {@link EstadoRegistro} correspondiente.
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es un estado valido.
     */
    public static EstadoRegistro desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado no puede ser nulo o vacio");
        }
        try {
            return EstadoRegistro.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado no valido: '" + valor + "'", e);
        }
    }
}
