package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Categoria;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.service.ItemService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de la vista de productos / servicios (patron MVC/MVP simplificado).
 * Recibe las acciones de la vista y delega en {@link ItemService}.
 * No contiene reglas de negocio, ni SQL, ni componentes Swing.
 * Capa: controller.
 */
public class ItemController {

    private final ItemService service;

    /** Constructor por defecto: crea su propio servicio. */
    public ItemController() {
        this(new ItemService());
    }

    /** Constructor que permite inyectar el servicio (util en pruebas). */
    public ItemController(ItemService service) {
        this.service = service;
    }

    /** Lista todos los items ordenados por nombre. */
    public List<Item> listar() throws SQLException {
        return service.listar();
    }

    /** Lista las categorias activas (para el selector del formulario). */
    public List<Categoria> listarCategorias() throws SQLException {
        return service.listarCategorias();
    }

    /** Proveedores activos para el selector de proveedor preferido (Paso F). */
    public List<com.gestorpyme.domain.model.Tercero> listarProveedores() throws SQLException {
        return service.listarProveedores();
    }

    /** Lista los subtipos activos de una categoria (para el combo dependiente del formulario). */
    public List<com.gestorpyme.domain.model.Subtipo> listarSubtipos(int idCategoria) throws SQLException {
        return service.listarSubtipos(idCategoria);
    }

    /** Valida y guarda (inserta o actualiza) un item. */
    /** Busca items activos por codigo o nombre (limite de resultados). */
    public List<Item> buscar(String texto, int limite) throws SQLException {
        return service.buscar(texto, limite);
    }

    /** Busca servicios / mano de obra (pestana Servicios de la OT, Paso U.2.1). */
    public List<Item> buscarServicios(String texto, int limite) throws SQLException {
        return service.buscarServicios(texto, limite);
    }

    /** Busca productos inventariables (pestana Repuestos de la OT, Paso U.2.1). */
    public List<Item> buscarInventariables(String texto, int limite) throws SQLException {
        return service.buscarInventariables(texto, limite);
    }

    public void guardar(Item item) throws SQLException {
        service.guardar(item);
    }

    /** Cambia el estado (activo/inactivo) de un item. */
    public void cambiarEstado(int idItem, EstadoRegistro estado) throws SQLException {
        service.cambiarEstado(idItem, estado);
    }
}
