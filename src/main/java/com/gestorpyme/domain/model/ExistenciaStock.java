package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Objeto de solo lectura que representa la existencia (stock) de un item en una
 * bodega. No corresponde 1:1 a una tabla: se arma con un JOIN entre
 * inventario_stock, items y bodegas para mostrarse en la vista de existencias.
 */
public class ExistenciaStock {

    private final int idItem;
    private final int idBodega;
    private final String codigoItem;
    private final String nombreItem;
    private final String nombreCategoria;
    private final String nombreSubtipo;
    private final String nombreBodega;
    private final BigDecimal cantidad;
    private final BigDecimal stockMinimo;
    /** Ubicacion interna dentro de la bodega (Paso A). Puede ser null. */
    private final String ubicacionInterna;

    public ExistenciaStock(int idItem, int idBodega, String codigoItem, String nombreItem,
                           String nombreCategoria, String nombreSubtipo, String nombreBodega,
                           BigDecimal cantidad, BigDecimal stockMinimo, String ubicacionInterna) {
        this.idItem = idItem;
        this.idBodega = idBodega;
        this.codigoItem = codigoItem;
        this.nombreItem = nombreItem;
        this.nombreCategoria = nombreCategoria;
        this.nombreSubtipo = nombreSubtipo;
        this.nombreBodega = nombreBodega;
        this.cantidad = cantidad;
        this.stockMinimo = stockMinimo;
        this.ubicacionInterna = ubicacionInterna;
    }

    public int getIdItem() {
        return idItem;
    }

    public int getIdBodega() {
        return idBodega;
    }

    public String getCodigoItem() {
        return codigoItem;
    }

    public String getNombreItem() {
        return nombreItem;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public String getNombreSubtipo() {
        return nombreSubtipo;
    }

    public String getNombreBodega() {
        return nombreBodega;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public BigDecimal getStockMinimo() {
        return stockMinimo;
    }

    /** Ubicacion interna dentro de la bodega (puede ser null o vacia). */
    public String getUbicacionInterna() {
        return ubicacionInterna;
    }

    /**
     * Estado de stock calculado (Paso B), no almacenado:
     * <ul>
     *   <li><b>SIN STOCK</b>: cantidad &le; 0.</li>
     *   <li><b>BAJO</b>: cantidad &gt; 0 y cantidad &lt; stock minimo.</li>
     *   <li><b>NORMAL</b>: cantidad &ge; stock minimo.</li>
     * </ul>
     * Decision documentada: si el stock minimo es 0, una cantidad &gt; 0 es NORMAL y una
     * cantidad 0 es SIN STOCK (coherente con el estado logistico de reabastecimiento).
     */
    public String getEstadoStock() {
        BigDecimal c = cantidad == null ? BigDecimal.ZERO : cantidad;
        BigDecimal m = stockMinimo == null ? BigDecimal.ZERO : stockMinimo;
        if (c.signum() <= 0) {
            return "SIN STOCK";
        }
        if (c.compareTo(m) < 0) {
            return "BAJO";
        }
        return "NORMAL";
    }
}
