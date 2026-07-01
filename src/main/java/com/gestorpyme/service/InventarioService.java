package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.TipoMovimiento;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.ExistenciaStock;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.MovimientoInventario;
import com.gestorpyme.repository.BodegaRepository;
import com.gestorpyme.repository.InventarioRepository;
import com.gestorpyme.repository.ItemRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas de negocio del inventario. Valida los movimientos y coordina los
 * repositorios de inventario, items y bodegas. La actualizacion transaccional
 * de stock + Kardex la realiza el repositorio (DEC-019).
 * No contiene SQL ni codigo de interfaz.
 * Capa: service.
 */
public class InventarioService {

    private final InventarioRepository inventarioRepository;
    private final ItemRepository itemRepository;
    private final BodegaRepository bodegaRepository;

    public InventarioService() {
        this(new InventarioRepository(), new ItemRepository(), new BodegaRepository());
    }

    public InventarioService(InventarioRepository inventarioRepository,
                             ItemRepository itemRepository,
                             BodegaRepository bodegaRepository) {
        this.inventarioRepository = inventarioRepository;
        this.itemRepository = itemRepository;
        this.bodegaRepository = bodegaRepository;
    }

    /** Lista las existencias (stock por item y bodega). */
    public List<ExistenciaStock> listarExistencias() throws SQLException {
        return inventarioRepository.listarExistencias();
    }

    /**
     * Conciliación de solo lectura entre el stock autoritativo y el stock loteado por item + bodega
     * (Paso Q). Delega en el repositorio; no modifica datos ni recalcula nada.
     */
    public java.util.List<com.gestorpyme.domain.model.ConciliacionLoteStockItem> conciliacionLoteStock()
            throws SQLException {
        return inventarioRepository.conciliacionLoteStock();
    }

    /** Items que controlan inventario y estan activos (los unicos que manejan stock). */
    public List<Item> itemsInventariables() throws SQLException {
        List<Item> resultado = new ArrayList<>();
        for (Item item : itemRepository.listar()) {
            if (item.isControlaInventario() && item.getEstado() == EstadoRegistro.ACTIVO) {
                resultado.add(item);
            }
        }
        return resultado;
    }

    /** Bodegas activas (destino/origen de los movimientos). */
    public List<Bodega> bodegasActivas() throws SQLException {
        return bodegaRepository.listarActivas();
    }

    /** Disponibilidad de un item por bodega activa (Paso I). Solo lectura. */
    public java.util.List<com.gestorpyme.domain.model.DisponibilidadBodegaItem> disponibilidadPorBodega(int idItem)
            throws SQLException {
        return inventarioRepository.disponibilidadPorBodega(idItem);
    }

    /**
     * Filtra existencias en memoria (Paso B). Todos los criterios se combinan con Y; un criterio
     * nulo o vacio no filtra. La ubicacion se compara por coincidencia parcial sin distinguir
     * mayusculas (ej. "a1" encuentra "Estante A1"). El estado de stock se compara con el estado
     * calculado de cada existencia (SIN STOCK / BAJO / NORMAL). No accede a la base de datos.
     *
     * @param existencias    lista base (no se modifica).
     * @param codigoItem     codigo exacto del item (o null/"" para no filtrar).
     * @param nombreBodega   nombre exacto de la bodega (o null/"" para no filtrar).
     * @param categoria      nombre exacto de la categoria (o null/"" para no filtrar).
     * @param subtipo        nombre exacto del subtipo (o null/"" para no filtrar).
     * @param estadoStock    estado a coincidir (o null/"" para no filtrar).
     * @param ubicacionParcial texto parcial de ubicacion (o null/"" para no filtrar).
     * @return nueva lista filtrada.
     */
    public List<ExistenciaStock> filtrarExistencias(List<ExistenciaStock> existencias,
            String codigoItem, String nombreBodega, String categoria, String subtipo,
            String estadoStock, String ubicacionParcial) {
        List<ExistenciaStock> resultado = new java.util.ArrayList<>();
        String ubic = ubicacionParcial == null ? "" : ubicacionParcial.trim().toLowerCase();
        for (ExistenciaStock e : existencias) {
            if (tieneValor(codigoItem) && !codigoItem.equals(e.getCodigoItem())) {
                continue;
            }
            if (tieneValor(nombreBodega) && !nombreBodega.equals(e.getNombreBodega())) {
                continue;
            }
            if (tieneValor(categoria) && !categoria.equals(textoSeguro(e.getNombreCategoria()))) {
                continue;
            }
            if (tieneValor(subtipo) && !subtipo.equals(textoSeguro(e.getNombreSubtipo()))) {
                continue;
            }
            if (tieneValor(estadoStock) && !estadoStock.equals(e.getEstadoStock())) {
                continue;
            }
            if (!ubic.isEmpty() && !textoSeguro(e.getUbicacionInterna()).toLowerCase().contains(ubic)) {
                continue;
            }
            resultado.add(e);
        }
        return resultado;
    }

