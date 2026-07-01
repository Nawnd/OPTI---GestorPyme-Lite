package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.repository.BodegaRepository;

import java.sql.SQLException;
import java.util.List;

/**
 * Reglas de negocio de las bodegas. Valida los datos y coordina el repositorio.
 * No contiene SQL ni codigo de interfaz.
 * Capa: service.
 */
public class BodegaService {

    private final BodegaRepository repository;

    public BodegaService() {
        this(new BodegaRepository());
    }

    public BodegaService(BodegaRepository repository) {
        this.repository = repository;
    }

    /** Lista todas las bodegas ordenadas por nombre. */
    public List<Bodega> listar() throws SQLException {
        return repository.listar();
    }

    /**
     * Valida y guarda una bodega. Si su id es 0 la inserta; en caso contrario la actualiza.
     *
     * @throws ValidacionException si algun dato no cumple las reglas.
     * @throws SQLException si ocurre un error de base de datos.
     */
    /** Busca bodegas activas por nombre o ubicacion (limite de resultados). */
    public List<Bodega> buscar(String texto, int limite) throws SQLException {
        return repository.buscar(texto, limite);
    }

    public void guardar(Bodega bodega) throws SQLException {
        if (esVacio(bodega.getNombre())) {
            throw new ValidacionException("El nombre de la bodega es obligatorio.");
        }
        if (repository.existeNombre(bodega.getNombre().trim(), bodega.getIdBodega())) {
            throw new ValidacionException("Ya existe una bodega con el nombre '"
                    + bodega.getNombre().trim() + "'.");
        }
        if (bodega.getEstado() == null) {
            bodega.setEstado(EstadoRegistro.ACTIVO);
        }
        if (bodega.getIdBodega() <= 0) {
            int nuevoId = repository.insertar(bodega);
            bodega.setIdBodega(nuevoId);
        } else {
            repository.actualizar(bodega);
        }
    }

    /** Cambia el estado (baja/alta logica) de una bodega. */
    public void cambiarEstado(int idBodega, EstadoRegistro estado) throws SQLException {
        repository.cambiarEstado(idBodega, estado);
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
