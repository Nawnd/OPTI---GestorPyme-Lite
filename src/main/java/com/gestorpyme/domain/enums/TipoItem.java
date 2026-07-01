package com.gestorpyme.domain.enums;

/**
 * Tipo de item. Valor canonico almacenado como TEXT en items.tipo_item;
 * el nombre de cada constante coincide con el valor guardado en la base de datos.
 */
public enum TipoItem {

    PRODUCTO("Producto"),
    SERVICIO("Servicio"),
    INSUMO("Insumo"),
    REPUESTO("Repuesto"),
    MANO_OBRA("Mano de obra");

    /** Texto legible para mostrar en la interfaz. */
    private final String etiqueta;

    TipoItem(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @param valor texto del tipo (p. ej. "PRODUCTO"); se ignoran espacios alrededor.
     * @return el {@link TipoItem} correspondiente.
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es valido.
     */
    public static TipoItem desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo de item no puede ser nulo o vacio");
        }
        try {
            return TipoItem.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de item no valido: '" + valor + "'", e);
        }
    }

    /** Devuelve la etiqueta legible (util para mostrar en listas y combos). */
    @Override
    public String toString() {
        return etiqueta;
    }
}
