package com.gestorpyme.service;

import com.gestorpyme.domain.enums.TipoSeguimiento;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Seguimiento;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las reglas de validacion de seguimientos de CRM.
 * Todos los casos fallan antes de tocar la base de datos.
 */
class SeguimientoServiceTest {

    private final SeguimientoService service = new SeguimientoService();

    @Test
    void terceroNoSeleccionadoLanzaValidacion() {
        Seguimiento s = new Seguimiento();
        s.setTipo(TipoSeguimiento.LLAMADA);
        s.setDescripcion("Cliente interesado");
        // idTercero queda en 0 -> invalido
        assertThrows(ValidacionException.class, () -> service.registrar(s));
    }

    @Test
    void descripcionVaciaLanzaValidacion() {
        Seguimiento s = new Seguimiento();
        s.setIdTercero(1);
        s.setTipo(TipoSeguimiento.NOTA);
        s.setDescripcion("   ");
        assertThrows(ValidacionException.class, () -> service.registrar(s));
    }
}
