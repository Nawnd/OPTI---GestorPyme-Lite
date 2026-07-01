package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.repository.CuentaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de la validacion de abonos de {@link CuentaService}.
 */
class CuentaServiceTest {

    private final CuentaService servicio = new CuentaService(new CuentaRepository());

    @Test
    void abonoCeroLanzaExcepcion() {
        Abono a = new Abono();
        a.setIdCuenta(1);
        a.setValor(BigDecimal.ZERO);
        assertThrows(ValidacionException.class, () -> servicio.registrarAbono(a));
    }

    @Test
    void abonoNuloLanzaExcepcion() {
        assertThrows(ValidacionException.class, () -> servicio.registrarAbono(null));
    }
}
