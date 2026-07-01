package com.gestorpyme.domain.enums;

/**
 * Estado de un lote (campo lotes.estado). El nombre de cada constante coincide
 * con el valor TEXT guardado en la base de datos (CHECK en el script SQL).
 *
 * - ACTIVO: lote vigente y utilizable.
 * - AGOTADO: sin existencias asociadas.
 * - VENCIDO: paso su fecha de vencimiento.
 * - INACTIVO: deshabilitado manualmente.
 */
public enum EstadoLote {

    ACTIVO("Activo"),
    AGOTADO("Agotado"),
    VENCIDO("Vencido"),
    INACTIVO("Inactivo");

    /** Texto legible para mostrar en la interfaz. */
    private final String etiqueta;

    EstadoLote(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del estado (p. ej. "ACTIVO"); se ignoran espacios alrededor.
     * @return el {@link EstadoLote} correspondiente.
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es valido.
     */
    public static EstadoLote desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado de lote no puede ser nulo o vacio");
        }
        try {
            return EstadoLote.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de lote no valido: '" + valor + "'", e);
        }
    }

    /** Devuelve la etiqueta legible (util para mostrar en listas y combos). */
    @Override
    public String toString() {
        return etiqueta;
    }
}
