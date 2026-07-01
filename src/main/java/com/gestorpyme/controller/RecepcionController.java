package com.gestorpyme.controller;

import com.gestorpyme.domain.model.RecepcionMercancia;
import com.gestorpyme.service.RecepcionService;

import java.sql.SQLException;

/**
 * Controlador de recepcion de mercancia. Coordina la vista con el servicio; no contiene
 * reglas de negocio ni SQL. Capa: controller.
 */
public class RecepcionController {

    private final RecepcionService service;

    public RecepcionController() {
        this(new RecepcionService());
    }

    public RecepcionController(RecepcionService service) {
        this.service = service;
    }

    /** Registra una recepcion (parcial o total) y devuelve su numero (RC-000001). */
    public String registrar(RecepcionMercancia rec) throws SQLException {
        return service.registrar(rec);
    }
}
