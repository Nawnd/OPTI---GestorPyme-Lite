package com.gestorpyme.domain.enums;

/**
 * Estado de una venta (campo ventas.estado). El nombre de cada constante coincide
 * con el valor TEXT guardado en la base de datos (CHECK del script SQL).
 */
public enum EstadoVenta {

    BORRADOR("Borrador"),
    CONFIRMADA("Confirmada"),
    PAGADA("Pagada"),
    PENDIENTE_PAGO("Pendiente de pago"),
    ANULADA("Anulada");

    private final String etiqueta;

    EstadoVenta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Convierte el texto almacenado en la BD al enum correspondiente. */
    public static EstadoVenta desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado de venta no puede ser nulo o vacio");
        }
        try {
            return EstadoVenta.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de venta no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
