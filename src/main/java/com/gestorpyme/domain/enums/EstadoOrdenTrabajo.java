package com.gestorpyme.domain.enums;

/**
 * Estados de una Orden de Trabajo de taller (Paso U.2).
 *
 * Patron canonico: {@code name()} coincide con el valor almacenado en BD; etiqueta legible via
 * {@link #getEtiqueta()} / {@link #toString()}. La transicion ENTREGADA se reserva para el cierre
 * a venta (U.3); CANCELADA y ENTREGADA bloquean la edicion del detalle.
 */
public enum EstadoOrdenTrabajo {

    ABIERTA("Abierta"),
    DIAGNOSTICO("Diagnostico"),
    APROBADA("Aprobada"),
    EN_PROCESO("En proceso"),
    TERMINADA("Terminada"),
    ENTREGADA("Entregada"),
    CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoOrdenTrabajo(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Estados en los que la OT ya no admite edicion de cabecera ni detalle. */
    public boolean esBloqueado() {
        return this == CANCELADA || this == ENTREGADA;
    }

    /**
     * Convierte el texto almacenado en el enum.
     *
     * @param valor texto del estado (p. ej. "ABIERTA"); se ignoran espacios alrededor.
     * @return el estado correspondiente, o null si el valor es null.
     */
    public static EstadoOrdenTrabajo desde(String valor) {
        if (valor == null) {
            return null;
        }
        return EstadoOrdenTrabajo.valueOf(valor.trim());
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
