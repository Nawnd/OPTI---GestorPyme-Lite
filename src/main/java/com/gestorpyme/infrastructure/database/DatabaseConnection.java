package com.gestorpyme.infrastructure.database;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Provee conexiones JDBC a la base de datos SQLite local (offline-first).
 * Cada conexion se abre con PRAGMA foreign_keys = ON.
 * Capa: infrastructure (no contiene reglas de negocio).
 */
public final class DatabaseConnection {

    private static final String DB_DIR = "data";
    private static final String DB_FILE = "gestorpyme.db";
    private static final String URL = "jdbc:sqlite:" + DB_DIR + "/" + DB_FILE;

    private DatabaseConnection() {
        // Clase de utilidad: no instanciable.
    }

    /**
     * Abre una nueva conexion a la base de datos con integridad referencial activa.
     * El llamador es responsable de cerrar la conexion (try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        ensureDataDirectory();
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true); // PRAGMA foreign_keys = ON
        return DriverManager.getConnection(URL, config.toProperties());
    }

    /** Ruta del archivo de base de datos local. */
    public static Path getDatabasePath() {
        return Paths.get(DB_DIR, DB_FILE);
    }

    private static void ensureDataDirectory() throws SQLException {
        try {
            Files.createDirectories(Paths.get(DB_DIR));
        } catch (IOException e) {
            throw new SQLException("No se pudo crear el directorio de datos: " + DB_DIR, e);
        }
    }
}
