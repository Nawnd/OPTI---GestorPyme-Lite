package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.OrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajoRepuesto;
import com.gestorpyme.domain.model.OrdenTrabajoServicio;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.repository.OrdenTrabajoRepository;
import com.gestorpyme.repository.VehiculoRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de las ordenes de trabajo (Paso U.2).
 *
 * Capa: service. Valida cabecera y detalle, recalcula subtotales y total (BigDecimal) y coordina el
 * repositorio. <b>No</b> toca inventario, lotes, Kardex ni ventas: la OT es un documento de trabajo
 * (el cierre a venta, con descuento FEFO delegando en VentaService, es U.3). Errores de negocio via
 * {@link ValidacionException}.
 */
public class OrdenTrabajoService {

    private final OrdenTrabajoRepository repository;
    private final ItemRepository itemRepository;
    private final VehiculoRepository vehiculoRepository;
    // Servicio de ventas: el cierre de la OT (Paso U.3) DELEGA en el para no duplicar la logica
    // de venta (descuento de stock, FEFO, Kardex, pago y cuentas por cobrar).
    private final VentaService ventaService;

    /** Constructor por defecto: crea sus propios repositorios. */
    public OrdenTrabajoService() {
        this(new OrdenTrabajoRepository(), new ItemRepository(), new VehiculoRepository(), new VentaService());
    }

    /** Constructor que permite inyectar dependencias (util en pruebas). */
    public OrdenTrabajoService(OrdenTrabajoRepository repository, ItemRepository itemRepository,
                               VehiculoRepository vehiculoRepository) {
        this(repository, itemRepository, vehiculoRepository, new VentaService());
    }

    /** Constructor completo (util en pruebas que tambien quieran inyectar el servicio de ventas). */
    public OrdenTrabajoService(OrdenTrabajoRepository repository, ItemRepository itemRepository,
                               VehiculoRepository vehiculoRepository, VentaService ventaService) {
        this.repository = repository;
        this.itemRepository = itemRepository;
        this.vehiculoRepository = vehiculoRepository;
        this.ventaService = ventaService;
    }

    /** Lista las ordenes de trabajo (sin detalle). */
    public List<OrdenTrabajo> listar() throws SQLException {
        return repository.listar();
    }

    /** Busca una OT con su detalle de servicios y repuestos. */
    public Optional<OrdenTrabajo> buscarConDetalles(int idOrdenTrabajo) throws SQLException {
        return repository.buscarConDetalles(idOrdenTrabajo);
    }

    /**
     * Valida y guarda una OT (cabecera + detalle). Recalcula subtotales y total. Inserta si el id es 0
     * (estado inicial ABIERTA); en caso contrario actualiza, salvo que la OT persistida este bloqueada
     * (CANCELADA o ENTREGADA), en cuyo caso se rechaza.
     *
     * @throws ValidacionException si algun dato es invalido o la OT no admite edicion.
     */
    public void guardar(OrdenTrabajo ot) throws SQLException {
        boolean esNueva = ot.getIdOrdenTrabajo() <= 0;
        if (!esNueva) {
            EstadoOrdenTrabajo persistido = estadoPersistido(ot.getIdOrdenTrabajo());
            if (persistido != null && persistido.esBloqueado()) {
                throw new ValidacionException(
                        "No se puede editar una orden de trabajo " + persistido.getEtiqueta().toLowerCase() + ".");
            }
        }
        if (ot.getEstado() == null) {
            ot.setEstado(EstadoOrdenTrabajo.ABIERTA);
        }
        validarCabecera(ot);
        validarYRecalcularServicios(ot);
        validarYRecalcularRepuestos(ot);
        recalcularTotales(ot);

        if (esNueva) {
            repository.crear(ot);
        } else {
            repository.actualizar(ot);
        }
    }

