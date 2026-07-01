package com.gestorpyme.service;

import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Tercero;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las reglas de validacion de terceros.
 * No tocan la base de datos: la validacion falla antes de llegar al repositorio.
 */
class TerceroServiceTest {

    private final TerceroService service = new TerceroService();

    @Test
    void nombreVacioLanzaValidacion() {
        Tercero t = new Tercero();
        t.setTipoTercero(TipoTercero.CLIENTE);
        t.setNombre("   ");
        assertThrows(ValidacionException.class, () -> service.guardar(t));
    }

    @Test
    void tipoNuloLanzaValidacion() {
        Tercero t = new Tercero();
        t.setNombre("Juan Perez");
        assertThrows(ValidacionException.class, () -> service.guardar(t));
    }

    @Test
    void correoInvalidoLanzaValidacion() {
        Tercero t = new Tercero();
        t.setTipoTercero(TipoTercero.CLIENTE);
        t.setNombre("Juan Perez");
        t.setCorreo("correo-sin-arroba");
        assertThrows(ValidacionException.class, () -> service.guardar(t));
    }
}
