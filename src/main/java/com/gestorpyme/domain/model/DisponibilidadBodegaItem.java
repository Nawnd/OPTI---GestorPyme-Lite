package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Disponibilidad de un item en una bodega (Paso I), para la asignacion inteligente de bodega
 * por linea de venta. Capa: domain.model (POJO de solo lectura).
 *
 * Responsabilidad: transportar, por bodega activa, cuanto stock hay de un item, para que el
 * servicio de ventas decida desde que bodega sale cada linea.
 */
public class DisponibilidadBodegaItem {

    private final int idItem;
    private final int idBodega;
    private final String nombreBodega;
    private final BigDecimal cantidadDisponible;

    public DisponibilidadBodegaItem(int idItem, int idBodega, String nombreBodega, BigDecimal cantidadDisponible) {
        this.idItem = idItem;
        this.idBodega = idBodega;
        this.nombreBodega = nombreBodega;
        this.cantidadDisponible = cantidadDisponible == null ? BigDecimal.ZERO : cantidadDisponible;
    }

    public int getIdItem() {
        return idItem;
    }

    public int getIdBodega() {
        return idBodega;
    }

    public String getNombreBodega() {
        return nombreBodega;
    }

    public BigDecimal getCantidadDisponible() {
        return cantidadDisponible;
    }
}
