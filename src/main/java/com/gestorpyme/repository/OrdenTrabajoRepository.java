package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoOrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajo;
import com.gestorpyme.domain.model.OrdenTrabajoRepuesto;
import com.gestorpyme.domain.model.OrdenTrabajoServicio;
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
 * Persistencia de ordenes de trabajo (Paso U.2).
 *
 * Capa: repository (JDBC). La creacion/actualizacion de la OT (cabecera + detalle de servicios y
 * repuestos + consecutivo OT-NNNNNN) ocurre dentro de una transaccion. NO toca inventario, lotes,
 * Kardex ni ventas: la OT es un documento de trabajo (el cierre a venta es U.3). No contiene reglas
 * de negocio (validaciones y totales los aplica el servicio).
 */
public class OrdenTrabajoRepository {

    /** Cabecera + nombre de cliente y placa (JOIN), sin detalle. */
    private static final String SELECT_CABECERA =
            "SELECT ot.id_orden_trabajo, ot.numero_ot, ot.id_tercero, t.nombre AS nombre_cliente, "
          + "ot.id_vehiculo, v.placa AS placa_vehiculo, ot.fecha_ingreso, ot.fecha_entrega_estimada, "
          + "ot.kilometraje_ingreso, ot.motivo_ingreso, ot.diagnostico, ot.estado, ot.observaciones, "
          + "ot.id_usuario, ot.id_venta, ot.subtotal_servicios, ot.subtotal_repuestos, ot.total, "
          + "ot.fecha_creacion, ot.fecha_actualizacion "
          + "FROM ordenes_trabajo ot "
          + "JOIN terceros t ON ot.id_tercero = t.id_tercero "
          + "JOIN vehiculos v ON ot.id_vehiculo = v.id_vehiculo ";

