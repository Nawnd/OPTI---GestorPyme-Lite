package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.service.TerceroService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de la vista de clientes / prospectos (patron MVC/MVP simplificado).
 * Recibe las acciones de la vista y delega en {@link TerceroService}.
 * No contiene reglas de negocio, ni SQL, ni componentes Swing.
 * Capa: controller.
 */
public class TerceroController {

    private final TerceroService service;

    /** Constructor por defecto: crea su propio servicio. */
    public TerceroController() {
        this(new TerceroService());
    }

    /** Constructor que permite inyectar el servicio (util en pruebas). */
    public TerceroController(TerceroService service) {
        this.service = service;
    }

    /** Lista clientes y prospectos ordenados por nombre. */
    public List<Tercero> listarClientesYProspectos() throws SQLException {
        return service.listarClientesYProspectos();
    }

    /** Valida y guarda (inserta o actualiza) un tercero. */
    /** Busca clientes y prospectos activos (limite de resultados). */
    public List<Tercero> buscarClientesYProspectos(String texto, int limite) throws SQLException {
        return service.buscarClientesYProspectos(texto, limite);
    }

    /** Busca proveedores activos (para ordenes de compra). */
    /** Lista todos los proveedores. */
    public List<Tercero> listarProveedores() throws SQLException {
        return service.listarProveedores();
    }

    public List<Tercero> buscarProveedores(String texto, int limite) throws SQLException {
        return service.buscarProveedores(texto, limite);
    }

    public void guardar(Tercero tercero) throws SQLException {
        service.guardar(tercero);
    }

    /** Cambia el estado (activo/inactivo) de un tercero. */
    public void cambiarEstado(int idTercero, EstadoRegistro estado) throws SQLException {
        service.cambiarEstado(idTercero, estado);
    }
}
