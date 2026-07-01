package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.service.BodegaService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de la vista de bodegas (patron MVC/MVP simplificado).
 * Recibe las acciones de la vista y delega en {@link BodegaService}.
 * No contiene reglas de negocio, ni SQL, ni componentes Swing.
 * Capa: controller.
 */
public class BodegaController {

    private final BodegaService service;

    public BodegaController() {
        this(new BodegaService());
    }

    public BodegaController(BodegaService service) {
        this.service = service;
    }

    /** Lista todas las bodegas ordenadas por nombre. */
    public List<Bodega> listar() throws SQLException {
        return service.listar();
    }

    /** Valida y guarda (inserta o actualiza) una bodega. */
    /** Busca bodegas activas por nombre o ubicacion (limite de resultados). */
    public List<Bodega> buscar(String texto, int limite) throws SQLException {
        return service.buscar(texto, limite);
    }

    public void guardar(Bodega bodega) throws SQLException {
        service.guardar(bodega);
    }

    /** Cambia el estado (activo/inactivo) de una bodega. */
    public void cambiarEstado(int idBodega, EstadoRegistro estado) throws SQLException {
        service.cambiarEstado(idBodega, estado);
    }
}
