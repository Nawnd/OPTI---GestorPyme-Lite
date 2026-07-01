package com.gestorpyme.view.components;

import com.gestorpyme.controller.BodegaController;
import com.gestorpyme.controller.ItemController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.controller.VentaController;
import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.util.MoneyFormatter;

/**
 * Fabricas de {@link EntitySearchSpec} para las entidades maestras del sistema.
 *
 * Centraliza columnas, etiquetas y la conexion con la capa de controlador, de modo que
 * cualquier formulario use la misma busqueda inteligente con una sola linea:
 * {@code new EntityLookupField<>(SearchSpecs.items(itemController))}.
 * La funcion de busqueda llama al controlador (que llama a servicio/repositorio), por lo
 * que NO hay SQL en la vista. Se limita el numero de resultados para no cargar listas
 * interminables.
 */
public final class SearchSpecs {

    /** Maximo de resultados por busqueda (evita listas interminables). */
    public static final int LIMITE = 50;

    private SearchSpecs() {
    }

    /** Busqueda de productos / servicios por codigo o nombre. */
    public static EntitySearchSpec<Item> items(ItemController controller) {
        return new EntitySearchSpec<>(
                "Buscar producto / servicio",
                "Buscar producto por codigo o nombre...",
                new String[]{"Codigo", "Nombre", "Tipo", "Precio venta"},
                texto -> controller.buscar(texto, LIMITE),
                it -> new Object[]{
                        it.getCodigo(),
                        it.getNombre(),
                        it.isControlaInventario() ? textoTipo(it) : "Servicio",
                        MoneyFormatter.cop(it.getPrecioVenta())
                },
                it -> it.getCodigo() + " - " + it.getNombre());
    }

    /**
     * Busqueda de productos / servicios para VENTAS (Paso H): ademas de codigo, nombre, tipo y precio,
     * muestra el stock disponible (todas las bodegas) y el estado de disponibilidad. Solo informa;
     * no cambia la logica de venta ni bloquea por stock.
     */
    public static EntitySearchSpec<Item> itemsParaVenta(ItemController controller) {
        return new EntitySearchSpec<>(
                "Buscar producto / servicio",
                "Buscar producto por codigo o nombre...",
                new String[]{"Codigo", "Nombre", "Tipo", "Precio venta", "Inventario", "Stock disp.", "Estado"},
                texto -> controller.buscar(texto, LIMITE),
                it -> new Object[]{
                        it.getCodigo(),
                        it.getNombre(),
                        it.isControlaInventario() ? textoTipo(it) : "Servicio",
                        MoneyFormatter.cop(it.getPrecioVenta()),
                        it.isControlaInventario() ? "Si" : "No",
                        it.isControlaInventario()
                                ? (it.getStockTotal() == null ? "0" : it.getStockTotal().stripTrailingZeros().toPlainString())
                                : "-",
                        it.estadoDisponibilidadVenta()
                },
                it -> it.getCodigo() + " - " + it.getNombre());
    }

    /** Busqueda de SERVICIO / MANO_OBRA para la pestana Servicios de la OT (Paso U.2.1). */
    public static EntitySearchSpec<Item> serviciosParaOT(ItemController controller) {
        return new EntitySearchSpec<>(
                "Buscar servicio / mano de obra",
                "Buscar servicio por codigo o nombre...",
                new String[]{"Codigo", "Nombre", "Tipo", "Precio venta"},
                texto -> controller.buscarServicios(texto, LIMITE),
                it -> new Object[]{
                        it.getCodigo(),
                        it.getNombre(),
                        it.getTipoItem() == null ? "" : it.getTipoItem().getEtiqueta(),
                        MoneyFormatter.cop(it.getPrecioVenta())
                },
                it -> it.getCodigo() + " - " + it.getNombre());
    }

    /**
     * Busqueda de productos inventariables (repuestos / insumos / consumibles que controlan inventario)
     * para la pestana Repuestos de la OT (Paso U.2.1). Excluye servicios y mano de obra.
     */
    public static EntitySearchSpec<Item> repuestosParaOT(ItemController controller) {
        return new EntitySearchSpec<>(
                "Buscar repuesto / insumo inventariable",
                "Buscar producto inventariable por codigo o nombre...",
                new String[]{"Codigo", "Nombre", "Precio venta", "Stock disp."},
                texto -> controller.buscarInventariables(texto, LIMITE),
                it -> new Object[]{
                        it.getCodigo(),
                        it.getNombre(),
                        MoneyFormatter.cop(it.getPrecioVenta()),
                        it.getStockTotal() == null ? "0" : it.getStockTotal().stripTrailingZeros().toPlainString()
                },
                it -> it.getCodigo() + " - " + it.getNombre());
    }

    /** Busqueda de ventas por consecutivo (V-000002) o por cliente. */
    public static EntitySearchSpec<Venta> ventas(VentaController controller) {
        return new EntitySearchSpec<>(
                "Buscar venta",
                "Buscar por consecutivo (V-...) o cliente...",
                new String[]{"Numero", "Cliente", "Fecha", "Total", "Estado"},
                texto -> controller.buscar(texto, LIMITE),
                v -> new Object[]{
                        v.getNumeroVenta(),
                        v.getNombreTercero() == null ? "" : v.getNombreTercero(),
                        v.getFecha(),
                        MoneyFormatter.cop(v.getTotal()),
                        v.getEstado() == null ? "" : v.getEstado().toString()
                },
                v -> v.getNumeroVenta() + " - " + (v.getNombreTercero() == null ? "" : v.getNombreTercero()));
    }

    /** Busqueda de clientes y prospectos por nombre, documento, telefono o correo. */
    public static EntitySearchSpec<Tercero> clientes(TerceroController controller) {
        return new EntitySearchSpec<>(
                "Buscar cliente / prospecto",
                "Buscar cliente por nombre o documento...",
                new String[]{"Nombre", "Documento", "Telefono", "Tipo"},
                texto -> controller.buscarClientesYProspectos(texto, LIMITE),
                t -> new Object[]{t.getNombre(), t.getDocumento(), t.getTelefono(),
                        t.getTipoTercero() == null ? "" : t.getTipoTercero().toString()},
                Tercero::getNombre);
    }

    /** Busqueda de proveedores (para ordenes de compra). */
    public static EntitySearchSpec<Tercero> proveedores(TerceroController controller) {
        return new EntitySearchSpec<>(
                "Buscar proveedor",
                "Buscar proveedor por nombre o documento...",
                new String[]{"Proveedor", "Documento", "Telefono"},
                texto -> controller.buscarProveedores(texto, LIMITE),
                t -> new Object[]{t.getNombre(), t.getDocumento(), t.getTelefono()},
                Tercero::getNombre);
    }

    /** Busqueda de bodegas por nombre o ubicacion. */
    public static EntitySearchSpec<Bodega> bodegas(BodegaController controller) {
        return new EntitySearchSpec<>(
                "Buscar bodega",
                "Buscar bodega por nombre o ubicacion...",
                new String[]{"Bodega", "Ubicacion", "Estado"},
                texto -> controller.buscar(texto, LIMITE),
                b -> new Object[]{b.getNombre(),
                        b.getUbicacion() == null ? "" : b.getUbicacion(),
                        b.getEstado() == null ? "" : b.getEstado().toString()},
                Bodega::getNombre);
    }

    private static String textoTipo(Item it) {
        return it.getTipoItem() == null ? "Producto" : it.getTipoItem().toString();
    }
}
