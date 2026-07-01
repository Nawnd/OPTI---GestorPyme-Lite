package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.repository.TerceroRepository;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * Reglas de negocio de los terceros (clientes / prospectos).
 * Valida los datos y coordina el repositorio. No contiene SQL ni codigo de interfaz.
 * Capa: service.
 */
public class TerceroService {

    private final TerceroRepository repository;

    /** Constructor por defecto: crea su propio repositorio. */
    public TerceroService() {
        this(new TerceroRepository());
    }

    /** Constructor que permite inyectar el repositorio (util en pruebas). */
    public TerceroService(TerceroRepository repository) {
        this.repository = repository;
    }

    /** Lista clientes y prospectos ordenados por nombre. */
    public List<Tercero> listarClientesYProspectos() throws SQLException {
        return repository.listarPorTipos(Arrays.asList(TipoTercero.CLIENTE, TipoTercero.PROSPECTO));
    }

    /**
     * Valida y guarda un tercero. Si su id es 0 lo inserta (y le asigna el id generado);
     * en caso contrario lo actualiza.
     *
     * @param tercero datos a guardar.
     * @throws ValidacionException si algun dato no cumple las reglas (mensaje para el usuario).
     * @throws SQLException si ocurre un error de base de datos.
     */
    /** Busca clientes y prospectos activos (limite de resultados). */
    public List<Tercero> buscarClientesYProspectos(String texto, int limite) throws SQLException {
        return repository.buscarClientesYProspectos(texto, limite);
    }

    /** Busca proveedores activos (para ordenes de compra). */
    /** Lista todos los proveedores (para el modulo Proveedores). */
    public List<Tercero> listarProveedores() throws SQLException {
        return repository.listarProveedores();
    }

    public List<Tercero> buscarProveedores(String texto, int limite) throws SQLException {
        return repository.buscarProveedores(texto, limite);
    }

    public void guardar(Tercero tercero) throws SQLException {
        validar(tercero);
        if (tercero.getEstado() == null) {
            tercero.setEstado(EstadoRegistro.ACTIVO); // por defecto, un registro nuevo nace activo
        }
        if (tercero.getIdTercero() <= 0) {
            int nuevoId = repository.insertar(tercero);
            tercero.setIdTercero(nuevoId);
        } else {
            repository.actualizar(tercero);
        }
    }

    /** Cambia el estado (baja/alta logica) de un tercero. */
    public void cambiarEstado(int idTercero, EstadoRegistro estado) throws SQLException {
        repository.cambiarEstado(idTercero, estado);
    }

    /** Aplica las reglas minimas de validacion antes de persistir. */
    private void validar(Tercero tercero) {
        if (tercero.getTipoTercero() == null) {
            throw new ValidacionException("Debe indicar el tipo (Cliente o Prospecto).");
        }
        if (esVacio(tercero.getNombre())) {
            throw new ValidacionException("El nombre es obligatorio.");
        }
        if (!esVacio(tercero.getCorreo()) && !tercero.getCorreo().contains("@")) {
            throw new ValidacionException("El correo no tiene un formato valido.");
        }
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
