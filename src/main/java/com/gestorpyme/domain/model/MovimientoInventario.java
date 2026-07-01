package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.TipoMovimiento;

import java.math.BigDecimal;

/**
 * Fila de consulta del Kardex (tabla 'inventario_movimientos' con JOIN a item y bodega).
 *
 * Es un objeto de SOLO LECTURA orientado a mostrar movimientos en pantalla:
 * incluye los nombres de item y bodega ya resueltos para no consultar de nuevo.
 * POJO: no depende de Swing ni de JDBC.
 */
public class MovimientoInventario {

    private int idMovimiento;
    private String fecha;
    private String nombreItem;
    private String nombreBodega;
    private TipoMovimiento tipo;
    private BigDecimal cantidad;
    private String motivo;
    /** Usuario responsable del movimiento (solo lectura, resuelto por JOIN). Paso D / RF-06. */
    private String nombreUsuario;
    /** Numero de lote asociado a la salida por venta (solo lectura, JOIN). Paso J; null si no aplica. */
    private String numeroLote;

    public MovimientoInventario() {
    }

    public int getIdMovimiento() {
        return idMovimiento;
    }

    public void setIdMovimiento(int idMovimiento) {
        this.idMovimiento = idMovimiento;
    }

    public String getFecha() {
        return fecha;
    }

    public void setFecha(String fecha) {
        this.fecha = fecha;
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

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public void setTipo(TipoMovimiento tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = cantidad;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    /** Usuario responsable del movimiento (puede ser "Sistema" si no hay usuario asociado). */
    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getNumeroLote() {
        return numeroLote;
    }

    public void setNumeroLote(String numeroLote) {
        this.numeroLote = numeroLote;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }
}
