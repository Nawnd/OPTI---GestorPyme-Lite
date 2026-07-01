package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoCuenta;
import com.gestorpyme.domain.enums.TipoDrillDown;
import com.gestorpyme.domain.enums.EstadoVenta;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.ComprasPeriodoResumen;
import com.gestorpyme.domain.model.DashboardChartSegment;
import com.gestorpyme.domain.model.DashboardComprasResumen;
import com.gestorpyme.domain.model.DashboardDrillDownItem;
import com.gestorpyme.domain.model.DashboardFinancieroResumen;
import com.gestorpyme.domain.model.DashboardInventarioResumen;
import com.gestorpyme.domain.model.DashboardLotesResumen;
import com.gestorpyme.domain.model.DashboardMeta;
import com.gestorpyme.domain.model.DashboardOperativoResumen;
import com.gestorpyme.domain.model.DashboardResumen;
import com.gestorpyme.domain.model.LoteVencimientoItem;
import com.gestorpyme.domain.model.PagoReciente;
import com.gestorpyme.domain.model.StockBajoItem;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceso a datos del Dashboard. Encapsula todas las consultas de indicadores y
 * listados (capa repository). Adapta las consultas al esquema real v0.2:
 * ventas, pagos, cuentas_por_cobrar, abonos_cuenta, items, inventario_stock, lotes,
 * terceros. No contiene logica de presentacion.
 */
public class DashboardRepository {

