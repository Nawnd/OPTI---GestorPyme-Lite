package com.gestorpyme.controller;

import com.gestorpyme.domain.model.EmpresaConfiguracion;
import com.gestorpyme.service.EmpresaService;

import java.util.Optional;

/**
 * Controlador del modulo Configuracion de empresa.
 * Coordina la vista con el servicio; no contiene reglas de negocio ni SQL.
 */
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    /** Obtiene la configuracion guardada (vacio si aun no existe). */
    public Optional<EmpresaConfiguracion> obtener() {
        return empresaService.obtener();
    }

    /** Valida y guarda la configuracion de la empresa. */
    public void guardar(EmpresaConfiguracion empresa) {
        empresaService.guardar(empresa);
    }
}
