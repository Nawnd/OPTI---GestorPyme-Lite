package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoExportacion;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.*;
import com.gestorpyme.infrastructure.export.CsvWriter;
import com.gestorpyme.util.ExportValueFormatter;
import com.gestorpyme.repository.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Servicio de exportacion. Agrega datos de los distintos modulos (clientes,
 * items, inventario, kardex, ventas, pagos, cartera, CRM), los escribe en un CSV
 * (separador ';', UTF-8) y registra la operacion en exportaciones_log.
 *
 * Mantiene la representacion de los datos SEPARADA del formato de archivo: arma
 * una tabla generica (encabezados + filas) y luego la entrega a {@link CsvWriter}.
 * Esto deja preparada la futura exportacion a Excel (bastaria un escritor .xlsx
 * que reciba la misma tabla). Capa: service.
 */
public class ExportacionService {

    private final TerceroRepository terceroRepository = new TerceroRepository();
    private final ItemRepository itemRepository = new ItemRepository();
    private final InventarioRepository inventarioRepository = new InventarioRepository();
    private final VentaRepository ventaRepository = new VentaRepository();
    private final PagoRepository pagoRepository = new PagoRepository();
    private final CuentaRepository cuentaRepository = new CuentaRepository();
    private final SeguimientoRepository seguimientoRepository = new SeguimientoRepository();
    private final ExportacionRepository exportacionRepository = new ExportacionRepository();
    // Fuentes para las exportaciones nuevas (v0.8.2). Todas de solo lectura.
    private final OrdenCompraRepository ordenCompraRepository = new OrdenCompraRepository();
    private final RecepcionRepository recepcionRepository = new RecepcionRepository();
    /** Para la exportación gerencial: reutiliza el resumen ejecutivo ya calculado (Paso R). */
    private final DashboardService dashboardService = new DashboardService();
    private final LoteRepository loteRepository = new LoteRepository();
    private final InventarioLogisticoService inventarioLogisticoService = new InventarioLogisticoService();

    /** Tabla generica lista para cualquier escritor (CSV hoy, Excel a futuro). */
    public static final class Contenido {
        public final List<String> encabezados;
        public final List<List<String>> filas;
        Contenido(List<String> encabezados, List<List<String>> filas) {
            this.encabezados = encabezados;
            this.filas = filas;
        }
    }

    /**
     * Exporta el tipo indicado a un archivo CSV y registra el resultado.
     *
     * @param tipo        que se exporta.
     * @param rutaArchivo ruta destino elegida por el usuario.
     * @param idUsuario   usuario que exporta (puede ser null).
     * @throws SQLException si falla la lectura de datos.
     * @throws IOException  si falla la escritura del archivo.
     */
    public void exportar(TipoExportacion tipo, String rutaArchivo, Integer idUsuario)
            throws SQLException, IOException {
        try {
            Contenido contenido = construir(tipo);
            CsvWriter.escribir(rutaArchivo, contenido.encabezados, contenido.filas);
            registrar(tipo, rutaArchivo, idUsuario, EstadoExportacion.GENERADO);
        } catch (SQLException | IOException | RuntimeException e) {
            // Se deja constancia del error y se relanza para que la vista avise.
            registrarSilencioso(tipo, rutaArchivo, idUsuario, EstadoExportacion.ERROR);
            throw e;
        }
    }

    /** Arma la tabla (encabezados + filas) segun el tipo. */
    private Contenido construir(TipoExportacion tipo) throws SQLException {
        switch (tipo) {
            case CLIENTES:        return terceros(TipoTercero.CLIENTE);
            case PROSPECTOS:      return terceros(TipoTercero.PROSPECTO);
            case PROVEEDORES:     return proveedores();
            case ITEMS:           return items();
            case INVENTARIO:      return inventario();
            case REABASTECIMIENTO:return reabastecimiento();
            case KARDEX:          return kardex();
            case LOTES:           return lotes();
            case CRM:             return crm();
            case VENTAS:          return ventas();
            case VENTA_DETALLES:  return ventaDetalles();
            case PAGOS:           return pagos();
            case CARTERA:         return cartera();
            case ABONOS:          return abonos();
            case COMPRAS:         return compras();
            case RECEPCIONES:     return recepciones();
            case DETALLE_RECEPCIONES: return detalleRecepciones();
            case DASHBOARD_GERENCIAL: return dashboardGerencial();
            default:
                throw new IllegalArgumentException("Tipo de exportacion no soportado: " + tipo);
        }
    }

