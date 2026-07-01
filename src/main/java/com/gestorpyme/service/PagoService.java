package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoCuenta;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.Pago;
import com.gestorpyme.repository.PagoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de pagos. Capa: service.
 *
 * <p><b>Coherencia financiera (v0.7.1).</b> Cuando la venta tiene una cuenta por cobrar
 * asociada (venta a credito), el cobro registrado desde el modulo Pagos NO se inserta en
 * la tabla {@code pagos} (eso crearia un libro paralelo desconectado): se enruta hacia
 * {@link CuentaService#registrarAbono(Abono)}, que de forma transaccional reduce el saldo
 * de la cuenta, registra el abono y sincroniza {@code ventas.estado} cuando el saldo llega
 * a cero. De esta forma Pagos y Cuentas por cobrar muestran siempre el mismo saldo y no se
 * duplica el dinero.</p>
 *
 * <p>Si la venta no tiene cuenta por cobrar, es una venta de contado (cuyo pago se registro
 * al crearse la venta) y no se admite un segundo cobro desde Pagos.</p>
 */
public class PagoService {

    private final PagoRepository pagoRepository;
    private final CuentaService cuentaService;

    public PagoService() {
        this(new PagoRepository(), new CuentaService());
    }

    /**
     * Constructor que conserva la firma usada por las pruebas existentes; cablea un
     * {@link CuentaService} por defecto.
     */
    public PagoService(PagoRepository pagoRepository) {
        this(pagoRepository, new CuentaService());
    }

    /** Constructor inyectable (para pruebas de integracion del flujo Pagos -&gt; Cuenta -&gt; Venta). */
    public PagoService(PagoRepository pagoRepository, CuentaService cuentaService) {
        this.pagoRepository = pagoRepository;
        this.cuentaService = cuentaService;
    }

    /**
     * Registra el cobro de una venta desde el modulo Pagos.
     *
     * <ul>
     *   <li><b>Venta a credito (con cuenta por cobrar):</b> el cobro se trata como un
     *       <i>abono</i> de la cuenta (reduce saldo, registra abono y sincroniza el estado
     *       de la venta si queda saldada), de forma atomica. No inserta en la tabla pagos.</li>
     *   <li><b>Venta de contado (sin cuenta):</b> ya quedo pagada al crearse; se rechaza el
     *       intento de un segundo cobro con un mensaje claro.</li>
     * </ul>
     *
     * @param pago           datos del cobro (id_venta, medio, valor, observaciones).
     * @param saldoPendiente saldo mostrado en la vista (para una validacion temprana);
     *                       el tope real se revalida de forma atomica contra la cuenta.
     * @throws ValidacionException si el valor es &lt;= 0, supera el saldo, o la venta no
     *                             admite el cobro (ya pagada / sin cuenta).
     * @throws SQLException        ante errores de base de datos.
     */
    public void registrarPago(Pago pago, BigDecimal saldoPendiente) throws SQLException {
        if (pago == null) {
            throw new ValidacionException("El pago no puede ser nulo.");
        }
        // Validacion temprana del valor (mayor a 0 y, si se conoce, dentro del saldo mostrado).
        validarValor(pago.getValor(), saldoPendiente);

        Optional<CuentaPorCobrar> cuentaOpt = cuentaService.buscarPorVenta(pago.getIdVenta());

        if (cuentaOpt.isPresent()) {
            CuentaPorCobrar cuenta = cuentaOpt.get();
            EstadoCuenta estado = cuenta.getEstado();
            if (estado == EstadoCuenta.PAGADA || estado == EstadoCuenta.CANCELADA) {
                throw new ValidacionException("La venta ya se encuentra pagada.");
            }
            // Enrutar el cobro como abono de la cuenta. El repositorio de cuentas valida que
            // el valor no supere el saldo y sincroniza la venta dentro de la transaccion.
            Abono abono = new Abono();
            abono.setIdCuenta(cuenta.getIdCuenta());
            abono.setValor(pago.getValor());
            abono.setMedioPago(pago.getMedioPago());
            abono.setObservaciones(pago.getObservaciones());
            cuentaService.registrarAbono(abono);
            return;
        }

        // Sin cuenta por cobrar: es una venta de contado (su pago se registro al venderse).
        throw new ValidacionException("Esta venta no tiene cuenta por cobrar asociada. "
                + "Si es una venta de contado, ya se encuentra pagada; no se puede registrar "
                + "otro cobro desde Pagos.");
    }

    /** Validacion reutilizable del valor de un pago. */
    public void validarValor(BigDecimal valor, BigDecimal saldoPendiente) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionException("El valor del pago debe ser mayor a 0.");
        }
        if (saldoPendiente != null && valor.compareTo(saldoPendiente) > 0) {
            throw new ValidacionException("El pago no puede superar el saldo pendiente ("
                    + saldoPendiente.toPlainString() + ").");
        }
    }

    public List<Pago> listarPorVenta(int idVenta) throws SQLException {
        return pagoRepository.listarPorVenta(idVenta);
    }

    public BigDecimal totalPagado(int idVenta) throws SQLException {
        return pagoRepository.totalPagado(idVenta);
    }
}
