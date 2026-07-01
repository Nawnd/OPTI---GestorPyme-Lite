package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.Rol;
import com.gestorpyme.domain.model.Usuario;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas del acceso a datos de usuarios.
 */
class UsuarioRepositoryTest {

    private final UsuarioRepository repositorio = new UsuarioRepository();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        // Garantiza que el esquema y el usuario semilla 'admin' existan.
        DatabaseInitializer.initialize();
    }

    @Test
    void encuentraAlUsuarioAdmin() throws SQLException {
        Optional<Usuario> resultado = repositorio.buscarPorNombreUsuario("admin");
        assertTrue(resultado.isPresent(), "Deberia existir el usuario 'admin'");
        assertEquals(Rol.ADMIN, resultado.get().getRol(), "El rol de 'admin' deberia ser ADMIN");
    }

    @Test
    void usuarioInexistenteDevuelveVacio() throws SQLException {
        Optional<Usuario> resultado = repositorio.buscarPorNombreUsuario("no_existe_xyz");
        assertFalse(resultado.isPresent(), "No deberia existir ese usuario");
    }
}
