package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.repository.PagoRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la validacion de montos de {@link PagoService}.
 */
class PagoServiceTest {

    private final PagoService servicio = new PagoService(new PagoRepository());

    @Test
    void valorCeroONegativoLanzaExcepcion() {
        assertThrows(ValidacionException.class, () -> servicio.validarValor(BigDecimal.ZERO, null));
        assertThrows(ValidacionException.class, () -> servicio.validarValor(new BigDecimal("-10"), null));
    }

    @Test
    void valorMayorAlSaldoLanzaExcepcion() {
        assertThrows(ValidacionException.class,
                () -> servicio.validarValor(new BigDecimal("100"), new BigDecimal("50")));
    }

    @Test
    void valorDentroDelSaldoEsValido() {
        assertDoesNotThrow(() -> servicio.validarValor(new BigDecimal("50"), new BigDecimal("50")));
    }
}
