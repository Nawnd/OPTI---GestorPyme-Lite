package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.repository.VehiculoRepository;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Reglas de negocio de los vehiculos (Paso U.1).
 *
 * Capa: service. Valida y normaliza los datos y coordina el repositorio. No contiene SQL ni
 * codigo de interfaz. No toca ventas, inventario ni FEFO. Errores de negocio via
 * {@link ValidacionException}.
 */
public class VehiculoService {

    /** Limite inferior razonable para el año del vehiculo. */
    private static final int ANIO_MINIMO = 1900;

    private final VehiculoRepository repository;

    /** Constructor por defecto: crea su propio repositorio. */
    public VehiculoService() {
        this(new VehiculoRepository());
    }

    /** Constructor que permite inyectar el repositorio (util en pruebas). */
    public VehiculoService(VehiculoRepository repository) {
        this.repository = repository;
    }

    /** Lista todos los vehiculos (con nombre de cliente). */
    public List<Vehiculo> listar() throws SQLException {
        return repository.listar();
    }

    /** Lista los vehiculos de un cliente. */
    public List<Vehiculo> listarPorCliente(int idTercero) throws SQLException {
        return repository.listarPorCliente(idTercero);
    }

    /**
     * Valida y guarda un vehiculo. Normaliza la placa (mayusculas, sin espacios extremos),
     * verifica que no este duplicada y asigna estado ACTIVO por defecto. Inserta si el id es 0;
     * en caso contrario actualiza.
     *
     * @throws ValidacionException si algun dato es invalido o la placa ya existe.
     */
    public void guardar(Vehiculo vehiculo) throws SQLException {
        // Normaliza antes de validar para que la verificacion de obligatoriedad y duplicado sea correcta.
        vehiculo.setPlaca(normalizarPlaca(vehiculo.getPlaca()));
        validar(vehiculo);
        if (vehiculo.getEstado() == null) {
            vehiculo.setEstado(EstadoRegistro.ACTIVO);
        }
        int idActual = vehiculo.getIdVehiculo();
        if (repository.existePlaca(vehiculo.getPlaca(), idActual)) {
            throw new ValidacionException("Ya existe un vehiculo con la placa " + vehiculo.getPlaca() + ".");
        }
        if (idActual <= 0) {
            int nuevoId = repository.insertar(vehiculo);
            vehiculo.setIdVehiculo(nuevoId);
        } else {
            repository.actualizar(vehiculo);
        }
    }

    /** Cambia el estado (alta/baja logica) de un vehiculo. */
    public void cambiarEstado(int idVehiculo, EstadoRegistro estado) throws SQLException {
        repository.cambiarEstado(idVehiculo, estado);
    }

    /** Aplica las reglas minimas de validacion antes de persistir. */
    private void validar(Vehiculo v) {
        if (v.getIdTercero() <= 0) {
            throw new ValidacionException("Debe seleccionar el cliente del vehiculo.");
        }
        if (esVacio(v.getPlaca())) {
            throw new ValidacionException("La placa es obligatoria.");
        }
        if (v.getKilometraje() < 0) {
            throw new ValidacionException("El kilometraje no puede ser negativo.");
        }
        if (v.getAnio() != null) {
            int maximo = LocalDate.now().getYear() + 1; // permite modelos del año siguiente
            if (v.getAnio() < ANIO_MINIMO || v.getAnio() > maximo) {
                throw new ValidacionException("El año del vehiculo no es valido (entre "
                        + ANIO_MINIMO + " y " + maximo + ").");
            }
        }
    }

    /** Normaliza la placa: recorta espacios extremos y la lleva a mayusculas. */
    private String normalizarPlaca(String placa) {
        if (placa == null) {
            return null;
        }
        return placa.trim().toUpperCase();
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
