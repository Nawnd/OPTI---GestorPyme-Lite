package com.gestorpyme.domain.enums;

/**
 * Estado de una cuenta por cobrar (campo cuentas_por_cobrar.estado). El nombre de
 * cada constante coincide con el valor TEXT guardado en la BD (CHECK del script SQL).
 *
 * Nota: el estado "parcial" del enunciado corresponde a ABONADA.
 */
public enum EstadoCuenta {

    PENDIENTE("Pendiente"),
    ABONADA("Parcial"),
    PAGADA("Pagada"),
    VENCIDA("Vencida"),
    CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoCuenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Convierte el texto almacenado en la BD al enum correspondiente. */
    public static EstadoCuenta desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado de cuenta no puede ser nulo o vacio");
        }
        try {
            return EstadoCuenta.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de cuenta no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
