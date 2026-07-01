package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoCuenta;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de cuentas por cobrar (tabla cuentas_por_cobrar) y abonos
 * (tabla abonos_cuenta). Capa: repository (encapsula JDBC).
 *
 * El registro de un abono es TRANSACCIONAL: inserta el abono y actualiza el
 * acumulado pagado, el saldo y el estado de la cuenta en una sola operacion.
 */
public class CuentaRepository {

    /** Lista las cuentas por cobrar con numero de venta y nombre del cliente. */
    public List<CuentaPorCobrar> listar() throws SQLException {
        String sql = "SELECT c.id_cuenta, c.id_venta, v.numero_venta, c.id_tercero, t.nombre AS tercero_nombre, "
                   + "c.valor_total, c.valor_pagado, c.saldo_pendiente, c.fecha_vencimiento, c.estado "
                   + "FROM cuentas_por_cobrar c "
                   + "JOIN ventas v ON v.id_venta = c.id_venta "
                   + "JOIN terceros t ON t.id_tercero = c.id_tercero "
                   + "ORDER BY c.id_cuenta DESC";
        List<CuentaPorCobrar> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
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
        return lista;
    }

    /**
     * Busca la cuenta por cobrar asociada a una venta (si existe). Se usa para que el
     * modulo Pagos pueda enrutar el cobro hacia la cuenta (registrar un abono) en lugar
     * de mantener un libro paralelo en la tabla pagos.
     *
     * @param idVenta identificador de la venta.
     * @return un {@link Optional} con la cuenta, o vacio si la venta no tiene cuenta (p. ej. contado).
     * @throws SQLException ante errores de base de datos.
     */
    public Optional<CuentaPorCobrar> buscarPorVenta(int idVenta) throws SQLException {
        String sql = "SELECT c.id_cuenta, c.id_venta, v.numero_venta, c.id_tercero, t.nombre AS tercero_nombre, "
                   + "c.valor_total, c.valor_pagado, c.saldo_pendiente, c.fecha_vencimiento, c.estado "
                   + "FROM cuentas_por_cobrar c "
                   + "JOIN ventas v ON v.id_venta = c.id_venta "
                   + "JOIN terceros t ON t.id_tercero = c.id_tercero "
                   + "WHERE c.id_venta = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
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
                return Optional.of(c);
            }
        }
    }
    public List<Abono> listarAbonos(int idCuenta) throws SQLException {
        String sql = "SELECT id_abono, id_cuenta, valor, fecha, medio_pago, observaciones "
                   + "FROM abonos_cuenta WHERE id_cuenta = ? ORDER BY id_abono";
        List<Abono> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCuenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Abono a = new Abono();
                    a.setIdAbono(rs.getInt("id_abono"));
                    a.setIdCuenta(rs.getInt("id_cuenta"));
                    a.setValor(BigDecimal.valueOf(rs.getDouble("valor")));
                    a.setFecha(rs.getString("fecha"));
                    a.setMedioPago(MedioPago.desde(rs.getString("medio_pago")));
                    a.setObservaciones(rs.getString("observaciones"));
                    lista.add(a);
                }
            }
        }
        return lista;
    }

    /**
     * Registra un abono de forma transaccional: inserta el abono y actualiza
     * valor_pagado, saldo_pendiente y estado de la cuenta.
     *
     * Estado resultante: PAGADA si el saldo llega a 0; ABONADA (parcial) si queda saldo.
     *
     * @param abono abono a registrar (id_cuenta, valor, medio).
     * @throws ValidacionException si el abono supera el saldo pendiente.
     * @throws SQLException        ante errores de base de datos.
     */
    public void registrarAbono(Abono abono) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommitPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                BigDecimal[] totales = leerTotales(conn, abono.getIdCuenta());
                BigDecimal valorTotal = totales[0];
                BigDecimal pagadoActual = totales[1];
                BigDecimal saldo = valorTotal.subtract(pagadoActual);

                if (abono.getValor().compareTo(saldo) > 0) {
                    throw new ValidacionException("El abono (" + abono.getValor().toPlainString()
                            + ") no puede superar el saldo pendiente (" + saldo.toPlainString() + ").");
                }

                insertarAbono(conn, abono);

                BigDecimal nuevoPagado = pagadoActual.add(abono.getValor());
                BigDecimal nuevoSaldo = valorTotal.subtract(nuevoPagado);
                String estado = (nuevoSaldo.compareTo(BigDecimal.ZERO) <= 0) ? "PAGADA"
                        : (nuevoPagado.compareTo(BigDecimal.ZERO) > 0 ? "ABONADA" : "PENDIENTE");

                actualizarCuenta(conn, abono.getIdCuenta(), nuevoPagado, nuevoSaldo, estado);
                // Coherencia financiera (Opcion A): si la cuenta queda saldada, la venta
                // asociada pasa a PAGADA en la MISMA transaccion (evita desincronizacion).
                if ("PAGADA".equals(estado)) {
                    int idVenta = obtenerIdVenta(conn, abono.getIdCuenta());
                    actualizarEstadoVenta(conn, idVenta, "PAGADA");
                }
                conn.commit();
            } catch (SQLException | ValidacionException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitPrevio);
            }
        }
    }

    /** Lee [valor_total, valor_pagado] de una cuenta dentro de la transaccion. */
    private BigDecimal[] leerTotales(Connection conn, int idCuenta) throws SQLException {
        String sql = "SELECT valor_total, valor_pagado FROM cuentas_por_cobrar WHERE id_cuenta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCuenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("La cuenta por cobrar no existe: " + idCuenta);
                }
                return new BigDecimal[]{
                        BigDecimal.valueOf(rs.getDouble("valor_total")),
                        BigDecimal.valueOf(rs.getDouble("valor_pagado"))
                };
            }
        }
    }

    private void insertarAbono(Connection conn, Abono a) throws SQLException {
        boolean conFecha = a.getFecha() != null && !a.getFecha().trim().isEmpty();
        String sql = conFecha
                ? "INSERT INTO abonos_cuenta (id_cuenta, valor, medio_pago, observaciones, fecha) VALUES (?, ?, ?, ?, ?)"
                : "INSERT INTO abonos_cuenta (id_cuenta, valor, medio_pago, observaciones) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, a.getIdCuenta());
            ps.setDouble(2, a.getValor().doubleValue());
            ps.setString(3, (a.getMedioPago() == null ? MedioPago.EFECTIVO : a.getMedioPago()).name());
            ps.setString(4, a.getObservaciones());
            if (conFecha) {
                ps.setString(5, a.getFecha().trim());
            }
            ps.executeUpdate();
        }
    }

    private void actualizarCuenta(Connection conn, int idCuenta, BigDecimal pagado,
                                  BigDecimal saldo, String estado) throws SQLException {
        String sql = "UPDATE cuentas_por_cobrar "
                   + "SET valor_pagado = ?, saldo_pendiente = ?, estado = ? WHERE id_cuenta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, pagado.doubleValue());
            ps.setDouble(2, Math.max(0, saldo.doubleValue()));
            ps.setString(3, estado);
            ps.setInt(4, idCuenta);
            ps.executeUpdate();
        }
    }

    /** Devuelve el id_venta asociado a una cuenta por cobrar. */
    private int obtenerIdVenta(Connection conn, int idCuenta) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id_venta FROM cuentas_por_cobrar WHERE id_cuenta = ?")) {
            ps.setInt(1, idCuenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("La cuenta por cobrar no existe: " + idCuenta);
                }
                return rs.getInt("id_venta");
            }
        }
    }

    /** Actualiza el estado de una venta (usado al saldar su cuenta por cobrar). */
    private void actualizarEstadoVenta(Connection conn, int idVenta, String estado) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ventas SET estado = ? WHERE id_venta = ?")) {
            ps.setString(1, estado);
            ps.setInt(2, idVenta);
            ps.executeUpdate();
        }
    }
}
