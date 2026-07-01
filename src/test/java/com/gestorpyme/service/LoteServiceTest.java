package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Lote;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las reglas de validacion de lotes.
 * Todos los casos fallan antes de tocar la base de datos.
 */
class LoteServiceTest {

    private final LoteService service = new LoteService();

    @Test
    void itemNoSeleccionadoLanzaValidacion() {
        Lote lote = new Lote();
        lote.setNumeroLote("L-001");
        // idItem queda en 0 -> invalido
        assertThrows(ValidacionException.class, () -> service.registrar(lote));
    }

    @Test
    void numeroLoteVacioLanzaValidacion() {
        Lote lote = new Lote();
        lote.setIdItem(1);
        lote.setNumeroLote("  ");
        assertThrows(ValidacionException.class, () -> service.registrar(lote));
    }

    @Test
    void cantidadNegativaLanzaValidacion() {
        Lote lote = new Lote();
        lote.setIdItem(1);
        lote.setNumeroLote("L-001");
        lote.setCantidadInicial(new BigDecimal("-5"));
        assertThrows(ValidacionException.class, () -> service.registrar(lote));
    }
}
