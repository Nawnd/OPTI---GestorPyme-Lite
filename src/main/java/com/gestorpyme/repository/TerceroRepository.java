package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de la tabla 'terceros'. Encapsula el JDBC/SQL: ninguna otra capa
 * ejecuta SQL sobre 'terceros'. Usa consultas parametrizadas para evitar inyeccion.
 * Capa: repository.
 */
public class TerceroRepository {

    /** Columnas en el orden esperado por el metodo de mapeo. */
    private static final String COLUMNAS =
            "id_tercero, tipo_tercero, nombre, documento, telefono, correo, "
          + "direccion, estado, observaciones, fecha_creacion";

    /**
     * Lista los terceros cuyo tipo este en la lista indicada, ordenados por nombre.
     *
     * @param tipos tipos a incluir (p. ej. CLIENTE y PROSPECTO).
     * @return lista de terceros (vacia si no hay coincidencias o la lista de tipos es vacia).
     */
    public List<Tercero> listarPorTipos(List<TipoTercero> tipos) throws SQLException {
        if (tipos == null || tipos.isEmpty()) {
            return new ArrayList<>();
        }
        // Genera tantos marcadores '?' como tipos, para la clausula IN (...).
        String marcadores = String.join(",", Collections.nCopies(tipos.size(), "?"));
        String sql = "SELECT " + COLUMNAS + " FROM terceros "
                   + "WHERE tipo_tercero IN (" + marcadores + ") ORDER BY nombre";

        List<Tercero> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < tipos.size(); i++) {
                ps.setString(i + 1, tipos.get(i).name());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Busca terceros ACTIVOS de ciertos tipos por nombre, documento, telefono o correo
     * (para selectores inteligentes). LIKE con PreparedStatement y limite de resultados.
     *
     * @param texto  texto a buscar (vacio devuelve los primeros {@code limite}).
     * @param tipos  tipos de tercero a incluir (CLIENTE/PROSPECTO/PROVEEDOR).
     * @param limite maximo de filas.
     * @return terceros que coinciden, ordenados por nombre.
     */
    public List<Tercero> buscarPorTipos(String texto, List<TipoTercero> tipos, int limite)
            throws SQLException {
        if (tipos == null || tipos.isEmpty()) {
            return new ArrayList<>();
        }
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        String marcadores = String.join(",", Collections.nCopies(tipos.size(), "?"));
        String sql = "SELECT " + COLUMNAS + " FROM terceros "
                   + "WHERE tipo_tercero IN (" + marcadores + ") AND estado = 'ACTIVO' "
                   + "AND (nombre LIKE ? OR documento LIKE ? OR telefono LIKE ? OR correo LIKE ?) "
                   + "ORDER BY nombre LIMIT ?";
        List<Tercero> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int i = 1;
            for (TipoTercero t : tipos) {
                ps.setString(i++, t.name());
            }
            ps.setString(i++, patron);
            ps.setString(i++, patron);
            ps.setString(i++, patron);
            ps.setString(i++, patron);
            ps.setInt(i, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Conveniencia: busca clientes y prospectos activos. */
    public List<Tercero> buscarClientesYProspectos(String texto, int limite) throws SQLException {
        return buscarPorTipos(texto, java.util.Arrays.asList(TipoTercero.CLIENTE, TipoTercero.PROSPECTO), limite);
    }

    /** Conveniencia: busca proveedores activos (para ordenes de compra). */
    /** Lista todos los proveedores (activos e inactivos), ordenados por nombre. */
    public List<Tercero> listarProveedores() throws SQLException {
        return listarPorTipos(java.util.Collections.singletonList(TipoTercero.PROVEEDOR));
    }

    public List<Tercero> buscarProveedores(String texto, int limite) throws SQLException {
        return buscarPorTipos(texto, java.util.Collections.singletonList(TipoTercero.PROVEEDOR), limite);
    }

    /** Busca un tercero por su identificador. */
    public Optional<Tercero> buscarPorId(int idTercero) throws SQLException {
        String sql = "SELECT " + COLUMNAS + " FROM terceros WHERE id_tercero = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idTercero);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Inserta un nuevo tercero y devuelve el id generado por la base de datos.
     */
    public int insertar(Tercero tercero) throws SQLException {
        String sql = "INSERT INTO terceros "
                   + "(tipo_tercero, nombre, documento, telefono, correo, direccion, estado, observaciones) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignarCampos(ps, tercero);
            ps.executeUpdate();
            try (ResultSet llaves = ps.getGeneratedKeys()) {
                if (llaves.next()) {
                    return llaves.getInt(1);
                }
            }
            throw new SQLException("No se pudo obtener el id generado del tercero");
        }
    }

    /** Actualiza todos los campos editables de un tercero existente. */
    public void actualizar(Tercero tercero) throws SQLException {
        String sql = "UPDATE terceros SET tipo_tercero=?, nombre=?, documento=?, telefono=?, "
                   + "correo=?, direccion=?, estado=?, observaciones=? WHERE id_tercero=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            asignarCampos(ps, tercero);
            ps.setInt(9, tercero.getIdTercero());
            ps.executeUpdate();
        }
    }

    /** Cambia el estado (baja/alta logica) de un tercero. */
    public void cambiarEstado(int idTercero, EstadoRegistro estado) throws SQLException {
        String sql = "UPDATE terceros SET estado=? WHERE id_tercero=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idTercero);
            ps.executeUpdate();
        }
    }

    /** Asigna los 8 campos editables (posiciones 1..8) al PreparedStatement. */
    private void asignarCampos(PreparedStatement ps, Tercero t) throws SQLException {
        ps.setString(1, t.getTipoTercero().name());
        ps.setString(2, t.getNombre());
        ps.setString(3, t.getDocumento());
        ps.setString(4, t.getTelefono());
        ps.setString(5, t.getCorreo());
        ps.setString(6, t.getDireccion());
        ps.setString(7, t.getEstado().name());
        ps.setString(8, t.getObservaciones());
    }

    /** Construye un {@link Tercero} a partir de la fila actual del ResultSet. */
    private Tercero mapear(ResultSet rs) throws SQLException {
        Tercero t = new Tercero();
        t.setIdTercero(rs.getInt("id_tercero"));
        t.setTipoTercero(TipoTercero.desde(rs.getString("tipo_tercero")));
        t.setNombre(rs.getString("nombre"));
        t.setDocumento(rs.getString("documento"));
        t.setTelefono(rs.getString("telefono"));
        t.setCorreo(rs.getString("correo"));
        t.setDireccion(rs.getString("direccion"));
        t.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        t.setObservaciones(rs.getString("observaciones"));
        t.setFechaCreacion(rs.getString("fecha_creacion"));
        return t;
    }
}
