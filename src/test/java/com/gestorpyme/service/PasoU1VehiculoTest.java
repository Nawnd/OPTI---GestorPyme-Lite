package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Vehiculo;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Paso U.1 — CRUD de vehiculos por cliente. Marcador ZPU1TEST.
 *
 * Garantiza el esquema con {@link DatabaseInitializer#initialize()} (idempotente, crea la tabla
 * vehiculos si falta) y usa un cliente de prueba propio. Limpia en @AfterAll en orden FK-seguro
 * (primero vehiculos, luego el tercero). No toca ventas, inventario ni FEFO.
 */
public class PasoU1VehiculoTest {

    private static final String MARCA = "ZPU1";
    private static final VehiculoService service = new VehiculoService();
    private static int idClienteTest;

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize(); // crea la tabla vehiculos de forma idempotente
        try (Connection c = DatabaseConnection.getConnection()) {
            // Limpieza previa por si una corrida anterior quedo a medias.
            limpiar(c);
            idClienteTest = insertarClienteTest(c);
        }
    }

    @AfterAll
    static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            limpiar(c);
        }
    }

    @Test
    void crearVehiculoValido() throws Exception {
        Vehiculo v = nuevo("ZPU1-ABC123");
        v.setMarca("Toyota");
        v.setAnio(2018);
        v.setKilometraje(45000);
        service.guardar(v);
        assertTrue(v.getIdVehiculo() > 0, "Debe asignarse un id al crear");
        assertNotNull(buscarPorPlaca("ZPU1-ABC123"), "El vehiculo creado debe listarse");
    }

    @Test
    void placaSeNormalizaAMayusculas() throws Exception {
        Vehiculo v = nuevo("  zpu1-low01  ");
        service.guardar(v);
        assertEquals("ZPU1-LOW01", v.getPlaca(), "La placa debe normalizarse a mayusculas y sin espacios");
        assertNotNull(buscarPorPlaca("ZPU1-LOW01"), "Debe encontrarse por la placa normalizada");
    }

    @Test
    void placaObligatoriaSeRechaza() {
        Vehiculo v = nuevo("   ");
        assertThrows(ValidacionException.class, () -> service.guardar(v), "Placa vacia debe rechazarse");
    }

    @Test
    void clienteObligatorioSeRechaza() {
        Vehiculo v = nuevo("ZPU1-NOCLI");
        v.setIdTercero(0);
        assertThrows(ValidacionException.class, () -> service.guardar(v), "Cliente obligatorio");
    }

    @Test
    void kilometrajeNegativoSeRechaza() {
        Vehiculo v = nuevo("ZPU1-NEG01");
        v.setKilometraje(-5);
        assertThrows(ValidacionException.class, () -> service.guardar(v), "Kilometraje negativo se rechaza");
    }

    @Test
    void placaDuplicadaSeRechaza() throws Exception {
        service.guardar(nuevo("ZPU1-DUP01"));
        Vehiculo repetido = nuevo("zpu1-dup01"); // misma placa tras normalizar
        assertThrows(ValidacionException.class, () -> service.guardar(repetido),
                "No se permite placa duplicada");
    }

    @Test
    void cambiarEstadoActivoInactivo() throws Exception {
        Vehiculo v = nuevo("ZPU1-EST01");
        service.guardar(v);
        service.cambiarEstado(v.getIdVehiculo(), EstadoRegistro.INACTIVO);
        Vehiculo recargado = buscarPorPlaca("ZPU1-EST01");
        assertNotNull(recargado);
        assertEquals(EstadoRegistro.INACTIVO, recargado.getEstado(), "El estado debe quedar INACTIVO");
    }

    @Test
    void listarIncluyeNombreCliente() throws Exception {
        Vehiculo v = nuevo("ZPU1-CLI01");
        service.guardar(v);
        Vehiculo recargado = buscarPorPlaca("ZPU1-CLI01");
        assertNotNull(recargado);
        assertEquals("ZPU1 Cliente Test", recargado.getNombreCliente(),
                "El listado debe incluir el nombre del cliente (JOIN)");
    }

    // ---------------------------------------------------------------------

    private static Vehiculo nuevo(String placa) {
        Vehiculo v = new Vehiculo();
        v.setIdTercero(idClienteTest);
        v.setPlaca(placa);
        return v;
    }

    private static Vehiculo buscarPorPlaca(String placa) throws SQLException {
        for (Vehiculo v : service.listar()) {
            if (placa.equals(v.getPlaca())) {
                return v;
            }
        }
        return null;
    }

    private static int insertarClienteTest(Connection c) throws SQLException {
        String sql = "INSERT INTO terceros (tipo_tercero, nombre, estado, fecha_creacion) "
                   + "VALUES ('CLIENTE', 'ZPU1 Cliente Test', 'ACTIVO', datetime('now','localtime'))";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        throw new SQLException("No se pudo crear el cliente de prueba ZPU1");
    }

    /** Borra primero los vehiculos de prueba (placa/marca ZPU1) y luego el cliente de prueba. */
    private static void limpiar(Connection c) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM vehiculos WHERE placa LIKE 'ZPU1%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre = 'ZPU1 Cliente Test'");
        }
    }
}
