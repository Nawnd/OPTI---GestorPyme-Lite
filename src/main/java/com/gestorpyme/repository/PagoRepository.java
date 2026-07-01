package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.model.Pago;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de pagos (tabla pagos). Capa: repository (encapsula JDBC).
 */
public class PagoRepository {

    /** Inserta un pago y devuelve su id generado. Si no se indica fecha, la BD usa la actual. */
    public int insertar(Pago pago) throws SQLException {
        boolean conFecha = pago.getFecha() != null && !pago.getFecha().trim().isEmpty();
        String sql = conFecha
                ? "INSERT INTO pagos (id_venta, medio_pago, valor, referencia, observaciones, fecha) VALUES (?, ?, ?, ?, ?, ?)"
                : "INSERT INTO pagos (id_venta, medio_pago, valor, referencia, observaciones) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, pago.getIdVenta());
            ps.setString(2, pago.getMedioPago().name());
            ps.setDouble(3, pago.getValor().doubleValue());
            ps.setString(4, pago.getReferencia());
            ps.setString(5, pago.getObservaciones());
            if (conFecha) {
                ps.setString(6, pago.getFecha().trim());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    pago.setIdPago(rs.getInt(1));
                }
            }
            return pago.getIdPago();
        }
    }

    /** Lista los pagos de una venta (mas antiguos primero). */
    public List<Pago> listarPorVenta(int idVenta) throws SQLException {
        String sql = "SELECT id_pago, id_venta, medio_pago, valor, fecha, referencia, observaciones "
                   + "FROM pagos WHERE id_venta = ? ORDER BY id_pago";
        List<Pago> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Pago p = new Pago();
                    p.setIdPago(rs.getInt("id_pago"));
                    p.setIdVenta(rs.getInt("id_venta"));
                    p.setMedioPago(MedioPago.desde(rs.getString("medio_pago")));
                    p.setValor(BigDecimal.valueOf(rs.getDouble("valor")));
                    p.setFecha(rs.getString("fecha"));
                    p.setReferencia(rs.getString("referencia"));
                    p.setObservaciones(rs.getString("observaciones"));
                    lista.add(p);
                }
            }
        }
        return lista;
    }

    /** Suma de los pagos registrados para una venta (0 si no hay). */
    public BigDecimal totalPagado(int idVenta) throws SQLException {
        String sql = "SELECT COALESCE(SUM(valor), 0) AS total FROM pagos WHERE id_venta = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return BigDecimal.valueOf(rs.getDouble("total"));
                }
                return BigDecimal.ZERO;
            }
        }
    }
}
