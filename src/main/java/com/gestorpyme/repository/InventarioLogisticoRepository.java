package com.gestorpyme.repository;

import com.gestorpyme.domain.model.ItemLogistico;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio del inventario logistico. Devuelve, por item inventariable y activo, el stock
 * actual total (suma de todas las bodegas), el stock minimo y el stock en pedido (de la vista
 * vw_stock_en_pedido). El calculo del sugerido y del estado lo hace el servicio.
 * Capa: repository.
 */
public class InventarioLogisticoRepository {

    /**
     * @return una fila por item inventariable activo, con stock actual, minimo y en pedido.
     *         (Sugerido y estado quedan en cero/null; los completa el servicio.)
     */
    public List<ItemLogistico> listarBase() throws SQLException {
        String sql = "SELECT i.id_item, i.codigo, i.nombre, i.stock_minimo, i.precio_compra, "
                   + "i.stock_maximo, i.id_proveedor_preferido, prov.nombre AS proveedor_pref_nombre, "
                   + "IFNULL(s.total, 0) AS stock_actual, "
                   + "IFNULL(p.cantidad_en_pedido, 0) AS en_pedido "
                   + "FROM items i "
                   + "LEFT JOIN (SELECT id_item, SUM(cantidad) AS total FROM inventario_stock GROUP BY id_item) s "
                   + "  ON s.id_item = i.id_item "
                   + "LEFT JOIN vw_stock_en_pedido p ON p.id_item = i.id_item "
                   + "LEFT JOIN terceros prov ON prov.id_tercero = i.id_proveedor_preferido "
                   + "WHERE i.controla_inventario = 1 AND i.estado = 'ACTIVO' "
                   + "ORDER BY i.nombre";
        List<ItemLogistico> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ItemLogistico it = new ItemLogistico();
                it.setIdItem(rs.getInt("id_item"));
                it.setCodigo(rs.getString("codigo"));
                it.setNombre(rs.getString("nombre"));
                it.setStockMinimo(BigDecimal.valueOf(rs.getDouble("stock_minimo")));
                it.setStockActual(BigDecimal.valueOf(rs.getDouble("stock_actual")));
                it.setEnPedido(BigDecimal.valueOf(rs.getDouble("en_pedido")));
                it.setPrecioCompra(BigDecimal.valueOf(rs.getDouble("precio_compra")));
                // Paso F: stock maximo (null si no esta definido) y proveedor preferido.
                double maximo = rs.getDouble("stock_maximo");
                it.setStockMaximo(rs.wasNull() ? null : BigDecimal.valueOf(maximo));
                int idProv = rs.getInt("id_proveedor_preferido");
                it.setIdProveedorPreferido(rs.wasNull() ? null : idProv);
                it.setNombreProveedorPreferido(rs.getString("proveedor_pref_nombre"));
                lista.add(it);
            }
        }
        return lista;
    }
}
