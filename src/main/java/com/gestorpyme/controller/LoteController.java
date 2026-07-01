package com.gestorpyme.controller;

import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.service.LoteService;

import java.sql.SQLException;
import java.util.List;

/**
 * Controlador del modulo Lotes y vencimientos.
 * Coordina la vista con el servicio; sin reglas de negocio ni SQL.
 */
public class LoteController {

    private final LoteService loteService;

    public LoteController() {
        this(new LoteService());
    }

    public LoteController(LoteService loteService) {
        this.loteService = loteService;
    }

    /** Lista todos los lotes. */
    public List<Lote> listar() throws SQLException {
        return loteService.listar();
    }

    /** Filtra lotes en memoria por item, bodega y estado de vencimiento (Paso H). */
    public List<Lote> filtrar(List<Lote> lotes, Integer idItem, Integer idBodega, String estadoVencimiento) {
        return loteService.filtrar(lotes, idItem, idBodega, estadoVencimiento);
    }

    /** Lista los lotes proximos a vencer dentro de N dias. */
    public List<Lote> listarProximosAVencer(int dias) throws SQLException {
        return loteService.listarProximosAVencer(dias);
    }

    /** Valida y registra un lote nuevo. */
    public void registrar(Lote lote) throws SQLException {
        loteService.registrar(lote);
    }

    /** Cambia el estado de un lote. */
    public void cambiarEstado(int idLote, EstadoLote estado) throws SQLException {
        loteService.cambiarEstado(idLote, estado);
    }
}
