package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Subtipo;
import com.gestorpyme.repository.SubtipoRepository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de los subtipos de items (clasificacion fina por categoria).
 * No contiene SQL ni codigo de interfaz. Capa: service.
 */
public class SubtipoService {

    private final SubtipoRepository subtipoRepository;

    public SubtipoService() {
        this(new SubtipoRepository());
    }

    public SubtipoService(SubtipoRepository subtipoRepository) {
        this.subtipoRepository = subtipoRepository;
    }

    /** Lista los subtipos activos de una categoria (para el combo dependiente). */
    public List<Subtipo> listarPorCategoria(int idCategoria) throws SQLException {
        return subtipoRepository.listarPorCategoria(idCategoria);
    }

    /** Lista todos los subtipos activos. */
    public List<Subtipo> listar() throws SQLException {
        return subtipoRepository.listar();
    }

    /** Busca un subtipo por id (vacio si no existe). */
    public Optional<Subtipo> buscarPorId(int idSubtipo) throws SQLException {
        return subtipoRepository.buscarPorId(idSubtipo);
    }

    /**
     * Valida y crea un subtipo asociado a una categoria.
     *
     * @throws ValidacionException si falta la categoria o el nombre.
     * @throws SQLException        ante errores de base de datos.
     */
    public int crear(Subtipo subtipo) throws SQLException {
        if (subtipo == null) {
            throw new ValidacionException("El subtipo no puede ser nulo.");
        }
        if (subtipo.getIdCategoria() <= 0) {
            throw new ValidacionException("El subtipo debe pertenecer a una categoria.");
        }
        if (subtipo.getNombre() == null || subtipo.getNombre().trim().isEmpty()) {
            throw new ValidacionException("El nombre del subtipo es obligatorio.");
        }
        subtipo.setNombre(subtipo.getNombre().trim());
        return subtipoRepository.insertar(subtipo);
    }
}
