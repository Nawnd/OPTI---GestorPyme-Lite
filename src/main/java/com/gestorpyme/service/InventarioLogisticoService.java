package com.gestorpyme.service;

import com.gestorpyme.domain.model.ItemLogistico;
import com.gestorpyme.repository.InventarioLogisticoRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Reglas del inventario logistico. A partir del stock actual, minimo y en pedido calcula:
 *  - Sugerido de compra (regla inicial documentada, BRUTA): max(0, minimo - actual).
 *    Se muestra el "en pedido" en columna aparte para que el usuario decida si compra mas
 *    (no se usa la variante neta para no ocultar faltantes ya cubiertos por pedidos).
 *  - Estado logistico: NORMAL / BAJO / SIN STOCK, combinado con "EN PEDIDO" si aplica.
 * Capa: service.
 */
public class InventarioLogisticoService {

    private final InventarioLogisticoRepository repository;

    public InventarioLogisticoService() {
        this(new InventarioLogisticoRepository());
    }

    public InventarioLogisticoService(InventarioLogisticoRepository repository) {
        this.repository = repository;
    }

    /** @return items inventariables con sugerido (neto) y estado ya calculados. */
    public List<ItemLogistico> listar() throws SQLException {
        List<ItemLogistico> base = repository.listarBase();
        for (ItemLogistico it : base) {
            it.setSugerido(calcularSugerido(it.getStockActual(), it.getStockMinimo(),
                    it.getStockMaximo(), it.getEnPedido()));
            it.setEstado(calcularEstado(it.getStockActual(), it.getStockMinimo(), it.getEnPedido()));
        }
        return base;
    }

    /**
     * Sugerido NETO (Paso C, RF-04): cantidad a comprar descontando lo que ya esta en pedido.
     * <pre>sugerido = max(0, stock_minimo - stock_actual - en_pedido)</pre>
     * Si un producto ya tiene suficiente en pedido, el sugerido es 0 (no se vuelve a comprar).
     * Valores nulos se tratan como 0. Nunca devuelve negativo. No accede a la base de datos.
     *
     * @param actual   stock actual.
     * @param minimo   stock minimo.
     * @param enPedido cantidad ya solicitada en ordenes de compra pendientes.
     * @return cantidad sugerida neta (>= 0).
     */
    BigDecimal calcularSugerido(BigDecimal actual, BigDecimal minimo, BigDecimal enPedido) {
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        BigDecimal m = minimo == null ? BigDecimal.ZERO : minimo;
        BigDecimal p = enPedido == null ? BigDecimal.ZERO : enPedido;
        BigDecimal dif = m.subtract(a).subtract(p);
        return dif.signum() > 0 ? dif : BigDecimal.ZERO;
    }

    /**
     * Sugerido de reabastecimiento considerando el stock maximo (Paso F).
     * <ol>
     *   <li>faltante_minimo = minimo - actual - enPedido.</li>
     *   <li>Si faltante_minimo &le; 0 -&gt; 0 (no hay faltante al minimo, no se sugiere comprar).</li>
     *   <li>Si faltante_minimo &gt; 0 y hay stock maximo -&gt; max(0, maximo - actual - enPedido)
     *       (se repone hasta el techo).</li>
     *   <li>Si faltante_minimo &gt; 0 y no hay stock maximo -&gt; max(0, minimo - actual - enPedido)
     *       (formula actual basada en el minimo).</li>
     * </ol>
     * El stock maximo solo actua como techo cuando realmente existe faltante operativo al minimo.
     *
     * @param maximo stock maximo (puede ser null = sin techo definido).
     */
    BigDecimal calcularSugerido(BigDecimal actual, BigDecimal minimo, BigDecimal maximo, BigDecimal enPedido) {
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        BigDecimal m = minimo == null ? BigDecimal.ZERO : minimo;
        BigDecimal p = enPedido == null ? BigDecimal.ZERO : enPedido;
        BigDecimal faltanteMinimo = m.subtract(a).subtract(p);
        if (faltanteMinimo.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (maximo != null) {
            BigDecimal hastaMaximo = maximo.subtract(a).subtract(p);
            return hastaMaximo.signum() > 0 ? hastaMaximo : BigDecimal.ZERO;
        }
        return faltanteMinimo; // ya es > 0
    }

    /**
     * Sobrecarga sin "en pedido" (equivale a en_pedido = 0). Se conserva por compatibilidad;
     * con en_pedido 0 el sugerido neto coincide con la regla bruta {@code max(0, minimo - actual)}.
     */
    BigDecimal calcularSugerido(BigDecimal actual, BigDecimal minimo) {
        return calcularSugerido(actual, minimo, BigDecimal.ZERO);
    }

    /** Estado logistico combinando nivel de stock y si hay pedido pendiente. */
    String calcularEstado(BigDecimal actual, BigDecimal minimo, BigDecimal enPedido) {
        BigDecimal a = actual == null ? BigDecimal.ZERO : actual;
        BigDecimal m = minimo == null ? BigDecimal.ZERO : minimo;
        BigDecimal p = enPedido == null ? BigDecimal.ZERO : enPedido;

        String base;
        if (a.signum() <= 0) {
            base = "SIN STOCK";
        } else if (a.compareTo(m) < 0) {
            base = "BAJO";
        } else {
            base = "NORMAL";
        }

        boolean hayPedido = p.signum() > 0;
        if (!hayPedido) {
            return base;
        }
        return base.equals("NORMAL") ? "EN PEDIDO" : (base + " / EN PEDIDO");
    }
}
