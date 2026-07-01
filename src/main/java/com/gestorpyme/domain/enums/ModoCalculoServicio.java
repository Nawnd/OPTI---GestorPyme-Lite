package com.gestorpyme.domain.enums;

/**
 * Modo de calculo del valor de un servicio (piloto Mano de Obra, v0.7.3).
 * Valor canonico almacenado como TEXT en items.modo_calculo_servicio; el nombre de
 * cada constante coincide con el valor guardado en la base de datos.
 *
 * <ul>
 *   <li>{@link #FIJO}: el servicio usa su precio de venta (comportamiento normal).</li>
 *   <li>{@link #PORCENTAJE_REPUESTOS}: el valor se sugiere como un porcentaje del
 *       subtotal de bienes fisicos (productos/repuestos) de la venta; es editable.</li>
 * </ul>
 *
 * <p>Nota: el modo POR_HORA (horas x tarifa) se deja documentado para una version
 * futura y NO esta implementado en esta version piloto.</p>
 */
public enum ModoCalculoServicio {

    FIJO("Valor fijo"),
    PORCENTAJE_REPUESTOS("Porcentaje sobre repuestos");

    /** Texto legible para mostrar en la interfaz. */
    private final String etiqueta;

    ModoCalculoServicio(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     * Por compatibilidad con datos previos, un valor nulo o vacio se asume {@link #FIJO}.
     *
     * @param valor texto del modo (p. ej. "FIJO"); se ignoran espacios alrededor.
     * @return el {@link ModoCalculoServicio} correspondiente (FIJO si es nulo/vacio).
     * @throws IllegalArgumentException si el valor no es nulo pero no es valido.
     */
    public static ModoCalculoServicio desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return FIJO;
        }
        try {
            return ModoCalculoServicio.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Modo de calculo de servicio no valido: '" + valor + "'", e);
        }
    }

    /** Devuelve la etiqueta legible (util para mostrar en combos). */
    @Override
    public String toString() {
        return etiqueta;
    }
}
