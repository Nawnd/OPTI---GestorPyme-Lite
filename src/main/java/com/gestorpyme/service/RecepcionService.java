package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.domain.model.RecepcionDetalle;
import com.gestorpyme.domain.model.RecepcionMercancia;
import com.gestorpyme.repository.OrdenCompraRepository;
import com.gestorpyme.repository.RecepcionRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reglas de negocio de la recepcion de mercancia. Valida contra la orden (estado y
 * cantidades pendientes) ANTES de tocar la base de datos; la recepcion fisica (stock +
 * Kardex + estado de la orden) se ejecuta en una transaccion del repositorio.
 *
 * Garantias clave (casos borde):
 *  - No se puede recibir mas de lo pendiente (escenario 7): se rechaza sin alterar nada.
 *  - Si la operacion falla a mitad, el repositorio hace rollback total (escenario 11).
 * Capa: service.
 */
public class RecepcionService {

    private final RecepcionRepository recepcionRepository;
    private final OrdenCompraRepository ordenRepository;

    public RecepcionService() {
        this(new RecepcionRepository(), new OrdenCompraRepository());
    }

    public RecepcionService(RecepcionRepository recepcionRepository, OrdenCompraRepository ordenRepository) {
        this.recepcionRepository = recepcionRepository;
        this.ordenRepository = ordenRepository;
    }

    /**
     * Valida y registra una recepcion (parcial o total) de la orden indicada.
     * Las lineas con cantidad cero se ignoran; debe quedar al menos una con cantidad > 0.
     *
     * @return el numero de recepcion generado (RC-000001).
     */
    public String registrar(RecepcionMercancia rec) throws SQLException {
        if (rec == null || rec.getIdOrden() <= 0) {
            throw new ValidacionException("Recepcion invalida: falta la orden de compra.");
        }

        Optional<OrdenCompra> opt = ordenRepository.buscarPorId(rec.getIdOrden());
        if (opt.isEmpty()) {
            throw new ValidacionException("La orden de compra no existe.");
        }
        OrdenCompra orden = opt.get();
        if (!orden.getEstado().permiteRecepcion()) {
            throw new ValidacionException(
                    "La orden no admite recepcion (estado: " + orden.getEstado().getEtiqueta() + ").");
        }

        // Pendiente por linea de la orden.
        Map<Integer, BigDecimal> pendientePorDetalle = new HashMap<>();
        for (OrdenCompraDetalle d : ordenRepository.listarDetalles(rec.getIdOrden())) {
            pendientePorDetalle.put(d.getIdDetalle(), d.getPendiente());
        }

        // Solo lineas con cantidad > 0.
        List<RecepcionDetalle> efectivas = new ArrayList<>();
        for (RecepcionDetalle d : rec.getDetalles()) {
            if (d.getCantidadRecibida() == null || d.getCantidadRecibida().signum() <= 0) {
                continue;
            }
            BigDecimal pendiente = pendientePorDetalle.get(d.getIdDetalleOc());
            if (pendiente == null) {
                throw new ValidacionException("Una linea no corresponde a la orden seleccionada.");
            }
            if (d.getCantidadRecibida().compareTo(pendiente) > 0) {
                throw new ValidacionException("No puede recibir mas de lo pendiente. Pendiente: "
                        + pendiente.stripTrailingZeros().toPlainString()
                        + ", intentado: " + d.getCantidadRecibida().stripTrailingZeros().toPlainString() + ".");
            }
            if (d.getIdBodega() <= 0) {
                throw new ValidacionException("Seleccione la bodega donde ingresa la mercancia.");
            }
            validarLote(d);
            efectivas.add(d);
        }

        if (efectivas.isEmpty()) {
            throw new ValidacionException("Indique al menos una cantidad a recibir (mayor que cero).");
        }

        rec.getDetalles().clear();
        rec.getDetalles().addAll(efectivas);
        return recepcionRepository.registrarRecepcionCompleta(rec);
    }

    /**
     * Validaciones estaticas del lote informado por linea (Paso M), previas a la transaccion:
     *  - si se informa lote en un item no inventariable (servicio) -> rechazo;
     *  - si la fecha de vencimiento tiene formato invalido -> rechazo;
     *  - si la fecha de vencimiento informada esta vencida -> rechazo (no crear lote vencido).
     * Las reglas que dependen del estado del lote existente (engrose, mezcla de vencimientos, lote ya
     * vencido) se validan dentro de la transaccion del repositorio.
     */
    private void validarLote(RecepcionDetalle d) throws SQLException {
        String numero = d.getNumeroLote();
        if (numero == null || numero.isBlank()) {
            return; // sin lote: nada que validar
        }
        if (!recepcionRepository.esInventariable(d.getIdItem())) {
            throw new ValidacionException("Los servicios no manejan lote. Quite el lote de esa linea.");
        }
        String venc = d.getFechaVencimiento();
        if (venc != null && !venc.isBlank()) {
            LocalDate fecha;
            try {
                fecha = LocalDate.parse(venc.trim().length() >= 10 ? venc.trim().substring(0, 10) : venc.trim());
            } catch (RuntimeException e) {
                throw new ValidacionException("Fecha de vencimiento invalida (use AAAA-MM-DD).");
            }
            if (fecha.isBefore(LocalDate.now())) {
                throw new ValidacionException("No se puede crear un lote vencido.");
            }
        }
    }
}
