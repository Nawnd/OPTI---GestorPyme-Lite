package com.gestorpyme.domain.enums;

/**
 * Estado de un seguimiento de CRM (campo crm_seguimientos.estado).
 * El nombre de cada constante coincide con el valor TEXT en la base de datos.
 */
public enum EstadoSeguimiento {

    REGISTRADO("Registrado"),
    CERRADO("Cerrado"),
    PENDIENTE("Pendiente");

    private final String etiqueta;

    EstadoSeguimiento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del estado; se ignoran espacios alrededor.
     * @throws IllegalArgumentException si es nulo, vacio o no valido.
     */
    public static EstadoSeguimiento desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado de seguimiento no puede ser nulo o vacio");
        }
        try {
            return EstadoSeguimiento.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de seguimiento no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
