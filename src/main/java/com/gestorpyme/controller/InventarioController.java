package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.MovimientoInventario;
import com.gestorpyme.service.InventarioService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Controlador de la vista de inventario (patron MVC/MVP simplificado).
 * Recibe las acciones de la vista y delega en {@link InventarioService}.
 * No contiene reglas de negocio, ni SQL, ni componentes Swing.
 * Capa: controller.
 */
public class InventarioController {

    private final InventarioService service;

    public InventarioController() {
        this(new InventarioService());
    }

    public InventarioController(InventarioService service) {
        this.service = service;
    }

    /** Lista las existencias (stock por item y bodega). */
    public List<ExistenciaStock> listarExistencias() throws SQLException {
        return service.listarExistencias();
    }

    /** Conciliación de solo lectura entre stock autoritativo y stock loteado por item + bodega (Paso Q). */
    public java.util.List<com.gestorpyme.domain.model.ConciliacionLoteStockItem> conciliacionLoteStock()
            throws SQLException {
        return service.conciliacionLoteStock();
    }

    /** Actualiza la ubicacion interna de una existencia (item + bodega). No afecta cantidad ni Kardex. */
    public void actualizarUbicacion(int idItem, int idBodega, String ubicacion) throws SQLException {
        service.actualizarUbicacion(idItem, idBodega, ubicacion);
    }

    /** Filtra existencias en memoria por item, bodega, categoria, subtipo, estado de stock y ubicacion. */
    public List<ExistenciaStock> filtrarExistencias(List<ExistenciaStock> existencias,
            String codigoItem, String nombreBodega, String categoria, String subtipo,
            String estadoStock, String ubicacionParcial) {
        return service.filtrarExistencias(existencias, codigoItem, nombreBodega,
                categoria, subtipo, estadoStock, ubicacionParcial);
    }

    /** Items que controlan inventario y estan activos (para el selector). */
    public List<Item> itemsInventariables() throws SQLException {
        return service.itemsInventariables();
    }

    /** Bodegas activas (para el selector). */
    public List<Bodega> bodegasActivas() throws SQLException {
        return service.bodegasActivas();
    }

    /** Valida y registra un movimiento de inventario (entrada/salida/ajuste). */
    public void registrarMovimiento(int idItem, int idBodega, TipoMovimiento tipo,
                                    BigDecimal cantidad, String motivo, Integer idUsuario) throws SQLException {
        service.registrarMovimiento(idItem, idBodega, tipo, cantidad, motivo, idUsuario);
    }

    /** Consulta de Kardex con filtros opcionales (item, bodega, tipo, rango de fechas). */
    public List<MovimientoInventario> listarMovimientos(Integer idItem, Integer idBodega,
                                                        TipoMovimiento tipo,
                                                        String fechaDesde, String fechaHasta) throws SQLException {
        return service.listarMovimientos(idItem, idBodega, tipo, fechaDesde, fechaHasta);
    }
}
