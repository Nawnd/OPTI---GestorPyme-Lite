package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos de la tabla 'items'. Encapsula el JDBC/SQL: ninguna otra capa
 * ejecuta SQL sobre 'items'. Usa consultas parametrizadas para evitar inyeccion.
 * Los montos REAL se leen como BigDecimal (DEC-020).
 * Capa: repository.
 */
public class ItemRepository {

    /** Consulta base con JOIN para traer el nombre de la categoria y del subtipo (ambos pueden ser null). */
    private static final String SELECT_BASE =
            "SELECT i.id_item, i.codigo, i.nombre, i.id_categoria, c.nombre AS categoria_nombre, "
          + "i.id_subtipo, st.nombre AS subtipo_nombre, "
          + "i.tipo_item, i.precio_compra, i.precio_venta, i.controla_inventario, "
          + "i.stock_minimo, i.estado, i.fecha_creacion, "
          + "i.modo_calculo_servicio, i.porcentaje_servicio, i.unidad_medida, "
          + "i.stock_maximo, i.id_proveedor_preferido, prov.nombre AS proveedor_pref_nombre, "
          + "(SELECT IFNULL(SUM(s.cantidad), 0) FROM inventario_stock s WHERE s.id_item = i.id_item) AS stock_total "
          + "FROM items i "
          + "LEFT JOIN categorias c ON c.id_categoria = i.id_categoria "
          + "LEFT JOIN subtipos st ON st.id_subtipo = i.id_subtipo "
          + "LEFT JOIN terceros prov ON prov.id_tercero = i.id_proveedor_preferido";

    /** Lista todos los items ordenados por nombre. */
    public List<Item> listar() throws SQLException {
        String sql = SELECT_BASE + " ORDER BY i.nombre";
        List<Item> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /**
     * Busca items ACTIVOS por codigo o nombre (para selectores inteligentes).
     * Usa LIKE con PreparedStatement y limita el numero de resultados.
     *
     * @param texto  texto a buscar (vacio devuelve los primeros {@code limite}).
     * @param limite maximo de filas a devolver.
     * @return items que coinciden, ordenados por nombre.
     */
    public List<Item> buscar(String texto, int limite) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        String sql = SELECT_BASE + " WHERE i.estado = 'ACTIVO' "
                   + "AND (i.codigo LIKE ? OR i.nombre LIKE ?) ORDER BY i.nombre LIMIT ?";
        List<Item> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patron);
            ps.setString(2, patron);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Busca items que sean SERVICIO o MANO_OBRA (para la pestana Servicios de la OT, Paso U.2.1).
     * Mismo patron que {@link #buscar} pero filtrando por tipo; excluye productos/repuestos.
     */
    public List<Item> buscarServicios(String texto, int limite) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        String sql = SELECT_BASE + " WHERE i.estado = 'ACTIVO' "
                   + "AND i.tipo_item IN ('SERVICIO','MANO_OBRA') "
                   + "AND (i.codigo LIKE ? OR i.nombre LIKE ?) ORDER BY i.nombre LIMIT ?";
        return ejecutarBusqueda(sql, patron, limite);
    }

    /**
     * Busca productos inventariables (controla_inventario = 1 y que no sean servicio/mano de obra),
     * para la pestana Repuestos de la OT (Paso U.2.1). Incluye PRODUCTO/INSUMO/REPUESTO inventariables.
     */
    public List<Item> buscarInventariables(String texto, int limite) throws SQLException {
        String patron = "%" + (texto == null ? "" : texto.trim()) + "%";
        String sql = SELECT_BASE + " WHERE i.estado = 'ACTIVO' "
                   + "AND i.controla_inventario = 1 "
                   + "AND i.tipo_item NOT IN ('SERVICIO','MANO_OBRA') "
                   + "AND (i.codigo LIKE ? OR i.nombre LIKE ?) ORDER BY i.nombre LIMIT ?";
        return ejecutarBusqueda(sql, patron, limite);
    }

    /** Ejecuta una busqueda de items con dos parametros LIKE (codigo/nombre) y un limite. */
    private List<Item> ejecutarBusqueda(String sql, String patron, int limite) throws SQLException {
        List<Item> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patron);
            ps.setString(2, patron);
            ps.setInt(3, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Busca un item por su identificador. */
    public Optional<Item> buscarPorId(int idItem) throws SQLException {
        String sql = SELECT_BASE + " WHERE i.id_item = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapear(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Indica si ya existe otro item con el mismo codigo.
     *
     * @param codigo    codigo a comprobar (no nulo).
     * @param idExcluir id del item que se esta editando (0 al crear, para no excluir nada).
     */
    public boolean existeCodigo(String codigo, int idExcluir) throws SQLException {
        String sql = "SELECT 1 FROM items WHERE codigo = ? AND id_item <> ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, codigo);
            ps.setInt(2, idExcluir);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Inserta un nuevo item y devuelve el id generado por la base de datos. */
    public int insertar(Item item) throws SQLException {
        String sql = "INSERT INTO items "
                   + "(codigo, nombre, id_categoria, tipo_item, precio_compra, precio_venta, "
                   + "controla_inventario, stock_minimo, estado, id_subtipo, "
                   + "modo_calculo_servicio, porcentaje_servicio, unidad_medida, "
                   + "stock_maximo, id_proveedor_preferido) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignarCampos(ps, item);
            ps.executeUpdate();
            try (ResultSet llaves = ps.getGeneratedKeys()) {
                if (llaves.next()) {
                    return llaves.getInt(1);
                }
            }
            throw new SQLException("No se pudo obtener el id generado del item");
        }
    }

    /** Actualiza todos los campos editables de un item existente. */
    public void actualizar(Item item) throws SQLException {
        String sql = "UPDATE items SET codigo=?, nombre=?, id_categoria=?, tipo_item=?, "
                   + "precio_compra=?, precio_venta=?, controla_inventario=?, stock_minimo=?, estado=?, "
                   + "id_subtipo=?, modo_calculo_servicio=?, porcentaje_servicio=?, unidad_medida=?, "
                   + "stock_maximo=?, id_proveedor_preferido=? "
                   + "WHERE id_item=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            asignarCampos(ps, item);
            ps.setInt(16, item.getIdItem());
            ps.executeUpdate();
        }
    }

    /** Cambia el estado (baja/alta logica) de un item. */
    public void cambiarEstado(int idItem, EstadoRegistro estado) throws SQLException {
        String sql = "UPDATE items SET estado=? WHERE id_item=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idItem);
            ps.executeUpdate();
        }
    }

