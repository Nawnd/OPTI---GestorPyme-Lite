package com.gestorpyme.view.components;

import java.util.List;
import java.util.function.Function;

/**
 * Configuracion reutilizable para la busqueda inteligente de una entidad.
 *
 * Agrupa todo lo que el componente necesita para buscar y mostrar resultados de un
 * tipo {@code T}, sin acoplarse a una entidad concreta: titulo, texto guia, columnas,
 * la funcion de busqueda (que internamente llama al controlador -> servicio ->
 * repositorio, nunca SQL en la vista), como convertir cada resultado en fila de tabla
 * y como mostrar la etiqueta del seleccionado.
 *
 * @param <T> tipo de la entidad buscada (Item, Tercero, Bodega, Venta, ...).
 */
public class EntitySearchSpec<T> {

    /** Funcion de busqueda que puede lanzar excepciones (p. ej. SQLException). */
    @FunctionalInterface
    public interface Buscador<T> {
        List<T> buscar(String texto) throws Exception;
    }

    private final String titulo;
    private final String textoGuia;
    private final String[] columnas;
    private final Buscador<T> buscador;
    private final Function<T, Object[]> aFila;
    private final Function<T, String> aEtiqueta;

    /**
     * @param titulo    titulo del dialogo de busqueda.
     * @param textoGuia mensaje guia (p. ej. "Buscar producto por codigo o nombre...").
     * @param columnas  encabezados de la tabla de resultados.
     * @param buscador  funcion texto -> resultados (llama a la capa de controlador).
     * @param aFila     convierte un resultado en los valores de su fila.
     * @param aEtiqueta convierte el seleccionado en una etiqueta amigable.
     */
    public EntitySearchSpec(String titulo, String textoGuia, String[] columnas,
                            Buscador<T> buscador, Function<T, Object[]> aFila,
                            Function<T, String> aEtiqueta) {
        this.titulo = titulo;
        this.textoGuia = textoGuia;
        this.columnas = columnas;
        this.buscador = buscador;
        this.aFila = aFila;
        this.aEtiqueta = aEtiqueta;
    }

    public String getTitulo() { return titulo; }
    public String getTextoGuia() { return textoGuia; }
    public String[] getColumnas() { return columnas; }
    public Buscador<T> getBuscador() { return buscador; }
    public Object[] aFila(T entidad) { return aFila.apply(entidad); }
    public String aEtiqueta(T entidad) { return aEtiqueta.apply(entidad); }
}
