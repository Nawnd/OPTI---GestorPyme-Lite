package com.gestorpyme.service;

import com.gestorpyme.domain.model.DashboardResumen;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.DashboardRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de {@link DashboardService}: calculo del porcentaje de cartera recuperada
 * (unitario) y que el resumen nunca devuelve nulls (integracion con la BD).
 */
class DashboardServiceTest {

    private final DashboardService servicio = new DashboardService(new DashboardRepository());

    @BeforeAll
    static void prepararBaseDeDatos() throws SQLException {
        DatabaseInitializer.initialize();
    }

    @Test
    void sinCreditoElPorcentajeEsCero() {
        assertEquals(0.0, servicio.porcentaje(BigDecimal.ZERO, BigDecimal.ZERO), 0.0001);
        assertEquals(0.0, servicio.porcentaje(null, new BigDecimal("1000")), 0.0001);
    }

    @Test
    void porcentajeParcial() {
        assertEquals(0.4, servicio.porcentaje(new BigDecimal("400"), new BigDecimal("1000")), 0.0001);
    }

    @Test
    void porcentajeCompleto() {
        assertEquals(1.0, servicio.porcentaje(new BigDecimal("1000"), new BigDecimal("1000")), 0.0001);
    }

    @Test
    void porcentajeNuncaSuperaUno() {
        assertEquals(1.0, servicio.porcentaje(new BigDecimal("1500"), new BigDecimal("1000")), 0.0001);
    }

    @Test
    void resumenNuncaDevuelveNulls() throws SQLException {
        DashboardResumen r = servicio.obtenerResumen();
        assertNotNull(r, "El resumen no debe ser null");
        // Los montos siempre deben venir inicializados (0 si no hay datos), nunca null.
        assertNotNull(r.getTotalVentasHoy());
        assertNotNull(r.getTotalVentasMes());
        assertNotNull(r.getCarteraPendiente());
        assertNotNull(r.getCreditoTotal());
        assertNotNull(r.getCreditoPagado());
        assertTrue(r.getPorcentajeCartera() >= 0.0 && r.getPorcentajeCartera() <= 1.0,
                "El porcentaje de cartera debe estar entre 0 y 1");
    }
}
