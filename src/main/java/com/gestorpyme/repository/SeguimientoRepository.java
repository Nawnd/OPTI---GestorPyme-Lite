package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoSeguimiento;
import com.gestorpyme.domain.enums.TipoSeguimiento;
import com.gestorpyme.domain.model.Seguimiento;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de la tabla 'crm_seguimientos'.
 * Encapsula el SQL del modulo CRM basico (capa repository). Usa PreparedStatement.
 */
public class SeguimientoRepository {

    /** SELECT base con JOIN al tercero para resolver su nombre. */
    private static final String SELECT_BASE =
            "SELECT s.id_seguimiento, s.id_tercero, s.tipo, s.descripcion, s.fecha, s.estado, s.id_usuario, "
          + "t.nombre AS tercero_nombre "
          + "FROM crm_seguimientos s "
          + "JOIN terceros t ON t.id_tercero = s.id_tercero ";

    /** Lista todos los seguimientos, del mas reciente al mas antiguo. */
    public List<Seguimiento> listar() throws SQLException {
        String sql = SELECT_BASE + "ORDER BY s.fecha DESC, s.id_seguimiento DESC";
        return ejecutar(sql, null);
    }

    /** Lista los seguimientos de un tercero concreto. */
    public List<Seguimiento> listarPorTercero(int idTercero) throws SQLException {
        String sql = SELECT_BASE + "WHERE s.id_tercero = ? ORDER BY s.fecha DESC, s.id_seguimiento DESC";
        return ejecutar(sql, idTercero);
    }

    /** Inserta un seguimiento nuevo y devuelve su id generado. */
    public int insertar(Seguimiento s) throws SQLException {
        String sql = "INSERT INTO crm_seguimientos (id_tercero, tipo, descripcion, estado, id_usuario) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, s.getIdTercero());
            ps.setString(2, s.getTipo().name());
            ps.setString(3, s.getDescripcion());
            ps.setString(4, s.getEstado().name());
            if (s.getIdUsuario() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, s.getIdUsuario());
            }
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    s.setIdSeguimiento(id);
                    return id;
                }
            }
            return 0;
        }
    }

    /** Cambia el estado de un seguimiento (p. ej. marcar CERRADO). */
    public void cambiarEstado(int idSeguimiento, EstadoSeguimiento estado) throws SQLException {
        String sql = "UPDATE crm_seguimientos SET estado = ? WHERE id_seguimiento = ?";
        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idSeguimiento);
            ps.executeUpdate();
        }
    }

    /** Ejecuta el SELECT_BASE con un parametro opcional de tercero. */
    private List<Seguimiento> ejecutar(String sql, Integer idTercero) throws SQLException {
        List<Seguimiento> lista = new ArrayList<>();
        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            if (idTercero != null) {
                ps.setInt(1, idTercero);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Construye un Seguimiento a partir de la fila actual del ResultSet. */
    private Seguimiento mapear(ResultSet rs) throws SQLException {
        Seguimiento s = new Seguimiento();
        s.setIdSeguimiento(rs.getInt("id_seguimiento"));
        s.setIdTercero(rs.getInt("id_tercero"));
        s.setTipo(TipoSeguimiento.desde(rs.getString("tipo")));
        s.setDescripcion(rs.getString("descripcion"));
        s.setFecha(rs.getString("fecha"));
        s.setEstado(EstadoSeguimiento.desde(rs.getString("estado")));

        int idUsuario = rs.getInt("id_usuario");
        s.setIdUsuario(rs.wasNull() ? null : idUsuario);

        s.setNombreTercero(rs.getString("tercero_nombre"));
        return s;
    }
}
