package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.TipoDrillDown;

/**
 * Segmento (barra) de una gráfica clicable del Dashboard. Capa: domain.model (POJO de presentación/lectura,
 * sin dependencia de Swing). Lleva su propio destino de desglose: al hacer clic en él, la vista usa
 * {@link #getTipo()} + {@link #getReferencia()} para pedir el detalle.
 *
 * El color se expresa como una pista semántica ({@link #getColorHint()}: "PRIMARIO", "EXITO", "ADVERTENCIA",
 * "PELIGRO", "NEUTRO"); la vista la traduce a un color concreto de la paleta, manteniendo el dominio libre de
 * dependencias de UI. Paso L.
 */
public class DashboardChartSegment {

    private String etiqueta;
    private double valor;
    private TipoDrillDown tipo;
    private String referencia;
    private String colorHint;

    public DashboardChartSegment() {
    }

    public DashboardChartSegment(String etiqueta, double valor, TipoDrillDown tipo, String referencia, String colorHint) {
        this.etiqueta = etiqueta;
        this.valor = valor;
        this.tipo = tipo;
        this.referencia = referencia;
        this.colorHint = colorHint;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public double getValor() {
        return valor;
    }

    public void setValor(double valor) {
        this.valor = valor;
    }

    public TipoDrillDown getTipo() {
        return tipo;
    }

    public void setTipo(TipoDrillDown tipo) {
        this.tipo = tipo;
    }

    public String getReferencia() {
        return referencia;
    }

    public void setReferencia(String referencia) {
        this.referencia = referencia;
    }

    public String getColorHint() {
        return colorHint;
    }

    public void setColorHint(String colorHint) {
        this.colorHint = colorHint;
    }
}
