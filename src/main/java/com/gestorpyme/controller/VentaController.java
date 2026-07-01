package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.service.VentaService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del modulo Ventas. Coordina la vista con {@link VentaService}.
 * Capa: controller (no contiene reglas de negocio ni SQL).
 */
public class VentaController {

    private final VentaService ventaService;

    public VentaController() {
        this(new VentaService());
    }

    public VentaController(VentaService ventaService) {
        this.ventaService = ventaService;
    }

    public String registrarVenta(Venta venta, List<VentaDetalle> detalles, int idBodega,
                                 boolean contado, String fechaVencimiento, MedioPago medioContado)
            throws SQLException {
        return ventaService.registrarVenta(venta, detalles, idBodega, contado, fechaVencimiento, medioContado);
    }

    /** Resuelve la bodega de salida sugerida para una linea (Paso I); lanza si no hay stock suficiente. */
    public com.gestorpyme.domain.model.DisponibilidadBodegaItem resolverBodegaSalida(
            int idItem, java.math.BigDecimal cantidad, int idBodegaPreferida) throws SQLException {
        return ventaService.resolverBodegaSalida(idItem, cantidad, idBodegaPreferida);
    }

    /** Previsualiza el plan FEFO de lotes de una linea (Paso J); lanza si los lotes no cubren la cantidad. */
    public java.util.List<com.gestorpyme.domain.model.VentaDetalleLote> planificarLotes(
            int idItem, int idBodega, java.math.BigDecimal cantidad) throws SQLException {
        return ventaService.planificarLotes(idItem, idBodega, cantidad);
    }

    public List<Venta> listar() throws SQLException {
        return ventaService.listar();
    }

    /** Busca ventas por consecutivo o cliente. */
    public List<Venta> buscar(String texto, int limite) throws SQLException {
        return ventaService.buscar(texto, limite);
    }

    public List<VentaDetalle> listarDetalles(int idVenta) throws SQLException {
        return ventaService.listarDetalles(idVenta);
    }
}
