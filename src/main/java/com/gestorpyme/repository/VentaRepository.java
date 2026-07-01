package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoVenta;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.LoteDisponible;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de ventas (tablas ventas y venta_detalles) y orquestacion
 * transaccional de la venta completa. Capa: repository (encapsula JDBC).
 *
 * Regla clave (DEC-018): el numero de venta es un consecutivo continuo "V-000001"
 * generado DENTRO de la transaccion. Operacion compuesta (venta + detalle + stock +
 * movimiento SALIDA_VENTA + pago/cuenta) en una sola transaccion con rollback.
 */
public class VentaRepository {

    /** Repositorio de lotes (Paso J): consultas FEFO y descuento de saldo dentro de la transaccion. */
    private final LoteRepository loteRepository = new LoteRepository();

    /**
     * Crea una venta completa en una unica transaccion:
     * <ol>
     *   <li>Genera el consecutivo "V-NNNNNN".</li>
     *   <li>Inserta el encabezado y las lineas de detalle.</li>
     *   <li>Para los items que controlan inventario: valida stock, descuenta de la
     *       bodega indicada e inserta el movimiento SALIDA_VENTA (Kardex).</li>
     *   <li>Si es de contado, registra el pago; si es a credito, crea la cuenta por
     *       cobrar.</li>
     * </ol>
     * Si cualquier paso falla (incluido stock insuficiente), se revierte todo.
     *
     * @param venta            encabezado (id_tercero, descuento, total, etc.).
     * @param detalles         lineas de la venta (al menos una).
     * @param idBodega         bodega de la que se descuenta el inventario (para items que lo controlan).
     * @param contado          true = contado (genera pago); false = credito (genera cuenta por cobrar).
     * @param fechaVencimiento fecha de vencimiento ISO para la cuenta (credito); puede ser null.
     * @param medioContado     medio de pago para la venta de contado (ignorado en credito).
     * @return el numero de venta generado.
     * @throws ValidacionException si el stock es insuficiente para algun item.
     * @throws SQLException        ante errores de base de datos.
     */
    public String crearVentaCompleta(Venta venta, List<VentaDetalle> detalles, int idBodega,
                                     boolean contado, String fechaVencimiento, MedioPago medioContado)
            throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommitPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String numero = siguienteNumero(conn);
                int idVenta = insertarEncabezado(conn, venta, numero);

                for (VentaDetalle d : detalles) {
                    int idDetalle = insertarDetalle(conn, idVenta, d);
                    if (controlaInventario(conn, d.getIdItem())) {
                        // Paso I: cada linea descuenta de SU bodega de salida (fallback a la preferida).
                        int idBodegaLinea = (d.getIdBodegaSalida() != null && d.getIdBodegaSalida() > 0)
                                ? d.getIdBodegaSalida() : idBodega;
                        // inventario_stock es la fuente autoritativa: se descuenta siempre.
                        descontarStock(conn, d.getIdItem(), idBodegaLinea, d.getCantidad());
                        // Paso J: trazabilidad por lote (FEFO) dentro de la bodega. Si no hay lotes usables,
                        // se registra una unica salida sin lote (comportamiento previo).
                        consumirLotesYRegistrarKardex(conn, idDetalle, d.getIdItem(), idBodegaLinea,
                                d.getCantidad(), idVenta, venta.getIdUsuario(), numero);
                    }
                }

                if (contado) {
                    insertarPagoContado(conn, idVenta, venta.getTotal(), medioContado);
                } else {
                    insertarCuenta(conn, idVenta, venta.getIdTercero(), venta.getTotal(), fechaVencimiento);
                }

