package com.gestorpyme.controller;

import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.service.CuentaService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del modulo Cuentas por cobrar. Coordina la vista con {@link CuentaService}.
 * Capa: controller.
 */
public class CuentaController {

    private final CuentaService cuentaService;

    public CuentaController() {
        this(new CuentaService());
    }

    public CuentaController(CuentaService cuentaService) {
        this.cuentaService = cuentaService;
    }

    public List<CuentaPorCobrar> listar() throws SQLException {
        return cuentaService.listar();
    }

    public List<Abono> listarAbonos(int idCuenta) throws SQLException {
        return cuentaService.listarAbonos(idCuenta);
    }

    public void registrarAbono(Abono abono) throws SQLException {
        cuentaService.registrarAbono(abono);
    }
}