    /**
     * Cambia el estado de una OT. Una OT bloqueada (CANCELADA/ENTREGADA) no admite cambios. En U.2 no
     * se permite marcar ENTREGADA porque requiere el cierre a venta (U.3).
     */
    public void cambiarEstado(int idOrdenTrabajo, EstadoOrdenTrabajo nuevo) throws SQLException {
        if (nuevo == null) {
            throw new ValidacionException("Debe indicar el nuevo estado.");
        }
        EstadoOrdenTrabajo actual = estadoPersistido(idOrdenTrabajo);
        if (actual != null && actual.esBloqueado()) {
            throw new ValidacionException(
                    "La orden de trabajo ya esta " + actual.getEtiqueta().toLowerCase()
                  + "; no admite cambios de estado.");
        }
        if (nuevo == EstadoOrdenTrabajo.ENTREGADA) {
            throw new ValidacionException(
                    "Marcar ENTREGADA requiere cerrar la orden a venta (disponible en el Paso U.3).");
        }
        repository.cambiarEstado(idOrdenTrabajo, nuevo);
    }

    // ----------------------------------------------------------------- validaciones

    private void validarCabecera(OrdenTrabajo ot) throws SQLException {
        if (ot.getIdTercero() <= 0) {
            throw new ValidacionException("Debe seleccionar el cliente.");
        }
        if (ot.getIdVehiculo() <= 0) {
            throw new ValidacionException("Debe seleccionar el vehiculo.");
        }
        if (ot.getKilometrajeIngreso() < 0) {
            throw new ValidacionException("El kilometraje de ingreso no puede ser negativo.");
        }
        // El vehiculo debe pertenecer al cliente seleccionado.
        Optional<Vehiculo> v = vehiculoRepository.buscarPorId(ot.getIdVehiculo());
        if (v.isEmpty()) {
            throw new ValidacionException("El vehiculo indicado no existe.");
        }
        if (v.get().getIdTercero() != ot.getIdTercero()) {
            throw new ValidacionException("El vehiculo seleccionado no pertenece al cliente.");
        }
    }

    private void validarYRecalcularServicios(OrdenTrabajo ot) throws SQLException {
        for (OrdenTrabajoServicio s : ot.getServicios()) {
            Item item = obtenerItem(s.getIdItem(), "servicio");
            TipoItem tipo = item.getTipoItem();
            if (tipo != TipoItem.SERVICIO && tipo != TipoItem.MANO_OBRA) {
                throw new ValidacionException("El item '" + item.getNombre()
                        + "' no es un servicio ni mano de obra.");
            }
            validarCantidadPrecio(s.getCantidad(), s.getPrecioUnitario(), item.getNombre());
            s.setSubtotal(subtotal(s.getCantidad(), s.getPrecioUnitario()));
        }
    }

    private void validarYRecalcularRepuestos(OrdenTrabajo ot) throws SQLException {
        for (OrdenTrabajoRepuesto r : ot.getRepuestos()) {
            Item item = obtenerItem(r.getIdItem(), "repuesto");
            TipoItem tipo = item.getTipoItem();
            if (tipo == TipoItem.SERVICIO || tipo == TipoItem.MANO_OBRA) {
                throw new ValidacionException("El item '" + item.getNombre()
                        + "' es un servicio; no puede agregarse como repuesto.");
            }
            if (!item.isControlaInventario()) {
                throw new ValidacionException("El repuesto '" + item.getNombre()
                        + "' debe ser un producto que controla inventario.");
            }
            validarCantidadPrecio(r.getCantidad(), r.getPrecioUnitario(), item.getNombre());
            // El repuesto es inventariable: la bodega de salida es obligatoria (la real la resuelve U.3).
            if (r.getIdBodegaSalida() == null || r.getIdBodegaSalida() <= 0) {
                throw new ValidacionException("Indique la bodega de salida del repuesto '"
                        + item.getNombre() + "'.");
            }
            r.setSubtotal(subtotal(r.getCantidad(), r.getPrecioUnitario()));
        }
    }

    private Item obtenerItem(int idItem, String rol) throws SQLException {
        if (idItem <= 0) {
            throw new ValidacionException("Debe seleccionar el item del " + rol + ".");
        }
        return itemRepository.buscarPorId(idItem)
                .orElseThrow(() -> new ValidacionException("El item del " + rol + " no existe."));
    }

