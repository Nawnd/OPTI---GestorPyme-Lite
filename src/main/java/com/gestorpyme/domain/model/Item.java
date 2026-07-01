package com.gestorpyme.domain.model;

import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.enums.TipoItem;

import java.math.BigDecimal;

/**
 * Modelo de dominio que representa un item (producto, servicio, insumo, repuesto o
 * mano de obra), mapeado a la tabla 'items'. Es un POJO: NO depende de Swing ni de JDBC.
 *
 * Los montos usan {@link BigDecimal} (DEC-020): en SQLite se guardan como REAL y en
 * Java se manejan con BigDecimal (2 decimales) para los calculos monetarios.
 *
 * Correspondencia con las columnas de 'items':
 *  idItem             &lt;-&gt; id_item             (clave primaria; 0 = aun no persistido)
 *  codigo             &lt;-&gt; codigo              (opcional, unico)
 *  nombre             &lt;-&gt; nombre              (obligatorio)
 *  idCategoria        &lt;-&gt; id_categoria        (opcional; null = sin categoria)
 *  tipoItem           &lt;-&gt; tipo_item           (ver {@link TipoItem})
 *  precioCompra       &lt;-&gt; precio_compra       (REAL, &gt;= 0)
 *  precioVenta        &lt;-&gt; precio_venta        (REAL, &gt;= 0)
 *  controlaInventario &lt;-&gt; controla_inventario (0/1)
 *  stockMinimo        &lt;-&gt; stock_minimo        (REAL, &gt;= 0)
 *  estado             &lt;-&gt; estado              (ver {@link EstadoRegistro})
 *  fechaCreacion      &lt;-&gt; fecha_creacion      (texto fecha-hora generado por la BD)
 *
 * nombreCategoria es solo de lectura (se obtiene por JOIN) y no se persiste.
 */
public class Item {

    private int idItem;
    private String codigo;
    private String nombre;
    private Integer idCategoria;       // puede ser null
    private String nombreCategoria;    // solo lectura (para mostrar en la tabla)
    private Integer idSubtipo;         // puede ser null (clasificacion fina opcional)
    private String nombreSubtipo;      // solo lectura (para mostrar en la tabla)
    private TipoItem tipoItem;
    private BigDecimal precioCompra = BigDecimal.ZERO;
    private BigDecimal precioVenta = BigDecimal.ZERO;
    private boolean controlaInventario;
    private BigDecimal stockMinimo = BigDecimal.ZERO;
    private EstadoRegistro estado;
    private String fechaCreacion;
    /** Modo de calculo del valor cuando el item es un servicio (v0.7.3). Por defecto FIJO. */
    private ModoCalculoServicio modoCalculoServicio = ModoCalculoServicio.FIJO;
    /** Porcentaje sobre repuestos (solo si modo = PORCENTAJE_REPUESTOS). Puede ser null. */
    private BigDecimal porcentajeServicio;
    /** Unidad de medida (Paso A v0.8). Informativa; por defecto "Unidad". */
    private String unidadMedida = "Unidad";
    // Paso F: techo de reposicion (nullable) y proveedor preferido (nullable).
    private BigDecimal stockMaximo;            // null = sin techo (no afecta el calculo actual)
    private Integer idProveedorPreferido;      // null = sin proveedor preferido
    private String nombreProveedorPreferido;   // solo lectura (JOIN), no se persiste
    // Paso H: stock total en todas las bodegas (solo lectura, derivado por subconsulta).
    private BigDecimal stockTotal;

    public Item() {
    }

    public int getIdItem() {
        return idItem;
    }

