package com.gestorpyme.repository;

import com.gestorpyme.domain.model.EmpresaConfiguracion;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Repositorio JDBC de la tabla 'empresa_configuracion'.
 *
 * La empresa es un REGISTRO UNICO: {@link #obtener()} devuelve la primera fila
 * (la de menor id) y {@link #guardar(EmpresaConfiguracion)} aplica un "upsert"
 * (UPDATE si ya existe un registro, INSERT si la tabla esta vacia).
 *
 * Toda la interaccion con SQLite usa PreparedStatement (sin concatenar valores).
 */
public class EmpresaRepository {

    /**
     * Obtiene la configuracion de la empresa, si existe.
     *
     * @return Optional con la configuracion, o vacio si aun no se ha guardado nada.
     */
    public Optional<EmpresaConfiguracion> obtener() {
        String sql = "SELECT id_empresa, nombre_empresa, documento, direccion, telefono, "
                + "correo, moneda, mensaje_recibo, ruta_logo, fecha_actualizacion "
                + "FROM empresa_configuracion ORDER BY id_empresa LIMIT 1";

        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return Optional.of(mapear(rs));
            }
            return Optional.empty();

        } catch (SQLException e) {
            throw new RuntimeException("Error al obtener la configuracion de la empresa: " + e.getMessage(), e);
        }
    }

    /**
     * Guarda la configuracion: actualiza el registro existente o inserta uno nuevo
     * si la tabla esta vacia. Tras guardar, el objeto recibido queda con su id real.
     *
     * @param empresa configuracion a persistir (ya validada por el servicio).
     */
    public void guardar(EmpresaConfiguracion empresa) {
        Optional<EmpresaConfiguracion> existente = obtener();
        if (existente.isPresent()) {
            empresa.setIdEmpresa(existente.get().getIdEmpresa());
            actualizar(empresa);
        } else {
            insertar(empresa);
        }
    }

    private void insertar(EmpresaConfiguracion e) {
        String sql = "INSERT INTO empresa_configuracion "
                + "(nombre_empresa, documento, direccion, telefono, correo, moneda, mensaje_recibo, ruta_logo) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {

            asignarParametros(ps, e);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    e.setIdEmpresa(keys.getInt(1));
                }
            }

        } catch (SQLException ex) {
            throw new RuntimeException("Error al insertar la configuracion de la empresa: " + ex.getMessage(), ex);
        }
    }

    private void actualizar(EmpresaConfiguracion e) {
        String sql = "UPDATE empresa_configuracion SET "
                + "nombre_empresa = ?, documento = ?, direccion = ?, telefono = ?, correo = ?, "
                + "moneda = ?, mensaje_recibo = ?, ruta_logo = ?, "
                + "fecha_actualizacion = datetime('now','localtime') "
                + "WHERE id_empresa = ?";

        try (Connection cn = DatabaseConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {

            asignarParametros(ps, e);
            ps.setInt(9, e.getIdEmpresa());
            ps.executeUpdate();

        } catch (SQLException ex) {
            throw new RuntimeException("Error al actualizar la configuracion de la empresa: " + ex.getMessage(), ex);
        }
    }

    /** Asigna los 8 parametros comunes de INSERT/UPDATE (posiciones 1..8). */
    private void asignarParametros(PreparedStatement ps, EmpresaConfiguracion e) throws SQLException {
        ps.setString(1, e.getNombreEmpresa());
        ps.setString(2, e.getDocumento());
        ps.setString(3, e.getDireccion());
        ps.setString(4, e.getTelefono());
        ps.setString(5, e.getCorreo());
        ps.setString(6, e.getMoneda());
        ps.setString(7, e.getMensajeRecibo());
        ps.setString(8, e.getRutaLogo());
    }

    /** Construye un objeto de dominio a partir de la fila actual del ResultSet. */
    private EmpresaConfiguracion mapear(ResultSet rs) throws SQLException {
        EmpresaConfiguracion e = new EmpresaConfiguracion();
        e.setIdEmpresa(rs.getInt("id_empresa"));
        e.setNombreEmpresa(rs.getString("nombre_empresa"));
        e.setDocumento(rs.getString("documento"));
        e.setDireccion(rs.getString("direccion"));
        e.setTelefono(rs.getString("telefono"));
        e.setCorreo(rs.getString("correo"));
        e.setMoneda(rs.getString("moneda"));
        e.setMensajeRecibo(rs.getString("mensaje_recibo"));
        e.setRutaLogo(rs.getString("ruta_logo"));
        e.setFechaActualizacion(rs.getString("fecha_actualizacion"));
        return e;
    }
}
