package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.Rol;
import com.gestorpyme.domain.model.Usuario;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Acceso a datos de la tabla 'usuarios'. Encapsula el JDBC/SQL: ninguna otra capa
 * ejecuta SQL sobre 'usuarios'. Recibe y devuelve modelos de dominio ({@link Usuario}),
 * traduciendo los textos de la BD a sus enums canonicos.
 * Capa: repository.
 */
public class UsuarioRepository {

    /** Columnas seleccionadas (en el orden esperado por el mapeo). */
    private static final String SELECT_BASE =
            "SELECT id_usuario, nombre_usuario, nombre_completo, password_hash, "
          + "rol, estado, fecha_creacion FROM usuarios";

    /**
     * Busca un usuario por su nombre de usuario (columna unica).
     * Usa consulta parametrizada (PreparedStatement) para evitar inyeccion de SQL,
     * ya que el valor proviene del formulario de inicio de sesion.
     *
     * @param nombreUsuario nombre de usuario a buscar.
     * @return un {@link Optional} con el usuario si existe; vacio si no.
     * @throws SQLException si ocurre un error de acceso a la base de datos.
     */
    public Optional<Usuario> buscarPorNombreUsuario(String nombreUsuario) throws SQLException {
        String sql = SELECT_BASE + " WHERE nombre_usuario = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, nombreUsuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Construye un {@link Usuario} a partir de la fila actual del ResultSet.
     * Convierte los textos de 'rol' y 'estado' a sus enums canonicos.
     */
    private Usuario mapear(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario();
        usuario.setIdUsuario(rs.getInt("id_usuario"));
        usuario.setNombreUsuario(rs.getString("nombre_usuario"));
        usuario.setNombreCompleto(rs.getString("nombre_completo"));
        usuario.setPasswordHash(rs.getString("password_hash"));
        usuario.setRol(Rol.desde(rs.getString("rol")));
        usuario.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        usuario.setFechaCreacion(rs.getString("fecha_creacion"));
        return usuario;
    }
}
