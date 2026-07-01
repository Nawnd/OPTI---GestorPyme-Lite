package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoOrdenCompra;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.repository.OrdenCompraRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de ordenes de compra. Valida la orden, calcula subtotales/total con
 * BigDecimal (DEC-020) y delega la persistencia transaccional en el repositorio.
 * No contiene SQL ni codigo de interfaz. Capa: service.
 */
public class OrdenCompraService {

    private final OrdenCompraRepository repository;

    public OrdenCompraService() {
        this(new OrdenCompraRepository());
    }

    public OrdenCompraService(OrdenCompraRepository repository) {
        this.repository = repository;
    }

    /**
     * Valida y crea una orden de compra. La orden se crea en estado EMITIDA para que pueda
     * recibirse mercancia; el stock NO cambia al crear la orden (solo al recibir).
     *
     * @return el numero de orden generado (OC-000001).
     */
    public String crear(OrdenCompra orden) throws SQLException {
        if (orden == null) {
            throw new ValidacionException("La orden no puede ser nula.");
        }
        if (orden.getIdProveedor() <= 0) {
            throw new ValidacionException("Seleccione un proveedor para la orden.");
        }
        List<OrdenCompraDetalle> detalles = orden.getDetalles();
        if (detalles == null || detalles.isEmpty()) {
            throw new ValidacionException("Agregue al menos un item a la orden.");
        }

        BigDecimal subtotalOrden = BigDecimal.ZERO;
        for (OrdenCompraDetalle d : detalles) {
            if (d.getIdItem() <= 0) {
                throw new ValidacionException("Cada linea debe tener un item valido.");
            }
            if (d.getCantidadSolicitada() == null || d.getCantidadSolicitada().signum() <= 0) {
                throw new ValidacionException("La cantidad solicitada debe ser mayor que cero.");
            }
            if (d.getPrecioUnitario() == null || d.getPrecioUnitario().signum() < 0) {
                throw new ValidacionException("El precio unitario no puede ser negativo.");
            }
            BigDecimal sub = d.getCantidadSolicitada().multiply(d.getPrecioUnitario())
                    .setScale(2, RoundingMode.HALF_UP);
            d.setSubtotal(sub);
            subtotalOrden = subtotalOrden.add(sub);
        }

        subtotalOrden = subtotalOrden.setScale(2, RoundingMode.HALF_UP);
        orden.setSubtotal(subtotalOrden);
        orden.setTotal(subtotalOrden); // v1 sin impuestos ni descuentos de cabecera
        if (orden.getFechaOrden() == null || orden.getFechaOrden().isEmpty()) {
            orden.setFechaOrden(LocalDate.now().toString());
        }
        orden.setEstado(EstadoOrdenCompra.EMITIDA);
        return repository.crearOrdenCompleta(orden);
    }

    /** Lista las ordenes (cabecera) de la mas reciente a la mas antigua. */
    public List<OrdenCompra> listar() throws SQLException {
        return repository.listar();
    }

    /** Carga una orden con sus detalles (o vacio si no existe). */
    public Optional<OrdenCompra> buscarConDetalles(int idOrden) throws SQLException {
        Optional<OrdenCompra> opt = repository.buscarPorId(idOrden);
        if (opt.isPresent()) {
            OrdenCompra o = opt.get();
            o.getDetalles().addAll(repository.listarDetalles(idOrden));
        }
        return opt;
    }

    /**
     * Cancela una orden. No reversa lo ya recibido (el stock fisico no se toca); solo se
     * cancela el saldo pendiente cambiando el estado a CANCELADA (escenario 8 de casos borde).
     */
    public void cancelar(int idOrden) throws SQLException {
        Optional<OrdenCompra> opt = repository.buscarPorId(idOrden);
        if (opt.isEmpty()) {
            throw new ValidacionException("La orden indicada no existe.");
        }
        EstadoOrdenCompra estado = opt.get().getEstado();
        if (estado == EstadoOrdenCompra.RECIBIDA || estado == EstadoOrdenCompra.CANCELADA) {
            throw new ValidacionException("No se puede cancelar una orden " + estado.getEtiqueta() + ".");
        }
        repository.cambiarEstado(idOrden, EstadoOrdenCompra.CANCELADA);
    }
}
