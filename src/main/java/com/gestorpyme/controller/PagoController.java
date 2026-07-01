package com.gestorpyme.controller;

import com.gestorpyme.domain.model.Pago;
import com.gestorpyme.service.PagoService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del modulo Pagos. Coordina la vista con {@link PagoService}.
 * Capa: controller.
 */
public class PagoController {

    private final PagoService pagoService;

    public PagoController() {
        this(new PagoService());
    }

    public PagoController(PagoService pagoService) {
        this.pagoService = pagoService;
    }

    public void registrarPago(Pago pago, BigDecimal saldoPendiente) throws SQLException {
        pagoService.registrarPago(pago, saldoPendiente);
    }

    public List<Pago> listarPorVenta(int idVenta) throws SQLException {
        return pagoService.listarPorVenta(idVenta);
    }

    public BigDecimal totalPagado(int idVenta) throws SQLException {
        return pagoService.totalPagado(idVenta);
    }
}
