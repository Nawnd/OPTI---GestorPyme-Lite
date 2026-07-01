package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla 'bodegas'. Encapsula el JDBC/SQL.
 * Capa: repository.
 */
public class BodegaRepository {

    private static final String COLUMNAS =
            "id_bodega, nombre, ubicacion, estado, fecha_creacion";

    /** Lista todas las bodegas ordenadas por nombre. */
    public List<Bodega> listar() throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM bodegas ORDER BY nombre");
    }

    /** Lista solo las bodegas activas (para selectores). */
    public List<Bodega> listarActivas() throws SQLException {
        return consultar("SELECT " + COLUMNAS + " FROM bodegas WHERE estado='ACTIVO' ORDER BY nombre");
    }

    /**
     * Busca bodegas ACTIVAS por nombre o ubicacion (para selectores inteligentes).
     * LIKE con PreparedStatement y limite de resultados.
     *
     * @param texto  texto a buscar (vacio devuelve las primeras {@code limite}).
     * @param limite maximo de filas.
     * @return bodegas que coinciden, ordenadas por nombre.
     */
    public List<Bodega> buscar(String texto, int limite) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        String sql = "SELECT " + COLUMNAS + " FROM bodegas "
                   + "WHERE estado = 'ACTIVO' AND (nombre LIKE ? OR IFNULL(ubicacion,'') LIKE ?) "
                   + "ORDER BY nombre LIMIT ?";
        List<Bodega> lista = new ArrayList<>();
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

    private List<Bodega> consultar(String sql) throws SQLException {
        List<Bodega> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /** Indica si ya existe otra bodega con el mismo nombre. */
    public boolean existeNombre(String nombre, int idExcluir) throws SQLException {
        String sql = "SELECT 1 FROM bodegas WHERE nombre = ? AND id_bodega <> ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setInt(2, idExcluir);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserta una nueva bodega y devuelve el id generado. */
    public int insertar(Bodega bodega) throws SQLException {
        String sql = "INSERT INTO bodegas (nombre, ubicacion, estado) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, bodega.getNombre());
            ps.setString(2, bodega.getUbicacion());
            ps.setString(3, bodega.getEstado().name());
            ps.executeUpdate();
            try (ResultSet llaves = ps.getGeneratedKeys()) {
                if (llaves.next()) {
                    return llaves.getInt(1);
                }
            }
            throw new SQLException("No se pudo obtener el id generado de la bodega");
        }
    }

    /** Actualiza los campos editables de una bodega existente. */
    public void actualizar(Bodega bodega) throws SQLException {
        String sql = "UPDATE bodegas SET nombre=?, ubicacion=?, estado=? WHERE id_bodega=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, bodega.getNombre());
            ps.setString(2, bodega.getUbicacion());
            ps.setString(3, bodega.getEstado().name());
            ps.setInt(4, bodega.getIdBodega());
            ps.executeUpdate();
        }
    }

    /** Cambia el estado (baja/alta logica) de una bodega. */
    public void cambiarEstado(int idBodega, EstadoRegistro estado) throws SQLException {
        String sql = "UPDATE bodegas SET estado=? WHERE id_bodega=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idBodega);
            ps.executeUpdate();
        }
    }

    private Bodega mapear(ResultSet rs) throws SQLException {
        Bodega bodega = new Bodega();
        bodega.setIdBodega(rs.getInt("id_bodega"));
        bodega.setNombre(rs.getString("nombre"));
        bodega.setUbicacion(rs.getString("ubicacion"));
        bodega.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        bodega.setFechaCreacion(rs.getString("fecha_creacion"));
        return bodega;
    }
}
