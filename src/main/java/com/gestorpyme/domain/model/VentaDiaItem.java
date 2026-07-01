package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Total de ventas de un dia, para la grafica del Dashboard. Sin Swing.
 */
public class VentaDiaItem {

    private final String etiqueta;
    private final BigDecimal total;

    public VentaDiaItem(String etiqueta, BigDecimal total) {
        this.etiqueta = etiqueta;
        this.total = total;
    }

    public String getEtiqueta() { return etiqueta; }
    public BigDecimal getTotal() { return total; }
}
