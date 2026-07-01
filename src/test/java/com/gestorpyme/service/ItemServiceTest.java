package com.gestorpyme.service;

import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Item;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las reglas de validacion de items.
 * No tocan la base de datos: la validacion falla antes de llegar al repositorio.
 */
class ItemServiceTest {

    private final ItemService service = new ItemService();

    @Test
    void nombreVacioLanzaValidacion() {
        Item item = new Item();
        item.setTipoItem(TipoItem.PRODUCTO);
        item.setNombre("  ");
        assertThrows(ValidacionException.class, () -> service.guardar(item));
    }

    @Test
    void tipoNuloLanzaValidacion() {
        Item item = new Item();
        item.setNombre("Tornillo");
        assertThrows(ValidacionException.class, () -> service.guardar(item));
    }

    @Test
    void precioNegativoLanzaValidacion() {
        Item item = new Item();
        item.setTipoItem(TipoItem.PRODUCTO);
        item.setNombre("Tornillo");
        item.setPrecioVenta(new BigDecimal("-5"));
        assertThrows(ValidacionException.class, () -> service.guardar(item));
    }
}
