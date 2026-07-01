package com.gestorpyme.domain.enums;

/**
 * Tipo de exportacion (campo exportaciones_log.tipo). El nombre de cada constante
 * coincide con el valor TEXT guardado en la BD (CHECK del script SQL).
 */
public enum TipoExportacion {

    CLIENTES("Clientes"),
    PROSPECTOS("Prospectos"),
    PROVEEDORES("Proveedores"),
    ITEMS("Productos / Servicios"),
    INVENTARIO("Inventario"),
    REABASTECIMIENTO("Reabastecimiento"),
    KARDEX("Kardex"),
    LOTES("Lotes y vencimientos"),
    CRM("CRM"),
    VENTAS("Ventas"),
    VENTA_DETALLES("Detalle de ventas"),
    PAGOS("Pagos"),
    CARTERA("Cuentas por cobrar"),
    ABONOS("Abonos"),
    COMPRAS("Compras"),
    RECEPCIONES("Recepciones"),
    DETALLE_RECEPCIONES("Detalle de recepciones"),
    DASHBOARD_GERENCIAL("Dashboard gerencial"),
    OTRO("Otro");

    private final String etiqueta;

    TipoExportacion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    @Override
    public String toString() {
        return etiqueta;
    }
}
