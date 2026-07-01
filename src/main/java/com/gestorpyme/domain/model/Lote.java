package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoLote;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Modelo de dominio de un lote (tabla 'lotes').
 *
 * Los lotes son INFORMATIVOS para control de vencimientos (DEC-019): el stock
 * autoritativo vive en inventario_stock. Aqui se registra el numero de lote,
 * la cantidad inicial, las fechas de ingreso y vencimiento y el estado.
 *
 * POJO: no depende de Swing ni de JDBC. Los campos nombreItem/nombreBodega son
 * de SOLO LECTURA, resueltos por JOIN para mostrarlos en pantalla.
 */
public class Lote {

    private int idLote;
    private int idItem;
    private Integer idBodega;       // puede ser null (lote no asignado a bodega)
    private String numeroLote;
    private BigDecimal cantidadInicial;
    private BigDecimal cantidadDisponible; // Paso J: saldo del lote (IFNULL cantidad_inicial al leer)
    private String fechaIngreso;    // 'YYYY-MM-DD'
    private String fechaVencimiento; // 'YYYY-MM-DD' o null
    private EstadoLote estado;

    // Campos de presentacion (no se guardan; vienen de JOIN).
    private String nombreItem;
    private String nombreBodega;
    private String codigoItem;   // solo lectura (JOIN), Paso H

    public Lote() {
    }

    public int getIdLote() {
        return idLote;
    }

    public void setIdLote(int idLote) {
        this.idLote = idLote;
    }

    public int getIdItem() {
        return idItem;
    }

    public void setIdItem(int idItem) {
        this.idItem = idItem;
    }

    public Integer getIdBodega() {
        return idBodega;
    }

    public void setIdBodega(Integer idBodega) {
        this.idBodega = idBodega;
    }

    public String getNumeroLote() {
        return numeroLote;
    }

    public void setNumeroLote(String numeroLote) {
        this.numeroLote = numeroLote;
    }

    public BigDecimal getCantidadInicial() {
        return cantidadInicial;
    }

    public void setCantidadInicial(BigDecimal cantidadInicial) {
        this.cantidadInicial = cantidadInicial;
    }

    /** Saldo disponible del lote (Paso J). Al leer, si la columna es null se devuelve la cantidad inicial. */
    public BigDecimal getCantidadDisponible() {
        return cantidadDisponible;
    }

    public void setCantidadDisponible(BigDecimal cantidadDisponible) {
        this.cantidadDisponible = cantidadDisponible;
    }

    public String getFechaIngreso() {
        return fechaIngreso;
    }

    public void setFechaIngreso(String fechaIngreso) {
        this.fechaIngreso = fechaIngreso;
    }

    public String getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(String fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public EstadoLote getEstado() {
        return estado;
    }

    public void setEstado(EstadoLote estado) {
        this.estado = estado;
    }

    public String getNombreItem() {
        return nombreItem;
    }

    public void setNombreItem(String nombreItem) {
        this.nombreItem = nombreItem;
    }

    public String getNombreBodega() {
        return nombreBodega;
    }

    public void setNombreBodega(String nombreBodega) {
        this.nombreBodega = nombreBodega;
    }

    /** Codigo del item (solo lectura, via JOIN). Paso H. */
    public String getCodigoItem() {
        return codigoItem;
    }

    public void setCodigoItem(String codigoItem) {
        this.codigoItem = codigoItem;
    }

    /**
     * Dias para vencer desde hoy hasta la fecha de vencimiento (Paso H).
     * Negativo si ya venció; null si no tiene fecha de vencimiento (o es ilegible).
     */
    public Long diasParaVencer() {
        LocalDate venc = parseVencimiento();
        return venc == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), venc);
    }

    /**
     * Estado de vencimiento calculado (Paso H), sin almacenarse:
     * <ul>
     *   <li>SIN FECHA: sin fecha de vencimiento.</li>
     *   <li>VENCIDO: fecha de vencimiento anterior a hoy.</li>
     *   <li>POR VENCER: vence entre hoy y hoy + 30 dias (inclusive).</li>
     *   <li>VIGENTE: vence despues de hoy + 30 dias.</li>
     * </ul>
     */
    public String estadoVencimiento() {
        LocalDate venc = parseVencimiento();
        if (venc == null) {
            return "SIN FECHA";
        }
        LocalDate hoy = LocalDate.now();
        if (venc.isBefore(hoy)) {
            return "VENCIDO";
        }
        if (!venc.isAfter(hoy.plusDays(30))) {
            return "POR VENCER";
        }
        return "VIGENTE";
    }

    /** Convierte fechaVencimiento ('YYYY-MM-DD') a LocalDate; null si vacia o ilegible. */
    private LocalDate parseVencimiento() {
        if (fechaVencimiento == null || fechaVencimiento.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(fechaVencimiento.trim());
        } catch (Exception e) {
            return null; // fecha en formato inesperado: se trata como "sin fecha"
        }
    }
}
