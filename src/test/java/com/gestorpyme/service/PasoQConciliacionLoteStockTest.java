package com.gestorpyme.service;

import com.gestorpyme.domain.model.ConciliacionLoteStockItem;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Paso Q — Conciliación lote vs stock (solo lectura). Marcador ZPQTEST.
 *
 * Siembra (vía JDBC crudo) una bodega y cinco items inventariables con distintas combinaciones de
 * stock e importes loteados, para validar los cuatro estados y que un item sin existencia no aparece:
 *  - OK: stock 10 = lotes (6+4).
 *  - FALTA_LOTEAR: stock 10 > lotes 4.
 *  - EXCESO_LOTEADO: stock 5 < lotes 8.
 *  - SIN_LOTES: stock 7 sin lotes.
 *  - SIN INVENTARIO: item con lote pero sin fila en inventario_stock (no debe aparecer).
 *
 * La conciliación incluye TODAS las existencias de la base; las aserciones localizan las filas propias
 * por el nombre de item (prefijo "ZPQ"). En @AfterAll se limpian los datos en orden FK-seguro.
 */
public class PasoQConciliacionLoteStockTest {

    private static final String IT_OK = "ZPQ Item OK";
    private static final String IT_FALTA = "ZPQ Item FALTA";
    private static final String IT_EXCESO = "ZPQ Item EXCESO";
    private static final String IT_SIN = "ZPQ Item SIN";
    private static final String IT_NOINV = "ZPQ Item NOINV";
    private static final String BODEGA = "ZPQ Bodega";

    private static int idBodega;
    private static int idOk;
    private static int idFalta;
    private static int idExceso;
    private static int idSin;
    private static int idNoinv;

    private static final InventarioService service = new InventarioService();

    @BeforeAll
    static void sembrar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            idBodega = insertar(c, "INSERT INTO bodegas (nombre, estado) VALUES (?, 'ACTIVO')", BODEGA);
            idOk = item(c, IT_OK);
            idFalta = item(c, IT_FALTA);
            idExceso = item(c, IT_EXCESO);
            idSin = item(c, IT_SIN);
            idNoinv = item(c, IT_NOINV);

            // Existencias (autoritativo).
            stock(c, idOk, 10);
            stock(c, idFalta, 10);
            stock(c, idExceso, 5);
            stock(c, idSin, 7);
            // idNoinv: SIN fila en inventario_stock a proposito.

            // Lotes (sumas por item).
            lote(c, idOk, "ZPQ-OK-1", 6);
            lote(c, idOk, "ZPQ-OK-2", 4);   // OK: 6+4 = 10
            lote(c, idFalta, "ZPQ-FA-1", 4); // FALTA: 10 > 4
            lote(c, idExceso, "ZPQ-EX-1", 8); // EXCESO: 5 < 8
            // idSin: sin lotes -> SIN_LOTES
            lote(c, idNoinv, "ZPQ-NI-1", 3); // tiene lote pero no tiene stock: no debe aparecer
        }
    }

    @AfterAll
    static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection()) {
            ejecutar(c, "DELETE FROM lotes WHERE numero_lote LIKE 'ZPQ-%'");
            ejecutar(c, "DELETE FROM inventario_stock WHERE id_bodega = " + idBodega);
            ejecutar(c, "DELETE FROM items WHERE id_item IN ("
                    + idOk + "," + idFalta + "," + idExceso + "," + idSin + "," + idNoinv + ")");
            ejecutar(c, "DELETE FROM bodegas WHERE id_bodega = " + idBodega);
        }
    }

    // ---------------------------------------------------------------------

    @Test
    void casoOk_stockIgualALoteado() throws SQLException {
        ConciliacionLoteStockItem c = buscar(IT_OK);
        assertNotNull(c, "El item OK debe aparecer (tiene existencia)");
        assertEquals(ConciliacionLoteStockItem.ESTADO_OK, c.getEstado());
        assertEquals(0, c.getDiferencia().compareTo(BigDecimal.ZERO), "Diferencia 0 en OK");
    }

    @Test
    void casoFaltaLotear_stockMayorQueLoteado() throws SQLException {
        ConciliacionLoteStockItem c = buscar(IT_FALTA);
        assertNotNull(c);
        assertEquals(ConciliacionLoteStockItem.ESTADO_FALTA_LOTEAR, c.getEstado());
        assertEquals(0, c.getDiferencia().compareTo(new BigDecimal("6")), "10 - 4 = 6");
    }

    @Test
    void casoExcesoLoteado_loteadoMayorQueStock() throws SQLException {
        ConciliacionLoteStockItem c = buscar(IT_EXCESO);
        assertNotNull(c);
        assertEquals(ConciliacionLoteStockItem.ESTADO_EXCESO_LOTEADO, c.getEstado());
        assertEquals(0, c.getDiferencia().compareTo(new BigDecimal("-3")), "5 - 8 = -3");
    }

    @Test
    void casoSinLotes_stockPositivoSinLotes() throws SQLException {
        ConciliacionLoteStockItem c = buscar(IT_SIN);
        assertNotNull(c);
        assertEquals(ConciliacionLoteStockItem.ESTADO_SIN_LOTES, c.getEstado());
        assertEquals(0, c.getStockLoteado().compareTo(BigDecimal.ZERO), "Sin lotes -> loteado 0");
    }

    @Test
    void itemSinInventario_noAparece() throws SQLException {
        assertNull(buscar(IT_NOINV), "Un item sin fila en inventario_stock no debe aparecer");
    }

    // ---------------------------------------------------------------------

    private static ConciliacionLoteStockItem buscar(String nombreItem) throws SQLException {
        List<ConciliacionLoteStockItem> filas = service.conciliacionLoteStock();
        for (ConciliacionLoteStockItem c : filas) {
            if (nombreItem.equals(c.getNombreItem())) {
                return c;
            }
        }
        return null;
    }

    private static int item(Connection c, String nombre) throws SQLException {
        return insertar(c,
                "INSERT INTO items (nombre, tipo_item, controla_inventario, estado, unidad_medida) "
                + "VALUES (?, 'PRODUCTO', 1, 'ACTIVO', 'Unidad')",
                nombre);
    }

    private static void stock(Connection c, int idItem, double cantidad) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setDouble(3, cantidad);
            ps.executeUpdate();
        }
    }

    private static void lote(Connection c, int idItem, String numero, double disponible) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO lotes (id_item, id_bodega, numero_lote, cantidad_inicial, estado, cantidad_disponible) "
                + "VALUES (?, ?, ?, ?, 'ACTIVO', ?)")) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, numero);
            ps.setDouble(4, disponible);
            ps.setDouble(5, disponible);
            ps.executeUpdate();
        }
    }

    private static int insertar(Connection c, String sql, String param) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, param);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
