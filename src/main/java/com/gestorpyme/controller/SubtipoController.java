package com.gestorpyme.controller;

import com.gestorpyme.domain.model.Subtipo;
import com.gestorpyme.service.SubtipoService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del modulo de subtipos: coordina la vista con {@link SubtipoService}.
 * No contiene reglas de negocio ni SQL. Capa: controller.
 */
public class SubtipoController {

    private final SubtipoService subtipoService;

    public SubtipoController() {
        this(new SubtipoService());
    }

    public SubtipoController(SubtipoService subtipoService) {
        this.subtipoService = subtipoService;
    }

    /** Lista los subtipos activos de una categoria (para el combo dependiente). */
    public List<Subtipo> listarPorCategoria(int idCategoria) throws SQLException {
        return subtipoService.listarPorCategoria(idCategoria);
    }

    /** Lista todos los subtipos activos. */
    public List<Subtipo> listar() throws SQLException {
        return subtipoService.listar();
    }

    /** Crea un subtipo asociado a una categoria. */
    public int crear(Subtipo subtipo) throws SQLException {
        return subtipoService.crear(subtipo);
    }
}
