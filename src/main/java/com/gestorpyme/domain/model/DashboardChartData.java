package com.gestorpyme.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Datos de una gráfica del Dashboard: un título y la lista de sus segmentos (barras). Capa: domain.model
 * (POJO de presentación/lectura, sin dependencia de Swing). Alimenta de forma genérica al componente de
 * gráfica clicable. Paso L.
 */
public class DashboardChartData {

    private String titulo;
    private List<DashboardChartSegment> segmentos = new ArrayList<>();

    public DashboardChartData() {
    }

    public DashboardChartData(String titulo, List<DashboardChartSegment> segmentos) {
        this.titulo = titulo;
        this.segmentos = segmentos != null ? segmentos : new ArrayList<>();
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public List<DashboardChartSegment> getSegmentos() {
        return segmentos;
    }

    public void setSegmentos(List<DashboardChartSegment> segmentos) {
        this.segmentos = segmentos != null ? segmentos : new ArrayList<>();
    }

    /** @return true si no hay segmentos que graficar (estado vacío seguro). */
    public boolean estaVacia() {
        return segmentos == null || segmentos.isEmpty();
    }
}
