package com.gestorpyme.domain.model;

import java.math.BigDecimal;

/**
 * Lote candidato a consumo en una venta (Paso J), ya filtrado y ordenado por FEFO
 * (First Expired, First Out). Capa: domain.model (POJO de solo lectura).
 *
 * Responsabilidad: transportar, para un item en una bodega, los lotes ACTIVOS no vencidos con saldo
 * disponible &gt; 0, en el orden en que deben consumirse (vencimiento mas cercano primero; los sin
 * fecha al final). El servicio/repositorio de ventas decide cuanto toma de cada uno.
 */
public class LoteDisponible {

    private final int idLote;
    private final String numeroLote;
    private final String fechaVencimiento; // 'YYYY-MM-DD' o null
    private final BigDecimal cantidadDisponible;

    public LoteDisponible(int idLote, String numeroLote, String fechaVencimiento, BigDecimal cantidadDisponible) {
        this.idLote = idLote;
        this.numeroLote = numeroLote;
        this.fechaVencimiento = fechaVencimiento;
        this.cantidadDisponible = cantidadDisponible == null ? BigDecimal.ZERO : cantidadDisponible;
    }

    public int getIdLote() {
        return idLote;
    }

    public String getNumeroLote() {
        return numeroLote;
    }

    public String getFechaVencimiento() {
        return fechaVencimiento;
    }

    public BigDecimal getCantidadDisponible() {
        return cantidadDisponible;
    }
}
