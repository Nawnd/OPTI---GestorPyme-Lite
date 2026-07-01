package com.gestorpyme.domain.enums;

/**
 * Tipo de seguimiento de CRM (campo crm_seguimientos.tipo).
 * El nombre de cada constante coincide con el valor TEXT en la base de datos.
 */
public enum TipoSeguimiento {

    LLAMADA("Llamada"),
    NOTA("Nota"),
    VISITA("Visita"),
    WHATSAPP("WhatsApp"),
    CORREO("Correo"),
    OTRO("Otro");

    private final String etiqueta;

    TipoSeguimiento(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del tipo; se ignoran espacios alrededor.
     * @throws IllegalArgumentException si es nulo, vacio o no valido.
     */
    public static TipoSeguimiento desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo de seguimiento no puede ser nulo o vacio");
        }
        try {
            return TipoSeguimiento.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de seguimiento no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
