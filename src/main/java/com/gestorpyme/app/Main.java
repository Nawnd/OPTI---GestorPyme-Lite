package com.gestorpyme.app;

import com.gestorpyme.controller.LoginController;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.view.login.LoginView;

import javax.swing.SwingUtilities;
import java.sql.SQLException;

/**
 * Punto de entrada de OPTI - GestorPyme Lite.
 * 1) Inicializa la base de datos (crea el esquema si no existe y repara el admin).
 * 2) Abre la ventana de inicio de sesion (Swing) en el hilo de eventos (EDT).
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.out.println("Iniciando OPTI - GestorPyme Lite...");

        try {
            boolean creada = DatabaseInitializer.initialize();
            System.out.println((creada ? "Base de datos creada en: " : "Base de datos lista en: ")
                    + DatabaseConnection.getDatabasePath().toAbsolutePath());
        } catch (SQLException e) {
            System.err.println("ERROR inicializando la base de datos: " + e.getMessage());
            e.printStackTrace();
            return; // sin base de datos no tiene sentido continuar
        }

        // Toda la interfaz Swing debe construirse y mostrarse en el hilo de eventos (EDT).
        SwingUtilities.invokeLater(() -> new LoginView(new LoginController()).setVisible(true));
    }
}
