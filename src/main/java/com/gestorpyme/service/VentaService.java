package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoVenta;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.DisponibilidadBodegaItem;
import com.gestorpyme.domain.model.LoteDisponible;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.domain.model.VentaDetalleLote;
import com.gestorpyme.repository.LoteRepository;
import com.gestorpyme.repository.VentaRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de ventas. Valida los datos, calcula subtotales y total con
 * {@link BigDecimal} (DEC-020) y delega la persistencia transaccional en
 * {@link VentaRepository}. Capa: service.
 *
 * Paso I: la bodega global de la venta es la "bodega preferida"; el servicio resuelve, por cada
 * linea inventariable, la bodega REAL de salida segun disponibilidad (preferida si alcanza; si no,
 * la bodega activa con menor excedente suficiente; si ninguna individual cubre, rechaza con detalle).
 */
public class VentaService {

    private final VentaRepository ventaRepository;
    private final InventarioService inventarioService;
    private final LoteRepository loteRepository;

    public VentaService() {
        this(new VentaRepository());
    }

    public VentaService(VentaRepository ventaRepository) {
        this.ventaRepository = ventaRepository;
        this.inventarioService = new InventarioService();
        this.loteRepository = new LoteRepository();
    }

    /**
     * Valida y registra una venta completa.
     *
     * @param venta            encabezado (debe tener cliente).
     * @param detalles         lineas (al menos una; cantidades &gt; 0).
     * @param idBodega         bodega para descontar inventario (si hay items que lo controlan).
     * @param contado          true = contado; false = credito.
     * @param fechaVencimiento fecha ISO de vencimiento (credito); puede ser null.
     * @param medioContado     medio de pago para contado.
     * @return el numero de venta generado (V-NNNNNN).
     * @throws ValidacionException si algun dato no cumple las reglas.
     * @throws SQLException        ante errores de base de datos.
     */
    public String registrarVenta(Venta venta, List<VentaDetalle> detalles, int idBodega,
                                 boolean contado, String fechaVencimiento, MedioPago medioContado)
            throws SQLException {
        if (venta == null) {
            throw new ValidacionException("La venta no puede ser nula.");
        }
        if (venta.getIdTercero() == null) {
            throw new ValidacionException("Seleccione un cliente para la venta.");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new ValidacionException("Agregue al menos un producto o servicio a la venta.");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        boolean requiereBodega = false;

        for (VentaDetalle d : detalles) {
            if (d.getCantidad() == null || d.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidacionException("La cantidad de cada linea debe ser mayor a 0.");
            }
            if (d.getPrecioUnitario() == null || d.getPrecioUnitario().compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidacionException("El precio unitario no puede ser negativo.");
            }
            if (d.getDescuentoLinea() == null) {
                d.setDescuentoLinea(BigDecimal.ZERO);
            }
            // subtotal_linea = (cantidad * precio) - descuento_linea
            BigDecimal bruto = d.getCantidad().multiply(d.getPrecioUnitario());
            BigDecimal subtotalLinea = bruto.subtract(d.getDescuentoLinea());
            if (subtotalLinea.compareTo(BigDecimal.ZERO) < 0) {
                throw new ValidacionException("El descuento de una linea no puede superar su importe.");
            }
            d.setSubtotalLinea(subtotalLinea);
            subtotal = subtotal.add(subtotalLinea);

            if (d.isControlaInventario()) {
                requiereBodega = true;
            }
        }

        BigDecimal descuento = venta.getDescuento() == null ? BigDecimal.ZERO : venta.getDescuento();
        if (descuento.compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidacionException("El descuento no puede ser negativo.");
        }
        if (descuento.compareTo(subtotal) > 0) {
            throw new ValidacionException("El descuento no puede superar el subtotal de la venta.");
        }
        BigDecimal total = subtotal.subtract(descuento);

        if (requiereBodega && idBodega <= 0) {
            throw new ValidacionException("Seleccione una bodega para los productos que controlan inventario.");
        }

        // Paso I: resuelve la bodega REAL de salida de cada linea inventariable (la global es la preferida).
        // Los servicios no llevan bodega (queda null). Se reasigna siempre aqui (no se confia en la vista).
        for (VentaDetalle d : detalles) {
            if (d.isControlaInventario()) {
                DisponibilidadBodegaItem elegida = resolverBodegaSalida(d.getIdItem(), d.getCantidad(), idBodega);
                d.setIdBodegaSalida(elegida.getIdBodega());
                d.setNombreBodegaSalida(elegida.getNombreBodega());
            } else {
                d.setIdBodegaSalida(null);
                d.setNombreBodegaSalida(null);
            }
        }

        venta.setSubtotal(subtotal);
        venta.setDescuento(descuento);
        venta.setTotal(total);
        // Contado: queda pagada. Credito: pendiente de pago (genera cuenta por cobrar).
        venta.setEstado(contado ? EstadoVenta.PAGADA : EstadoVenta.PENDIENTE_PAGO);

        return ventaRepository.crearVentaCompleta(venta, detalles, idBodega, contado, fechaVencimiento, medioContado);
    }

    /**
     * Resuelve la bodega de salida de una linea inventariable (Paso I): consulta la disponibilidad por
     * bodega y aplica {@link #elegirBodega}. Si ninguna bodega individual cubre la cantidad, lanza una
     * excepcion con el desglose de disponibilidad por bodega (sin hacer split multi-bodega).
     *
     * @param idItem    item de la linea.
     * @param cantidad  cantidad requerida.
     * @param idBodegaPreferida bodega global de la venta (preferida), o &le; 0 si no hay.
     * @return la bodega elegida (id + nombre + disponible).
     * @throws ValidacionException si no hay una sola bodega con stock suficiente.
     */
    public DisponibilidadBodegaItem resolverBodegaSalida(int idItem, BigDecimal cantidad, int idBodegaPreferida)
            throws SQLException {
        List<DisponibilidadBodegaItem> disponibilidad = inventarioService.disponibilidadPorBodega(idItem);
        DisponibilidadBodegaItem elegida = elegirBodega(disponibilidad, cantidad, idBodegaPreferida);
        if (elegida == null) {
            throw new ValidacionException(mensajeSinBodega(disponibilidad, cantidad));
        }
        return elegida;
    }

    /**
     * Elige la bodega de salida (Paso I), metodo puro y testeable:
     * <ol>
     *   <li>Si la bodega preferida tiene stock &ge; cantidad, se usa la preferida.</li>
     *   <li>Si no, entre las bodegas con stock &ge; cantidad se elige la de MENOR excedente suficiente
     *       (la menor cantidad que aun cubre), para no dispersar saldos pequeños.</li>
     *   <li>Si ninguna individual cubre, devuelve null.</li>
     * </ol>
     */
    public DisponibilidadBodegaItem elegirBodega(List<DisponibilidadBodegaItem> disponibilidad,
                                                 BigDecimal cantidad, int idBodegaPreferida) {
        if (disponibilidad == null || cantidad == null) {
            return null;
        }
        // 1) Bodega preferida, si alcanza.
        if (idBodegaPreferida > 0) {
            for (DisponibilidadBodegaItem d : disponibilidad) {
                if (d.getIdBodega() == idBodegaPreferida && d.getCantidadDisponible().compareTo(cantidad) >= 0) {
                    return d;
                }
            }
        }
        // 2) Menor excedente suficiente.
        DisponibilidadBodegaItem mejor = null;
        for (DisponibilidadBodegaItem d : disponibilidad) {
            if (d.getCantidadDisponible().compareTo(cantidad) >= 0) {
                if (mejor == null || d.getCantidadDisponible().compareTo(mejor.getCantidadDisponible()) < 0) {
                    mejor = d;
                }
            }
        }
        return mejor;
    }

    /** Construye el mensaje de error con el desglose de disponibilidad por bodega (Paso I). */
    private String mensajeSinBodega(List<DisponibilidadBodegaItem> disponibilidad, BigDecimal cantidad) {
        StringBuilder sb = new StringBuilder("No hay una bodega individual con stock suficiente para este ítem.\n");
        sb.append("Disponible por bodega:\n");
        BigDecimal total = BigDecimal.ZERO;
        for (DisponibilidadBodegaItem d : disponibilidad) {
            if (d.getCantidadDisponible().signum() > 0) {
                sb.append("- ").append(d.getNombreBodega()).append(": ")
                        .append(d.getCantidadDisponible().stripTrailingZeros().toPlainString()).append("\n");
            }
            total = total.add(d.getCantidadDisponible());
        }
        sb.append("Total disponible: ").append(total.stripTrailingZeros().toPlainString()).append("\n");
        sb.append("Requerido: ").append(cantidad.stripTrailingZeros().toPlainString()).append("\n");
        sb.append("Sugerencia: realizar traslado o habilitar venta dividida por bodegas en un paso futuro.");
        return sb.toString();
    }

    /**
     * Previsualiza (solo lectura, sin persistir) el plan FEFO de lotes para una linea (Paso J): qué lote(s)
     * se consumirían y cuánto de cada uno. Reglas iguales al consumo real:
     * <ul>
     *   <li>Si el item NO tiene lotes usables en la bodega: devuelve lista vacía ("Sin lote" en la UI).</li>
     *   <li>Si tiene lotes usables pero su saldo total no cubre la cantidad: lanza {@link ValidacionException}.</li>
     *   <li>Si cubren: devuelve el plan (lote + cantidad por FEFO).</li>
     * </ul>
     * El consumo autoritativo se realiza dentro de la transacción de la venta; esto es solo apoyo visual.
     *
     * @param idItem   item de la línea (debe controlar inventario).
     * @param idBodega bodega de salida ya resuelta (Paso I).
     * @param cantidad cantidad requerida.
     */
    public List<VentaDetalleLote> planificarLotes(int idItem, int idBodega, BigDecimal cantidad) throws SQLException {
        List<LoteDisponible> usables = loteRepository.disponiblesFefo(idItem, idBodega);
        List<VentaDetalleLote> plan = new ArrayList<>();
        if (usables.isEmpty()) {
            return plan; // sin lotes usables -> flujo sin lote
        }
        BigDecimal total = BigDecimal.ZERO;
        for (LoteDisponible l : usables) {
            total = total.add(l.getCantidadDisponible());
        }
        if (total.compareTo(cantidad) < 0) {
            throw new ValidacionException(
                    "El producto tiene control por lotes, pero los lotes disponibles no cubren la cantidad requerida.");
        }
        BigDecimal restante = cantidad;
        for (LoteDisponible l : usables) {
            if (restante.signum() <= 0) {
                break;
            }
            BigDecimal toma = l.getCantidadDisponible().min(restante);
            plan.add(new VentaDetalleLote(0, l.getIdLote(), l.getNumeroLote(), toma));
            restante = restante.subtract(toma);
        }
        return plan;
    }

    /** Lista las ventas registradas (mas recientes primero). */
    public List<Venta> listar() throws SQLException {
        return ventaRepository.listar();
    }

    /** Busca ventas por consecutivo o cliente (busqueda inteligente). */
    public List<Venta> buscar(String texto, int limite) throws SQLException {
        return ventaRepository.buscar(texto, limite);
    }

    /** Devuelve el detalle de una venta. */
    /**
     * Devuelve el id de una venta por su numero exacto (V-NNNNNN). Lectura usada por el cierre de
     * Orden de Trabajo (Paso U.3) para enlazar la OT con la venta generada al delegar en esta clase.
     */
    public Optional<Integer> idVentaPorNumero(String numeroVenta) throws SQLException {
        return ventaRepository.buscarIdPorNumero(numeroVenta);
    }

    public List<VentaDetalle> listarDetalles(int idVenta) throws SQLException {
        return ventaRepository.listarDetalles(idVenta);
    }
}
