package com.gestorpyme.domain.enums;

/**
 * Tipo de movimiento de inventario. Valor canonico almacenado como TEXT en
 * inventario_movimientos.tipo_movimiento; el nombre de cada constante coincide
 * con el valor guardado en la base de datos.
 *
 * El metodo {@link #incrementaStock()} indica si el movimiento suma o resta stock.
 * (TRASLADO y SALIDA_VENTA existen en el modelo pero se usaran en otros modulos.)
 */
public enum TipoMovimiento {

    ENTRADA("Entrada", true),
    ENTRADA_COMPRA("Entrada por compra", true),
    SALIDA("Salida", false),
    AJUSTE_POSITIVO("Ajuste positivo", true),
    AJUSTE_NEGATIVO("Ajuste negativo", false),
    TRASLADO("Traslado", false),
    SALIDA_VENTA("Salida por venta", false);

    private final String etiqueta;
    private final boolean incrementaStock;

    TipoMovimiento(String etiqueta, boolean incrementaStock) {
        this.etiqueta = etiqueta;
        this.incrementaStock = incrementaStock;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    /** @return true si el movimiento suma stock; false si lo resta. */
    public boolean incrementaStock() {
        return incrementaStock;
    }

    /**
     * Convierte el texto almacenado en la base de datos al enum correspondiente.
     *
     * @throws IllegalArgumentException si el valor es nulo, vacio o no es valido.
     */
    public static TipoMovimiento desde(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("El tipo de movimiento no puede ser nulo o vacio");
        }
        try {
            return TipoMovimiento.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Tipo de movimiento no valido: '" + valor + "'", e);
        }
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
