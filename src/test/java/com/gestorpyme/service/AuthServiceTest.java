package com.gestorpyme.service;

import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la logica de autenticacion.
 */
class AuthServiceTest {

    private final AuthService authService = new AuthService();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        // Garantiza el esquema y el admin con su contrasena por defecto (admin123).
        DatabaseInitializer.initialize();
    }

    @Test
    void loginCorrectoConAdmin() {
        ResultadoAutenticacion r = authService.autenticar("admin", "admin123");
        assertTrue(r.esExito(), "admin/admin123 deberia autenticar");
        assertEquals("admin", r.getUsuario().getNombreUsuario());
    }

    @Test
    void passwordIncorrectaFalla() {
        ResultadoAutenticacion r = authService.autenticar("admin", "claveMala");
        assertEquals(ResultadoAutenticacion.Estado.CREDENCIALES_INVALIDAS, r.getEstado());
    }

    @Test
    void usuarioInexistenteFalla() {
        ResultadoAutenticacion r = authService.autenticar("fantasma", "x");
        assertEquals(ResultadoAutenticacion.Estado.CREDENCIALES_INVALIDAS, r.getEstado());
    }

    @Test
    void camposVaciosFallan() {
        ResultadoAutenticacion r = authService.autenticar("", "");
        assertEquals(ResultadoAutenticacion.Estado.CREDENCIALES_INVALIDAS, r.getEstado());
    }
}
