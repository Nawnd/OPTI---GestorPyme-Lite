package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

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
 * Persistencia de vehiculos (Paso U.1).
 *
 * Capa: repository (JDBC con PreparedStatement). No contiene reglas de negocio.
 * El listado resuelve el nombre del cliente con un JOIN a terceros (solo lectura).
 */
public class VehiculoRepository {

    /** Columnas del vehiculo en el orden esperado por el mapeo (con alias del cliente). */
    private static final String SELECT_BASE =
            "SELECT v.id_vehiculo, v.id_tercero, t.nombre AS nombre_cliente, v.placa, v.marca, "
          + "v.linea, v.anio, v.color, v.kilometraje, v.observaciones, v.estado, v.fecha_creacion "
          + "FROM vehiculos v JOIN terceros t ON v.id_tercero = t.id_tercero ";

    /** Lista todos los vehiculos ordenados por placa, incluyendo el nombre del cliente. */
    public List<Vehiculo> listar() throws SQLException {
        String sql = SELECT_BASE + "ORDER BY v.placa";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Vehiculo> lista = new ArrayList<>();
            while (rs.next()) {
                lista.add(mapear(rs));
            }
            return lista;
        }
    }

    /** Lista los vehiculos de un cliente concreto (para historial/seleccion). */
    public List<Vehiculo> listarPorCliente(int idTercero) throws SQLException {
        String sql = SELECT_BASE + "WHERE v.id_tercero = ? ORDER BY v.placa";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTercero);
            try (ResultSet rs = ps.executeQuery()) {
                List<Vehiculo> lista = new ArrayList<>();
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
                return lista;
            }
        }
    }

    /** Busca un vehiculo por id. */
    public Optional<Vehiculo> buscarPorId(int idVehiculo) throws SQLException {
        String sql = SELECT_BASE + "WHERE v.id_vehiculo = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idVehiculo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapear(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Indica si ya existe un vehiculo con la placa dada, excluyendo un id (para permitir editar
     * el mismo vehiculo sin falso positivo). La placa debe venir ya normalizada por el servicio.
     */
    public boolean existePlaca(String placa, int idExcluir) throws SQLException {
        String sql = "SELECT 1 FROM vehiculos WHERE placa = ? AND id_vehiculo <> ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, placa);
            ps.setInt(2, idExcluir);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserta un vehiculo y devuelve el id generado. */
    public int insertar(Vehiculo v) throws SQLException {
        String sql = "INSERT INTO vehiculos "
                   + "(id_tercero, placa, marca, linea, anio, color, kilometraje, observaciones, estado) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignarCampos(ps, v);
            ps.executeUpdate();
            try (ResultSet llaves = ps.getGeneratedKeys()) {
                if (llaves.next()) {
                    return llaves.getInt(1);
                }
            }
            throw new SQLException("No se pudo obtener el id generado del vehiculo");
        }
    }

    /** Actualiza los campos editables de un vehiculo existente y marca la fecha de actualizacion. */
    public void actualizar(Vehiculo v) throws SQLException {
        String sql = "UPDATE vehiculos SET id_tercero=?, placa=?, marca=?, linea=?, anio=?, color=?, "
                   + "kilometraje=?, observaciones=?, estado=?, "
                   + "fecha_actualizacion=datetime('now','localtime') WHERE id_vehiculo=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            asignarCampos(ps, v);
            ps.setInt(10, v.getIdVehiculo());
            ps.executeUpdate();
        }
    }

    /** Cambia el estado (alta/baja logica) de un vehiculo. */
    public void cambiarEstado(int idVehiculo, EstadoRegistro estado) throws SQLException {
        String sql = "UPDATE vehiculos SET estado=?, fecha_actualizacion=datetime('now','localtime') "
                   + "WHERE id_vehiculo=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idVehiculo);
            ps.executeUpdate();
        }
    }

    /** Asigna los 9 campos editables (posiciones 1..9) al PreparedStatement. */
    private void asignarCampos(PreparedStatement ps, Vehiculo v) throws SQLException {
        ps.setInt(1, v.getIdTercero());
        ps.setString(2, v.getPlaca());
        ps.setString(3, v.getMarca());
        ps.setString(4, v.getLinea());
        if (v.getAnio() == null) {
            ps.setNull(5, Types.INTEGER);
        } else {
            ps.setInt(5, v.getAnio());
        }
        ps.setString(6, v.getColor());
        ps.setDouble(7, v.getKilometraje());
        ps.setString(8, v.getObservaciones());
        ps.setString(9, v.getEstado().name());
    }

    /** Construye un {@link Vehiculo} a partir de la fila actual del ResultSet. */
    private Vehiculo mapear(ResultSet rs) throws SQLException {
        Vehiculo v = new Vehiculo();
        v.setIdVehiculo(rs.getInt("id_vehiculo"));
        v.setIdTercero(rs.getInt("id_tercero"));
        v.setNombreCliente(rs.getString("nombre_cliente"));
        v.setPlaca(rs.getString("placa"));
        v.setMarca(rs.getString("marca"));
        v.setLinea(rs.getString("linea"));
        int anio = rs.getInt("anio");
        v.setAnio(rs.wasNull() ? null : anio);
        v.setColor(rs.getString("color"));
        v.setKilometraje(rs.getDouble("kilometraje"));
        v.setObservaciones(rs.getString("observaciones"));
        v.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        v.setFechaCreacion(rs.getString("fecha_creacion"));
        return v;
    }
}
