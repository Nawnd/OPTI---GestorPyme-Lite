package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoLote;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Lote;
import com.gestorpyme.repository.LoteRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Servicio del modulo Lotes y vencimientos.
 * Contiene las reglas de negocio antes de delegar la persistencia al repositorio.
 */
public class LoteService {

    /** Horizonte por defecto (dias) para "proximos a vencer". */
    public static final int DIAS_PROXIMOS_DEFECTO = 30;

    private final LoteRepository loteRepository;

    public LoteService() {
        this(new LoteRepository());
    }

    public LoteService(LoteRepository loteRepository) {
        this.loteRepository = loteRepository;
    }

    /** Lista todos los lotes. */
    public List<Lote> listar() throws SQLException {
        return loteRepository.listar();
    }

    /**
     * Filtra una lista de lotes en memoria (Paso H), de forma combinable (Y). Metodo puro y testeable.
     *
     * @param lotes             lista base (p. ej. de {@link #listar()}).
     * @param idItem            item a filtrar, o null para no filtrar por item.
     * @param idBodega          bodega a filtrar, o null para no filtrar por bodega.
     * @param estadoVencimiento estado de vencimiento (VIGENTE/POR VENCER/VENCIDO/SIN FECHA) o null/"" para todos.
     * @return lotes que cumplen todos los filtros indicados.
     */
    public List<Lote> filtrar(List<Lote> lotes, Integer idItem, Integer idBodega, String estadoVencimiento) {
        boolean filtraEstado = estadoVencimiento != null && !estadoVencimiento.trim().isEmpty();
        List<Lote> out = new java.util.ArrayList<>();
        for (Lote l : lotes) {
            if (idItem != null && l.getIdItem() != idItem) {
                continue;
            }
            if (idBodega != null && (l.getIdBodega() == null || !l.getIdBodega().equals(idBodega))) {
                continue;
            }
            if (filtraEstado && !estadoVencimiento.equalsIgnoreCase(l.estadoVencimiento())) {
                continue;
            }
            out.add(l);
        }
        return out;
    }

    /**
     * Lista los lotes proximos a vencer dentro del horizonte indicado.
     *
     * @param dias horizonte en dias; si es menor o igual a 0 se usa el valor por defecto.
     */
    public List<Lote> listarProximosAVencer(int dias) throws SQLException {
        int horizonte = (dias <= 0) ? DIAS_PROXIMOS_DEFECTO : dias;
        return loteRepository.listarProximosAVencer(horizonte);
    }

    /**
     * Valida y registra un lote nuevo.
     *
     * Reglas:
     * - El item es obligatorio.
     * - El numero de lote es obligatorio.
     * - La cantidad inicial no puede ser negativa.
     * - Si se asigna estado nulo, se asume ACTIVO.
     *
     * @param lote lote a registrar.
     * @throws ValidacionException si algun dato no cumple las reglas.
     */
    public void registrar(Lote lote) throws SQLException {
        if (lote == null) {
            throw new ValidacionException("No hay datos de lote para registrar.");
        }
        if (lote.getIdItem() <= 0) {
            throw new ValidacionException("Debe seleccionar un item para el lote.");
        }
        if (esVacio(lote.getNumeroLote())) {
            throw new ValidacionException("El numero de lote es obligatorio.");
        }
        if (lote.getCantidadInicial() == null) {
            lote.setCantidadInicial(BigDecimal.ZERO);
        }
        if (lote.getCantidadInicial().signum() < 0) {
            throw new ValidacionException("La cantidad inicial no puede ser negativa.");
        }
        if (lote.getEstado() == null) {
            lote.setEstado(EstadoLote.ACTIVO);
        }
        // fecha_ingreso es NOT NULL en BD: si no se indica, se asume la fecha de hoy.
        // (No se puede enviar null y dejar el DEFAULT, porque SQLite no lo aplica
        //  cuando la columna se incluye explicitamente en el INSERT.)
        if (esVacio(lote.getFechaIngreso())) {
            lote.setFechaIngreso(LocalDate.now().toString());
        }
        loteRepository.insertar(lote);
    }

    /** Cambia el estado de un lote. */
    public void cambiarEstado(int idLote, EstadoLote estado) throws SQLException {
        if (estado == null) {
            throw new ValidacionException("El estado de lote es obligatorio.");
        }
        loteRepository.cambiarEstado(idLote, estado);
    }

    private boolean esVacio(String v) {
        return v == null || v.trim().isEmpty();
    }
}
