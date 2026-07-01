package com.gestorpyme.domain.enums;

/**
 * Tipo de desglose (drill-down) que una gráfica o indicador del Dashboard puede solicitar al hacer clic.
 * Capa: domain.enums. Permite que un componente de gráfica emita un evento de selección desacoplado de la
 * vista de detalle: la vista contenedora decide, según este tipo, qué consulta de desglose ejecutar.
 *
 * Paso L. No representa datos persistidos; es un valor de coordinación entre vista y controlador.
 */
public enum TipoDrillDown {
    /** Desglose de ventas (por día/rango). */
    VENTAS,
    /** Desglose de pagos. */
    PAGO,
    /** Desglose de inventario (genérico). */
    INVENTARIO,
    /** Desglose por bodega (ítems y stock de una bodega). */
    BODEGA,
    /** Desglose por ítem (p. ej. ventas de un producto). */
    ITEM,
    /** Desglose de lotes. */
    LOTE,
    /** Desglose de compras (órdenes). */
    COMPRA,
    /** Desglose de cartera (cuentas por cobrar por estado). */
    CARTERA
}
