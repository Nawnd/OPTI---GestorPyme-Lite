package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.enums.EstadoOrdenCompra;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.domain.model.RecepcionDetalle;
import com.gestorpyme.domain.model.RecepcionMercancia;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Repositorio de recepcion de mercancia. Toda la recepcion (cabecera + detalles +
 * actualizacion de stock + Kardex ENTRADA_COMPRA + recalculo del estado de la orden)
 * ocurre en UNA sola transaccion JDBC con rollback: si algo falla, no queda recepcion
 * parcial, ni stock alterado, ni Kardex alterado (escenario 11 de casos borde).
 * Capa: repository.
 */
public class RecepcionRepository {

    /** Repositorio de lotes reutilizado para crear/engrosar lotes dentro de la transaccion (Paso M). */
    private final LoteRepository loteRepository = new LoteRepository();

    /**
     * Lista todas las recepciones de mercancia (solo lectura), incluyendo el numero de la
     * orden asociada (JOIN con ordenes_compra). Ordenadas por numero de recepcion.
     * El proveedor y el estado de la orden no estan en la cabecera de recepcion; quien
     * exporte puede cruzarlos con la orden por su numero. Util para exportacion.
     *
     * @return lista de {@link RecepcionMercancia} (sin detalles cargados).
     * @throws SQLException si ocurre un error de acceso a datos.
     */
    public java.util.List<RecepcionMercancia> listar() throws SQLException {
        String sql = "SELECT r.id_recepcion, r.numero_recepcion, r.id_orden, "
                   + "oc.numero_orden AS numero_orden, r.fecha, r.observaciones, r.id_usuario "
                   + "FROM recepciones_mercancia r "
                   + "JOIN ordenes_compra oc ON oc.id_orden = r.id_orden "
                   + "ORDER BY r.numero_recepcion";
        java.util.List<RecepcionMercancia> lista = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                RecepcionMercancia r = new RecepcionMercancia();
                r.setIdRecepcion(rs.getInt("id_recepcion"));
                r.setNumeroRecepcion(rs.getString("numero_recepcion"));
                r.setIdOrden(rs.getInt("id_orden"));
                r.setNumeroOrden(rs.getString("numero_orden"));
                r.setFecha(rs.getString("fecha"));
                r.setObservaciones(rs.getString("observaciones"));
                int idUsuario = rs.getInt("id_usuario");
                r.setIdUsuario(rs.wasNull() ? null : idUsuario);
                lista.add(r);
            }
        }
        return lista;
    }

    /**
     * Registra una recepcion completa de forma transaccional y devuelve el estado en que
     * queda la orden tras la recepcion (PARCIALMENTE_RECIBIDA o RECIBIDA).
     *
     * Por cada linea: inserta el detalle, suma stock, genera el movimiento ENTRADA_COMPRA,
     * y aumenta cantidad_recibida en la linea de la orden. Al final recalcula el estado.
     *
     * @param rec recepcion con id_orden, usuario y detalles ya cargados.
     * @return el numero de recepcion generado (RC-000001).
     */
    public String registrarRecepcionCompleta(RecepcionMercancia rec) throws SQLException {
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean autoCommitPrevio = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                String numeroOrden = obtenerNumeroOrden(conn, rec.getIdOrden());
                String numeroRec = generarNumeroRecepcion(conn);
                int idRecepcion = insertarCabecera(conn, rec, numeroRec);

                String motivo = "Recepcion " + numeroRec + " / Orden " + numeroOrden;
                String fechaIngreso = (rec.getFecha() != null && !rec.getFecha().isBlank())
                        ? rec.getFecha().trim() : LocalDate.now().toString();
                for (RecepcionDetalle d : rec.getDetalles()) {
                    // Paso M: si la linea trae numero de lote, crear o engrosar el lote y fijar d.idLote
                    // ANTES de insertar el detalle y el Kardex, todo en esta misma transaccion.
                    resolverLote(conn, d, fechaIngreso);
                    insertarDetalle(conn, idRecepcion, d);
                    sumarStock(conn, d.getIdItem(), d.getIdBodega(), d.getCantidadRecibida());
                    registrarMovimientoEntradaCompra(conn, d, motivo, rec.getIdUsuario());
                    aumentarCantidadRecibida(conn, d.getIdDetalleOc(), d.getCantidadRecibida());
                }

                // Recalcula el estado de la orden tras la recepcion (sin variable: no se usa el valor).
                recalcularEstadoOrden(conn, rec.getIdOrden());
                conn.commit();
                rec.setIdRecepcion(idRecepcion);
                rec.setNumeroRecepcion(numeroRec);
                rec.setNumeroOrden(numeroOrden);
                return numeroRec;
            } catch (SQLException | RuntimeException e) {
                // Incluye ValidacionException (lote vencido / vencimientos distintos): rollback total
                // para no dejar stock ni Kardex parciales (escenario transaccional).
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(autoCommitPrevio);
            }
        }
    }

    private String obtenerNumeroOrden(Connection conn, int idOrden) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT numero_orden FROM ordenes_compra WHERE id_orden = ?")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : ("ID-" + idOrden);
            }
        }
    }

    private String generarNumeroRecepcion(Connection conn) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTR(numero_recepcion, 4) AS INTEGER)) AS maximo "
                   + "FROM recepciones_mercancia WHERE numero_recepcion LIKE 'RC-%'";
        int siguiente = 1;
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                siguiente = rs.getInt("maximo") + 1;
            }
        }
        return String.format("RC-%06d", siguiente);
    }

    private int insertarCabecera(Connection conn, RecepcionMercancia rec, String numero) throws SQLException {
        String sql = "INSERT INTO recepciones_mercancia (numero_recepcion, id_orden, observaciones, id_usuario) "
                   + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, numero);
            ps.setInt(2, rec.getIdOrden());
            ps.setString(3, rec.getObservaciones());
            if (rec.getIdUsuario() == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, rec.getIdUsuario());
            }
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Paso M: si la linea trae numero de lote, crea un lote nuevo o engrosa el existente (clave
     * item + bodega + numero_lote) usando la conexion de la transaccion, y fija {@code d.idLote}. Si no hay
     * numero de lote, no hace nada (la recepcion sigue funcionando sin lote). Las reglas que requieren leer el
     * estado actual del lote se validan aqui (dentro de la transaccion) y, si fallan, lanzan
     * {@link ValidacionException} para forzar el rollback:
     *  - no se engrosa un lote ya vencido;
     *  - no se mezclan vencimientos: solo se engrosa si ambos vencimientos son nulos o exactamente iguales
     *    (si uno tiene fecha y el otro no, o difieren, se rechaza por ambiguedad).
     */
    private void resolverLote(Connection conn, RecepcionDetalle d, String fechaIngreso) throws SQLException {
        String numero = d.getNumeroLote();
        if (numero == null || numero.isBlank()) {
            return; // sin lote: flujo actual intacto
        }
        numero = numero.trim();
        String vencNuevo = normalizarFecha(d.getFechaVencimiento());

        Lote existente = loteRepository.buscarLoteEntrada(conn, d.getIdItem(), d.getIdBodega(), numero);
        if (existente == null) {
            Lote nuevo = new Lote();
            nuevo.setIdItem(d.getIdItem());
            nuevo.setIdBodega(d.getIdBodega());
            nuevo.setNumeroLote(numero);
            nuevo.setCantidadInicial(d.getCantidadRecibida());
            nuevo.setFechaIngreso(fechaIngreso);
            nuevo.setFechaVencimiento(vencNuevo);
            nuevo.setEstado(EstadoLote.ACTIVO);
            int id = loteRepository.insertarEnTransaccion(conn, nuevo);
            d.setIdLote(id);
        } else {
            String vencExist = normalizarFecha(existente.getFechaVencimiento());
            if (estaVencida(vencExist)) {
                throw new ValidacionException("El lote " + numero + " esta vencido y no puede engrosarse.");
            }
            if (!vencimientosCompatibles(vencExist, vencNuevo)) {
                throw new ValidacionException("El lote " + numero + " ya existe con otra fecha de vencimiento.");
            }
            loteRepository.engrosar(conn, existente.getIdLote(), d.getCantidadRecibida());
            d.setIdLote(existente.getIdLote());
        }
    }

    /** Normaliza una fecha a ISO 'AAAA-MM-DD' (toma los primeros 10 chars); null/blank -> null. */
    private String normalizarFecha(String f) {
        if (f == null || f.isBlank()) {
            return null;
        }
        String t = f.trim();
        return t.length() >= 10 ? t.substring(0, 10) : t;
    }

    /** @return true si la fecha ISO es anterior a hoy (defensivo ante datos no parseables). */
    private boolean estaVencida(String isoFecha) {
        if (isoFecha == null) {
            return false;
        }
        try {
            return LocalDate.parse(isoFecha).isBefore(LocalDate.now());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Compatibles solo si ambos vencimientos son nulos o exactamente iguales (no mezclar vencimientos). */
    private boolean vencimientosCompatibles(String existente, String nuevo) {
        if (existente == null && nuevo == null) {
            return true;
        }
        return existente != null && existente.equals(nuevo);
    }

    /**
     * Detalle de recepciones para exportacion (Paso N), uniendo cabecera, orden, item, bodega y lote.
     * Usa LEFT JOIN a {@code lotes} porque una linea recibida puede no tener lote (campos de lote nulos),
     * y LEFT JOIN a {@code ordenes_compra} de forma defensiva. Solo lectura; ordenado por recepcion y detalle.
     *
     * @return filas de detalle con datos de lote/vencimiento cuando aplique.
     */
    public java.util.List<com.gestorpyme.domain.model.RecepcionDetalleExport> listarDetalleExport()
            throws SQLException {
        String sql = "SELECT rm.numero_recepcion, rm.fecha, oc.numero_orden, "
                + "i.nombre AS item, b.nombre AS bodega, rd.cantidad_recibida, "
                + "rd.id_lote, l.numero_lote, l.fecha_vencimiento "
                + "FROM recepciones_detalles rd "
                + "JOIN recepciones_mercancia rm ON rm.id_recepcion = rd.id_recepcion "
                + "LEFT JOIN ordenes_compra oc ON oc.id_orden = rm.id_orden "
                + "JOIN items i ON i.id_item = rd.id_item "
                + "JOIN bodegas b ON b.id_bodega = rd.id_bodega "
                + "LEFT JOIN lotes l ON l.id_lote = rd.id_lote "
                + "ORDER BY rm.id_recepcion, rd.id_detalle_rec";
        java.util.List<com.gestorpyme.domain.model.RecepcionDetalleExport> lista = new java.util.ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                com.gestorpyme.domain.model.RecepcionDetalleExport d =
                        new com.gestorpyme.domain.model.RecepcionDetalleExport();
                d.setNumeroRecepcion(rs.getString("numero_recepcion"));
                d.setFecha(rs.getString("fecha"));
                d.setNumeroOrden(rs.getString("numero_orden"));
                d.setNombreItem(rs.getString("item"));
                d.setNombreBodega(rs.getString("bodega"));
                d.setCantidadRecibida(BigDecimal.valueOf(rs.getDouble("cantidad_recibida")));
                int idLote = rs.getInt("id_lote");
                d.setIdLote(rs.wasNull() ? null : idLote);
                d.setNumeroLote(rs.getString("numero_lote"));
                d.setFechaVencimiento(rs.getString("fecha_vencimiento"));
                lista.add(d);
            }
        }
        return lista;
    }

    /** @return true si el item controla inventario (puede manejar lote); false para servicios. */
    public boolean esInventariable(int idItem) throws SQLException {
        String sql = "SELECT controla_inventario FROM items WHERE id_item = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idItem);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) == 1;
            }
        }
    }

    private void insertarDetalle(Connection conn, int idRecepcion, RecepcionDetalle d) throws SQLException {
        String sql = "INSERT INTO recepciones_detalles "
                   + "(id_recepcion, id_detalle_oc, id_item, id_bodega, cantidad_recibida, id_lote) "
                   + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idRecepcion);
            ps.setInt(2, d.getIdDetalleOc());
            ps.setInt(3, d.getIdItem());
            ps.setInt(4, d.getIdBodega());
            ps.setDouble(5, d.getCantidadRecibida().doubleValue());
            if (d.getIdLote() == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, d.getIdLote());
            }
            ps.executeUpdate();
        }
    }

    /** Suma stock al par (item, bodega): actualiza si existe, inserta si no (upsert). */
    private void sumarStock(Connection conn, int idItem, int idBodega, BigDecimal cantidad) throws SQLException {
        String update = "UPDATE inventario_stock "
                      + "SET cantidad = cantidad + ?, fecha_actualizacion = datetime('now','localtime') "
                      + "WHERE id_item = ? AND id_bodega = ?";
        int filas;
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setDouble(1, cantidad.doubleValue());
            ps.setInt(2, idItem);
            ps.setInt(3, idBodega);
            filas = ps.executeUpdate();
        }
        if (filas == 0) {
            String insert = "INSERT INTO inventario_stock (id_item, id_bodega, cantidad) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insert)) {
                ps.setInt(1, idItem);
                ps.setInt(2, idBodega);
                ps.setDouble(3, cantidad.doubleValue());
                ps.executeUpdate();
            }
        }
    }

    private void registrarMovimientoEntradaCompra(Connection conn, RecepcionDetalle d,
                                                   String motivo, Integer idUsuario) throws SQLException {
        String sql = "INSERT INTO inventario_movimientos "
                   + "(id_item, id_bodega, tipo_movimiento, cantidad, id_lote, motivo, id_usuario) "
                   + "VALUES (?, ?, 'ENTRADA_COMPRA', ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, d.getIdItem());
            ps.setInt(2, d.getIdBodega());
            ps.setDouble(3, d.getCantidadRecibida().doubleValue());
            if (d.getIdLote() == null) {
                ps.setNull(4, java.sql.Types.INTEGER);
            } else {
                ps.setInt(4, d.getIdLote());
            }
            ps.setString(5, motivo);
            if (idUsuario == null) {
                ps.setNull(6, java.sql.Types.INTEGER);
            } else {
                ps.setInt(6, idUsuario);
            }
            ps.executeUpdate();
        }
    }

    private void aumentarCantidadRecibida(Connection conn, int idDetalleOc, BigDecimal cantidad) throws SQLException {
        String sql = "UPDATE ordenes_compra_detalles SET cantidad_recibida = cantidad_recibida + ? "
                   + "WHERE id_detalle = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDouble(1, cantidad.doubleValue());
            ps.setInt(2, idDetalleOc);
            ps.executeUpdate();
        }
    }

    /**
     * Recalcula el estado de la orden comparando lo solicitado vs lo recibido en sus detalles:
     * RECIBIDA si todo esta recibido; PARCIALMENTE_RECIBIDA si hay algo recibido; EMITIDA si no.
     */
    private EstadoOrdenCompra recalcularEstadoOrden(Connection conn, int idOrden) throws SQLException {
        String sql = "SELECT SUM(cantidad_solicitada) AS sol, SUM(cantidad_recibida) AS rec "
                   + "FROM ordenes_compra_detalles WHERE id_orden = ?";
        double sol = 0;
        double rec = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    sol = rs.getDouble("sol");
                    rec = rs.getDouble("rec");
                }
            }
        }
        EstadoOrdenCompra estado;
        if (rec >= sol && sol > 0) {
            estado = EstadoOrdenCompra.RECIBIDA;
        } else if (rec > 0) {
            estado = EstadoOrdenCompra.PARCIALMENTE_RECIBIDA;
        } else {
            estado = EstadoOrdenCompra.EMITIDA;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE ordenes_compra SET estado = ?, fecha_actualizacion = datetime('now','localtime') "
                + "WHERE id_orden = ?")) {
            ps.setString(1, estado.name());
            ps.setInt(2, idOrden);
            ps.executeUpdate();
        }
        return estado;
    }
}
