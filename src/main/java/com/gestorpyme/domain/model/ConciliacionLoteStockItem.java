package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Fila de conciliación entre el stock autoritativo ({@code inventario_stock.cantidad}) y el stock
 * loteado (suma de {@code lotes.cantidad_disponible}) para un mismo item + bodega (Paso Q).
 *
 * Capa: domain.model (POJO de solo lectura). El estado y la diferencia son <b>derivados</b> (no se
 * almacenan), igual que {@code ExistenciaStock.getEstadoStock()}. Es un dato de auditoría informativo:
 * una diferencia no siempre es un error (puede provenir de una entrada sin lote válida, carga histórica
 * o un ajuste manual). No modifica datos ni recalcula nada.
 */
public class ConciliacionLoteStockItem {

    /** Estados posibles de la conciliación (texto mostrado y comparado en pruebas/UI). */
    public static final String ESTADO_OK = "OK";
    public static final String ESTADO_FALTA_LOTEAR = "FALTA_LOTEAR";
    public static final String ESTADO_EXCESO_LOTEADO = "EXCESO_LOTEADO";
    public static final String ESTADO_SIN_LOTES = "SIN_LOTES";

    private String codigoItem;
    private String nombreItem;
    private String nombreBodega;
    private BigDecimal stockActual;
    private BigDecimal stockLoteado;

    public String getCodigoItem() { return codigoItem; }
    public void setCodigoItem(String codigoItem) { this.codigoItem = codigoItem; }

    public String getNombreItem() { return nombreItem; }
    public void setNombreItem(String nombreItem) { this.nombreItem = nombreItem; }

    public String getNombreBodega() { return nombreBodega; }
    public void setNombreBodega(String nombreBodega) { this.nombreBodega = nombreBodega; }

    public BigDecimal getStockActual() { return stockActual; }
    public void setStockActual(BigDecimal stockActual) { this.stockActual = stockActual; }

    public BigDecimal getStockLoteado() { return stockLoteado; }
    public void setStockLoteado(BigDecimal stockLoteado) { this.stockLoteado = stockLoteado; }

    /** @return stock autoritativo menos stock loteado (puede ser negativo si hay exceso loteado). */
    public BigDecimal getDiferencia() {
        return seguro(stockActual).subtract(seguro(stockLoteado));
    }

    /**
     * Clasifica la fila:
     * <ul>
     *   <li>OK: el stock coincide exactamente con lo loteado.</li>
     *   <li>EXCESO_LOTEADO: lo loteado supera al stock autoritativo.</li>
     *   <li>SIN_LOTES: hay stock positivo pero ningún lote registrado.</li>
     *   <li>FALTA_LOTEAR: el stock supera a lo loteado (pero existe algún lote).</li>
     * </ul>
     */
    public String getEstado() {
        BigDecimal actual = seguro(stockActual);
        BigDecimal loteado = seguro(stockLoteado);
        int cmp = actual.compareTo(loteado);
        if (cmp == 0) {
            return ESTADO_OK;
        }
        if (loteado.compareTo(actual) > 0) {
            return ESTADO_EXCESO_LOTEADO;
        }
        // Aquí actual > loteado.
        if (loteado.signum() == 0 && actual.signum() > 0) {
            return ESTADO_SIN_LOTES;
        }
        return ESTADO_FALTA_LOTEAR;
    }

    /** @return observación breve y legible acorde al estado. */
    public String getObservacion() {
        switch (getEstado()) {
            case ESTADO_OK:             return "Coincide con lo loteado.";
            case ESTADO_SIN_LOTES:      return "Hay stock sin ningún lote registrado.";
            case ESTADO_EXCESO_LOTEADO: return "Lo loteado supera al stock disponible.";
            case ESTADO_FALTA_LOTEAR:   return "Stock mayor que lo loteado; faltan unidades por lotear.";
            default:                    return "";
        }
    }

    private static BigDecimal seguro(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