    /** Construye el resumen de KPIs con varias consultas de agregacion. */
    public DashboardResumen obtenerResumen() throws SQLException {
        DashboardResumen r = new DashboardResumen();
        try (Connection conn = DatabaseConnection.getConnection()) {
            // Ventas de hoy (cantidad y total), excluyendo anuladas.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) c, COALESCE(SUM(total),0) s FROM ventas "
                            + "WHERE date(fecha)=date('now','localtime') AND estado<>'ANULADA'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    r.setCantidadVentasHoy(rs.getLong("c"));
                    r.setTotalVentasHoy(BigDecimal.valueOf(rs.getDouble("s")));
                }
            }
            // Ventas del mes actual.
            r.setTotalVentasMes(escalar(conn,
                    "SELECT COALESCE(SUM(total),0) FROM ventas "
                            + "WHERE strftime('%Y-%m',fecha)=strftime('%Y-%m','now','localtime') AND estado<>'ANULADA'"));
            // Cartera pendiente (saldo de cuentas no pagadas ni canceladas).
            r.setCarteraPendiente(escalar(conn,
                    "SELECT COALESCE(SUM(saldo_pendiente),0) FROM cuentas_por_cobrar "
                            + "WHERE estado IN ('PENDIENTE','ABONADA','VENCIDA')"));
            // Clientes / prospectos activos.
            r.setClientesProspectos(contar(conn,
                    "SELECT COUNT(*) FROM terceros WHERE tipo_tercero IN ('CLIENTE','PROSPECTO') AND estado='ACTIVO'"));
            // Productos con stock total por debajo del minimo.
            r.setProductosStockBajo(contar(conn,
                    "SELECT COUNT(*) FROM items i WHERE i.controla_inventario=1 AND i.estado='ACTIVO' "
                            + "AND i.stock_minimo>0 AND COALESCE("
                            + "(SELECT SUM(s.cantidad) FROM inventario_stock s WHERE s.id_item=i.id_item),0) < i.stock_minimo"));
            // Cuentas por cobrar pendientes.
            r.setCuentasPendientes(contar(conn,
                    "SELECT COUNT(*) FROM cuentas_por_cobrar WHERE estado IN ('PENDIENTE','ABONADA','VENCIDA')"));
            // Compras pendientes: ordenes emitidas o parcialmente recibidas (por recibir).
            r.setComprasPendientes(contar(conn,
                    "SELECT COUNT(*) FROM ordenes_compra WHERE estado IN ('EMITIDA','PARCIALMENTE_RECIBIDA')"));
            // Totales de credito para el porcentaje de cartera recuperada.
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(valor_total),0) t, COALESCE(SUM(valor_pagado),0) p "
                            + "FROM cuentas_por_cobrar WHERE estado<>'CANCELADA'");
                 ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    r.setCreditoTotal(BigDecimal.valueOf(rs.getDouble("t")));
                    r.setCreditoPagado(BigDecimal.valueOf(rs.getDouble("p")));
                }
            }
        }
        return r;
    }

    /** Ultimas ventas (mas recientes primero). */
    public List<Venta> ultimasVentas(int limite) throws SQLException {
        String sql = "SELECT v.id_venta, v.numero_venta, v.id_tercero, t.nombre tercero_nombre, "
                   + "v.fecha, v.total, v.estado "
                   + "FROM ventas v LEFT JOIN terceros t ON t.id_tercero=v.id_tercero "
                   + "ORDER BY v.id_venta DESC LIMIT ?";
        List<Venta> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Venta v = new Venta();
                    v.setIdVenta(rs.getInt("id_venta"));
                    v.setNumeroVenta(rs.getString("numero_venta"));
                    int idT = rs.getInt("id_tercero");
                    v.setIdTercero(rs.wasNull() ? null : idT);
                    v.setNombreTercero(rs.getString("tercero_nombre"));
                    v.setFecha(rs.getString("fecha"));
                    v.setTotal(BigDecimal.valueOf(rs.getDouble("total")));
                    v.setEstado(EstadoVenta.desde(rs.getString("estado")));
                    lista.add(v);
                }
            }
        }
        return lista;
    }

    /** Ultimos pagos registrados (con numero de venta y cliente). */
    public List<PagoReciente> ultimosPagos(int limite) throws SQLException {
        String sql = "SELECT p.fecha, p.medio_pago, p.valor, v.numero_venta, t.nombre tercero_nombre "
                   + "FROM pagos p JOIN ventas v ON v.id_venta=p.id_venta "
                   + "LEFT JOIN terceros t ON t.id_tercero=v.id_tercero "
                   + "ORDER BY p.id_pago DESC LIMIT ?";
        List<PagoReciente> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PagoReciente p = new PagoReciente();
                    p.setFecha(rs.getString("fecha"));
                    p.setMedioPago(MedioPago.desde(rs.getString("medio_pago")));
                    p.setValor(BigDecimal.valueOf(rs.getDouble("valor")));
                    p.setNumeroVenta(rs.getString("numero_venta"));
                    p.setNombreTercero(rs.getString("tercero_nombre"));
                    lista.add(p);
                }
            }
        }
        return lista;
    }

    /** Productos con stock por debajo del minimo, por bodega. */
    public List<StockBajoItem> stockBajo(int limite) throws SQLException {
        String sql = "SELECT i.nombre item_nombre, b.nombre bodega_nombre, s.cantidad, i.stock_minimo "
                   + "FROM inventario_stock s "
                   + "JOIN items i ON i.id_item=s.id_item "
                   + "JOIN bodegas b ON b.id_bodega=s.id_bodega "
                   + "WHERE i.controla_inventario=1 AND i.stock_minimo>0 AND s.cantidad < i.stock_minimo "
                   + "ORDER BY (s.cantidad - i.stock_minimo) ASC LIMIT ?";
        List<StockBajoItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StockBajoItem s = new StockBajoItem();
                    s.setNombreItem(rs.getString("item_nombre"));
                    s.setNombreBodega(rs.getString("bodega_nombre"));
                    s.setCantidad(BigDecimal.valueOf(rs.getDouble("cantidad")));
                    s.setStockMinimo(BigDecimal.valueOf(rs.getDouble("stock_minimo")));
                    lista.add(s);
                }
            }
        }
        return lista;
    }

    /** Cuentas por cobrar pendientes (ordenadas por vencimiento, nulos al final). */
    public List<CuentaPorCobrar> cuentasPendientes(int limite) throws SQLException {
        String sql = "SELECT c.id_cuenta, c.id_venta, v.numero_venta, c.id_tercero, t.nombre tercero_nombre, "
                   + "c.valor_total, c.valor_pagado, c.saldo_pendiente, c.fecha_vencimiento, c.estado "
                   + "FROM cuentas_por_cobrar c "
                   + "JOIN ventas v ON v.id_venta=c.id_venta "
                   + "JOIN terceros t ON t.id_tercero=c.id_tercero "
                   + "WHERE c.estado IN ('PENDIENTE','ABONADA','VENCIDA') "
                   + "ORDER BY (c.fecha_vencimiento IS NULL), c.fecha_vencimiento ASC LIMIT ?";
        List<CuentaPorCobrar> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CuentaPorCobrar c = new CuentaPorCobrar();
                    c.setIdCuenta(rs.getInt("id_cuenta"));
                    c.setIdVenta(rs.getInt("id_venta"));
                    c.setNumeroVenta(rs.getString("numero_venta"));
                    c.setIdTercero(rs.getInt("id_tercero"));
                    c.setNombreTercero(rs.getString("tercero_nombre"));
                    c.setValorTotal(BigDecimal.valueOf(rs.getDouble("valor_total")));
                    c.setValorPagado(BigDecimal.valueOf(rs.getDouble("valor_pagado")));
                    c.setSaldoPendiente(BigDecimal.valueOf(rs.getDouble("saldo_pendiente")));
                    c.setFechaVencimiento(rs.getString("fecha_vencimiento"));
                    c.setEstado(EstadoCuenta.desde(rs.getString("estado")));
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    /** Lotes activos con vencimiento dentro de los proximos {@code dias} dias. */
    public List<LoteVencimientoItem> lotesPorVencer(int dias, int limite) throws SQLException {
        String sql = "SELECT i.nombre item_nombre, l.numero_lote, l.fecha_vencimiento "
                   + "FROM lotes l JOIN items i ON i.id_item=l.id_item "
                   + "WHERE l.fecha_vencimiento IS NOT NULL AND l.estado='ACTIVO' "
                   + "AND date(l.fecha_vencimiento) <= date('now','localtime','+' || ? || ' day') "
                   + "ORDER BY l.fecha_vencimiento ASC LIMIT ?";
        List<LoteVencimientoItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dias);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LoteVencimientoItem l = new LoteVencimientoItem();
                    l.setNombreItem(rs.getString("item_nombre"));
                    l.setNumeroLote(rs.getString("numero_lote"));
                    l.setFechaVencimiento(rs.getString("fecha_vencimiento"));
                    lista.add(l);
                }
            }
        }
        return lista;
    }

    /**
     * Totales de venta por dia de los ultimos {@code dias} dias, como mapa
     * (clave ISO yyyy-MM-dd -> total). El servicio rellena los dias sin ventas.
     */
    public Map<String, BigDecimal> ventasPorDia(int dias) throws SQLException {
        String sql = "SELECT date(fecha) d, COALESCE(SUM(total),0) s FROM ventas "
                   + "WHERE date(fecha) >= date('now','localtime','-' || ? || ' day') AND estado<>'ANULADA' "
                   + "GROUP BY date(fecha)";
        Map<String, BigDecimal> mapa = new LinkedHashMap<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, dias - 1);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    mapa.put(rs.getString("d"), BigDecimal.valueOf(rs.getDouble("s")));
                }
            }
        }
        return mapa;
    }

    // ----------------------- helpers -----------------------

    /**
     * Indicadores de compras de un periodo (Paso G). Solo lectura.
     *
     * Definiciones (documentadas): "ordenado" = total de ordenes efectivamente emitidas
     * (estados EMITIDA / PARCIALMENTE_RECIBIDA / RECIBIDA; se excluyen BORRADOR y CANCELADA),
     * filtradas por fecha de orden; "recibido" = valor de la mercancia recibida en el periodo
     * (cantidad_recibida x precio_unitario de la linea de OC), filtrado por fecha de recepcion.
     * No se reporta "pagado": no existe modulo de pagos a proveedores.
     *
     * @param anio año a consultar (p. ej. 2026).
     * @param mes  mes 1..12, o {@code null} para "todos los meses" del año.
     * @return resumen de compras del periodo (el promedio por orden lo calcula el servicio).
     */
    public ComprasPeriodoResumen comprasPeriodo(int anio, Integer mes) throws SQLException {
        ComprasPeriodoResumen r = new ComprasPeriodoResumen();
        String anioStr = String.valueOf(anio);
        boolean filtraMes = mes != null;
        String mesStr = filtraMes ? String.format("%02d", mes) : null;
        String emitidas = "('EMITIDA','PARCIALMENTE_RECIBIDA','RECIBIDA')";

        try (Connection conn = DatabaseConnection.getConnection()) {
            r.setTotalOrdenado(escalarPeriodo(conn,
                    "SELECT COALESCE(SUM(total),0) FROM ordenes_compra WHERE estado IN " + emitidas
                    + periodo("fecha_orden", filtraMes), anioStr, mesStr));
            r.setCantidadOrdenes(contarPeriodo(conn,
                    "SELECT COUNT(*) FROM ordenes_compra WHERE estado IN " + emitidas
                    + periodo("fecha_orden", filtraMes), anioStr, mesStr));
            r.setOrdenesPendientes(contarPeriodo(conn,
                    "SELECT COUNT(*) FROM ordenes_compra WHERE estado IN ('EMITIDA','PARCIALMENTE_RECIBIDA')"
                    + periodo("fecha_orden", filtraMes), anioStr, mesStr));
            r.setOrdenesRecibidas(contarPeriodo(conn,
                    "SELECT COUNT(*) FROM ordenes_compra WHERE estado = 'RECIBIDA'"
                    + periodo("fecha_orden", filtraMes), anioStr, mesStr));
            // Valor recibido: une recepciones con el detalle de la OC para tomar el precio unitario.
            r.setTotalRecibido(escalarPeriodo(conn,
                    "SELECT COALESCE(SUM(rd.cantidad_recibida * ocd.precio_unitario),0) "
                    + "FROM recepciones_detalles rd "
                    + "JOIN recepciones_mercancia rm ON rm.id_recepcion = rd.id_recepcion "
                    + "JOIN ordenes_compra_detalles ocd ON ocd.id_detalle = rd.id_detalle_oc "
                    + "WHERE 1 = 1" + periodo("rm.fecha", filtraMes), anioStr, mesStr));
            // Proveedor con mayor valor comprado en el periodo.
            String sqlTop = "SELECT t.nombre, COALESCE(SUM(oc.total),0) v "
                    + "FROM ordenes_compra oc JOIN terceros t ON t.id_tercero = oc.id_proveedor "
                    + "WHERE oc.estado IN " + emitidas + periodo("oc.fecha_orden", filtraMes)
                    + " GROUP BY oc.id_proveedor ORDER BY v DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlTop)) {
                bindPeriodo(ps, anioStr, mesStr);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.setProveedorTopNombre(rs.getString(1));
                        r.setProveedorTopValor(BigDecimal.valueOf(rs.getDouble(2)));
                    }
                }
            }
        }
        return r;
    }

    /** Fragmento de filtro por periodo sobre una columna de fecha (año y, opcionalmente, mes). */
    private static String periodo(String columnaFecha, boolean filtraMes) {
        String f = " AND strftime('%Y', " + columnaFecha + ") = ?";
        if (filtraMes) {
            f += " AND strftime('%m', " + columnaFecha + ") = ?";
        }
        return f;
    }

    /** Vincula los parametros del periodo (año y, si aplica, mes) en orden. */
    private static void bindPeriodo(PreparedStatement ps, String anioStr, String mesStr) throws SQLException {
        ps.setString(1, anioStr);
        if (mesStr != null) {
            ps.setString(2, mesStr);
        }
    }

    private BigDecimal escalarPeriodo(Connection conn, String sql, String anioStr, String mesStr) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPeriodo(ps, anioStr, mesStr);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
            }
        }
    }

    private long contarPeriodo(Connection conn, String sql, String anioStr, String mesStr) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindPeriodo(ps, anioStr, mesStr);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private BigDecimal escalar(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
        }
    }

    private long contar(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    // ======================= Paso K: Dashboard Ejecutivo 360 =======================
    // Las consultas por período usan rango de fechas ISO [ini..fin] (soporta semanas operativas).
    // Las KPIs de inventario y lotes son foto ACTUAL (estado puntual), no del período.

    /** KPIs financieros del período. ticket y margen los completa el servicio (guardas de división). */
    public DashboardFinancieroResumen financiero(String ini, String fin) throws SQLException {
        DashboardFinancieroResumen r = new DashboardFinancieroResumen();
        String rango = " AND date(fecha) BETWEEN date(?) AND date(?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            r.setVentas(sumaRango(conn,
                    "SELECT COALESCE(SUM(total),0) FROM ventas WHERE estado<>'ANULADA'" + rango, ini, fin));
            r.setCantidadVentas(contarRango(conn,
                    "SELECT COUNT(*) FROM ventas WHERE estado<>'ANULADA'" + rango, ini, fin));
            r.setDescuentos(sumaRango(conn,
                    "SELECT COALESCE(SUM(descuento),0) FROM ventas WHERE estado<>'ANULADA'" + rango, ini, fin));
            // Crédito: ventas del período que generaron cuenta por cobrar. Contado = ventas - crédito.
            BigDecimal credito = sumaRango(conn,
                    "SELECT COALESCE(SUM(v.total),0) FROM ventas v WHERE v.estado<>'ANULADA'"
                    + " AND date(v.fecha) BETWEEN date(?) AND date(?)"
                    + " AND EXISTS (SELECT 1 FROM cuentas_por_cobrar c WHERE c.id_venta = v.id_venta)", ini, fin);
            r.setVentasCredito(credito);
            r.setVentasContado(r.getVentas().subtract(credito));
            // Pagos recibidos en el período: pagos de contado + abonos a crédito (por su fecha).
            BigDecimal pagos = sumaRango(conn,
                    "SELECT COALESCE(SUM(valor),0) FROM pagos WHERE date(fecha) BETWEEN date(?) AND date(?)", ini, fin);
            BigDecimal abonos = sumaRango(conn,
                    "SELECT COALESCE(SUM(valor),0) FROM abonos_cuenta WHERE date(fecha) BETWEEN date(?) AND date(?)", ini, fin);
            r.setPagosRecibidos(pagos.add(abonos));
            // Cartera pendiente: saldo ACTUAL (no del período).
            r.setCarteraPendiente(escalar(conn,
                    "SELECT COALESCE(SUM(saldo_pendiente),0) FROM cuentas_por_cobrar "
                    + "WHERE estado IN ('PENDIENTE','ABONADA','VENCIDA')"));
            // Utilidad bruta ESTIMADA: solo líneas inventariables con precio_compra > 0.
            // base = subtotal de esas líneas (denominador del margen); se cuentan las excluidas.
            String sqlUtil = "SELECT "
                    + "COALESCE(SUM(CASE WHEN it.controla_inventario=1 AND it.precio_compra>0 "
                    + "  THEN (vd.precio_unitario - it.precio_compra)*vd.cantidad - vd.descuento_linea ELSE 0 END),0) AS utilidad, "
                    + "COALESCE(SUM(CASE WHEN it.controla_inventario=1 AND it.precio_compra>0 "
                    + "  THEN vd.subtotal_linea ELSE 0 END),0) AS base, "
                    + "COALESCE(SUM(CASE WHEN it.controla_inventario=1 AND it.precio_compra>0 THEN 1 ELSE 0 END),0) AS con_costo, "
                    + "COALESCE(SUM(CASE WHEN NOT (it.controla_inventario=1 AND it.precio_compra>0) THEN 1 ELSE 0 END),0) AS sin_costo "
                    + "FROM venta_detalles vd "
                    + "JOIN ventas v ON v.id_venta = vd.id_venta "
                    + "JOIN items it ON it.id_item = vd.id_item "
                    + "WHERE v.estado<>'ANULADA' AND date(v.fecha) BETWEEN date(?) AND date(?)";
            try (PreparedStatement ps = conn.prepareStatement(sqlUtil)) {
                ps.setString(1, ini);
                ps.setString(2, fin);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.setUtilidadEstimada(BigDecimal.valueOf(rs.getDouble("utilidad")));
                        r.setBaseVentasConCosto(BigDecimal.valueOf(rs.getDouble("base")));
                        r.setLineasConCosto(rs.getLong("con_costo"));
                        r.setLineasSinCosto(rs.getLong("sin_costo"));
                    }
                }
            }
        }
        return r;
    }

    /** KPIs de inventario (foto actual). */
    public DashboardInventarioResumen inventario() throws SQLException {
        DashboardInventarioResumen r = new DashboardInventarioResumen();
        String totalStock = "IFNULL((SELECT SUM(cantidad) FROM inventario_stock s WHERE s.id_item=it.id_item),0)";
        String enPedido = "IFNULL((SELECT SUM(ocd.cantidad_solicitada - ocd.cantidad_recibida) "
                + "FROM ordenes_compra_detalles ocd JOIN ordenes_compra oc ON oc.id_orden=ocd.id_orden "
                + "WHERE ocd.id_item=it.id_item AND oc.estado IN ('EMITIDA','PARCIALMENTE_RECIBIDA')),0)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            r.setProductosSinStock(contar(conn,
                    "SELECT COUNT(*) FROM items it WHERE it.controla_inventario=1 AND it.estado='ACTIVO' "
                    + "AND " + totalStock + " <= 0"));
            r.setProductosBajoStock(contar(conn,
                    "SELECT COUNT(*) FROM items it WHERE it.controla_inventario=1 AND it.estado='ACTIVO' "
                    + "AND " + totalStock + " > 0 AND " + totalStock + " < it.stock_minimo"));
            r.setItemsStockMaximo(contar(conn,
                    "SELECT COUNT(*) FROM items WHERE stock_maximo IS NOT NULL"));
            r.setItemsProveedorPreferido(contar(conn,
                    "SELECT COUNT(*) FROM items WHERE id_proveedor_preferido IS NOT NULL"));
            // Sugerido total (neto): MAX(0, mínimo - actual - en pedido) sobre ítems inventariables.
            r.setSugeridoTotal(escalar(conn,
                    "SELECT COALESCE(SUM(CASE WHEN (it.stock_minimo - " + totalStock + " - " + enPedido + ") > 0 "
                    + "THEN (it.stock_minimo - " + totalStock + " - " + enPedido + ") ELSE 0 END),0) "
                    + "FROM items it WHERE it.controla_inventario=1 AND it.estado='ACTIVO'"));
            r.setValorInventarioEstimado(escalar(conn,
                    "SELECT COALESCE(SUM(s.cantidad * it.precio_compra),0) "
                    + "FROM inventario_stock s JOIN items it ON it.id_item=s.id_item WHERE it.precio_compra>0"));
        }
        return r;
    }

    /** KPIs de compras del período (ordenado por fecha de orden; recibido por fecha de recepción). */
    public DashboardComprasResumen comprasRango(String ini, String fin) throws SQLException {
        DashboardComprasResumen r = new DashboardComprasResumen();
        String emitidas = "('EMITIDA','PARCIALMENTE_RECIBIDA','RECIBIDA')";
        String rangoOrden = " AND date(fecha_orden) BETWEEN date(?) AND date(?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            r.setTotalOrdenado(sumaRango(conn,
                    "SELECT COALESCE(SUM(total),0) FROM ordenes_compra WHERE estado IN " + emitidas + rangoOrden, ini, fin));
            r.setOrdenesPendientes(contarRango(conn,
                    "SELECT COUNT(*) FROM ordenes_compra WHERE estado IN ('EMITIDA','PARCIALMENTE_RECIBIDA')" + rangoOrden, ini, fin));
            r.setOrdenesRecibidas(contarRango(conn,
                    "SELECT COUNT(*) FROM ordenes_compra WHERE estado='RECIBIDA'" + rangoOrden, ini, fin));
            r.setTotalRecibido(sumaRango(conn,
                    "SELECT COALESCE(SUM(rd.cantidad_recibida * ocd.precio_unitario),0) "
                    + "FROM recepciones_detalles rd "
                    + "JOIN recepciones_mercancia rm ON rm.id_recepcion = rd.id_recepcion "
                    + "JOIN ordenes_compra_detalles ocd ON ocd.id_detalle = rd.id_detalle_oc "
                    + "WHERE date(rm.fecha) BETWEEN date(?) AND date(?)", ini, fin));
            String sqlTop = "SELECT t.nombre, COALESCE(SUM(oc.total),0) v "
                    + "FROM ordenes_compra oc JOIN terceros t ON t.id_tercero = oc.id_proveedor "
                    + "WHERE oc.estado IN " + emitidas + " AND date(oc.fecha_orden) BETWEEN date(?) AND date(?) "
                    + "GROUP BY oc.id_proveedor ORDER BY v DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlTop)) {
                ps.setString(1, ini);
                ps.setString(2, fin);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.setProveedorTopNombre(rs.getString(1));
                        r.setProveedorTopValor(BigDecimal.valueOf(rs.getDouble(2)));
                    }
                }
            }
        }
        return r;
    }

    /** KPIs de lotes (foto actual de vencimientos). */
    public DashboardLotesResumen lotes() throws SQLException {
        DashboardLotesResumen r = new DashboardLotesResumen();
        String hoy = "date('now','localtime')";
        String hoy30 = "date('now','localtime','+30 day')";
        try (Connection conn = DatabaseConnection.getConnection()) {
            r.setProximosAVencer(contar(conn,
                    "SELECT COUNT(*) FROM lotes WHERE estado='ACTIVO' AND fecha_vencimiento IS NOT NULL "
                    + "AND date(fecha_vencimiento) >= " + hoy + " AND date(fecha_vencimiento) <= " + hoy30));
            r.setVencidos(contar(conn,
                    "SELECT COUNT(*) FROM lotes WHERE estado='ACTIVO' AND fecha_vencimiento IS NOT NULL "
                    + "AND date(fecha_vencimiento) < " + hoy));
            r.setAgotados(contar(conn, "SELECT COUNT(*) FROM lotes WHERE estado='AGOTADO'"));
            r.setSinFecha(contar(conn,
                    "SELECT COUNT(*) FROM lotes WHERE estado='ACTIVO' AND fecha_vencimiento IS NULL"));
            // Valor en riesgo: saldo de lotes ACTIVOS con fecha <= hoy+30 (incluye vencidos), costo > 0.
            r.setValorEnRiesgoEstimado(escalar(conn,
                    "SELECT COALESCE(SUM(IFNULL(l.cantidad_disponible,l.cantidad_inicial)*it.precio_compra),0) "
                    + "FROM lotes l JOIN items it ON it.id_item=l.id_item "
                    + "WHERE it.precio_compra>0 AND l.estado='ACTIVO' AND l.fecha_vencimiento IS NOT NULL "
                    + "AND date(l.fecha_vencimiento) <= " + hoy30));
        }
        return r;
    }

    /** KPIs operativos del período (Kardex). */
    public DashboardOperativoResumen operativo(String ini, String fin) throws SQLException {
        DashboardOperativoResumen r = new DashboardOperativoResumen();
        String rango = " AND date(fecha) BETWEEN date(?) AND date(?)";
        try (Connection conn = DatabaseConnection.getConnection()) {
            r.setMovimientosKardex(contarRango(conn,
                    "SELECT COUNT(*) FROM inventario_movimientos WHERE 1=1" + rango, ini, fin));
            r.setSalidasVenta(contarRango(conn,
                    "SELECT COUNT(*) FROM inventario_movimientos WHERE tipo_movimiento='SALIDA_VENTA'" + rango, ini, fin));
            r.setEntradasCompra(contarRango(conn,
                    "SELECT COUNT(*) FROM inventario_movimientos WHERE tipo_movimiento='ENTRADA_COMPRA'" + rango, ini, fin));
            r.setAjustesManuales(contarRango(conn,
                    "SELECT COUNT(*) FROM inventario_movimientos "
                    + "WHERE tipo_movimiento IN ('AJUSTE_POSITIVO','AJUSTE_NEGATIVO')" + rango, ini, fin));
            // Producto más vendido del período (por cantidad).
            String sqlTop = "SELECT it.nombre, COALESCE(SUM(vd.cantidad),0) q "
                    + "FROM venta_detalles vd JOIN ventas v ON v.id_venta=vd.id_venta "
                    + "JOIN items it ON it.id_item=vd.id_item "
                    + "WHERE v.estado<>'ANULADA' AND date(v.fecha) BETWEEN date(?) AND date(?) "
                    + "GROUP BY vd.id_item ORDER BY q DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlTop)) {
                ps.setString(1, ini); ps.setString(2, fin);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.setTopProductoNombre(rs.getString(1));
                        r.setTopProductoCantidad(BigDecimal.valueOf(rs.getDouble(2)));
                    }
                }
            }
            // Bodega con mayor movimiento del período (por número de movimientos).
            String sqlBod = "SELECT b.nombre, COUNT(*) c FROM inventario_movimientos m "
                    + "JOIN bodegas b ON b.id_bodega=m.id_bodega "
                    + "WHERE date(m.fecha) BETWEEN date(?) AND date(?) GROUP BY m.id_bodega ORDER BY c DESC LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sqlBod)) {
                ps.setString(1, ini); ps.setString(2, fin);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        r.setBodegaMayorMovimientoNombre(rs.getString(1));
                        r.setBodegaMayorMovimientoCantidad(rs.getLong(2));
                    }
                }
            }
        }
        return r;
    }

    // ---------------- Metas gerenciales (dashboard_metas) ----------------

    /**
     * Inserta o actualiza la meta del período (clave anio + mes + semana, con null = agregado superior).
     * No toca datos reales. Devuelve la meta persistida.
     */
    public DashboardMeta guardarMeta(DashboardMeta m) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            Integer idExistente = idMeta(conn, m.getAnio(), m.getMes(), m.getSemana());
            if (idExistente == null) {
                String sql = "INSERT INTO dashboard_metas "
                        + "(anio, mes, semana, meta_ventas, meta_utilidad, meta_margen, comentario, fecha_actualizacion) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, datetime('now','localtime'))";
                try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    bindMeta(ps, m);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            m.setIdMeta(rs.getInt(1));
                        }
                    }
                }
            } else {
                String sql = "UPDATE dashboard_metas SET meta_ventas=?, meta_utilidad=?, meta_margen=?, "
                        + "comentario=?, fecha_actualizacion=datetime('now','localtime') WHERE id_meta=?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setNullableDouble(ps, 1, m.getMetaVentas());
                    setNullableDouble(ps, 2, m.getMetaUtilidad());
                    setNullableDouble(ps, 3, m.getMetaMargen());
                    ps.setString(4, m.getComentario());
                    ps.setInt(5, idExistente);
                    ps.executeUpdate();
                }
                m.setIdMeta(idExistente);
            }
        }
        return m;
    }

    /** Busca la meta del período exacto (anio + mes + semana). Devuelve null si no existe. */
    public DashboardMeta buscarMeta(int anio, Integer mes, Integer semana) throws SQLException {
        String sql = "SELECT id_meta, anio, mes, semana, meta_ventas, meta_utilidad, meta_margen, "
                + "comentario, fecha_actualizacion FROM dashboard_metas "
                + "WHERE anio=? AND " + clausulaNull("mes", mes) + " AND " + clausulaNull("semana", semana);
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, anio);
            if (mes != null) ps.setInt(i++, mes);
            if (semana != null) ps.setInt(i++, semana);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapearMeta(rs) : null;
            }
        }
    }

    /** Lista todas las metas (más recientes primero). */
    public List<DashboardMeta> listarMetas() throws SQLException {
        String sql = "SELECT id_meta, anio, mes, semana, meta_ventas, meta_utilidad, meta_margen, "
                + "comentario, fecha_actualizacion FROM dashboard_metas ORDER BY anio DESC, mes, semana";
        List<DashboardMeta> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapearMeta(rs));
            }
        }
        return lista;
    }

    private Integer idMeta(Connection conn, int anio, Integer mes, Integer semana) throws SQLException {
        String sql = "SELECT id_meta FROM dashboard_metas WHERE anio=? AND "
                + clausulaNull("mes", mes) + " AND " + clausulaNull("semana", semana);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, anio);
            if (mes != null) ps.setInt(i++, mes);
            if (semana != null) ps.setInt(i++, semana);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static String clausulaNull(String col, Integer valor) {
        return valor == null ? col + " IS NULL" : col + "=?";
    }

    private void bindMeta(PreparedStatement ps, DashboardMeta m) throws SQLException {
        ps.setInt(1, m.getAnio());
        if (m.getMes() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, m.getMes());
        if (m.getSemana() == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, m.getSemana());
        setNullableDouble(ps, 4, m.getMetaVentas());
        setNullableDouble(ps, 5, m.getMetaUtilidad());
        setNullableDouble(ps, 6, m.getMetaMargen());
        ps.setString(7, m.getComentario());
    }

    private static void setNullableDouble(PreparedStatement ps, int idx, BigDecimal v) throws SQLException {
        if (v == null) ps.setNull(idx, java.sql.Types.REAL); else ps.setDouble(idx, v.doubleValue());
    }

    private DashboardMeta mapearMeta(ResultSet rs) throws SQLException {
        DashboardMeta m = new DashboardMeta();
        m.setIdMeta(rs.getInt("id_meta"));
        m.setAnio(rs.getInt("anio"));
        int mes = rs.getInt("mes"); m.setMes(rs.wasNull() ? null : mes);
        int sem = rs.getInt("semana"); m.setSemana(rs.wasNull() ? null : sem);
        double mv = rs.getDouble("meta_ventas"); m.setMetaVentas(rs.wasNull() ? null : BigDecimal.valueOf(mv));
        double mu = rs.getDouble("meta_utilidad"); m.setMetaUtilidad(rs.wasNull() ? null : BigDecimal.valueOf(mu));
        double mm = rs.getDouble("meta_margen"); m.setMetaMargen(rs.wasNull() ? null : BigDecimal.valueOf(mm));
        m.setComentario(rs.getString("comentario"));
        m.setFechaActualizacion(rs.getString("fecha_actualizacion"));
        return m;
    }

    private BigDecimal sumaRango(Connection conn, String sql, String ini, String fin) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ini);
            ps.setString(2, fin);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? BigDecimal.valueOf(rs.getDouble(1)) : BigDecimal.ZERO;
            }
        }
    }

    private long contarRango(Connection conn, String sql, String ini, String fin) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ini);
            ps.setString(2, fin);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    // ======================= Paso L: gráficas clicables y desglose (solo lectura) =======================

    /**
     * Stock total por bodega activa (para gráfica clicable). Cada segmento referencia el id de la bodega
     * para permitir el desglose. Solo bodegas con stock. Color informativo.
     */
    public List<DashboardChartSegment> stockPorBodega() throws SQLException {
        String sql = "SELECT b.id_bodega, b.nombre, COALESCE(SUM(s.cantidad),0) total "
                + "FROM bodegas b LEFT JOIN inventario_stock s ON s.id_bodega=b.id_bodega "
                + "WHERE b.estado='ACTIVO' GROUP BY b.id_bodega HAVING total > 0 ORDER BY total DESC";
        List<DashboardChartSegment> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new DashboardChartSegment(rs.getString("nombre"), rs.getDouble("total"),
                        TipoDrillDown.BODEGA, String.valueOf(rs.getInt("id_bodega")), "PRIMARIO"));
            }
        }
        return lista;
    }

    /** Ítems y stock de una bodega (desglose de "stock por bodega"). */
    public List<DashboardDrillDownItem> itemsPorBodega(int idBodega, int limite) throws SQLException {
        String sql = "SELECT i.nombre, i.codigo, s.cantidad, i.unidad_medida "
                + "FROM inventario_stock s JOIN items i ON i.id_item=s.id_item "
                + "WHERE s.id_bodega=? AND s.cantidad > 0 ORDER BY s.cantidad DESC LIMIT ?";
        List<DashboardDrillDownItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idBodega);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String unidad = rs.getString("unidad_medida");
                    lista.add(new DashboardDrillDownItem(
                            rs.getString("nombre"),
                            rs.getString("codigo") == null ? "" : rs.getString("codigo"),
                            fmtCantidad(rs.getDouble("cantidad")) + (unidad == null ? "" : " " + unidad),
                            "", "PRIMARIO"));
                }
            }
        }
        return lista;
    }

    /**
     * Cartera agrupada por estado (para gráfica clicable). El segmento referencia el estado para el desglose.
     * Color semántico por estado. Solo cuentas con saldo.
     */
    public List<DashboardChartSegment> carteraPorEstado() throws SQLException {
        String sql = "SELECT estado, COALESCE(SUM(saldo_pendiente),0) saldo FROM cuentas_por_cobrar "
                + "WHERE estado IN ('PENDIENTE','ABONADA','VENCIDA') GROUP BY estado HAVING saldo > 0 "
                + "ORDER BY saldo DESC";
        List<DashboardChartSegment> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String estado = rs.getString("estado");
                String hint = "VENCIDA".equals(estado) ? "PELIGRO"
                        : "ABONADA".equals(estado) ? "EXITO" : "ADVERTENCIA";
                lista.add(new DashboardChartSegment(etiquetaEstadoCuenta(estado), rs.getDouble("saldo"),
                        TipoDrillDown.CARTERA, estado, hint));
            }
        }
        return lista;
    }

    /** Ventas dentro de un rango ISO (desglose al hacer clic en un día/barra de ventas). Excluye anuladas. */
    public List<DashboardDrillDownItem> ventasPorRango(String iniISO, String finISO, int limite) throws SQLException {
        String sql = "SELECT v.numero_venta, COALESCE(t.nombre,'Consumidor final') cliente, v.fecha, v.total, v.estado "
                + "FROM ventas v LEFT JOIN terceros t ON t.id_tercero=v.id_tercero "
                + "WHERE v.estado<>'ANULADA' AND date(v.fecha) BETWEEN date(?) AND date(?) "
                + "ORDER BY v.id_venta DESC LIMIT ?";
        List<DashboardDrillDownItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, iniISO);
            ps.setString(2, finISO);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DashboardDrillDownItem(
                            rs.getString("numero_venta"),
                            rs.getString("cliente"),
                            fmtCop(rs.getDouble("total")),
                            rs.getString("estado"), "PRIMARIO"));
                }
            }
        }
        return lista;
    }

    /**
     * Top de productos por cantidad vendida en un rango (rotación simple, para gráfica clicable). Cada
     * segmento referencia el id del ítem para el desglose. Excluye anuladas.
     */
    public List<DashboardChartSegment> rotacionProductos(String iniISO, String finISO, int limite) throws SQLException {
        String sql = "SELECT i.id_item, i.nombre, COALESCE(SUM(vd.cantidad),0) q "
                + "FROM venta_detalles vd JOIN ventas v ON v.id_venta=vd.id_venta "
                + "JOIN items i ON i.id_item=vd.id_item "
                + "WHERE v.estado<>'ANULADA' AND date(v.fecha) BETWEEN date(?) AND date(?) "
                + "GROUP BY vd.id_item HAVING q > 0 ORDER BY q DESC LIMIT ?";
        List<DashboardChartSegment> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, iniISO);
            ps.setString(2, finISO);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DashboardChartSegment(rs.getString("nombre"), rs.getDouble("q"),
                            TipoDrillDown.ITEM, String.valueOf(rs.getInt("id_item")), "EXITO"));
                }
            }
        }
        return lista;
    }

    /** Ventas de un ítem dentro de un rango (desglose de una barra de rotación). Excluye anuladas. */
    public List<DashboardDrillDownItem> ventasPorItem(int idItem, String iniISO, String finISO, int limite)
            throws SQLException {
        String sql = "SELECT v.numero_venta, v.fecha, vd.cantidad, vd.subtotal_linea, v.estado "
                + "FROM venta_detalles vd JOIN ventas v ON v.id_venta=vd.id_venta "
                + "WHERE vd.id_item=? AND v.estado<>'ANULADA' AND date(v.fecha) BETWEEN date(?) AND date(?) "
                + "ORDER BY v.id_venta DESC LIMIT ?";
        List<DashboardDrillDownItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            ps.setString(2, iniISO);
            ps.setString(3, finISO);
            ps.setInt(4, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DashboardDrillDownItem(
                            rs.getString("numero_venta"),
                            "Cant: " + fmtCantidad(rs.getDouble("cantidad")),
                            fmtCop(rs.getDouble("subtotal_linea")),
                            rs.getString("estado"), "PRIMARIO"));
                }
            }
        }
        return lista;
    }

    /**
     * Cuentas por cobrar de un estado (desglose al hacer clic en una barra de cartera).
     *
     * @param estado estado a desglosar (PENDIENTE/ABONADA/VENCIDA).
     * @param limite máximo de filas.
     * @return filas (número de venta, cliente, saldo, estado), o lista vacía.
     */
    public List<DashboardDrillDownItem> cuentasPorEstado(String estado, int limite) throws SQLException {
        String sql = "SELECT v.numero_venta, COALESCE(t.nombre,'Consumidor final') cliente, "
                + "c.saldo_pendiente, c.estado "
                + "FROM cuentas_por_cobrar c JOIN ventas v ON v.id_venta=c.id_venta "
                + "LEFT JOIN terceros t ON t.id_tercero=c.id_tercero "
                + "WHERE c.estado=? ORDER BY c.saldo_pendiente DESC LIMIT ?";
        List<DashboardDrillDownItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setInt(2, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DashboardDrillDownItem(
                            rs.getString("numero_venta"),
                            rs.getString("cliente"),
                            fmtCop(rs.getDouble("saldo_pendiente")),
                            etiquetaEstadoCuenta(rs.getString("estado")),
                            "VENCIDA".equals(rs.getString("estado")) ? "PELIGRO" : "ADVERTENCIA"));
                }
            }
        }
        return lista;
    }

    /**
     * Total recibido en el día de hoy: pagos de contado + abonos a crédito cuya fecha es la fecha local
     * actual. Solo lectura, para el KPI "Pagos del día" del Dashboard General.
     *
     * @return suma (≥ 0); cero si no hubo movimientos hoy.
     */
    public BigDecimal pagosDelDia() throws SQLException {
        String hoy = "date('now','localtime')";
        try (Connection conn = DatabaseConnection.getConnection()) {
            BigDecimal pagos = escalar(conn,
                    "SELECT COALESCE(SUM(valor),0) FROM pagos WHERE date(fecha) = " + hoy);
            BigDecimal abonos = escalar(conn,
                    "SELECT COALESCE(SUM(valor),0) FROM abonos_cuenta WHERE date(fecha) = " + hoy);
            return pagos.add(abonos);
        }
    }

    // Formateadores locales mínimos para filas de desglose (no acoplan la vista).
    private static String fmtCop(double v) {
        return "$ " + String.format("%,.0f", v);
    }

    private static String fmtCantidad(double v) {
        return v == Math.floor(v) ? String.format("%.0f", v) : String.format("%.2f", v);
    }

    private static String etiquetaEstadoCuenta(String estado) {
        if (estado == null) {
            return "";
        }
        switch (estado) {
            case "PENDIENTE": return "Pendiente";
            case "ABONADA": return "Abonada";
            case "VENCIDA": return "Vencida";
            default: return estado;
        }
    }
}
