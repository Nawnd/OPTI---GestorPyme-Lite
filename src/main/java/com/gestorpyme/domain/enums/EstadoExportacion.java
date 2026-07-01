package com.gestorpyme.domain.enums;

/**
 * Resultado de una exportacion (campo exportaciones_log.estado). El nombre coincide
 * con el valor TEXT guardado en la BD (CHECK del script SQL).
 */
public enum EstadoExportacion {

    GENERADO("Generado"),
    ERROR("Error");

    private final String etiqueta;

    EstadoExportacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
