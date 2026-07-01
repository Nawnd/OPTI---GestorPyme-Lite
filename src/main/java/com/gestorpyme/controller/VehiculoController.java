package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.service.VehiculoService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de la vista de vehiculos (Paso U.1).
 *
 * Capa: controller. Recibe las acciones de la vista y delega en {@link VehiculoService}.
 * No contiene reglas de negocio, ni SQL, ni componentes Swing.
 */
public class VehiculoController {

    private final VehiculoService service;

    /** Constructor por defecto: crea su propio servicio. */
    public VehiculoController() {
        this(new VehiculoService());
    }

    /** Constructor que permite inyectar el servicio (util en pruebas). */
    public VehiculoController(VehiculoService service) {
        this.service = service;
    }

    /** Lista todos los vehiculos (con nombre de cliente). */
    public List<Vehiculo> listar() throws SQLException {
        return service.listar();
    }

    /** Lista los vehiculos de un cliente. */
    public List<Vehiculo> listarPorCliente(int idTercero) throws SQLException {
        return service.listarPorCliente(idTercero);
    }

    /** Valida y guarda (inserta o actualiza) un vehiculo. */
    public void guardar(Vehiculo vehiculo) throws SQLException {
        service.guardar(vehiculo);
    }

    /** Cambia el estado (activo/inactivo) de un vehiculo. */
    public void cambiarEstado(int idVehiculo, EstadoRegistro estado) throws SQLException {
        service.cambiarEstado(idVehiculo, estado);
    }
}
