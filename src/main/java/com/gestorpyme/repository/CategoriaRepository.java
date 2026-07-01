package com.gestorpyme.repository;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.model.Categoria;
import com.gestorpyme.infrastructure.database.DatabaseConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a datos de la tabla 'categorias'. Por ahora solo se necesita la lectura
 * de categorias activas para poblar el selector del formulario de productos.
 * Capa: repository.
 */
public class CategoriaRepository {

    /** Lista las categorias activas ordenadas por nombre. */
    public List<Categoria> listarActivas() throws SQLException {
        String sql = "SELECT id_categoria, nombre, descripcion, estado FROM categorias "
                   + "WHERE estado = 'ACTIVO' ORDER BY nombre";
        List<Categoria> lista = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    private Categoria mapear(ResultSet rs) throws SQLException {
        Categoria categoria = new Categoria();
        categoria.setIdCategoria(rs.getInt("id_categoria"));
        categoria.setNombre(rs.getString("nombre"));
        categoria.setDescripcion(rs.getString("descripcion"));
        categoria.setEstado(EstadoRegistro.desde(rs.getString("estado")));
        return categoria;
    }
}
