package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Subtipo;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de la tabla 'subtipos'. Encapsula el JDBC/SQL: ninguna otra capa
 * ejecuta SQL sobre 'subtipos'. Usa consultas parametrizadas para evitar inyeccion.
 * Capa: repository.
 */
public class SubtipoRepository {

    private static final String SELECT_BASE =
            "SELECT s.id_subtipo, s.id_categoria, c.nombre AS categoria_nombre, s.nombre, s.estado "
          + "FROM subtipos s LEFT JOIN categorias c ON c.id_categoria = s.id_categoria";

    /** Lista los subtipos ACTIVOS de una categoria, ordenados por nombre. */
    public List<Subtipo> listarPorCategoria(int idCategoria) throws SQLException {
        String sql = SELECT_BASE + " WHERE s.id_categoria = ? AND s.estado = 'ACTIVO' ORDER BY s.nombre";
        List<Subtipo> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idCategoria);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Lista todos los subtipos ACTIVOS (con el nombre de su categoria). */
    public List<Subtipo> listar() throws SQLException {
        String sql = SELECT_BASE + " WHERE s.estado = 'ACTIVO' ORDER BY c.nombre, s.nombre";
        List<Subtipo> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /** Busca un subtipo por su identificador (para validar coherencia con la categoria). */
    public Optional<Subtipo> buscarPorId(int idSubtipo) throws SQLException {
        String sql = SELECT_BASE + " WHERE s.id_subtipo = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idSubtipo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Inserta un subtipo y devuelve el id generado. El nombre debe ser unico dentro
     * de la categoria (restriccion UNIQUE(id_categoria, nombre) en la tabla).
     */
    public int insertar(Subtipo subtipo) throws SQLException {
        String sql = "INSERT INTO subtipos (id_categoria, nombre, estado) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, subtipo.getIdCategoria());
            ps.setString(2, subtipo.getNombre());
            ps.setString(3, subtipo.getEstado() == null
                    ? EstadoRegistro.ACTIVO.name() : subtipo.getEstado().name());
            ps.executeUpdate();
            try (ResultSet llaves = ps.getGeneratedKeys()) {
                if (llaves.next()) {
                    return llaves.getInt(1);
                }
            }
            throw new SQLException("No se pudo obtener el id generado del subtipo");
        }
    }

    /** Construye un {@link Subtipo} a partir de la fila actual del ResultSet. */
    private Subtipo mapear(ResultSet rs) throws SQLException {
        Subtipo s = new Subtipo();
        s.setIdSubtipo(rs.getInt("id_subtipo"));
        s.setIdCategoria(rs.getInt("id_categoria"));
        s.setNombreCategoria(rs.getString("categoria_nombre"));
        s.setNombre(rs.getString("nombre"));
        s.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        return s;
    }
}
