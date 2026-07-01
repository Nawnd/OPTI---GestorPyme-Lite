package com.gestorpyme.infrastructure.database;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas basicas de la capa de conexion a la base de datos.
 */
class DatabaseConnectionTest {

    @Test
    void laConexionNoEsNula() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            assertNotNull(conn, "La conexion no deberia ser nula");
        }
    }

    @Test
    void foreignKeysHabilitado() throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next(), "PRAGMA foreign_keys deberia devolver un valor");
            assertEquals(1, rs.getInt(1), "foreign_keys deberia estar en ON (1)");
        }
    }
}
