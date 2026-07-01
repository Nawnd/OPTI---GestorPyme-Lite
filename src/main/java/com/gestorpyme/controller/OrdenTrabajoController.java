package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajo;
import com.gestorpyme.service.OrdenTrabajoService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de la vista de ordenes de trabajo (Paso U.2).
 *
 * Capa: controller. Recibe las acciones de la vista y delega en {@link OrdenTrabajoService}.
 * No contiene reglas de negocio, ni SQL, ni componentes Swing.
 */
public class OrdenTrabajoController {

    private final OrdenTrabajoService service;

    /** Constructor por defecto: crea su propio servicio. */
    public OrdenTrabajoController() {
        this(new OrdenTrabajoService());
    }

    /** Constructor que permite inyectar el servicio (util en pruebas). */
    public OrdenTrabajoController(OrdenTrabajoService service) {
        this.service = service;
    }

    /** Lista las ordenes de trabajo (sin detalle). */
    public List<OrdenTrabajo> listar() throws SQLException {
        return service.listar();
    }

    /** Busca una OT con su detalle de servicios y repuestos. */
    public Optional<OrdenTrabajo> buscarConDetalles(int idOrdenTrabajo) throws SQLException {
        return service.buscarConDetalles(idOrdenTrabajo);
    }

    /** Valida y guarda (inserta o actualiza) una OT con su detalle. */
    public void guardar(OrdenTrabajo ordenTrabajo) throws SQLException {
        service.guardar(ordenTrabajo);
    }

    /** Cambia el estado de una OT. */
    public void cambiarEstado(int idOrdenTrabajo, EstadoOrdenTrabajo estado) throws SQLException {
        service.cambiarEstado(idOrdenTrabajo, estado);
    }

    /**
     * Cierra y factura una OT delegando en el servicio (que a su vez delega en VentaService).
     *
     * @param idOrdenTrabajo   OT a cerrar.
     * @param contado          true = contado; false = credito.
     * @param medioContado     medio de pago si es contado (null en credito).
     * @param fechaVencimiento fecha ISO de vencimiento para credito (puede ser null).
     * @param observaciones    nota opcional del usuario.
     * @return el numero de venta generada (V-NNNNNN).
     */
    public String cerrarYFacturar(int idOrdenTrabajo, boolean contado,
                                  com.gestorpyme.domain.enums.MedioPago medioContado,
                                  String fechaVencimiento, String observaciones) throws SQLException {
        return service.cerrarYFacturar(idOrdenTrabajo, contado, medioContado, fechaVencimiento, observaciones);
    }
}
