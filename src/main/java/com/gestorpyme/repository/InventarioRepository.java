package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.model.DisponibilidadBodegaItem;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.MovimientoInventario;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos del inventario: existencias (inventario_stock) y movimientos
 * (inventario_movimientos / Kardex). El stock es la fuente autoritativa del
 * disponible (DEC-019); este repositorio lo concilia en cada movimiento.
 * Capa: repository.
 */
public class InventarioRepository {

    /** Lista las existencias (stock por item y bodega) ordenadas por item y bodega. */
    public List<ExistenciaStock> listarExistencias() throws SQLException {
        String sql = "SELECT s.id_item, s.id_bodega, it.codigo AS item_codigo, it.nombre AS item_nombre, "
                   + "c.nombre AS categoria_nombre, st.nombre AS subtipo_nombre, "
                   + "b.nombre AS bodega_nombre, s.cantidad, it.stock_minimo, s.ubicacion_interna "
                   + "FROM inventario_stock s "
                   + "JOIN items it ON it.id_item = s.id_item "
                   + "JOIN bodegas b ON b.id_bodega = s.id_bodega "
                   + "LEFT JOIN categorias c ON c.id_categoria = it.id_categoria "
                   + "LEFT JOIN subtipos st ON st.id_subtipo = it.id_subtipo "
                   + "ORDER BY it.nombre, b.nombre";
        List<ExistenciaStock> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new ExistenciaStock(
                        rs.getInt("id_item"),
                        rs.getInt("id_bodega"),
                        rs.getString("item_codigo"),
                        rs.getString("item_nombre"),
                        rs.getString("categoria_nombre"),
                        rs.getString("subtipo_nombre"),
                        rs.getString("bodega_nombre"),
                        BigDecimal.valueOf(rs.getDouble("cantidad")),
                        BigDecimal.valueOf(rs.getDouble("stock_minimo")),
                        rs.getString("ubicacion_interna")));
            }
        }
        return lista;
    }

    /**
     * Actualiza la ubicacion interna de una existencia (item + bodega). Solo afecta la
     * ubicacion: NO toca la cantidad ni genera movimiento de Kardex (Paso A). Si no existe
     * la combinacion item/bodega, no hace nada.
     *
     * @param idItem    item.
     * @param idBodega  bodega.
     * @param ubicacion ubicacion interna (puede ser null para limpiarla).
     * @throws SQLException si ocurre un error de acceso a datos.
     */
    public void actualizarUbicacion(int idItem, int idBodega, String ubicacion) throws SQLException {
        String sql = "UPDATE inventario_stock SET ubicacion_interna = ? WHERE id_item = ? AND id_bodega = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (ubicacion == null) {
                ps.setNull(1, java.sql.Types.VARCHAR);
            } else {
                ps.setString(1, ubicacion);
            }
            ps.setInt(2, idItem);
            ps.setInt(3, idBodega);
            ps.executeUpdate();
        }
    }

    /** Devuelve la cantidad disponible de un item en una bodega (0 si no hay registro). */
    public BigDecimal obtenerCantidad(int idItem, int idBodega) throws SQLException {
        String sql = "SELECT cantidad FROM inventario_stock WHERE id_item=? AND id_bodega=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return BigDecimal.valueOf(rs.getDouble("cantidad"));
                }
                return BigDecimal.ZERO;
            }
        }
    }

    /**
     * Registra un movimiento de inventario y actualiza el stock en una unica
     * transaccion (DEC-019): primero concilia inventario_stock (suma o resta segun
     * el tipo) y luego inserta la fila en inventario_movimientos (Kardex). Si algo
     * falla, se revierte todo.
     *
     * @param cantidad cantidad del movimiento (siempre positiva).
     * @param idUsuario usuario que registra el movimiento (puede ser null).
     */
    public void registrarMovimiento(int idItem, int idBodega, TipoMovimiento tipo,
                                    BigDecimal cantidad, String motivo, Integer idUsuario) throws SQLException {
        // El signo del cambio de stock depende del tipo de movimiento.
        BigDecimal delta = tipo.incrementaStock() ? cantidad : cantidad.negate();

        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommitPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                conciliarStock(conn, idItem, idBodega, delta);
                insertarMovimiento(conn, idItem, idBodega, tipo, cantidad, motivo, idUsuario);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitPrevio);
            }
        }
    }

    /** Suma 'delta' al stock de (item, bodega); crea la fila si aun no existe. */
    private void conciliarStock(Connection conn, int idItem, int idBodega, BigDecimal delta) throws SQLException {
        String update = "UPDATE inventario_stock "
                      + "SET cantidad = cantidad + ?, fecha_actualizacion = datetime('now','localtime') "
                      + "WHERE id_item=? AND id_bodega=?";
        int filas;
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setDouble(1, delta.doubleValue());
            ps.setInt(2, idItem);
            ps.setInt(3, idBodega);
            filas = ps.executeUpdate();
        }
        if (filas == 0) {
            // No existia stock para esa combinacion: se crea con la cantidad inicial.
            String insert = "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, idItem);
                ps.setInt(2, idBodega);
                ps.setDouble(3, delta.doubleValue());
                ps.executeUpdate();
            }
        }
    }

    /** Inserta la fila del Kardex (inventario_movimientos). */
    private void insertarMovimiento(Connection conn, int idItem, int idBodega, TipoMovimiento tipo,
                                    BigDecimal cantidad, String motivo, Integer idUsuario) throws SQLException {
        String sql = "INSERT INTO inventario_movimientos "
                   + "(id_item, id_bodega, tipo_movimiento, cantidad, motivo, id_usuario) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, tipo.name());
            ps.setDouble(4, cantidad.doubleValue());
            ps.setString(5, motivo);
            if (idUsuario == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, idUsuario);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Consulta de Kardex: lista movimientos de inventario con filtros opcionales.
     * Cualquier parametro nulo (o cadena vacia en fechas) se ignora en el WHERE,
     * por lo que sirve tanto para "todo" como para busquedas muy especificas.
     *
     * @param idItem      id de item a filtrar, o null para todos.
     * @param idBodega    id de bodega a filtrar, o null para todas.
     * @param tipo        tipo de movimiento a filtrar, o null para todos.
     * @param fechaDesde  fecha inicial 'YYYY-MM-DD' inclusive, o null/vacio.
     * @param fechaHasta  fecha final 'YYYY-MM-DD' inclusive, o null/vacio.
     * @return movimientos que cumplen los filtros, ordenados del mas reciente al mas antiguo.
     */
    public List<MovimientoInventario> listarMovimientos(Integer idItem, Integer idBodega,
                                                        TipoMovimiento tipo,
                                                        String fechaDesde, String fechaHasta) throws SQLException {
        // Se arma el SQL dinamicamente y se acumulan los parametros en orden.
        StringBuilder sql = new StringBuilder(
                "SELECT m.id_movimiento, m.fecha, it.nombre AS item_nombre, b.nombre AS bodega_nombre, "
              + "m.tipo_movimiento, m.cantidad, m.motivo, "
              + "COALESCE(u.nombre_completo, u.nombre_usuario) AS usuario_nombre, "
              + "lt.numero_lote AS lote_numero "
              + "FROM inventario_movimientos m "
              + "JOIN items it   ON it.id_item   = m.id_item "
              + "JOIN bodegas b  ON b.id_bodega  = m.id_bodega "
              + "LEFT JOIN usuarios u ON u.id_usuario = m.id_usuario "
              + "LEFT JOIN lotes lt   ON lt.id_lote  = m.id_lote "
              + "WHERE 1 = 1 ");

        List<Object> params = new ArrayList<>();
        if (idItem != null) {
            sql.append("AND m.id_item = ? ");
            params.add(idItem);
        }
        if (idBodega != null) {
            sql.append("AND m.id_bodega = ? ");
            params.add(idBodega);
        }
        if (tipo != null) {
            sql.append("AND m.tipo_movimiento = ? ");
            params.add(tipo.name());
        }
        if (fechaDesde != null && !fechaDesde.trim().isEmpty()) {
            sql.append("AND date(m.fecha) >= date(?) ");
            params.add(fechaDesde.trim());
        }
        if (fechaHasta != null && !fechaHasta.trim().isEmpty()) {
            sql.append("AND date(m.fecha) <= date(?) ");
            params.add(fechaHasta.trim());
        }
        sql.append("ORDER BY m.fecha DESC, m.id_movimiento DESC");

        List<MovimientoInventario> movimientos = new ArrayList<>();
        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    MovimientoInventario m = new MovimientoInventario();
                    m.setIdMovimiento(rs.getInt("id_movimiento"));
                    m.setFecha(rs.getString("fecha"));
                    m.setNombreItem(rs.getString("item_nombre"));
                    m.setNombreBodega(rs.getString("bodega_nombre"));
                    m.setTipo(TipoMovimiento.valueOf(rs.getString("tipo_movimiento")));
                    // REAL en SQLite se lee como BigDecimal para cantidades (coherente con DEC-020).
                    m.setCantidad(BigDecimal.valueOf(rs.getDouble("cantidad")));
                    m.setMotivo(rs.getString("motivo"));
                    // RF-06: usuario responsable. Si no hay usuario asociado (null), se muestra
                    // "Sistema" para no dejar la celda vacia y facilitar la auditoria.
                    String usuario = rs.getString("usuario_nombre");
                    m.setNombreUsuario(usuario == null ? "Sistema" : usuario);
                    m.setNumeroLote(rs.getString("lote_numero")); // Paso J: lote de la salida (o null)
                    movimientos.add(m);
                }
            }
        }
        return movimientos;
    }

    /**
     * Disponibilidad de un item por bodega ACTIVA (Paso I). Devuelve todas las bodegas activas con
     * la cantidad existente del item (0 si no hay fila de stock), ordenadas por cantidad descendente.
     * Solo lectura. Sirve para la asignacion inteligente de bodega por linea de venta.
     */
    public List<DisponibilidadBodegaItem> disponibilidadPorBodega(int idItem) throws SQLException {
        String sql = "SELECT b.id_bodega, b.nombre AS bodega_nombre, "
                   + "IFNULL(s.cantidad, 0) AS cantidad "
                   + "FROM bodegas b "
                   + "LEFT JOIN inventario_stock s ON s.id_bodega = b.id_bodega AND s.id_item = ? "
                   + "WHERE b.estado = 'ACTIVO' "
                   + "ORDER BY cantidad DESC, b.nombre";
        List<DisponibilidadBodegaItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new DisponibilidadBodegaItem(idItem, rs.getInt("id_bodega"),
                            rs.getString("bodega_nombre"), BigDecimal.valueOf(rs.getDouble("cantidad"))));
                }
            }
        }
        return lista;
    }

    /**
     * Conciliación de solo lectura entre el stock autoritativo y el stock loteado, por item + bodega
     * (Paso Q). Compara {@code inventario_stock.cantidad} contra la suma de
     * {@code IFNULL(lotes.cantidad_disponible, lotes.cantidad_inicial)} de los lotes de esa bodega.
     *
     * Parte de {@code inventario_stock} (solo aparecen items con existencia), hace LEFT JOIN a la suma
     * de lotes (los lotes sin bodega no se asignan a ninguna fila). No modifica datos. La clasificación
     * (OK / FALTA_LOTEAR / EXCESO_LOTEADO / SIN_LOTES) la deriva el modelo.
     *
     * @return filas de conciliación ordenadas por item y bodega.
     */
    public java.util.List<com.gestorpyme.domain.model.ConciliacionLoteStockItem> conciliacionLoteStock()
            throws SQLException {
        String sql = "SELECT i.codigo, i.nombre AS item, b.nombre AS bodega, "
                   + "s.cantidad AS stock_actual, COALESCE(L.loteado, 0) AS stock_loteado "
                   + "FROM inventario_stock s "
                   + "JOIN items i ON i.id_item = s.id_item "
                   + "JOIN bodegas b ON b.id_bodega = s.id_bodega "
                   + "LEFT JOIN (SELECT id_item, id_bodega, "
                   + "                  SUM(IFNULL(cantidad_disponible, cantidad_inicial)) AS loteado "
                   + "           FROM lotes WHERE id_bodega IS NOT NULL "
                   + "           GROUP BY id_item, id_bodega) L "
                   + "       ON L.id_item = s.id_item AND L.id_bodega = s.id_bodega "
                   + "ORDER BY i.nombre, b.nombre";
        java.util.List<com.gestorpyme.domain.model.ConciliacionLoteStockItem> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                com.gestorpyme.domain.model.ConciliacionLoteStockItem c =
                        new com.gestorpyme.domain.model.ConciliacionLoteStockItem();
                c.setCodigoItem(rs.getString("codigo"));
                c.setNombreItem(rs.getString("item"));
                c.setNombreBodega(rs.getString("bodega"));
                c.setStockActual(BigDecimal.valueOf(rs.getDouble("stock_actual")));
                c.setStockLoteado(BigDecimal.valueOf(rs.getDouble("stock_loteado")));
                lista.add(c);
            }
        }
        return lista;
    }
}