    private static boolean tieneValor(String s) {
        return s != null && !s.trim().isEmpty();
    }

    private static String textoSeguro(String s) {
        return s == null ? "" : s;
    }

    /**
     * Actualiza la ubicacion interna de una existencia (item + bodega) (Paso A).
     * Normaliza espacios (trim); si queda vacia, se guarda null. No afecta la cantidad,
     * ni el Kardex, ni ventas/compras: es solo informacion para localizar el producto.
     *
     * @param idItem    item.
     * @param idBodega  bodega.
     * @param ubicacion ubicacion interna (texto libre; vacia -> null).
     * @throws SQLException si ocurre un error de base de datos.
     */
    public void actualizarUbicacion(int idItem, int idBodega, String ubicacion) throws SQLException {
        String limpia = (ubicacion == null) ? null : ubicacion.trim();
        if (limpia != null && limpia.isEmpty()) {
            limpia = null;
        }
        inventarioRepository.actualizarUbicacion(idItem, idBodega, limpia);
    }

    /**
     * Valida y registra un movimiento de inventario.
     * Reglas:
     *  - La cantidad debe ser mayor que cero.
     *  - En salidas/ajustes negativos, no se puede sacar mas de lo disponible.
     * La actualizacion de stock + Kardex es transaccional (en el repositorio).
     *
     * @throws ValidacionException si algun dato no cumple las reglas.
     * @throws SQLException si ocurre un error de base de datos.
     */
    public void registrarMovimiento(int idItem, int idBodega, TipoMovimiento tipo,
                                    BigDecimal cantidad, String motivo, Integer idUsuario) throws SQLException {
        if (cantidad == null || cantidad.signum() <= 0) {
            throw new ValidacionException("La cantidad debe ser mayor que cero.");
        }
        // RF-05: en ajustes de inventario el motivo es obligatorio (trazabilidad). No aplica a
        // otros tipos (ENTRADA, SALIDA, ENTRADA_COMPRA, SALIDA_VENTA, TRASLADO), que conservan
        // su comportamiento previo (motivo opcional).
        if (esAjuste(tipo) && (motivo == null || motivo.trim().isEmpty())) {
            throw new ValidacionException("El motivo es obligatorio para ajustes de inventario.");
        }
        if (!tipo.incrementaStock()) {
            BigDecimal disponible = inventarioRepository.obtenerCantidad(idItem, idBodega);
            if (cantidad.compareTo(disponible) > 0) {
                throw new ValidacionException(
                        "Stock insuficiente. Disponible: " + disponible.toPlainString());
            }
        }
        inventarioRepository.registrarMovimiento(idItem, idBodega, tipo, cantidad, motivo, idUsuario);
    }

    /** @return true si el tipo es un ajuste manual de inventario (positivo o negativo). */
    private static boolean esAjuste(TipoMovimiento tipo) {
        return tipo == TipoMovimiento.AJUSTE_POSITIVO || tipo == TipoMovimiento.AJUSTE_NEGATIVO;
    }

    /**
     * Consulta de Kardex con filtros opcionales (item, bodega, tipo, rango de fechas).
     * No aplica reglas de negocio adicionales: es una lectura. Cualquier filtro nulo se ignora.
     *
     * @return lista de movimientos del mas reciente al mas antiguo.
     */
    public List<MovimientoInventario> listarMovimientos(Integer idItem, Integer idBodega,
                                                        TipoMovimiento tipo,
                                                        String fechaDesde, String fechaHasta) throws SQLException {
        return inventarioRepository.listarMovimientos(idItem, idBodega, tipo, fechaDesde, fechaHasta);
    }
}
