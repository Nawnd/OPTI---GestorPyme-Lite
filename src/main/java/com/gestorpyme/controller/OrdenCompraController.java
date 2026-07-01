package com.gestorpyme.controller;

import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.service.OrdenCompraService;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Controlador de ordenes de compra. Coordina la vista con el servicio; no contiene
 * reglas de negocio ni SQL. Capa: controller.
 */
public class OrdenCompraController {

    private final OrdenCompraService service;

    public OrdenCompraController() {
        this(new OrdenCompraService());
    }

    public OrdenCompraController(OrdenCompraService service) {
        this.service = service;
    }

    /** Crea una orden de compra (validada) y devuelve su numero (OC-000001). */
    public String crear(OrdenCompra orden) throws SQLException {
        return service.crear(orden);
    }

    /** Lista las ordenes de la mas reciente a la mas antigua. */
    public List<OrdenCompra> listar() throws SQLException {
        return service.listar();
    }

    /** Carga una orden con sus detalles. */
    public Optional<OrdenCompra> buscarConDetalles(int idOrden) throws SQLException {
        return service.buscarConDetalles(idOrden);
    }

    /** Cancela una orden (no reversa lo recibido). */
    public void cancelar(int idOrden) throws SQLException {
        service.cancelar(idOrden);
    }
}
