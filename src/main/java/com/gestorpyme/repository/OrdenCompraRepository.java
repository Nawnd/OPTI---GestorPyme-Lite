package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoOrdenCompra;
import com.gestorpyme.domain.model.OrdenCompra;
import com.gestorpyme.domain.model.OrdenCompraDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de ordenes de compra. Encapsula todo el JDBC/SQL del modulo; ni servicios
 * ni vistas ejecutan SQL. La creacion de la orden (cabecera + detalles + consecutivo
 * OC-000001) se hace en una sola transaccion con rollback ante cualquier error.
 * Capa: repository.
 */
public class OrdenCompraRepository {

    /**
     * Crea una orden de compra completa (cabecera + detalles) en una transaccion.
     * Genera el consecutivo OC-000001 dentro de la misma transaccion (DEC-018).
     *
     * @param orden orden con sus detalles ya cargados.
     * @return el numero de orden generado (OC-000001).
     */
    public String crearOrdenCompleta(OrdenCompra orden) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommitPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String numero = generarNumeroOrden(conn);
                int idOrden = insertarCabecera(conn, orden, numero);
                for (OrdenCompraDetalle d : orden.getDetalles()) {
                    insertarDetalle(conn, idOrden, d);
                }
                conn.commit();
                orden.setIdOrden(idOrden);
                orden.setNumeroOrden(numero);
                return numero;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitPrevio);
            }
        }
    }

    /** Genera el siguiente consecutivo OC-NNNNNN (continuo) dentro de la transaccion. */
    private String generarNumeroOrden(Connection conn) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTR(numero_orden, 4) AS INTEGER)) AS maximo "
                   + "FROM ordenes_compra WHERE numero_orden LIKE 'OC-%'";
        int siguiente = 1;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                siguiente = rs.getInt("maximo") + 1;
            }
        }
        return String.format("OC-%06d", siguiente);
    }

    private int insertarCabecera(Connection conn, OrdenCompra o, String numero) throws SQLException {
        String sql = "INSERT INTO ordenes_compra "
                   + "(numero_orden, id_proveedor, fecha_orden, fecha_estimada, estado, observaciones, subtotal, total) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, o.getIdProveedor());
            ps.setString(3, o.getFechaOrden());
            if (o.getFechaEstimada() == null) {
                ps.setNull(4, java.sql.Types.VARCHAR);
            } else {
                ps.setString(4, o.getFechaEstimada());
            }
            ps.setString(5, o.getEstado().name());
            ps.setString(6, o.getObservaciones());
            ps.setDouble(7, o.getSubtotal().doubleValue());
            ps.setDouble(8, o.getTotal().doubleValue());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private void insertarDetalle(Connection conn, int idOrden, OrdenCompraDetalle d) throws SQLException {
        String sql = "INSERT INTO ordenes_compra_detalles "
                   + "(id_orden, id_item, id_bodega_destino, cantidad_solicitada, cantidad_recibida, precio_unitario, subtotal) "
                   + "VALUES (?, ?, ?, ?, 0, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idOrden);
            ps.setInt(2, d.getIdItem());
            if (d.getIdBodegaDestino() == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, d.getIdBodegaDestino());
            }
            ps.setDouble(4, d.getCantidadSolicitada().doubleValue());
            ps.setDouble(5, d.getPrecioUnitario().doubleValue());
            ps.setDouble(6, d.getSubtotal().doubleValue());
            ps.executeUpdate();
        }
    }

    /** Lista las ordenes (con nombre del proveedor) de la mas reciente a la mas antigua. */
    public List<OrdenCompra> listar() throws SQLException {
        String sql = "SELECT o.id_orden, o.numero_orden, o.id_proveedor, t.nombre AS proveedor, "
                   + "o.fecha_orden, o.fecha_estimada, o.estado, o.observaciones, o.subtotal, o.total, "
                   + "o.fecha_creacion, o.fecha_actualizacion "
                   + "FROM ordenes_compra o JOIN terceros t ON t.id_tercero = o.id_proveedor "
                   + "ORDER BY o.id_orden DESC";
        List<OrdenCompra> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                lista.add(mapearCabecera(rs));
            }
        }
        return lista;
    }

    /** Busca una orden por id (cabecera con nombre de proveedor), sin detalles. */
    public Optional<OrdenCompra> buscarPorId(int idOrden) throws SQLException {
        String sql = "SELECT o.id_orden, o.numero_orden, o.id_proveedor, t.nombre AS proveedor, "
                   + "o.fecha_orden, o.fecha_estimada, o.estado, o.observaciones, o.subtotal, o.total, "
                   + "o.fecha_creacion, o.fecha_actualizacion "
                   + "FROM ordenes_compra o JOIN terceros t ON t.id_tercero = o.id_proveedor "
                   + "WHERE o.id_orden = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapearCabecera(rs)) : Optional.empty();
            }
        }
    }

    /** Lista los detalles de una orden, con codigo/nombre del item y nombre de la bodega. */
    public List<OrdenCompraDetalle> listarDetalles(int idOrden) throws SQLException {
        String sql = "SELECT d.id_detalle, d.id_orden, d.id_item, i.codigo, i.nombre AS item, "
                   + "d.id_bodega_destino, b.nombre AS bodega, d.cantidad_solicitada, d.cantidad_recibida, "
                   + "d.precio_unitario, d.subtotal "
                   + "FROM ordenes_compra_detalles d "
                   + "JOIN items i ON i.id_item = d.id_item "
                   + "LEFT JOIN bodegas b ON b.id_bodega = d.id_bodega_destino "
                   + "WHERE d.id_orden = ? ORDER BY d.id_detalle";
        List<OrdenCompraDetalle> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrdenCompraDetalle d = new OrdenCompraDetalle();
                    d.setIdDetalle(rs.getInt("id_detalle"));
                    d.setIdOrden(rs.getInt("id_orden"));
                    d.setIdItem(rs.getInt("id_item"));
                    d.setCodigoItem(rs.getString("codigo"));
                    d.setNombreItem(rs.getString("item"));
                    int idb = rs.getInt("id_bodega_destino");
                    d.setIdBodegaDestino(rs.wasNull() ? null : idb);
                    d.setNombreBodega(rs.getString("bodega"));
                    d.setCantidadSolicitada(BigDecimal.valueOf(rs.getDouble("cantidad_solicitada")));
                    d.setCantidadRecibida(BigDecimal.valueOf(rs.getDouble("cantidad_recibida")));
                    d.setPrecioUnitario(BigDecimal.valueOf(rs.getDouble("precio_unitario")));
                    d.setSubtotal(BigDecimal.valueOf(rs.getDouble("subtotal")));
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    /** Cambia el estado de una orden y actualiza la fecha. Usado al cancelar. */
    public void cambiarEstado(int idOrden, EstadoOrdenCompra estado) throws SQLException {
        String sql = "UPDATE ordenes_compra SET estado = ?, "
                   + "fecha_actualizacion = datetime('now','localtime') WHERE id_orden = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idOrden);
            ps.executeUpdate();
        }
    }

    private OrdenCompra mapearCabecera(ResultSet rs) throws SQLException {
        OrdenCompra o = new OrdenCompra();
        o.setIdOrden(rs.getInt("id_orden"));
        o.setNumeroOrden(rs.getString("numero_orden"));
        o.setIdProveedor(rs.getInt("id_proveedor"));
        o.setNombreProveedor(rs.getString("proveedor"));
        o.setFechaOrden(rs.getString("fecha_orden"));
        o.setFechaEstimada(rs.getString("fecha_estimada"));
        o.setEstado(EstadoOrdenCompra.desde(rs.getString("estado")));
        o.setObservaciones(rs.getString("observaciones"));
        o.setSubtotal(BigDecimal.valueOf(rs.getDouble("subtotal")));
        o.setTotal(BigDecimal.valueOf(rs.getDouble("total")));
        o.setFechaCreacion(rs.getString("fecha_creacion"));
        o.setFechaActualizacion(rs.getString("fecha_actualizacion"));
        return o;
    }
}
