package com.gestorpyme.domain.enums;

/**
 * Medio de pago (campo pagos.medio_pago). El nombre de cada constante coincide con
 * el valor TEXT guardado en la base de datos (CHECK del script SQL).
 *
 * La tabla de pagos admite los seis valores; la tabla de abonos admite solo el
 * subconjunto comun (EFECTIVO, TRANSFERENCIA, TARJETA, OTRO), que es el que se
 * ofrece en los formularios de cobro manual ({@link #COMUNES}).
 */
public enum MedioPago {

    EFECTIVO("Efectivo"),
    TRANSFERENCIA("Transferencia"),
    TARJETA("Tarjeta"),
    CREDITO("Credito"),
    MIXTO("Mixto"),
    OTRO("Otro");

    private final String etiqueta;

    MedioPago(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** Medios admitidos tanto en pagos como en abonos (formularios de cobro manual). */
    public static MedioPago[] comunes() {
        return new MedioPago[]{EFECTIVO, TRANSFERENCIA, TARJETA, OTRO};
    }

    /** Convierte el texto almacenado en la BD al enum correspondiente. */
    public static MedioPago desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El medio de pago no puede ser nulo o vacio");
        }
        try {
            return MedioPago.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Medio de pago no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
