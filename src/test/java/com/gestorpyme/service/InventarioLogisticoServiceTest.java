package com.gestorpyme.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pruebas unitarias de {@link InventarioLogisticoService}: calculo del sugerido (regla bruta
 * max(0, minimo - actual)) y del estado logistico (NORMAL / BAJO / SIN STOCK + EN PEDIDO).
 * No requieren base de datos.
 */
class InventarioLogisticoServiceTest {

    private final InventarioLogisticoService servicio = new InventarioLogisticoService();

    private static BigDecimal n(String v) {
        return new BigDecimal(v);
    }

    @Test
    void sugeridoCuandoActualBajoMinimo() {
        assertEquals(0, n("30").compareTo(servicio.calcularSugerido(n("70"), n("100"))),
                "Sugerido = 100 - 70 = 30");
    }

    @Test
    void sugeridoCeroCuandoActualAlcanzaMinimo() {
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.calcularSugerido(n("100"), n("100"))));
        assertEquals(0, BigDecimal.ZERO.compareTo(servicio.calcularSugerido(n("120"), n("100"))));
    }

    @Test
    void estadoSinStock() {
        assertEquals("SIN STOCK", servicio.calcularEstado(n("0"), n("10"), n("0")));
    }

    @Test
    void estadoBajo() {
        assertEquals("BAJO", servicio.calcularEstado(n("5"), n("10"), n("0")));
    }

    @Test
    void estadoNormal() {
        assertEquals("NORMAL", servicio.calcularEstado(n("20"), n("10"), n("0")));
    }

    @Test
    void estadoBajoConPedido() {
        assertEquals("BAJO / EN PEDIDO", servicio.calcularEstado(n("5"), n("10"), n("3")));
    }

    @Test
    void estadoNormalConPedidoMuestraEnPedido() {
        assertEquals("EN PEDIDO", servicio.calcularEstado(n("20"), n("10"), n("5")));
    }

    @Test
    void estadoSinStockConPedido() {
        assertEquals("SIN STOCK / EN PEDIDO", servicio.calcularEstado(n("0"), n("10"), n("5")));
    }
}