    private Contenido terceros(TipoTercero tipo) throws SQLException {
        List<String> cab = Arrays.asList("Tipo", "Nombre", "Documento", "Telefono", "Correo", "Direccion", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (Tercero t : terceroRepository.listarPorTipos(Collections.singletonList(tipo))) {
            filas.add(Arrays.asList(
                    estado(t.getTipoTercero()),
                    txt(t.getNombre()), txt(t.getDocumento()), txt(t.getTelefono()),
                    txt(t.getCorreo()), txt(t.getDireccion()),
                    estado(t.getEstado())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido proveedores() throws SQLException {
        List<String> cab = Arrays.asList("Nombre", "Documento", "Telefono", "Correo", "Direccion", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (Tercero t : terceroRepository.listarProveedores()) {
            filas.add(Arrays.asList(
                    txt(t.getNombre()), txt(t.getDocumento()), txt(t.getTelefono()),
                    txt(t.getCorreo()), txt(t.getDireccion()), estado(t.getEstado())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido items() throws SQLException {
        List<String> cab = Arrays.asList("Codigo", "Nombre", "Tipo", "Categoria", "Subtipo", "Unidad",
                "Precio_Compra_COP", "Precio_Venta_COP", "Inventario", "Stock_Minimo",
                "Stock_Maximo", "Proveedor_Preferido",
                "Modo_Servicio", "Porcentaje_Servicio", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (Item i : itemRepository.listar()) {
            // El modo y el porcentaje de servicio solo se exportan para servicios (sin inventario).
            boolean esServicio = !i.isControlaInventario();
            String modo = esServicio ? estado(i.getModoCalculoServicio()) : "";
            String porcentaje = (esServicio && i.getPorcentajeServicio() != null)
                    ? cant(i.getPorcentajeServicio()) : "";
            // Paso F: stock maximo vacio si es null; proveedor preferido como texto (vacio si null).
            String maximo = i.getStockMaximo() == null ? "" : cant(i.getStockMaximo());
            filas.add(Arrays.asList(
                    txt(i.getCodigo()), txt(i.getNombre()),
                    estado(i.getTipoItem()),
                    txt(i.getNombreCategoria()),
                    txt(i.getNombreSubtipo()),
                    txt(i.getUnidadMedida()),
                    dinero(i.getPrecioCompra()), dinero(i.getPrecioVenta()),
                    i.isControlaInventario() ? "Si" : "No",
                    cant(i.getStockMinimo()),
                    maximo, txt(i.getNombreProveedorPreferido()),
                    modo, porcentaje,
                    estado(i.getEstado())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido inventario() throws SQLException {
        List<String> cab = Arrays.asList("Codigo item", "Item", "Bodega", "Ubicacion", "Cantidad",
                "Stock_Minimo", "Estado_Stock");
        List<List<String>> filas = new ArrayList<>();
        for (ExistenciaStock e : inventarioRepository.listarExistencias()) {
            filas.add(Arrays.asList(txt(e.getCodigoItem()), txt(e.getNombreItem()),
                    txt(e.getNombreBodega()), txt(e.getUbicacionInterna()), cant(e.getCantidad()),
                    cant(e.getStockMinimo()), txt(e.getEstadoStock())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido kardex() throws SQLException {
        List<String> cab = Arrays.asList("Fecha", "Item", "Bodega", "Tipo", "Cantidad", "Motivo", "Usuario", "Lote");
        List<List<String>> filas = new ArrayList<>();
        for (MovimientoInventario m : inventarioRepository.listarMovimientos(null, null, null, "", "")) {
            // Paso J: lote de la salida por venta (vacio si el movimiento no esta asociado a un lote).
            filas.add(Arrays.asList(fecha(m.getFecha()), txt(m.getNombreItem()), txt(m.getNombreBodega()),
                    estado(m.getTipo()), cant(m.getCantidad()), txt(m.getMotivo()), txt(m.getNombreUsuario()),
                    txt(m.getNumeroLote())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido ventas() throws SQLException {
        List<String> cab = Arrays.asList("Numero", "Fecha", "Cliente", "Subtotal_COP", "Descuento_COP", "Total_COP", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (Venta v : ventaRepository.listar()) {
            filas.add(Arrays.asList(txt(v.getNumeroVenta()), fecha(v.getFecha()), txt(v.getNombreTercero()),
                    dinero(v.getSubtotal()), dinero(v.getDescuento()), dinero(v.getTotal()),
                    estado(v.getEstado())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido pagos() throws SQLException {
        List<String> cab = Arrays.asList("Venta", "Fecha", "Medio", "Valor_COP", "Referencia", "Observaciones");
        List<List<String>> filas = new ArrayList<>();
        // Se recorren las ventas y sus pagos (reutiliza los metodos existentes).
        for (Venta v : ventaRepository.listar()) {
            for (Pago p : pagoRepository.listarPorVenta(v.getIdVenta())) {
                filas.add(Arrays.asList(txt(v.getNumeroVenta()), fecha(p.getFecha()),
                        estado(p.getMedioPago()),
                        dinero(p.getValor()), txt(p.getReferencia()), txt(p.getObservaciones())));
            }
        }
        return new Contenido(cab, filas);
    }

    private Contenido cartera() throws SQLException {
        List<String> cab = Arrays.asList("Venta", "Cliente", "Valor_Total_COP", "Valor_Pagado_COP",
                "Saldo_COP", "Vencimiento", "Estado");
        List<List<String>> filas = new ArrayList<>();
        for (CuentaPorCobrar c : cuentaRepository.listar()) {
            filas.add(Arrays.asList(txt(c.getNumeroVenta()), txt(c.getNombreTercero()),
                    dinero(c.getValorTotal()), dinero(c.getValorPagado()), dinero(c.getSaldoPendiente()),
                    fecha(c.getFechaVencimiento()), estado(c.getEstado())));
        }
        return new Contenido(cab, filas);
    }

    private Contenido crm() throws SQLException {
        List<String> cab = Arrays.asList("Fecha", "Cliente / Prospecto", "Tipo", "Estado", "Descripcion");
        List<List<String>> filas = new ArrayList<>();
        for (Seguimiento s : seguimientoRepository.listar()) {
            filas.add(Arrays.asList(fecha(s.getFecha()), txt(s.getNombreTercero()),
                    estado(s.getTipo()), estado(s.getEstado()), txt(s.getDescripcion())));
        }
        return new Contenido(cab, filas);
    }

    // ===================== Exportaciones nuevas (v0.8.2) =====================

    /** Detalle de ventas: una fila por linea de cada venta (encabezado + item). */
    private Contenido ventaDetalles() throws SQLException {
        List<String> cab = Arrays.asList("Numero_Venta", "Fecha", "Cliente", "Item", "Bodega_Salida", "Lotes_Consumidos",
                "Cantidad", "Precio_Unitario_COP", "Descuento_COP", "Subtotal_COP");
        List<List<String>> filas = new ArrayList<>();
        for (Venta v : ventaRepository.listar()) {
            for (VentaDetalle d : ventaRepository.listarDetalles(v.getIdVenta())) {
                // Paso I: bodega de salida; "No registrado" en ventas historicas sin bodega.
                String bodega = d.getNombreBodegaSalida() == null ? "No registrado" : d.getNombreBodegaSalida();
                // Paso J: lotes consumidos por la linea ("L-001:4 | L-002:6"); "No aplica" si no hubo lote.
                String lotes = lotesConsumidos(d.getIdDetalle());
                filas.add(Arrays.asList(txt(v.getNumeroVenta()), fecha(v.getFecha()),
                        txt(v.getNombreTercero()), txt(d.getNombreItem()), txt(bodega), txt(lotes), cant(d.getCantidad()),
                        dinero(d.getPrecioUnitario()), dinero(d.getDescuentoLinea()), dinero(d.getSubtotalLinea())));
            }
        }
        return new Contenido(cab, filas);
    }

    /** Formatea los lotes consumidos por una linea de venta para el CSV (Paso J). "No aplica" si no hubo. */
    private String lotesConsumidos(int idDetalle) throws SQLException {
        List<com.gestorpyme.domain.model.VentaDetalleLote> consumos = loteRepository.consumosPorDetalle(idDetalle);
        if (consumos.isEmpty()) {
            return "No aplica";
        }
        StringBuilder sb = new StringBuilder();
        for (com.gestorpyme.domain.model.VentaDetalleLote c : consumos) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(c.getNumeroLote()).append(":").append(c.getCantidad().stripTrailingZeros().toPlainString());
        }
        return sb.toString();
    }

    /** Abonos: cobros de ventas a credito (clave tras v0.7.1; el credito vive como abono). */
    private Contenido abonos() throws SQLException {
        List<String> cab = Arrays.asList("Venta", "Cliente", "Fecha", "Medio", "Valor_COP", "Observaciones");
        List<List<String>> filas = new ArrayList<>();
        for (CuentaPorCobrar c : cuentaRepository.listar()) {
            for (Abono a : cuentaRepository.listarAbonos(c.getIdCuenta())) {
                filas.add(Arrays.asList(txt(c.getNumeroVenta()), txt(c.getNombreTercero()),
                        fecha(a.getFecha()), estado(a.getMedioPago()),
                        dinero(a.getValor()), txt(a.getObservaciones())));
            }
        }
        return new Contenido(cab, filas);
    }

    /** Compras / ordenes de compra (cabecera). */
    private Contenido compras() throws SQLException {
        List<String> cab = Arrays.asList("Numero_OC", "Fecha", "Proveedor", "Total_COP", "Estado",
                "Fecha_Estimada", "Observaciones");
        List<List<String>> filas = new ArrayList<>();
        for (OrdenCompra o : ordenCompraRepository.listar()) {
            filas.add(Arrays.asList(txt(o.getNumeroOrden()), fecha(o.getFechaOrden()),
                    txt(o.getNombreProveedor()), dinero(o.getTotal()), estado(o.getEstado()),
                    fecha(o.getFechaEstimada()), txt(o.getObservaciones())));
        }
        return new Contenido(cab, filas);
    }

    /** Recepciones de mercancia. Proveedor y estado se cruzan con la orden por su numero. */
    private Contenido recepciones() throws SQLException {
        List<String> cab = Arrays.asList("Numero_RC", "Numero_OC", "Fecha", "Proveedor", "Estado");
        // Mapa numero_orden -> orden, para resolver proveedor y estado (no estan en la recepcion).
        java.util.Map<String, OrdenCompra> ordenes = new java.util.HashMap<>();
        for (OrdenCompra o : ordenCompraRepository.listar()) {
            ordenes.put(o.getNumeroOrden(), o);
        }
        List<List<String>> filas = new ArrayList<>();
        for (RecepcionMercancia r : recepcionRepository.listar()) {
            OrdenCompra o = ordenes.get(r.getNumeroOrden());
            String proveedor = (o == null) ? "" : txt(o.getNombreProveedor());
            String est = (o == null) ? "" : estado(o.getEstado());
            filas.add(Arrays.asList(txt(r.getNumeroRecepcion()), txt(r.getNumeroOrden()),
                    fecha(r.getFecha()), proveedor, est));
        }
        return new Contenido(cab, filas);
    }

    /**
     * Detalle de recepciones con lote/vencimiento (Paso N). No reemplaza a {@link #recepciones()} (cabeceras):
     * es un tipo nuevo (DETALLE_RECEPCIONES). Las recepciones sin lote exportan los campos de lote vacios.
     */
    private Contenido detalleRecepciones() throws SQLException {
        List<String> cab = Arrays.asList("Numero_RC", "Fecha", "Numero_OC", "Item", "Bodega",
                "Cantidad", "Numero_Lote", "Fecha_Vencimiento", "Id_Lote");
        List<List<String>> filas = new ArrayList<>();
        for (com.gestorpyme.domain.model.RecepcionDetalleExport d : recepcionRepository.listarDetalleExport()) {
            String idLote = d.getIdLote() == null ? "" : String.valueOf(d.getIdLote());
            filas.add(Arrays.asList(
                    txt(d.getNumeroRecepcion()),
                    fecha(d.getFecha()),
                    txt(d.getNumeroOrden()),
                    txt(d.getNombreItem()),
                    txt(d.getNombreBodega()),
                    cant(d.getCantidadRecibida()),
                    txt(d.getNumeroLote()),
                    fecha(d.getFechaVencimiento()),
                    idLote));
        }
        return new Contenido(cab, filas);
    }

    /**
     * Exportación gerencial del Dashboard Ejecutivo 360 (Paso R). <b>Solo lectura</b>: reutiliza
     * {@code DashboardService.resumenEjecutivo} (los mismos cálculos del Dashboard) y no recalcula
     * nada distinto. El período es el <b>año actual</b> por defecto (mes y semana nulos), coherente con
     * la vista; la etiqueta del período se incluye en la sección "Periodo". Formato Seccion;Indicador;
     * Valor;Detalle. No reemplaza ni altera otras exportaciones (es un tipo nuevo, DASHBOARD_GERENCIAL).
     */
    private Contenido dashboardGerencial() throws SQLException {
        int anio = java.time.LocalDate.now().getYear();
        var r = dashboardService.resumenEjecutivo(anio, null, null);
        List<String> cab = Arrays.asList("Seccion", "Indicador", "Valor", "Detalle");
        List<List<String>> filas = new ArrayList<>();

        // A. Periodo
        filas.add(fila("Periodo", "Etiqueta", txt(r.getEtiquetaPeriodo()), ""));
        filas.add(fila("Periodo", "Fecha inicio", fecha(r.getFechaInicio()), ""));
        filas.add(fila("Periodo", "Fecha fin", fecha(r.getFechaFin()), ""));

        // B. Financiero / Comercial
        var f = r.getFinanciero();
        if (f != null) {
            String s = "Financiero / Comercial";
            boolean margen = f.isMargenDisponible() && f.getMargenEstimado() != null;
            filas.add(fila(s, "Ventas del periodo", dinero(f.getVentas()), ""));
            filas.add(fila(s, "Cantidad de ventas", ent(f.getCantidadVentas()), ""));
            filas.add(fila(s, "Ticket promedio", dinero(f.getTicketPromedio()), ""));
            filas.add(fila(s, "Utilidad estimada", dinero(f.getUtilidadEstimada()),
                    "Base: " + dinero(f.getBaseVentasConCosto())));
            filas.add(fila(s, "Margen estimado",
                    margen ? pct(f.getMargenEstimado().doubleValue()) : "No disponible",
                    margen ? "Bruto estimado" : "Sin base con costo"));
            filas.add(fila(s, "Base ventas con costo", dinero(f.getBaseVentasConCosto()), ""));
            filas.add(fila(s, "Lineas con costo", ent(f.getLineasConCosto()), ""));
            filas.add(fila(s, "Lineas sin costo", ent(f.getLineasSinCosto()), ""));
            filas.add(fila(s, "Descuentos", dinero(f.getDescuentos()), ""));
            filas.add(fila(s, "Ventas contado", dinero(f.getVentasContado()), ""));
            filas.add(fila(s, "Ventas credito", dinero(f.getVentasCredito()), ""));
            filas.add(fila(s, "Pagos recibidos", dinero(f.getPagosRecibidos()), ""));
            filas.add(fila(s, "Cartera pendiente", dinero(f.getCarteraPendiente()), ""));
        }

        // C. Inventario
        var inv = r.getInventario();
        if (inv != null) {
            String s = "Inventario";
            filas.add(fila(s, "Productos bajo stock", ent(inv.getProductosBajoStock()), ""));
            filas.add(fila(s, "Productos sin stock", ent(inv.getProductosSinStock()), ""));
            filas.add(fila(s, "Items con stock maximo", ent(inv.getItemsStockMaximo()), ""));
            filas.add(fila(s, "Items con proveedor preferido", ent(inv.getItemsProveedorPreferido()), ""));
            filas.add(fila(s, "Reabastecimiento sugerido", cant(inv.getSugeridoTotal()), ""));
            filas.add(fila(s, "Valor inventario estimado", dinero(inv.getValorInventarioEstimado()), ""));
        }

        // D. Compras
        var co = r.getCompras();
        if (co != null) {
            String s = "Compras";
            filas.add(fila(s, "Total ordenado", dinero(co.getTotalOrdenado()), ""));
            filas.add(fila(s, "Total recibido", dinero(co.getTotalRecibido()), ""));
            filas.add(fila(s, "Ordenes pendientes", ent(co.getOrdenesPendientes()), ""));
            filas.add(fila(s, "Ordenes recibidas", ent(co.getOrdenesRecibidas()), ""));
            filas.add(fila(s, "Proveedor top", txt(co.getProveedorTopNombre()),
                    dinero(co.getProveedorTopValor())));
        }

        // E. Lotes
        var lo = r.getLotes();
        if (lo != null) {
            String s = "Lotes";
            filas.add(fila(s, "Proximos a vencer", ent(lo.getProximosAVencer()), ""));
            filas.add(fila(s, "Vencidos", ent(lo.getVencidos()), ""));
            filas.add(fila(s, "Agotados", ent(lo.getAgotados()), ""));
            filas.add(fila(s, "Sin fecha", ent(lo.getSinFecha()), ""));
            filas.add(fila(s, "Valor en riesgo estimado", dinero(lo.getValorEnRiesgoEstimado()), ""));
        }

        // F. Operacion
        var op = r.getOperativo();
        if (op != null) {
            String s = "Operacion";
            filas.add(fila(s, "Movimientos Kardex", ent(op.getMovimientosKardex()), ""));
            filas.add(fila(s, "Salidas por venta", ent(op.getSalidasVenta()), ""));
            filas.add(fila(s, "Entradas por compra", ent(op.getEntradasCompra()), ""));
            filas.add(fila(s, "Ajustes manuales", ent(op.getAjustesManuales()), ""));
            filas.add(fila(s, "Producto top", txt(op.getTopProductoNombre()),
                    cant(op.getTopProductoCantidad())));
            filas.add(fila(s, "Bodega mas activa", txt(op.getBodegaMayorMovimientoNombre()),
                    ent(op.getBodegaMayorMovimientoCantidad()) + " movimientos"));
        }

        // G. Metas (separadas de los datos reales)
        var cm = r.getComparativoMeta();
        String sm = "Metas";
        if (cm != null && cm.isHayMeta()) {
            filas.add(fila(sm, "Meta ventas", dinero(cm.getMetaVentas()), ""));
            filas.add(fila(sm, "Cumplimiento ventas", pct(cm.getCumplimientoVentas()), ""));
            filas.add(fila(sm, "Meta utilidad", dinero(cm.getMetaUtilidad()), ""));
            filas.add(fila(sm, "Cumplimiento utilidad", pct(cm.getCumplimientoUtilidad()), ""));
            if (cm.getComentario() != null && !cm.getComentario().trim().isEmpty()) {
                filas.add(fila(sm, "Comentario", txt(cm.getComentario()), ""));
            }
        } else {
            filas.add(fila(sm, "Estado", "Sin meta definida para el periodo", ""));
        }

        return new Contenido(cab, filas);
    }

    /** Construye una fila gerencial (Seccion; Indicador; Valor; Detalle). */
    private static List<String> fila(String seccion, String indicador, String valor, String detalle) {
        return Arrays.asList(seccion, indicador, valor, detalle);
    }

    /** Formatea un conteo entero para CSV. */
    private static String ent(long valor) {
        return ExportValueFormatter.formatIntegerForCsv(valor);
    }

    /** Formatea una proporción (0..1) como porcentaje; vacío si es nula. */
    private static String pct(Double proporcion) {
        return proporcion == null ? "" : com.gestorpyme.util.MoneyFormatter.porcentaje(proporcion);
    }

    /** Reabastecimiento: usa el servicio logistico (calcula sugerido y estado logistico). */
    private Contenido reabastecimiento() throws SQLException {
        List<String> cab = Arrays.asList("Codigo_Item", "Item", "Stock_Actual", "Stock_Minimo",
                "Stock_Maximo", "En_Pedido", "Sugerido", "Proveedor_Preferido", "Estado_Logistico");
        List<List<String>> filas = new ArrayList<>();
        for (ItemLogistico i : inventarioLogisticoService.listar()) {
            // Paso F: stock maximo vacio si es null; proveedor preferido como texto (vacio si null).
            String maximo = i.getStockMaximo() == null ? "" : cant(i.getStockMaximo());
            filas.add(Arrays.asList(txt(i.getCodigo()), txt(i.getNombre()),
                    cant(i.getStockActual()), cant(i.getStockMinimo()),
                    maximo, cant(i.getEnPedido()), cant(i.getSugerido()),
                    txt(i.getNombreProveedorPreferido()), txt(i.getEstado())));
        }
        return new Contenido(cab, filas);
    }

    /** Lotes y vencimientos. */
    private Contenido lotes() throws SQLException {
        List<String> cab = Arrays.asList("Codigo_Item", "Item", "Bodega", "Lote", "Fecha_Ingreso",
                "Fecha_Vencimiento", "Dias_Para_Vencer", "Cantidad", "Cantidad_Disponible", "Estado", "Estado_Vencimiento");
        List<List<String>> filas = new ArrayList<>();
        for (Lote l : loteRepository.listar()) {
            // Dias para vencer: vacio si no hay fecha; numero (puede ser negativo) si la hay.
            Long dias = l.diasParaVencer();
            String diasTxt = dias == null ? "" : String.valueOf(dias);
            filas.add(Arrays.asList(txt(l.getCodigoItem()), txt(l.getNombreItem()), txt(l.getNombreBodega()),
                    txt(l.getNumeroLote()), fecha(l.getFechaIngreso()), fecha(l.getFechaVencimiento()),
                    diasTxt, cant(l.getCantidadInicial()), cant(l.getCantidadDisponible()),
                    estado(l.getEstado()), txt(l.estadoVencimiento())));
        }
        return new Contenido(cab, filas);
    }

    private void registrar(TipoExportacion tipo, String ruta, Integer idUsuario, EstadoExportacion estado)
            throws SQLException {
        ExportacionLog log = new ExportacionLog();
        log.setTipo(tipoParaLog(tipo));
        log.setRutaArchivo(ruta);
        log.setIdUsuario(idUsuario);
        log.setEstado(estado);
        exportacionRepository.registrar(log);
    }

    /**
     * Adapta el tipo al conjunto que admite la tabla exportaciones_log (su CHECK es previo a
     * v0.8.2). Los tipos nuevos (Proveedores, Detalle de ventas, Abonos, Compras, Recepciones,
     * Reabastecimiento, Lotes) se registran como OTRO para no requerir una migracion. El
     * archivo CSV exportado SI usa el tipo real; solo el registro historico se generaliza.
     */
    private static TipoExportacion tipoParaLog(TipoExportacion tipo) {
        switch (tipo) {
            case CLIENTES:
            case PROSPECTOS:
            case ITEMS:
            case VENTAS:
            case PAGOS:
            case INVENTARIO:
            case KARDEX:
            case CARTERA:
            case CRM:
                return tipo;
            default:
                return TipoExportacion.OTRO;
        }
    }

    /** Igual que registrar pero sin propagar errores (para el camino de fallo). */
    private void registrarSilencioso(TipoExportacion tipo, String ruta, Integer idUsuario, EstadoExportacion estado) {
        try {
            registrar(tipo, ruta, idUsuario, estado);
        } catch (SQLException ignore) {
            // No se interrumpe el reporte del error original.
        }
    }

    private static String txt(String v) {
        return ExportValueFormatter.formatTextForCsv(v);
    }

    /** Monto para CSV: numero limpio sin '$' ni miles, sin decimales innecesarios. */
    private static String dinero(BigDecimal v) {
        return ExportValueFormatter.formatMoneyForCsv(v);
    }

    /** Cantidad para CSV: numero limpio, coma decimal solo si aplica (85, 85,5). */
    private static String cant(BigDecimal v) {
        return ExportValueFormatter.formatQuantityForCsv(v);
    }

    /** Fecha para CSV normalizada a ISO (yyyy-MM-dd, o yyyy-MM-dd HH:mm si trae hora). */
    private static String fecha(String v) {
        return ExportValueFormatter.formatDateForCsv(v);
    }

    /** Estado para CSV: etiqueta legible del enum (no el name() crudo). */
    private static String estado(Object v) {
        return ExportValueFormatter.formatEstadoForCsv(v);
    }
}
