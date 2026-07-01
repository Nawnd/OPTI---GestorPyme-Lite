package com.gestorpyme.view.components;

import com.gestorpyme.domain.enums.TipoDrillDown;

/**
 * Contrato de selección para gráficas clicables del Dashboard (Paso L). Capa: view.components.
 * Una gráfica notifica, al hacer clic en un segmento, qué desglose se solicita, sin conocer cómo se
 * muestra el detalle: la vista contenedora decide qué consulta ejecutar y dónde presentarla.
 */
public interface MetricSelectionListener {

    /**
     * @param tipo       tipo de desglose solicitado.
     * @param referencia identificador asociado al segmento (id de bodega, estado, fecha ISO, id de ítem…).
     * @param etiqueta   etiqueta legible del segmento (para títulos del panel de detalle).
     */
    void onMetricSelected(TipoDrillDown tipo, String referencia, String etiqueta);
}
