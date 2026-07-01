package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.domain.model.LoteDisponible;
import com.gestorpyme.domain.model.VentaDetalleLote;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Repositorio JDBC de la tabla 'lotes'.
 * Encapsula todo el SQL del modulo Lotes y vencimientos (capa repository).
 * Toda interaccion usa PreparedStatement.
 */
public class LoteRepository {

    /** SELECT base con JOIN a item (y LEFT JOIN a bodega, que puede ser null). */
    private static final String SELECT_BASE =
            "SELECT l.id_lote, l.id_item, l.id_bodega, l.numero_lote, l.cantidad_inicial, "
          + "IFNULL(l.cantidad_disponible, l.cantidad_inicial) AS cantidad_disponible, "
          + "l.fecha_ingreso, l.fecha_vencimiento, l.estado, "
          + "it.nombre AS item_nombre, it.codigo AS item_codigo, b.nombre AS bodega_nombre "
          + "FROM lotes l "
          + "JOIN items it  ON it.id_item  = l.id_item "
          + "LEFT JOIN bodegas b ON b.id_bodega = l.id_bodega ";

    /** Lista todos los lotes ordenados por fecha de vencimiento (los sin fecha al final). */
    public List<Lote> listar() throws SQLException {
        // En SQLite, NULL ordena primero en ASC; se fuerza al final con un CASE.
        String sql = SELECT_BASE
                + "ORDER BY (l.fecha_vencimiento IS NULL), date(l.fecha_vencimiento) ASC, l.id_lote DESC";
        return ejecutarConsulta(sql);
    }

    /**
     * Lista los lotes ACTIVOS cuya fecha de vencimiento cae dentro de los proximos N dias
     * (incluye los ya vencidos con fecha pasada, para que el usuario actue).
     *
     * @param dias horizonte en dias (p. ej. 30).
     */
    public List<Lote> listarProximosAVencer(int dias) throws SQLException {
        String sql = SELECT_BASE
                + "WHERE l.estado = 'ACTIVO' "
                + "AND l.fecha_vencimiento IS NOT NULL "
                + "AND date(l.fecha_vencimiento) <= date('now','localtime','+" + dias + " day') "
                + "ORDER BY date(l.fecha_vencimiento) ASC, l.id_lote DESC";
        return ejecutarConsulta(sql);
    }

