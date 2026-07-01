package com.gestorpyme.domain.enums;

/**
 * Estado de una orden de compra (campo ordenes_compra.estado). El nombre de cada
 * constante coincide con el valor TEXT guardado en la base de datos (CHECK del script
 * de migracion de compras).
 *
 * - BORRADOR: orden en edicion; no afecta stock en pedido.
 * - EMITIDA: orden enviada al proveedor; suma al stock en pedido.
 * - PARCIALMENTE_RECIBIDA: se recibio parte de la mercancia.
 * - RECIBIDA: se recibio todo lo solicitado.
 * - CANCELADA: orden anulada; el saldo pendiente deja de contar.
 */
public enum EstadoOrdenCompra {

    BORRADOR("Borrador"),
    EMITIDA("Emitida"),
    PARCIALMENTE_RECIBIDA("Parcialmente recibida"),
    RECIBIDA("Recibida"),
    CANCELADA("Cancelada");

    private final String etiqueta;

    EstadoOrdenCompra(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** @return true si la orden admite recepcion de mercancia. */
    public boolean permiteRecepcion() {
        return this == EMITIDA || this == PARCIALMENTE_RECIBIDA;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del estado (p. ej. "EMITIDA"); se ignoran espacios alrededor.
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es valido.
     */
    public static EstadoOrdenCompra desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El estado de orden no puede ser nulo o vacio");
        }
        try {
            return EstadoOrdenCompra.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado de orden no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
