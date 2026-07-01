package com.gestorpyme.service;

import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.VentaDetalle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calcula el valor sugerido de un servicio (piloto Mano de Obra, v0.7.3).
 * Logica pura, sin acceso a base de datos ni a la interfaz (facil de probar).
 *
 * <p>Reglas:
 * <ul>
 *   <li>{@code FIJO}: devuelve el precio de venta del servicio.</li>
 *   <li>{@code PORCENTAJE_REPUESTOS}: devuelve {@code subtotalBienesFisicos * porcentaje / 100},
 *       calculado SOLO sobre las lineas de bienes fisicos (que controlan inventario), nunca
 *       sobre otros servicios ni sobre el total final. Si no hay bienes, devuelve 0.</li>
 * </ul>
 * El resultado nunca es negativo y se redondea a 2 decimales. El valor es solo una
 * <i>sugerencia</i>: la vista permite que el usuario lo ajuste manualmente.</p>
 */
public final class ManoObraCalculator {

    private ManoObraCalculator() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Suma el subtotal de las lineas que corresponden a bienes fisicos (productos/repuestos),
     * identificados porque controlan inventario. Los servicios (que no controlan inventario)
     * se excluyen, de modo que el porcentaje nunca se calcule sobre otros servicios.
     *
     * @param lineas lineas actuales de la venta (puede ser nula o vacia).
     * @return subtotal de bienes fisicos (>= 0).
     */
    public static BigDecimal subtotalBienesFisicos(List<VentaDetalle> lineas) {
        BigDecimal total = BigDecimal.ZERO;
        if (lineas == null) {
            return total;
        }
        for (VentaDetalle d : lineas) {
            if (d != null && d.isControlaInventario() && d.getSubtotalLinea() != null) {
                total = total.add(d.getSubtotalLinea());
            }
        }
        return total;
    }

    /**
     * Valor sugerido para una linea de servicio segun su modo de calculo.
     *
     * @param servicio item de servicio (con su modo y porcentaje).
     * @param lineasActuales lineas ya agregadas a la venta (base para el porcentaje).
     * @return valor sugerido (>= 0, 2 decimales). Para {@code FIJO}, el precio de venta;
     *         para {@code PORCENTAJE_REPUESTOS}, el porcentaje del subtotal de bienes fisicos.
     */
    public static BigDecimal valorSugerido(Item servicio, List<VentaDetalle> lineasActuales) {
        if (servicio == null) {
            return BigDecimal.ZERO;
        }
        ModoCalculoServicio modo = servicio.getModoCalculoServicio() == null
                ? ModoCalculoServicio.FIJO : servicio.getModoCalculoServicio();

        if (modo == ModoCalculoServicio.PORCENTAJE_REPUESTOS) {
            BigDecimal porcentaje = servicio.getPorcentajeServicio() == null
                    ? BigDecimal.ZERO : servicio.getPorcentajeServicio();
            if (porcentaje.signum() < 0) {
                porcentaje = BigDecimal.ZERO; // defensivo: el porcentaje negativo se valida al guardar el item
            }
            BigDecimal base = subtotalBienesFisicos(lineasActuales);
            BigDecimal valor = base.multiply(porcentaje)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return valor.signum() < 0 ? BigDecimal.ZERO : valor;
        }

        // FIJO: usa el precio de venta del servicio.
        BigDecimal precio = servicio.getPrecioVenta() == null ? BigDecimal.ZERO : servicio.getPrecioVenta();
        return precio.signum() < 0 ? BigDecimal.ZERO : precio.setScale(2, RoundingMode.HALF_UP);
    }
}
