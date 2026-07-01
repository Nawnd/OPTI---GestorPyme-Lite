package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las reglas de validacion de bodegas.
 * El caso de nombre vacio falla antes de tocar la base de datos.
 */
class BodegaServiceTest {

    private final BodegaService service = new BodegaService();

    @Test
    void nombreVacioLanzaValidacion() {
        Bodega bodega = new Bodega();
        bodega.setNombre("   ");
        assertThrows(ValidacionException.class, () -> service.guardar(bodega));
    }
}
