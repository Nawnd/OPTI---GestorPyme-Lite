package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoSeguimiento;
import com.gestorpyme.domain.model.Seguimiento;
import com.gestorpyme.service.SeguimientoService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del modulo CRM basico.
 * Coordina la vista con el servicio; sin reglas de negocio ni SQL.
 */
public class SeguimientoController {

    private final SeguimientoService seguimientoService;

    public SeguimientoController() {
        this(new SeguimientoService());
    }

    public SeguimientoController(SeguimientoService seguimientoService) {
        this.seguimientoService = seguimientoService;
    }

    /** Lista todos los seguimientos. */
    public List<Seguimiento> listar() throws SQLException {
        return seguimientoService.listar();
    }

    /** Lista los seguimientos de un tercero. */
    public List<Seguimiento> listarPorTercero(int idTercero) throws SQLException {
        return seguimientoService.listarPorTercero(idTercero);
    }

    /** Valida y registra un seguimiento. */
    public void registrar(Seguimiento s) throws SQLException {
        seguimientoService.registrar(s);
    }

    /** Cambia el estado de un seguimiento. */
    public void cambiarEstado(int idSeguimiento, EstadoSeguimiento estado) throws SQLException {
        seguimientoService.cambiarEstado(idSeguimiento, estado);
    }
}
