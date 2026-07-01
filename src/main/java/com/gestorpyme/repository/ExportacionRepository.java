package com.gestorpyme.repository;

import com.gestorpyme.domain.model.ExportacionLog;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Acceso a datos del registro de exportaciones (tabla exportaciones_log).
 * Capa: repository (encapsula JDBC).
 */
public class ExportacionRepository {

    /** Registra una exportacion realizada (o su error). */
    public void registrar(ExportacionLog log) throws SQLException {
        String sql = "INSERT INTO exportaciones_log (tipo, ruta_archivo, id_usuario, estado) "
                   + "VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, log.getTipo().name());
            ps.setString(2, log.getRutaArchivo());
            if (log.getIdUsuario() == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, log.getIdUsuario());
            }
            ps.setString(4, log.getEstado().name());
            ps.executeUpdate();
        }
    }
}