                conn.commit();
                venta.setIdVenta(idVenta);
                venta.setNumeroVenta(numero);
                return numero;
            } catch (SQLException | ValidacionException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitPrevio);
            }
        }
    }

    /** Calcula el siguiente consecutivo "V-NNNNNN" a partir del maximo numerico existente. */
    private String siguienteNumero(Connection conn) throws SQLException {
        // Se extrae la parte numerica tras "V-" y se toma el maximo, para un consecutivo continuo.
        String sql = "SELECT MAX(CAST(SUBSTR(numero_venta, 3) AS INTEGER)) AS maximo "
                   + "FROM ventas WHERE numero_venta LIKE 'V-%'";
        int siguiente = 1;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int maximo = rs.getInt("maximo"); // 0 si era NULL (tabla vacia)
                siguiente = maximo + 1;
            }
        }
        return String.format("V-%06d", siguiente);
    }

    /** Inserta el encabezado y devuelve el id generado. */
    private int insertarEncabezado(Connection conn, Venta v, String numero) throws SQLException {
        String sql = "INSERT INTO ventas "
                   + "(numero_venta, id_tercero, subtotal, descuento, total, estado, id_usuario, observaciones) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            if (v.getIdTercero() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, v.getIdTercero());
            }
            ps.setDouble(3, v.getSubtotal().doubleValue());
            ps.setDouble(4, v.getDescuento().doubleValue());
            ps.setDouble(5, v.getTotal().doubleValue());
            ps.setString(6, v.getEstado().name());
            if (v.getIdUsuario() == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, v.getIdUsuario());
            }
            ps.setString(8, v.getObservaciones());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("No se pudo obtener el id de la venta.");
            }
        }
    }

    private int insertarDetalle(Connection conn, int idVenta, VentaDetalle d) throws SQLException {
        String sql = "INSERT INTO venta_detalles "
                   + "(id_venta, id_item, cantidad, precio_unitario, descuento_linea, subtotal_linea, id_bodega_salida) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idVenta);
            ps.setInt(2, d.getIdItem());
            ps.setDouble(3, d.getCantidad().doubleValue());
            ps.setDouble(4, d.getPrecioUnitario().doubleValue());
            ps.setDouble(5, d.getDescuentoLinea().doubleValue());
            ps.setDouble(6, d.getSubtotalLinea().doubleValue());
            // Paso I: bodega de salida (null para servicios o si no se asigno).
            if (d.getIdBodegaSalida() == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, d.getIdBodegaSalida());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                throw new SQLException("No se pudo obtener el id del detalle de venta.");
            }
        }
    }

    /**
     * Consume lotes por FEFO para una linea inventariable y registra el Kardex (Paso J), dentro de la
     * transaccion de la venta. Reglas:
     * <ul>
     *   <li>Si el item NO tiene lotes usables (ACTIVOS, no vencidos, saldo &gt; 0) en esa bodega: registra
     *       una unica salida SALIDA_VENTA sin lote (comportamiento previo a Paso J).</li>
     *   <li>Si tiene lotes usables pero su saldo total no cubre la cantidad: rechaza la venta.</li>
     *   <li>Si los lotes cubren: consume FEFO (vencimiento mas cercano primero), reduce el saldo de cada
     *       lote (marcandolo AGOTADO si llega a 0), registra venta_detalle_lotes e inserta un movimiento
     *       SALIDA_VENTA por cada lote con su id_lote.</li>
     * </ul>
     * inventario_stock ya fue descontado antes por la bodega (fuente autoritativa).
     */
    private void consumirLotesYRegistrarKardex(Connection conn, int idDetalle, int idItem, int idBodega,
                                               BigDecimal cantidad, int idVenta, Integer idUsuario, String numero)
            throws SQLException {
        List<LoteDisponible> usables = loteRepository.disponiblesFefo(conn, idItem, idBodega);
        if (usables.isEmpty()) {
            // Sin trazabilidad por lote: una sola salida sin id_lote.
            insertarMovimientoVenta(conn, idItem, idBodega, cantidad, idVenta, idUsuario, numero, null);
            return;
        }
        // Validacion: el saldo total por lotes debe cubrir la cantidad requerida.
        BigDecimal totalDisponible = BigDecimal.ZERO;
        for (LoteDisponible l : usables) {
            totalDisponible = totalDisponible.add(l.getCantidadDisponible());
        }
        if (totalDisponible.compareTo(cantidad) < 0) {
            throw new ValidacionException(
                    "El producto tiene control por lotes, pero los lotes disponibles no cubren la cantidad requerida.");
        }
        // Consumo FEFO: se toma de cada lote hasta satisfacer la cantidad.
        BigDecimal restante = cantidad;
        for (LoteDisponible l : usables) {
            if (restante.signum() <= 0) {
                break;
            }
            BigDecimal toma = l.getCantidadDisponible().min(restante);
            BigDecimal nuevoSaldo = l.getCantidadDisponible().subtract(toma);
            loteRepository.descontarDisponible(conn, l.getIdLote(), nuevoSaldo);   // reduce saldo (AGOTADO si 0)
            loteRepository.insertarConsumo(conn, idDetalle, l.getIdLote(), toma);  // venta_detalle_lotes
            insertarMovimientoVenta(conn, idItem, idBodega, toma, idVenta, idUsuario, numero, l.getIdLote());
            restante = restante.subtract(toma);
        }
    }

    /** Consulta autoritativa en la tabla items de si el item controla inventario. */
    private boolean controlaInventario(Connection conn, int idItem) throws SQLException {
        String sql = "SELECT controla_inventario FROM items WHERE id_item = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt("controla_inventario") == 1;
            }
        }
    }

    /** Valida y descuenta stock de la bodega indicada (lanza si es insuficiente). */
    private void descontarStock(Connection conn, int idItem, int idBodega, BigDecimal cantidad)
            throws SQLException {
        if (idBodega <= 0) {
            throw new ValidacionException("Seleccione una bodega para descontar el inventario.");
        }
        BigDecimal disponible = BigDecimal.ZERO;
        String sel = "SELECT cantidad FROM inventario_stock WHERE id_item=? AND id_bodega=?";
        try (PreparedStatement ps = conn.prepareStatement(sel)) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    disponible = BigDecimal.valueOf(rs.getDouble("cantidad"));
                }
            }
        }
        if (disponible.compareTo(cantidad) < 0) {
            throw new ValidacionException("Stock insuficiente para el item " + idItem
                    + " en la bodega seleccionada (disponible: " + disponible.toPlainString()
                    + ", requerido: " + cantidad.toPlainString() + ").");
        }
        String upd = "UPDATE inventario_stock "
                   + "SET cantidad = cantidad - ?, fecha_actualizacion = datetime('now','localtime') "
                   + "WHERE id_item=? AND id_bodega=?";
        try (PreparedStatement ps = conn.prepareStatement(upd)) {
            ps.setDouble(1, cantidad.doubleValue());
            ps.setInt(2, idItem);
            ps.setInt(3, idBodega);
            ps.executeUpdate();
        }
    }

    /** Inserta el movimiento SALIDA_VENTA en el Kardex, vinculado a la venta y opcionalmente a un lote (Paso J). */
    private void insertarMovimientoVenta(Connection conn, int idItem, int idBodega, BigDecimal cantidad,
                                         int idVenta, Integer idUsuario, String numero, Integer idLote)
            throws SQLException {
        String sql = "INSERT INTO inventario_movimientos "
                   + "(id_item, id_bodega, tipo_movimiento, cantidad, motivo, id_usuario, id_venta, id_lote) "
                   + "VALUES (?, ?, 'SALIDA_VENTA', ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setDouble(3, cantidad.doubleValue());
            ps.setString(4, "Venta " + numero);
            if (idUsuario == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, idUsuario);
            }
            ps.setInt(6, idVenta);
            if (idLote == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, idLote);
            }
            ps.executeUpdate();
        }
    }

    /** Registra el pago de una venta de contado por el total. */
    private void insertarPagoContado(Connection conn, int idVenta, BigDecimal total, MedioPago medio)
            throws SQLException {
        String sql = "INSERT INTO pagos (id_venta, medio_pago, valor, observaciones) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            ps.setString(2, (medio == null ? MedioPago.EFECTIVO : medio).name());
            ps.setDouble(3, total.doubleValue());
            ps.setString(4, "Pago de contado");
            ps.executeUpdate();
        }
    }

    /** Crea la cuenta por cobrar de una venta a credito (saldo = total). */
    private void insertarCuenta(Connection conn, int idVenta, Integer idTercero, BigDecimal total,
                                String fechaVencimiento) throws SQLException {
        String sql = "INSERT INTO cuentas_por_cobrar "
                   + "(id_venta, id_tercero, valor_total, valor_pagado, saldo_pendiente, fecha_vencimiento, estado) "
                   + "VALUES (?, ?, ?, 0, ?, ?, 'PENDIENTE')";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            if (idTercero == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, idTercero);
            }
            ps.setDouble(3, total.doubleValue());
            ps.setDouble(4, total.doubleValue());
            if (fechaVencimiento == null || fechaVencimiento.trim().isEmpty()) {
                ps.setNull(5, Types.VARCHAR);
            } else {
                ps.setString(5, fechaVencimiento.trim());
            }
            ps.executeUpdate();
        }
    }

    /** Lista las ventas (mas recientes primero) con el nombre del cliente. */
    public List<Venta> listar() throws SQLException {
        String sql = "SELECT v.id_venta, v.numero_venta, v.id_tercero, t.nombre AS tercero_nombre, "
                   + "v.fecha, v.subtotal, v.descuento, v.total, v.estado, v.id_usuario, v.observaciones "
                   + "FROM ventas v LEFT JOIN terceros t ON t.id_tercero = v.id_tercero "
                   + "ORDER BY v.id_venta DESC";
        List<Venta> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /**
     * Busca ventas por consecutivo (V-000002) o por nombre del cliente, con tope de resultados.
     * Usa LIKE + LIMIT con PreparedStatement (sin SQL en vistas). Para la busqueda inteligente
     * en Pagos y Cuentas por cobrar.
     *
     * @param texto  fragmento a buscar (numero o cliente).
     * @param limite maximo de resultados.
     */
    public List<Venta> buscar(String texto, int limite) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        String sql = "SELECT v.id_venta, v.numero_venta, v.id_tercero, t.nombre AS tercero_nombre, "
                   + "v.fecha, v.subtotal, v.descuento, v.total, v.estado, v.id_usuario, v.observaciones "
                   + "FROM ventas v LEFT JOIN terceros t ON t.id_tercero = v.id_tercero "
                   + "WHERE v.numero_venta LIKE ? OR t.nombre LIKE ? "
                   + "ORDER BY v.id_venta DESC LIMIT ?";
        List<Venta> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patron);
            ps.setString(2, patron);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Devuelve el id de una venta a partir de su numero exacto (V-NNNNNN). Lectura puntual usada por
     * el cierre de Orden de Trabajo (Paso U.3) para enlazar la OT con la venta recien creada. Usa
     * comparacion exacta (=) para no confundir numeros que sean subcadena de otros.
     *
     * @param numeroVenta numero exacto de la venta.
     * @return el id de la venta, o vacio si no existe.
     */
    public Optional<Integer> buscarIdPorNumero(String numeroVenta) throws SQLException {
        String sql = "SELECT id_venta FROM ventas WHERE numero_venta = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, numeroVenta);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }

    /** Devuelve las lineas de detalle de una venta. */
    public List<VentaDetalle> listarDetalles(int idVenta) throws SQLException {
        String sql = "SELECT d.id_detalle, d.id_venta, d.id_item, it.nombre AS item_nombre, "
                   + "d.cantidad, d.precio_unitario, d.descuento_linea, d.subtotal_linea, "
                   + "d.id_bodega_salida, b.nombre AS bodega_salida_nombre "
                   + "FROM venta_detalles d JOIN items it ON it.id_item = d.id_item "
                   + "LEFT JOIN bodegas b ON b.id_bodega = d.id_bodega_salida "
                   + "WHERE d.id_venta = ? ORDER BY d.id_detalle";
        List<VentaDetalle> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VentaDetalle d = new VentaDetalle();
                    d.setIdDetalle(rs.getInt("id_detalle"));
                    d.setIdVenta(rs.getInt("id_venta"));
                    d.setIdItem(rs.getInt("id_item"));
                    d.setNombreItem(rs.getString("item_nombre"));
                    d.setCantidad(BigDecimal.valueOf(rs.getDouble("cantidad")));
                    d.setPrecioUnitario(BigDecimal.valueOf(rs.getDouble("precio_unitario")));
                    d.setDescuentoLinea(BigDecimal.valueOf(rs.getDouble("descuento_linea")));
                    d.setSubtotalLinea(BigDecimal.valueOf(rs.getDouble("subtotal_linea")));
                    // Paso I: bodega de salida (null en ventas historicas o servicios).
                    int idBod = rs.getInt("id_bodega_salida");
                    d.setIdBodegaSalida(rs.wasNull() ? null : idBod);
                    d.setNombreBodegaSalida(rs.getString("bodega_salida_nombre"));
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    private Venta mapear(ResultSet rs) throws SQLException {
        Venta v = new Venta();
        v.setIdVenta(rs.getInt("id_venta"));
        v.setNumeroVenta(rs.getString("numero_venta"));
        int idTercero = rs.getInt("id_tercero");
        v.setIdTercero(rs.wasNull() ? null : idTercero);
        v.setNombreTercero(rs.getString("tercero_nombre"));
        v.setFecha(rs.getString("fecha"));
        v.setSubtotal(BigDecimal.valueOf(rs.getDouble("subtotal")));
        v.setDescuento(BigDecimal.valueOf(rs.getDouble("descuento")));
        v.setTotal(BigDecimal.valueOf(rs.getDouble("total")));
        v.setEstado(EstadoVenta.desde(rs.getString("estado")));
        int idUsuario = rs.getInt("id_usuario");
        v.setIdUsuario(rs.wasNull() ? null : idUsuario);
        v.setObservaciones(rs.getString("observaciones"));
        return v;
    }
}