    /** Inserta un lote nuevo y devuelve su id generado. */
    public int insertar(Lote lote) throws SQLException {
        String sql = "INSERT INTO lotes "
                + "(id_item, id_bodega, numero_lote, cantidad_inicial, cantidad_disponible, "
                + "fecha_ingreso, fecha_vencimiento, estado) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, lote.getIdItem());
            if (lote.getIdBodega() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, lote.getIdBodega());
            }
            ps.setString(3, lote.getNumeroLote());
            double inicial = lote.getCantidadInicial() == null ? 0d : lote.getCantidadInicial().doubleValue();
            ps.setDouble(4, inicial);
            // Paso J: un lote nuevo nace con cantidad_disponible = cantidad_inicial.
            ps.setDouble(5, inicial);
            ps.setString(6, lote.getFechaIngreso());        // null -> usa DEFAULT date('now')
            ps.setString(7, lote.getFechaVencimiento());    // puede ser null
            ps.setString(8, lote.getEstado().name());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    lote.setIdLote(id);
                    return id;
                }
            }
            return 0;
        }
    }

    /** Cambia el estado de un lote (p. ej. marcar VENCIDO o INACTIVO). */
    public void cambiarEstado(int idLote, EstadoLote estado) throws SQLException {
        String sql = "UPDATE lotes SET estado = ? WHERE id_lote = ?";
        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, estado.name());
            ps.setInt(2, idLote);
            ps.executeUpdate();
        }
    }

    /** Ejecuta una consulta del SELECT_BASE y mapea los resultados. */
    private List<Lote> ejecutarConsulta(String sql) throws SQLException {
        List<Lote> lotes = new ArrayList<>();
        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lotes.add(mapear(rs));
            }
        }
        return lotes;
    }

    /** Construye un Lote a partir de la fila actual del ResultSet. */
    private Lote mapear(ResultSet rs) throws SQLException {
        Lote l = new Lote();
        l.setIdLote(rs.getInt("id_lote"));
        l.setIdItem(rs.getInt("id_item"));

        int idBodega = rs.getInt("id_bodega");
        l.setIdBodega(rs.wasNull() ? null : idBodega);

        l.setNumeroLote(rs.getString("numero_lote"));
        l.setCantidadInicial(BigDecimal.valueOf(rs.getDouble("cantidad_inicial")));
        l.setCantidadDisponible(BigDecimal.valueOf(rs.getDouble("cantidad_disponible")));
        l.setFechaIngreso(rs.getString("fecha_ingreso"));
        l.setFechaVencimiento(rs.getString("fecha_vencimiento"));
        l.setEstado(EstadoLote.desde(rs.getString("estado")));
        l.setNombreItem(rs.getString("item_nombre"));
        l.setCodigoItem(rs.getString("item_codigo"));
        l.setNombreBodega(rs.getString("bodega_nombre"));
        return l;
    }

    // ---------------- Paso J: FEFO y trazabilidad de consumo ----------------

    /**
     * SELECT FEFO: lotes ACTIVOS del item en la bodega, no vencidos y con saldo &gt; 0, ordenados para
     * consumir primero el de vencimiento mas cercano y dejar los sin fecha al final.
     */
    private static final String SELECT_FEFO =
            "SELECT id_lote, numero_lote, fecha_vencimiento, "
          + "IFNULL(cantidad_disponible, cantidad_inicial) AS disponible "
          + "FROM lotes "
          + "WHERE id_item = ? AND id_bodega = ? AND estado = 'ACTIVO' "
          + "AND IFNULL(cantidad_disponible, cantidad_inicial) > 0 "
          + "AND (fecha_vencimiento IS NULL OR date(fecha_vencimiento) >= date(?)) "
          + "ORDER BY (fecha_vencimiento IS NULL) ASC, date(fecha_vencimiento) ASC, id_lote ASC";

    /**
     * Lotes consumibles por FEFO para un item en una bodega (saldo &gt; 0, ACTIVOS, no vencidos), en el
     * orden de consumo. Variante que abre su propia conexion (uso de solo lectura / previsualizacion).
     */
    public List<LoteDisponible> disponiblesFefo(int idItem, int idBodega) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            return disponiblesFefo(conn, idItem, idBodega);
        }
    }

    /**
     * Igual que {@link #disponiblesFefo(int, int)} pero reutiliza una conexion existente, para poder
     * participar en la transaccion de la venta (consumo real de lotes).
     */
    public List<LoteDisponible> disponiblesFefo(Connection conn, int idItem, int idBodega) throws SQLException {
        List<LoteDisponible> lista = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SELECT_FEFO)) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, LocalDate.now().toString()); // hoy ISO; compara fechas ISO como texto/date()
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new LoteDisponible(rs.getInt("id_lote"), rs.getString("numero_lote"),
                            rs.getString("fecha_vencimiento"), BigDecimal.valueOf(rs.getDouble("disponible"))));
                }
            }
        }
        return lista;
    }

    /**
     * Reduce el saldo de un lote (Paso J) dentro de una transaccion. Si el nuevo saldo llega a 0,
     * marca el lote como AGOTADO; en otro caso lo deja ACTIVO.
     */
    public void descontarDisponible(Connection conn, int idLote, BigDecimal nuevoSaldo) throws SQLException {
        String estado = nuevoSaldo.signum() <= 0 ? EstadoLote.AGOTADO.name() : EstadoLote.ACTIVO.name();
        String sql = "UPDATE lotes SET cantidad_disponible = ?, estado = ? WHERE id_lote = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, nuevoSaldo.doubleValue());
            ps.setString(2, estado);
            ps.setInt(3, idLote);
            ps.executeUpdate();
        }
    }

    /** Inserta una fila en venta_detalle_lotes (consumo de un lote por una linea), dentro de la transaccion. */
    public void insertarConsumo(Connection conn, int idDetalle, int idLote, BigDecimal cantidad) throws SQLException {
        String sql = "INSERT INTO venta_detalle_lotes (id_detalle, id_lote, cantidad) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idDetalle);
            ps.setInt(2, idLote);
            ps.setDouble(3, cantidad.doubleValue());
            ps.executeUpdate();
        }
    }

    /** Lista los lotes consumidos por una linea de venta (para exportacion/trazabilidad). */
    public List<VentaDetalleLote> consumosPorDetalle(int idDetalle) throws SQLException {
        String sql = "SELECT vdl.id_venta_detalle_lote, vdl.id_detalle, vdl.id_lote, vdl.cantidad, "
                   + "l.numero_lote "
                   + "FROM venta_detalle_lotes vdl "
                   + "JOIN lotes l ON l.id_lote = vdl.id_lote "
                   + "WHERE vdl.id_detalle = ? ORDER BY vdl.id_venta_detalle_lote";
        List<VentaDetalleLote> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idDetalle);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    VentaDetalleLote c = new VentaDetalleLote(rs.getInt("id_detalle"), rs.getInt("id_lote"),
                            rs.getString("numero_lote"), BigDecimal.valueOf(rs.getDouble("cantidad")));
                    c.setIdVentaDetalleLote(rs.getInt("id_venta_detalle_lote"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }

    // ======================= Paso M: entrada por lote (conn-aware, dentro de la transaccion) =======================

    /**
     * Busca un lote por la clave logica item + bodega + numero_lote, usando la conexion de la transaccion en
     * curso. Devuelve un {@link Lote} con los campos necesarios para decidir el engrose (id, vencimiento,
     * estado, cantidades), o {@code null} si no existe.
     */
    public Lote buscarLoteEntrada(Connection conn, int idItem, int idBodega, String numeroLote)
            throws SQLException {
        String sql = "SELECT id_lote, fecha_vencimiento, estado, cantidad_inicial, cantidad_disponible "
                + "FROM lotes WHERE id_item = ? AND id_bodega = ? AND numero_lote = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            ps.setInt(2, idBodega);
            ps.setString(3, numeroLote);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Lote l = new Lote();
                l.setIdLote(rs.getInt("id_lote"));
                l.setIdItem(idItem);
                l.setIdBodega(idBodega);
                l.setNumeroLote(numeroLote);
                l.setFechaVencimiento(rs.getString("fecha_vencimiento"));
                l.setEstado(EstadoLote.valueOf(rs.getString("estado")));
                l.setCantidadInicial(BigDecimal.valueOf(rs.getDouble("cantidad_inicial")));
                Object disp = rs.getObject("cantidad_disponible");
                l.setCantidadDisponible(disp == null ? null : BigDecimal.valueOf(rs.getDouble("cantidad_disponible")));
                return l;
            }
        }
    }

    /**
     * Inserta un lote nuevo usando la conexion de la transaccion en curso (a diferencia de
     * {@link #insertar(Lote)}, que abre su propia conexion). Un lote nuevo nace con
     * cantidad_disponible = cantidad_inicial (coherente con Paso J).
     *
     * @return el id generado.
     */
    public int insertarEnTransaccion(Connection conn, Lote lote) throws SQLException {
        String sql = "INSERT INTO lotes "
                + "(id_item, id_bodega, numero_lote, cantidad_inicial, cantidad_disponible, "
                + "fecha_ingreso, fecha_vencimiento, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, lote.getIdItem());
            if (lote.getIdBodega() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, lote.getIdBodega());
            }
            ps.setString(3, lote.getNumeroLote());
            double inicial = lote.getCantidadInicial() == null ? 0d : lote.getCantidadInicial().doubleValue();
            ps.setDouble(4, inicial);
            ps.setDouble(5, inicial); // nuevo: disponible = inicial
            ps.setString(6, lote.getFechaIngreso());     // null -> usa DEFAULT del esquema
            ps.setString(7, lote.getFechaVencimiento());  // puede ser null
            ps.setString(8, lote.getEstado().name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    lote.setIdLote(id);
                    return id;
                }
            }
            return 0;
        }
    }

    /**
     * Engrosa un lote existente sumando la misma cantidad a cantidad_inicial y cantidad_disponible, usando la
     * conexion de la transaccion en curso. Las expresiones usan los valores ANTERIORES de la fila; si
     * cantidad_disponible fuera null (lotes heredados), se toma cantidad_inicial como base. Reactiva el estado
     * a ACTIVO porque tras la entrada el lote tiene existencia.
     */
    public void engrosar(Connection conn, int idLote, BigDecimal cantidadExtra) throws SQLException {
        String sql = "UPDATE lotes SET "
                + "cantidad_inicial = cantidad_inicial + ?, "
                + "cantidad_disponible = IFNULL(cantidad_disponible, cantidad_inicial) + ?, "
                + "estado = ? WHERE id_lote = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            double extra = cantidadExtra == null ? 0d : cantidadExtra.doubleValue();
            ps.setDouble(1, extra);
            ps.setDouble(2, extra);
            ps.setString(3, EstadoLote.ACTIVO.name());
            ps.setInt(4, idLote);
            ps.executeUpdate();
        }
    }
}