    private void validarCantidadPrecio(BigDecimal cantidad, BigDecimal precio, String nombre) {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ValidacionException("La cantidad de '" + nombre + "' debe ser mayor que cero.");
        }
        if (precio == null || precio.signum() < 0) {
            throw new ValidacionException("El precio de '" + nombre + "' no puede ser negativo.");
        }
    }

    /** Recalcula subtotal de servicios, subtotal de repuestos y total a partir del detalle. */
    private void recalcularTotales(OrdenTrabajo ot) {
        BigDecimal totalServicios = BigDecimal.ZERO;
        for (OrdenTrabajoServicio s : ot.getServicios()) {
            totalServicios = totalServicios.add(s.getSubtotal());
        }
        BigDecimal totalRepuestos = BigDecimal.ZERO;
        for (OrdenTrabajoRepuesto r : ot.getRepuestos()) {
            totalRepuestos = totalRepuestos.add(r.getSubtotal());
        }
        totalServicios = totalServicios.setScale(2, RoundingMode.HALF_UP);
        totalRepuestos = totalRepuestos.setScale(2, RoundingMode.HALF_UP);
        ot.setSubtotalServicios(totalServicios);
        ot.setSubtotalRepuestos(totalRepuestos);
        ot.setTotal(totalServicios.add(totalRepuestos));
    }

    private BigDecimal subtotal(BigDecimal cantidad, BigDecimal precio) {
        return cantidad.multiply(precio).setScale(2, RoundingMode.HALF_UP);
    }

    /** Lee el estado persistido de una OT (o null si no existe). */
    private EstadoOrdenTrabajo estadoPersistido(int idOrdenTrabajo) throws SQLException {
        return repository.buscarConDetalles(idOrdenTrabajo)
                .map(OrdenTrabajo::getEstado)
                .orElse(null);
    }

    // ----------------------------------------------------------------- cierre a venta (Paso U.3)

    /**
     * Cierra y factura una Orden de Trabajo: la convierte en una venta real DELEGANDO en
     * {@link VentaService#registrarVenta} (que aplica descuento de stock, FEFO, Kardex, pago o
     * cuenta por cobrar en una sola transaccion). Este metodo NO reimplementa nada de eso: solo
     * orquesta (valida la OT, arma la venta, delega y enlaza el resultado).
     *
     * <p>Reglas: la OT debe existir, no estar CANCELADA ni ENTREGADA, no tener ya una venta
     * asociada y tener al menos una linea (servicio o repuesto). Los servicios van como lineas sin
     * bodega; los repuestos como lineas que controlan inventario (la bodega real de cada linea la
     * resuelve VentaService a partir de una bodega preferida). Si la venta falla (por ejemplo, por
     * stock insuficiente), se propaga la excepcion y la OT queda intacta (sin id_venta y sin
     * cambiar de estado), evitando dobles descuentos.</p>
     *
     * <p><b>Atomicidad:</b> la venta se crea en su propia transaccion (VentaService) y, una vez
     * confirmada, se enlaza la OT en un UPDATE por clave primaria. Si ese ultimo enlace fallara
     * (caso muy improbable), la venta ya existe: se informa el numero generado y se pide enlace
     * manual sin reintentar el cierre, para no facturar dos veces.</p>
     *
     * @param idOrdenTrabajo    OT a cerrar.
     * @param contado           true = contado (registra pago); false = credito (genera cuenta por cobrar).
     * @param medioContado      medio de pago cuando es contado (obligatorio en contado; ignorado en credito).
     * @param fechaVencimiento  fecha ISO de vencimiento para credito (puede ser null).
     * @param observaciones     nota opcional del usuario (se concatena a la observacion de la venta).
     * @return el numero de la venta generada (V-NNNNNN).
     * @throws ValidacionException si la OT no cumple las reglas de cierre o la venta es invalida.
     * @throws SQLException        ante errores de base de datos.
     */
    public String cerrarYFacturar(int idOrdenTrabajo, boolean contado, MedioPago medioContado,
                                  String fechaVencimiento, String observaciones) throws SQLException {
        OrdenTrabajo ot = repository.buscarConDetalles(idOrdenTrabajo)
                .orElseThrow(() -> new ValidacionException("La orden de trabajo no existe."));

        // Validaciones de estado: no cerrar canceladas, ya entregadas o ya facturadas.
        EstadoOrdenTrabajo estado = ot.getEstado();
        if (estado == EstadoOrdenTrabajo.CANCELADA) {
            throw new ValidacionException("No se puede cerrar y facturar una orden cancelada.");
        }
        if (estado == EstadoOrdenTrabajo.ENTREGADA || ot.getIdVenta() != null) {
            throw new ValidacionException("La orden ya fue facturada (tiene una venta asociada).");
        }

        List<OrdenTrabajoServicio> servicios = ot.getServicios();
        List<OrdenTrabajoRepuesto> repuestos = ot.getRepuestos();
        boolean sinServicios = servicios == null || servicios.isEmpty();
        boolean sinRepuestos = repuestos == null || repuestos.isEmpty();
        if (sinServicios && sinRepuestos) {
            throw new ValidacionException("Agregue al menos un servicio o repuesto antes de cerrar y facturar.");
        }
        if (contado && medioContado == null) {
            throw new ValidacionException("Seleccione el medio de pago para una venta de contado.");
        }

        // Cabecera de la venta: cliente de la OT + observacion trazable hacia la OT.
        Venta venta = new Venta();
        venta.setIdTercero(ot.getIdTercero());
        venta.setIdUsuario(ot.getIdUsuario());
        venta.setDescuento(BigDecimal.ZERO);
        String obs = "Generada desde OT " + ot.getNumeroOt();
        if (observaciones != null && !observaciones.trim().isEmpty()) {
            obs = obs + " - " + observaciones.trim();
        }
        venta.setObservaciones(obs);

        // Lineas: servicios (sin inventario) y repuestos (controlan inventario). La bodega real de
        // cada linea la resuelve VentaService a partir de una bodega preferida (la del primer repuesto).
        List<VentaDetalle> detalles = new java.util.ArrayList<>();
        if (!sinServicios) {
            for (OrdenTrabajoServicio s : servicios) {
                detalles.add(lineaVenta(s.getIdItem(), s.getNombreItem(), false,
                        s.getCantidad(), s.getPrecioUnitario()));
            }
        }
        int idBodegaPreferida = 0;
        if (!sinRepuestos) {
            for (OrdenTrabajoRepuesto r : repuestos) {
                detalles.add(lineaVenta(r.getIdItem(), r.getNombreItem(), true,
                        r.getCantidad(), r.getPrecioUnitario()));
                if (idBodegaPreferida == 0 && r.getIdBodegaSalida() != null) {
                    idBodegaPreferida = r.getIdBodegaSalida();
                }
            }
        }

        // DELEGACION: VentaService ejecuta la transaccion real (stock, FEFO, Kardex, pago/cartera).
        // Si algo falla (p. ej. stock insuficiente) se propaga y la OT NO se modifica.
        String numeroVenta = ventaService.registrarVenta(venta, detalles, idBodegaPreferida,
                contado, contado ? null : fechaVencimiento, contado ? medioContado : null);

        // Enlace de la OT con la venta ya creada (id_venta + ENTREGADA). Ver nota de atomicidad.
        Optional<Integer> idVenta = ventaService.idVentaPorNumero(numeroVenta);
        if (idVenta.isEmpty()) {
            throw new SQLException("La venta " + numeroVenta + " se creo, pero no se pudo localizar para "
                    + "enlazar la orden de trabajo. Realice el enlace manual y NO reintente el cierre "
                    + "para evitar una doble facturacion.");
        }
        try {
            repository.marcarEntregadaConVenta(idOrdenTrabajo, idVenta.get());
        } catch (SQLException ex) {
            throw new SQLException("La venta " + numeroVenta + " se creo correctamente, pero fallo el "
                    + "enlace con la orden de trabajo (" + ex.getMessage() + "). Realice el enlace manual "
                    + "y NO reintente el cierre para evitar una doble facturacion.");
        }
        return numeroVenta;
    }

    /** Construye una linea de venta a partir de una linea de OT (servicio o repuesto). */
    private VentaDetalle lineaVenta(int idItem, String nombreItem, boolean controlaInventario,
                                    BigDecimal cantidad, BigDecimal precioUnitario) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setNombreItem(nombreItem);
        d.setControlaInventario(controlaInventario);
        d.setCantidad(cantidad);
        d.setPrecioUnitario(precioUnitario);
        d.setDescuentoLinea(BigDecimal.ZERO);
        return d;
    }
}
