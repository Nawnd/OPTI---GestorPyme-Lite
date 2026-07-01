package com.gestorpyme.domain.enums;

/**
 * Tipo de tercero. Valor canonico almacenado como TEXT en terceros.tipo_tercero;
 * el nombre de cada constante coincide con el valor guardado en la base de datos.
 */
public enum TipoTercero {

    CLIENTE("Cliente"),
    PROSPECTO("Prospecto"),
    PROVEEDOR("Proveedor");

    /** Texto legible para mostrar en la interfaz. */
    private final String etiqueta;

    TipoTercero(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del tipo (p. ej. "CLIENTE"); se ignoran espacios alrededor.
     * @return el {@link TipoTercero} correspondiente.
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es valido.
     */
    public static TipoTercero desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo de tercero no puede ser nulo o vacio");
        }
        try {
            return TipoTercero.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de tercero no valido: '" + valor + "'", e);
        }
    }

    /** Devuelve la etiqueta legible (util para mostrar en listas y combos). */
    @Override
    public String toString() {
        return etiqueta;
    }
}
