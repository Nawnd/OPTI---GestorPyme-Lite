package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Categoria;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Subtipo;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.repository.CategoriaRepository;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.repository.SubtipoRepository;
import com.gestorpyme.repository.TerceroRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Reglas de negocio de los items (productos / servicios).
 * Valida los datos y coordina los repositorios de items, categorias y subtipos.
 * No contiene SQL ni codigo de interfaz.
 * Capa: service.
 */
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubtipoRepository subtipoRepository;
    private final TerceroRepository terceroRepository;

    /** Constructor por defecto: crea sus propios repositorios. */
    public ItemService() {
        this(new ItemRepository(), new CategoriaRepository(), new SubtipoRepository());
    }

    /** Constructor que conserva la firma usada por pruebas existentes (cablea SubtipoRepository por defecto). */
    public ItemService(ItemRepository itemRepository, CategoriaRepository categoriaRepository) {
        this(itemRepository, categoriaRepository, new SubtipoRepository());
    }

    /** Constructor que permite inyectar los tres repositorios (util en pruebas). */
    public ItemService(ItemRepository itemRepository, CategoriaRepository categoriaRepository,
                       SubtipoRepository subtipoRepository) {
        this.itemRepository = itemRepository;
        this.categoriaRepository = categoriaRepository;
        this.subtipoRepository = subtipoRepository;
        this.terceroRepository = new TerceroRepository();
    }

    /** Lista todos los items ordenados por nombre. */
    public List<Item> listar() throws SQLException {
        return itemRepository.listar();
    }

    /** Lista las categorias activas (para el selector del formulario). */
    public List<Categoria> listarCategorias() throws SQLException {
        return categoriaRepository.listarActivas();
    }

    /** Lista los proveedores activos (para el selector de proveedor preferido, Paso F). */
    public List<Tercero> listarProveedores() throws SQLException {
        return terceroRepository.listarProveedores();
    }

    /** Lista los subtipos activos de una categoria (para el combo dependiente del formulario). */
    public List<Subtipo> listarSubtipos(int idCategoria) throws SQLException {
        return subtipoRepository.listarPorCategoria(idCategoria);
    }

    /**
     * Valida y guarda un item. Si su id es 0 lo inserta (y le asigna el id generado);
     * en caso contrario lo actualiza.
     *
     * @throws ValidacionException si algun dato no cumple las reglas (mensaje para el usuario).
     * @throws SQLException si ocurre un error de base de datos.
     */
    /** Busca items activos por codigo o nombre (limite de resultados). */
    public List<Item> buscar(String texto, int limite) throws SQLException {
        return itemRepository.buscar(texto, limite);
    }

    /** Busca servicios / mano de obra (pestana Servicios de la OT, Paso U.2.1). */
    public List<Item> buscarServicios(String texto, int limite) throws SQLException {
        return itemRepository.buscarServicios(texto, limite);
    }

    /** Busca productos inventariables (pestana Repuestos de la OT, Paso U.2.1). */
    public List<Item> buscarInventariables(String texto, int limite) throws SQLException {
        return itemRepository.buscarInventariables(texto, limite);
    }

    public void guardar(Item item) throws SQLException {
        validar(item);
        validarSubtipo(item);
        validarServicio(item);
        // Paso F: stock maximo y proveedor preferido (rangos y tipo de tercero).
        validarStockMaximoYProveedor(item);
        // Unidad de medida (Paso A): default seguro "Unidad" si viene nula o vacia.
        if (item.getUnidadMedida() == null || item.getUnidadMedida().trim().isEmpty()) {
            item.setUnidadMedida("Unidad");
        } else {
            item.setUnidadMedida(item.getUnidadMedida().trim());
        }
        // El codigo es opcional, pero si se indica debe ser unico.
        if (!esVacio(item.getCodigo())
                && itemRepository.existeCodigo(item.getCodigo().trim(), item.getIdItem())) {
            throw new ValidacionException("Ya existe un item con el codigo '" + item.getCodigo().trim() + "'.");
        }
        if (item.getEstado() == null) {
            item.setEstado(EstadoRegistro.ACTIVO);
        }
        if (item.getIdItem() <= 0) {
            int nuevoId = itemRepository.insertar(item);
            item.setIdItem(nuevoId);
        } else {
            itemRepository.actualizar(item);
        }
    }

    /** Cambia el estado (baja/alta logica) de un item. */
    public void cambiarEstado(int idItem, EstadoRegistro estado) throws SQLException {
        itemRepository.cambiarEstado(idItem, estado);
    }

    /**
     * Si el item trae subtipo, valida su coherencia: debe existir y pertenecer a la
     * categoria seleccionada. El subtipo es opcional (puede quedar null).
     */
    private void validarSubtipo(Item item) throws SQLException {
        if (item.getIdSubtipo() == null) {
            return; // subtipo opcional
        }
        if (item.getIdCategoria() == null) {
            throw new ValidacionException("No se puede asignar un subtipo sin una categoria.");
        }
        Optional<Subtipo> sub = subtipoRepository.buscarPorId(item.getIdSubtipo());
        if (sub.isEmpty()) {
            throw new ValidacionException("El subtipo seleccionado no existe.");
        }
        if (sub.get().getIdCategoria() != item.getIdCategoria()) {
            throw new ValidacionException("El subtipo no corresponde a la categoria seleccionada.");
        }
    }

    /**
     * Valida la configuracion de servicio (piloto Mano de Obra, v0.7.3).
     * <ul>
     *   <li>Si el modo es nulo, se asume FIJO.</li>
     *   <li>El modo PORCENTAJE_REPUESTOS solo aplica a servicios (items que NO controlan
     *       inventario) y exige un porcentaje entre 0 y 100.</li>
     *   <li>En modo FIJO el porcentaje no aplica y se descarta (queda null).</li>
     * </ul>
     */
    private void validarServicio(Item item) {
        if (item.getModoCalculoServicio() == null) {
            item.setModoCalculoServicio(ModoCalculoServicio.FIJO);
        }
        if (item.getModoCalculoServicio() == ModoCalculoServicio.PORCENTAJE_REPUESTOS) {
            if (item.isControlaInventario()) {
                throw new ValidacionException(
                        "El modo 'porcentaje sobre repuestos' solo aplica a servicios (items que no controlan inventario).");
            }
            BigDecimal pct = item.getPorcentajeServicio();
            if (pct == null) {
                throw new ValidacionException("Indique el porcentaje de mano de obra.");
            }
            if (pct.signum() < 0) {
                throw new ValidacionException("El porcentaje no puede ser negativo.");
            }
            if (pct.compareTo(new BigDecimal("100")) > 0) {
                throw new ValidacionException("El porcentaje no puede superar 100%.");
            }
        } else {
            // FIJO: el porcentaje no aplica.
            item.setPorcentajeServicio(null);
        }
    }

    /**
     * Reglas del Paso F (stock maximo y proveedor preferido):
     * <ul>
     *   <li>Si el item NO controla inventario, el stock maximo se limpia a null (no aplica).</li>
     *   <li>Stock maximo null: valido. Negativo: se rechaza. Menor que el stock minimo: se rechaza.</li>
     *   <li>Proveedor preferido null: valido. Debe existir, ser de tipo PROVEEDOR y estar ACTIVO.</li>
     * </ul>
     * No modifica stock actual, Kardex, compras ni recepciones.
     */
    private void validarStockMaximoYProveedor(Item item) throws SQLException {
        // Stock maximo solo tiene sentido en items que controlan inventario.
        if (!item.isControlaInventario()) {
            item.setStockMaximo(null);
        }
        BigDecimal maximo = item.getStockMaximo();
        if (maximo != null) {
            if (maximo.signum() < 0) {
                throw new ValidacionException("El stock máximo no puede ser negativo.");
            }
            BigDecimal minimo = item.getStockMinimo() == null ? BigDecimal.ZERO : item.getStockMinimo();
            if (maximo.compareTo(minimo) < 0) {
                throw new ValidacionException("El stock máximo no puede ser menor que el stock mínimo.");
            }
        }
        // Proveedor preferido: debe ser un proveedor activo (si se indica).
        Integer idProv = item.getIdProveedorPreferido();
        if (idProv != null) {
            Optional<Tercero> prov = terceroRepository.buscarPorId(idProv);
            boolean valido = prov.isPresent()
                    && prov.get().getTipoTercero() == TipoTercero.PROVEEDOR
                    && prov.get().getEstado() == EstadoRegistro.ACTIVO;
            if (!valido) {
                throw new ValidacionException("El proveedor preferido debe ser un proveedor activo.");
            }
        }
    }

    /** Aplica las reglas minimas de validacion antes de persistir. */
    private void validar(Item item) {
        if (item.getTipoItem() == null) {
            throw new ValidacionException("Debe indicar el tipo de item.");
        }
        if (esVacio(item.getNombre())) {
            throw new ValidacionException("El nombre es obligatorio.");
        }
        validarNoNegativo(item.getPrecioCompra(), "El precio de compra");
        validarNoNegativo(item.getPrecioVenta(), "El precio de venta");
        validarNoNegativo(item.getStockMinimo(), "El stock minimo");
    }

    private void validarNoNegativo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) {
            throw new ValidacionException(campo + " no puede ser negativo.");
        }
    }

    private boolean esVacio(String valor) {
        return valor == null || valor.trim().isEmpty();
    }
}
