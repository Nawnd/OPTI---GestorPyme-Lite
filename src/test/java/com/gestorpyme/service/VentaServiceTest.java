package com.gestorpyme.service;

import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.repository.VentaRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pruebas de las validaciones de negocio de {@link VentaService}.
 * Son unitarias: las reglas se evaluan antes de tocar la base de datos.
 */
class VentaServiceTest {

    private final VentaService servicio = new VentaService(new VentaRepository());

    /** Construye una linea valida para aislar la regla que se quiere probar. */
    private VentaDetalle lineaValida() {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(1);
        d.setCantidad(new BigDecimal("2"));
        d.setPrecioUnitario(new BigDecimal("100"));
        d.setDescuentoLinea(BigDecimal.ZERO);
        return d;
    }

    @Test
    void sinClienteLanzaExcepcion() {
        Venta v = new Venta();
        v.setIdTercero(null); // sin cliente
        List<VentaDetalle> detalles = Collections.singletonList(lineaValida());
        assertThrows(ValidacionException.class,
                () -> servicio.registrarVenta(v, detalles, 0, true, null, null));
    }

    @Test
    void sinDetalleLanzaExcepcion() {
        Venta v = new Venta();
        v.setIdTercero(5);
        assertThrows(ValidacionException.class,
                () -> servicio.registrarVenta(v, new ArrayList<>(), 0, true, null, null));
    }

    @Test
    void cantidadCeroLanzaExcepcion() {
        Venta v = new Venta();
        v.setIdTercero(5);
        VentaDetalle d = lineaValida();
        d.setCantidad(BigDecimal.ZERO); // cantidad invalida
        assertThrows(ValidacionException.class,
                () -> servicio.registrarVenta(v, Collections.singletonList(d), 0, true, null, null));
    }
}