    /** Lista las ordenes de trabajo (mas recientes primero), sin detalle. */
    public List<OrdenTrabajo> listar() throws SQLException {
        String sql = SELECT_CABECERA + "ORDER BY ot.id_orden_trabajo DESC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<OrdenTrabajo> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapearCabecera(rs));
            }
            return lista;
        }
    }

    /** Busca una OT por id, cargando su detalle de servicios y repuestos. */
    public Optional<OrdenTrabajo> buscarConDetalles(int idOrdenTrabajo) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            OrdenTrabajo ot;
            try (PreparedStatement ps = conn.prepareStatement(
                    SELECT_CABECERA + "WHERE ot.id_orden_trabajo = ?")) {
                ps.setInt(1, idOrdenTrabajo);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    ot = mapearCabecera(rs);
                }
            }
            ot.setServicios(cargarServicios(conn, idOrdenTrabajo));
            ot.setRepuestos(cargarRepuestos(conn, idOrdenTrabajo));
            return Optional.of(ot);
        }
    }

    /**
     * Crea la OT (cabecera + detalle) en una transaccion y devuelve el numero generado.
     * Los totales ya vienen calculados por el servicio.
     */
    public String crear(OrdenTrabajo ot) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String numero = siguienteNumero(conn);
                int idOt = insertarCabecera(conn, ot, numero);
                insertarDetalle(conn, idOt, ot);
                conn.commit();
                ot.setIdOrdenTrabajo(idOt);
                ot.setNumeroOt(numero);
                return numero;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoPrevio);
            }
        }
    }

    /** Actualiza la OT (cabecera + detalle: se reemplaza el detalle) en una transaccion. */
    public void actualizar(OrdenTrabajo ot) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                actualizarCabecera(conn, ot);
                borrarDetalle(conn, ot.getIdOrdenTrabajo());
                insertarDetalle(conn, ot.getIdOrdenTrabajo(), ot);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoPrevio);
            }
        }
    }

    /** Cambia el estado de una OT y marca la fecha de actualizacion. */
    public void cambiarEstado(int idOrdenTrabajo, EstadoOrdenTrabajo estado) throws SQLException {
        String sql = "UPDATE ordenes_trabajo SET estado=?, "
                   + "fecha_actualizacion=datetime('now','localtime') WHERE id_orden_trabajo=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idOrdenTrabajo);
            ps.executeUpdate();
        }
    }

    /**
     * Enlaza la OT con la venta generada al cerrarla (Paso U.3): fija id_venta, pasa el estado a
     * ENTREGADA y actualiza la fecha. Se invoca SOLO despues de que la venta se creo con exito (la
     * transaccion de la venta la maneja VentaService); deja la OT como documento ya facturado.
     *
     * @param idOrdenTrabajo OT a enlazar.
     * @param idVenta        id de la venta recien creada.
     */
    public void marcarEntregadaConVenta(int idOrdenTrabajo, int idVenta) throws SQLException {
        String sql = "UPDATE ordenes_trabajo SET estado='ENTREGADA', id_venta=?, "
                   + "fecha_actualizacion=datetime('now','localtime') WHERE id_orden_trabajo=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            ps.setInt(2, idOrdenTrabajo);
            ps.executeUpdate();
        }
    }

    // ----------------------------------------------------------------- helpers

    /** Consecutivo continuo OT-NNNNNN a partir del maximo numerico existente (dentro de la transaccion). */
    private String siguienteNumero(Connection conn) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTR(numero_ot, 4) AS INTEGER)) AS maximo "
                   + "FROM ordenes_trabajo WHERE numero_ot LIKE 'OT-%'";
        int siguiente = 1;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                siguiente = rs.getInt("maximo") + 1; // 0 si NULL (tabla vacia)
            }
        }
        return String.format("OT-%06d", siguiente);
    }

    private int insertarCabecera(Connection conn, OrdenTrabajo ot, String numero) throws SQLException {
        String sql = "INSERT INTO ordenes_trabajo "
                   + "(numero_ot, id_tercero, id_vehiculo, fecha_entrega_estimada, kilometraje_ingreso, "
                   + "motivo_ingreso, diagnostico, estado, observaciones, id_usuario, id_venta, "
                   + "subtotal_servicios, subtotal_repuestos, total) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, ot.getIdTercero());
            ps.setInt(3, ot.getIdVehiculo());
            ps.setString(4, ot.getFechaEntregaEstimada());
            ps.setDouble(5, ot.getKilometrajeIngreso());
            ps.setString(6, ot.getMotivoIngreso());
            ps.setString(7, ot.getDiagnostico());
            ps.setString(8, ot.getEstado().name());
            ps.setString(9, ot.getObservaciones());
            setIntOrNull(ps, 10, ot.getIdUsuario());
            setIntOrNull(ps, 11, ot.getIdVenta());
            ps.setDouble(12, valor(ot.getSubtotalServicios()));
            ps.setDouble(13, valor(ot.getSubtotalRepuestos()));
            ps.setDouble(14, valor(ot.getTotal()));
            ps.executeUpdate();
            try (ResultSet llaves = ps.getGeneratedKeys()) {
                if (llaves.next()) {
                    return llaves.getInt(1);
                }
            }
            throw new SQLException("No se pudo obtener el id generado de la orden de trabajo");
        }
    }

    private void actualizarCabecera(Connection conn, OrdenTrabajo ot) throws SQLException {
        String sql = "UPDATE ordenes_trabajo SET id_tercero=?, id_vehiculo=?, fecha_entrega_estimada=?, "
                   + "kilometraje_ingreso=?, motivo_ingreso=?, diagnostico=?, estado=?, observaciones=?, "
                   + "subtotal_servicios=?, subtotal_repuestos=?, total=?, "
                   + "fecha_actualizacion=datetime('now','localtime') WHERE id_orden_trabajo=?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, ot.getIdTercero());
            ps.setInt(2, ot.getIdVehiculo());
            ps.setString(3, ot.getFechaEntregaEstimada());
            ps.setDouble(4, ot.getKilometrajeIngreso());
            ps.setString(5, ot.getMotivoIngreso());
            ps.setString(6, ot.getDiagnostico());
            ps.setString(7, ot.getEstado().name());
            ps.setString(8, ot.getObservaciones());
            ps.setDouble(9, valor(ot.getSubtotalServicios()));
            ps.setDouble(10, valor(ot.getSubtotalRepuestos()));
            ps.setDouble(11, valor(ot.getTotal()));
            ps.setInt(12, ot.getIdOrdenTrabajo());
            ps.executeUpdate();
        }
    }

    /** Inserta el detalle (servicios y repuestos) de la OT. */
    private void insertarDetalle(Connection conn, int idOt, OrdenTrabajo ot) throws SQLException {
        String sqlServ = "INSERT INTO orden_trabajo_servicios "
                       + "(id_orden_trabajo, id_item, cantidad, precio_unitario, subtotal) "
                       + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sqlServ)) {
            for (OrdenTrabajoServicio s : ot.getServicios()) {
                ps.setInt(1, idOt);
                ps.setInt(2, s.getIdItem());
                ps.setDouble(3, valor(s.getCantidad()));
                ps.setDouble(4, valor(s.getPrecioUnitario()));
                ps.setDouble(5, valor(s.getSubtotal()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
        String sqlRep = "INSERT INTO orden_trabajo_repuestos "
                      + "(id_orden_trabajo, id_item, id_bodega_salida, cantidad, precio_unitario, subtotal) "
                      + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sqlRep)) {
            for (OrdenTrabajoRepuesto r : ot.getRepuestos()) {
                ps.setInt(1, idOt);
                ps.setInt(2, r.getIdItem());
                setIntOrNull(ps, 3, r.getIdBodegaSalida());
                ps.setDouble(4, valor(r.getCantidad()));
                ps.setDouble(5, valor(r.getPrecioUnitario()));
                ps.setDouble(6, valor(r.getSubtotal()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void borrarDetalle(Connection conn, int idOt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM orden_trabajo_servicios WHERE id_orden_trabajo=?")) {
            ps.setInt(1, idOt);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM orden_trabajo_repuestos WHERE id_orden_trabajo=?")) {
            ps.setInt(1, idOt);
            ps.executeUpdate();
        }
    }

    private List<OrdenTrabajoServicio> cargarServicios(Connection conn, int idOt) throws SQLException {
        String sql = "SELECT s.id_ot_servicio, s.id_orden_trabajo, s.id_item, i.nombre AS nombre_item, "
                   + "s.cantidad, s.precio_unitario, s.subtotal "
                   + "FROM orden_trabajo_servicios s JOIN items i ON s.id_item = i.id_item "
                   + "WHERE s.id_orden_trabajo = ? ORDER BY s.id_ot_servicio";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idOt);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrdenTrabajoServicio> lista = new ArrayList<>();
                while (rs.next()) {
                    OrdenTrabajoServicio s = new OrdenTrabajoServicio();
                    s.setIdOtServicio(rs.getInt("id_ot_servicio"));
                    s.setIdOrdenTrabajo(rs.getInt("id_orden_trabajo"));
                    s.setIdItem(rs.getInt("id_item"));
                    s.setNombreItem(rs.getString("nombre_item"));
                    s.setCantidad(bd(rs.getDouble("cantidad")));
                    s.setPrecioUnitario(bd(rs.getDouble("precio_unitario")));
                    s.setSubtotal(bd(rs.getDouble("subtotal")));
                    lista.add(s);
                }
                return lista;
            }
        }
    }

    private List<OrdenTrabajoRepuesto> cargarRepuestos(Connection conn, int idOt) throws SQLException {
        String sql = "SELECT r.id_ot_repuesto, r.id_orden_trabajo, r.id_item, i.nombre AS nombre_item, "
                   + "r.id_bodega_salida, b.nombre AS nombre_bodega, "
                   + "r.cantidad, r.precio_unitario, r.subtotal "
                   + "FROM orden_trabajo_repuestos r JOIN items i ON r.id_item = i.id_item "
                   + "LEFT JOIN bodegas b ON r.id_bodega_salida = b.id_bodega "
                   + "WHERE r.id_orden_trabajo = ? ORDER BY r.id_ot_repuesto";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idOt);
            try (ResultSet rs = ps.executeQuery()) {
                List<OrdenTrabajoRepuesto> lista = new ArrayList<>();
                while (rs.next()) {
                    OrdenTrabajoRepuesto r = new OrdenTrabajoRepuesto();
                    r.setIdOtRepuesto(rs.getInt("id_ot_repuesto"));
                    r.setIdOrdenTrabajo(rs.getInt("id_orden_trabajo"));
                    r.setIdItem(rs.getInt("id_item"));
                    r.setNombreItem(rs.getString("nombre_item"));
                    int idBod = rs.getInt("id_bodega_salida");
                    r.setIdBodegaSalida(rs.wasNull() ? null : idBod);
                    r.setNombreBodega(rs.getString("nombre_bodega"));
                    r.setCantidad(bd(rs.getDouble("cantidad")));
                    r.setPrecioUnitario(bd(rs.getDouble("precio_unitario")));
                    r.setSubtotal(bd(rs.getDouble("subtotal")));
                    lista.add(r);
                }
                return lista;
            }
        }
    }

    private OrdenTrabajo mapearCabecera(ResultSet rs) throws SQLException {
        OrdenTrabajo ot = new OrdenTrabajo();
        ot.setIdOrdenTrabajo(rs.getInt("id_orden_trabajo"));
        ot.setNumeroOt(rs.getString("numero_ot"));
        ot.setIdTercero(rs.getInt("id_tercero"));
        ot.setNombreCliente(rs.getString("nombre_cliente"));
        ot.setIdVehiculo(rs.getInt("id_vehiculo"));
        ot.setPlacaVehiculo(rs.getString("placa_vehiculo"));
        ot.setFechaIngreso(rs.getString("fecha_ingreso"));
        ot.setFechaEntregaEstimada(rs.getString("fecha_entrega_estimada"));
        ot.setKilometrajeIngreso(rs.getDouble("kilometraje_ingreso"));
        ot.setMotivoIngreso(rs.getString("motivo_ingreso"));
        ot.setDiagnostico(rs.getString("diagnostico"));
        ot.setEstado(EstadoOrdenTrabajo.desde(rs.getString("estado")));
        ot.setObservaciones(rs.getString("observaciones"));
        int idUsuario = rs.getInt("id_usuario");
        ot.setIdUsuario(rs.wasNull() ? null : idUsuario);
        int idVenta = rs.getInt("id_venta");
        ot.setIdVenta(rs.wasNull() ? null : idVenta);
        ot.setSubtotalServicios(bd(rs.getDouble("subtotal_servicios")));
        ot.setSubtotalRepuestos(bd(rs.getDouble("subtotal_repuestos")));
        ot.setTotal(bd(rs.getDouble("total")));
        ot.setFechaCreacion(rs.getString("fecha_creacion"));
        ot.setFechaActualizacion(rs.getString("fecha_actualizacion"));
        return ot;
    }

    private static void setIntOrNull(PreparedStatement ps, int idx, Integer valor) throws SQLException {
        if (valor == null) {
            ps.setNull(idx, Types.INTEGER);
        } else {
            ps.setInt(idx, valor);
        }
    }

    /** REAL de SQLite -> BigDecimal. */
    private static BigDecimal bd(double valor) {
        return BigDecimal.valueOf(valor);
    }

    /** BigDecimal -> double para almacenar en REAL (null se trata como 0). */
    private static double valor(BigDecimal valor) {
        return valor == null ? 0d : valor.doubleValue();
    }
}