    /** Asigna los 13 campos editables (posiciones 1..13) al PreparedStatement. */
    private void asignarCampos(PreparedStatement ps, Item item) throws SQLException {
        ps.setString(1, item.getCodigo());
        ps.setString(2, item.getNombre());
        if (item.getIdCategoria() == null) {
            ps.setNull(3, Types.INTEGER);
        } else {
            ps.setInt(3, item.getIdCategoria());
        }
        ps.setString(4, item.getTipoItem().name());
        ps.setDouble(5, item.getPrecioCompra().doubleValue());
        ps.setDouble(6, item.getPrecioVenta().doubleValue());
        ps.setInt(7, item.isControlaInventario() ? 1 : 0);
        ps.setDouble(8, item.getStockMinimo().doubleValue());
        ps.setString(9, item.getEstado().name());
        if (item.getIdSubtipo() == null) {
            ps.setNull(10, Types.INTEGER);
        } else {
            ps.setInt(10, item.getIdSubtipo());
        }
        // Modo de calculo del servicio (por defecto FIJO) y porcentaje (nullable).
        ModoCalculoServicio modo = item.getModoCalculoServicio() == null
                ? ModoCalculoServicio.FIJO : item.getModoCalculoServicio();
        ps.setString(11, modo.name());
        if (item.getPorcentajeServicio() == null) {
            ps.setNull(12, Types.REAL);
        } else {
            ps.setDouble(12, item.getPorcentajeServicio().doubleValue());
        }
        // Unidad de medida (Paso A): por defecto "Unidad" si viene vacia.
        String unidad = item.getUnidadMedida() == null || item.getUnidadMedida().trim().isEmpty()
                ? "Unidad" : item.getUnidadMedida().trim();
        ps.setString(13, unidad);
        // Paso F: stock maximo (nullable) y proveedor preferido (nullable).
        if (item.getStockMaximo() == null) {
            ps.setNull(14, Types.REAL);
        } else {
            ps.setDouble(14, item.getStockMaximo().doubleValue());
        }
        if (item.getIdProveedorPreferido() == null) {
            ps.setNull(15, Types.INTEGER);
        } else {
            ps.setInt(15, item.getIdProveedorPreferido());
        }
    }

    /** Construye un {@link Item} a partir de la fila actual del ResultSet. */
    private Item mapear(ResultSet rs) throws SQLException {
        Item item = new Item();
        item.setIdItem(rs.getInt("id_item"));
        item.setCodigo(rs.getString("codigo"));
        item.setNombre(rs.getString("nombre"));

        int idCategoria = rs.getInt("id_categoria");
        item.setIdCategoria(rs.wasNull() ? null : idCategoria);
        item.setNombreCategoria(rs.getString("categoria_nombre"));

        int idSubtipo = rs.getInt("id_subtipo");
        item.setIdSubtipo(rs.wasNull() ? null : idSubtipo);
        item.setNombreSubtipo(rs.getString("subtipo_nombre"));

        item.setTipoItem(TipoItem.desde(rs.getString("tipo_item")));
        item.setPrecioCompra(BigDecimal.valueOf(rs.getDouble("precio_compra")));
        item.setPrecioVenta(BigDecimal.valueOf(rs.getDouble("precio_venta")));
        item.setControlaInventario(rs.getInt("controla_inventario") == 1);
        item.setStockMinimo(BigDecimal.valueOf(rs.getDouble("stock_minimo")));
        item.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        item.setFechaCreacion(rs.getString("fecha_creacion"));

        // Servicio (v0.7.3): modo (FIJO si null) y porcentaje (null si no aplica).
        item.setModoCalculoServicio(ModoCalculoServicio.desde(rs.getString("modo_calculo_servicio")));
        double pct = rs.getDouble("porcentaje_servicio");
        item.setPorcentajeServicio(rs.wasNull() ? null : BigDecimal.valueOf(pct));
        // Unidad de medida (Paso A): "Unidad" si es null/vacia (datos previos).
        String unidad = rs.getString("unidad_medida");
        item.setUnidadMedida(unidad == null || unidad.trim().isEmpty() ? "Unidad" : unidad.trim());
        // Paso F: stock maximo (null si no esta definido) y proveedor preferido (id + nombre).
        double maximo = rs.getDouble("stock_maximo");
        item.setStockMaximo(rs.wasNull() ? null : BigDecimal.valueOf(maximo));
        int idProv = rs.getInt("id_proveedor_preferido");
        item.setIdProveedorPreferido(rs.wasNull() ? null : idProv);
        item.setNombreProveedorPreferido(rs.getString("proveedor_pref_nombre"));
        // Paso H: stock total en todas las bodegas (derivado por subconsulta).
        item.setStockTotal(BigDecimal.valueOf(rs.getDouble("stock_total")));
        return item;
    }
}
