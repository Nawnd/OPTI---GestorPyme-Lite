package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba del ciclo completo de acceso a datos de terceros.
 * Inserta un registro temporal, lo verifica y al final lo elimina fisicamente
 * (solo como limpieza de la prueba; el repositorio en si usa baja logica).
 */
class TerceroRepositoryTest {

    private final TerceroRepository repositorio = new TerceroRepository();

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void cicloInsertarBuscarActualizarCambiarEstado() throws SQLException {
        int id = 0;
        try {
            // Insertar
            Tercero nuevo = new Tercero();
            nuevo.setTipoTercero(TipoTercero.CLIENTE);
            nuevo.setNombre("PRUEBA Cliente Temporal");
            nuevo.setEstado(EstadoRegistro.ACTIVO);
            id = repositorio.insertar(nuevo);
            assertTrue(id > 0, "El id generado deberia ser positivo");

            // Buscar
            Optional<Tercero> guardado = repositorio.buscarPorId(id);
            assertTrue(guardado.isPresent(), "Deberia encontrarse el tercero insertado");
            assertEquals("PRUEBA Cliente Temporal", guardado.get().getNombre());

            // Actualizar
            Tercero editar = guardado.get();
            editar.setNombre("PRUEBA Cliente Editado");
            repositorio.actualizar(editar);
            assertEquals("PRUEBA Cliente Editado",
                    repositorio.buscarPorId(id).orElseThrow().getNombre());

            // Cambiar estado (baja logica)
            repositorio.cambiarEstado(id, EstadoRegistro.INACTIVO);
            assertEquals(EstadoRegistro.INACTIVO,
                    repositorio.buscarPorId(id).orElseThrow().getEstado());
        } finally {
            if (id > 0) {
                eliminarFisicamente(id); // limpieza: el registro de prueba no debe quedar
            }
        }
    }

    /** Borrado fisico SOLO para limpieza de esta prueba. */
    private void eliminarFisicamente(int idTercero) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM terceros WHERE id_tercero = ?")) {
            ps.setInt(1, idTercero);
            ps.executeUpdate();
        }
    }
}
