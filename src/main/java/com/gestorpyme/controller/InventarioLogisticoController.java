package com.gestorpyme.controller;

import com.gestorpyme.domain.model.ItemLogistico;
import com.gestorpyme.service.InventarioLogisticoService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del inventario logistico. Coordina la vista con el servicio; no contiene
 * reglas de negocio ni SQL. Capa: controller.
 */
public class InventarioLogisticoController {

    private final InventarioLogisticoService service;

    public InventarioLogisticoController() {
        this(new InventarioLogisticoService());
    }

    public InventarioLogisticoController(InventarioLogisticoService service) {
        this.service = service;
    }

    /** @return items inventariables con stock actual, minimo, en pedido, sugerido y estado. */
    public List<ItemLogistico> listar() throws SQLException {
        return service.listar();
    }
}
