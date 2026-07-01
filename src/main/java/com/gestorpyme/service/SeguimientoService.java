package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoSeguimiento;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Seguimiento;
import com.gestorpyme.repository.SeguimientoRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Servicio del modulo CRM basico (seguimientos).
 * Aplica las reglas de negocio antes de delegar en el repositorio.
 */
public class SeguimientoService {

    private final SeguimientoRepository seguimientoRepository;

    public SeguimientoService() {
        this(new SeguimientoRepository());
    }

    public SeguimientoService(SeguimientoRepository seguimientoRepository) {
        this.seguimientoRepository = seguimientoRepository;
    }

    /** Lista todos los seguimientos. */
    public List<Seguimiento> listar() throws SQLException {
        return seguimientoRepository.listar();
    }

    /** Lista los seguimientos de un tercero. */
    public List<Seguimiento> listarPorTercero(int idTercero) throws SQLException {
        return seguimientoRepository.listarPorTercero(idTercero);
    }

    /**
     * Valida y registra un seguimiento.
     *
     * Reglas:
     * - El tercero es obligatorio.
     * - El tipo es obligatorio.
     * - La descripcion es obligatoria.
     * - Si el estado es nulo, se asume REGISTRADO.
     *
     * @throws ValidacionException si algun dato no cumple las reglas.
     */
    public void registrar(Seguimiento s) throws SQLException {
        if (s == null) {
            throw new ValidacionException("No hay datos de seguimiento para registrar.");
        }
        if (s.getIdTercero() <= 0) {
            throw new ValidacionException("Debe seleccionar un cliente o prospecto.");
        }
        if (s.getTipo() == null) {
            throw new ValidacionException("El tipo de seguimiento es obligatorio.");
        }
        if (esVacio(s.getDescripcion())) {
            throw new ValidacionException("La descripcion es obligatoria.");
        }
        if (s.getEstado() == null) {
            s.setEstado(EstadoSeguimiento.REGISTRADO);
        }
        seguimientoRepository.insertar(s);
    }

    /** Cambia el estado de un seguimiento. */
    public void cambiarEstado(int idSeguimiento, EstadoSeguimiento estado) throws SQLException {
        if (estado == null) {
            throw new ValidacionException("El estado de seguimiento es obligatorio.");
        }
        seguimientoRepository.cambiarEstado(idSeguimiento, estado);
    }

    private boolean esVacio(String v) {
        return v == null || v.trim().isEmpty();
    }
}
