package com.gestorpyme.domain.model;

/**
 * Fila genérica del panel de desglose (drill-down) del Dashboard. Capa: domain.model (POJO de
 * presentación/lectura, sin dependencia de Swing). Representa de forma uniforme una venta, un ítem, una
 * cuenta o un lote dentro del panel de detalle, con un texto principal, uno secundario, un valor y un estado.
 *
 * El color del estado se expresa como pista semántica ({@link #getColorHint()}); la vista la traduce. Paso L.
 */
public class DashboardDrillDownItem {

    private String principal;
    private String secundario;
    private String valor;
    private String estado;
    private String colorHint;

    public DashboardDrillDownItem() {
    }

    public DashboardDrillDownItem(String principal, String secundario, String valor, String estado, String colorHint) {
        this.principal = principal;
        this.secundario = secundario;
        this.valor = valor;
        this.estado = estado;
        this.colorHint = colorHint;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getSecundario() {
        return secundario;
    }

    public void setSecundario(String secundario) {
        this.secundario = secundario;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getColorHint() {
        return colorHint;
    }

    public void setColorHint(String colorHint) {
        this.colorHint = colorHint;
    }
}