    public void setIdItem(int idItem) {
        this.idItem = idItem;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Integer getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(Integer idCategoria) {
        this.idCategoria = idCategoria;
    }

    public String getNombreCategoria() {
        return nombreCategoria;
    }

    public void setNombreCategoria(String nombreCategoria) {
        this.nombreCategoria = nombreCategoria;
    }

    public Integer getIdSubtipo() {
        return idSubtipo;
    }

    public void setIdSubtipo(Integer idSubtipo) {
        this.idSubtipo = idSubtipo;
    }

    public String getNombreSubtipo() {
        return nombreSubtipo;
    }

    public void setNombreSubtipo(String nombreSubtipo) {
        this.nombreSubtipo = nombreSubtipo;
    }

    public TipoItem getTipoItem() {
        return tipoItem;
    }

    public void setTipoItem(TipoItem tipoItem) {
        this.tipoItem = tipoItem;
    }

    public BigDecimal getPrecioCompra() {
        return precioCompra;
    }

    public void setPrecioCompra(BigDecimal precioCompra) {
        this.precioCompra = precioCompra;
    }

    public BigDecimal getPrecioVenta() {
        return precioVenta;
    }

    public void setPrecioVenta(BigDecimal precioVenta) {
        this.precioVenta = precioVenta;
    }

    public boolean isControlaInventario() {
        return controlaInventario;
    }

    public void setControlaInventario(boolean controlaInventario) {
        this.controlaInventario = controlaInventario;
    }

    public BigDecimal getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(BigDecimal stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public EstadoRegistro getEstado() {
        return estado;
    }

    public void setEstado(EstadoRegistro estado) {
        this.estado = estado;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }

    public ModoCalculoServicio getModoCalculoServicio() {
        return modoCalculoServicio;
    }

    public void setModoCalculoServicio(ModoCalculoServicio modoCalculoServicio) {
        this.modoCalculoServicio = modoCalculoServicio;
    }

    public BigDecimal getPorcentajeServicio() {
        return porcentajeServicio;
    }

    public void setPorcentajeServicio(BigDecimal porcentajeServicio) {
        this.porcentajeServicio = porcentajeServicio;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    /** Stock maximo (techo de reposicion). Null si no esta definido. */
    public BigDecimal getStockMaximo() {
        return stockMaximo;
    }

    public void setStockMaximo(BigDecimal stockMaximo) {
        this.stockMaximo = stockMaximo;
    }

    /** Id del proveedor preferido (tercero PROVEEDOR). Null si no esta definido. */
    public Integer getIdProveedorPreferido() {
        return idProveedorPreferido;
    }

    public void setIdProveedorPreferido(Integer idProveedorPreferido) {
        this.idProveedorPreferido = idProveedorPreferido;
    }

    /** Nombre del proveedor preferido (solo lectura, via JOIN). */
    public String getNombreProveedorPreferido() {
        return nombreProveedorPreferido;
    }

    public void setNombreProveedorPreferido(String nombreProveedorPreferido) {
        this.nombreProveedorPreferido = nombreProveedorPreferido;
    }

    /** Stock total en todas las bodegas (solo lectura). Null si no se consultó. */
    public BigDecimal getStockTotal() {
        return stockTotal;
    }

    public void setStockTotal(BigDecimal stockTotal) {
        this.stockTotal = stockTotal;
    }

    /**
     * Estado de disponibilidad para la búsqueda en ventas (Paso H). Derivado de los campos del item:
     * <ul>
     *   <li>SERVICIO: no controla inventario.</li>
     *   <li>SIN STOCK: controla inventario y stock total &le; 0.</li>
     *   <li>BAJO: controla inventario y 0 &lt; stock total &le; stock mínimo.</li>
     *   <li>DISPONIBLE: controla inventario y stock total &gt; stock mínimo.</li>
     * </ul>
     * No modifica datos; solo informa.
     */
    public String estadoDisponibilidadVenta() {
        if (!controlaInventario) {
            return "SERVICIO";
        }
        BigDecimal total = stockTotal == null ? BigDecimal.ZERO : stockTotal;
        BigDecimal min = stockMinimo == null ? BigDecimal.ZERO : stockMinimo;
        if (total.signum() <= 0) {
            return "SIN STOCK";
        }
        if (total.compareTo(min) <= 0) {
            return "BAJO";
        }
        return "DISPONIBLE";
    }

    /**
     * Representacion amigable para mostrar en listas y combos: "codigo - nombre".
     * Si el item no tiene codigo (o es un centinela), devuelve solo el nombre.
     * No expone informacion tecnica interna.
     */
    @Override
    public String toString() {
        if (codigo == null || codigo.trim().isEmpty()) {
            return nombre == null ? "" : nombre;
        }
        return codigo + " - " + nombre;
    }
}
