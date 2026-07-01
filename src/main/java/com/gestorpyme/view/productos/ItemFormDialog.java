package com.gestorpyme.view.productos;

import com.gestorpyme.controller.ItemController;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.exception.ValidacionException;
import com.gestorpyme.domain.model.Categoria;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Subtipo;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Formulario modal para crear o editar un producto / servicio (item).
 * Captura los datos, convierte los montos a BigDecimal (DEC-020) y delega la
 * validacion y el guardado en {@link ItemController}. No contiene reglas de negocio ni SQL.
 * Capa: view.
 */
public class ItemFormDialog extends JDialog {

    private static final String FUENTE = "Segoe UI";
    /** Categoria "ficticia" que representa la ausencia de categoria (id 0 = null). */
    private static final Categoria SIN_CATEGORIA = new Categoria(0, "(Sin categoria)");
    /** Subtipo "ficticio" que representa la ausencia de subtipo (id 0 = null). */
    private static final Subtipo SIN_SUBTIPO = new Subtipo(0, "(Sin subtipo)");
    /** Proveedor "ficticio" que representa la ausencia de proveedor preferido (id 0 = null). */
    private static final Tercero SIN_PROVEEDOR = crearSinProveedor();

    private static Tercero crearSinProveedor() {
        Tercero t = new Tercero();
        t.setIdTercero(0);
        t.setNombre("(Sin proveedor)");
        return t;
    }

    private final ItemController controller;
    /** Item que se esta editando; null cuando se crea uno nuevo. */
    private final Item itemEnEdicion;

    private final JTextField campoCodigo = new JTextField(20);
    private final JTextField campoNombre = new JTextField(20);
    private final JComboBox<Categoria> comboCategoria = new JComboBox<>();
    private final JComboBox<Subtipo> comboSubtipo = new JComboBox<>();
    private final JComboBox<TipoItem> comboTipo = new JComboBox<>(TipoItem.values());
    private final JComboBox<ModoCalculoServicio> comboModo = new JComboBox<>(ModoCalculoServicio.values());
    /** Unidad de medida (Paso A): combo editable con sugerencias; admite texto libre. */
    private final JComboBox<String> comboUnidad = new JComboBox<>(
            new String[]{"Unidad", "Caja", "Metro", "Litro", "Galón", "Kit", "Par", "Servicio"});
    private final JTextField campoPorcentaje = new JTextField(20);
    private final JTextField campoPrecioCompra = new JTextField(20);
    private final JTextField campoPrecioVenta = new JTextField(20);
    private final JCheckBox checkInventario = new JCheckBox("Controla inventario");
    private final JTextField campoStockMinimo = new JTextField(20);
    // Paso F: techo de reposicion y proveedor preferido.
    private final JTextField campoStockMaximo = new JTextField(20);
    private final JComboBox<Tercero> comboProveedor = new JComboBox<>();

    private boolean guardado = false;

    public ItemFormDialog(Window propietario, ItemController controller, Item item) {
        super(propietario, ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.itemEnEdicion = item;
        setTitle(item == null ? "Nuevo producto / servicio" : "Editar producto / servicio");
        construir();
        cargarCategorias();
        cargarProveedores();
        // El stock maximo solo aplica si el item controla inventario.
        checkInventario.addActionListener(e -> actualizarStockMaximoHabilitado());
        if (item != null) {
            cargarDatos(item);
        } else {
            campoPrecioCompra.setText("0");
            campoPrecioVenta.setText("0");
            campoStockMinimo.setText("0");
        }
        actualizarCamposServicio(); // habilita/deshabilita modo y porcentaje segun el tipo
        actualizarStockMaximoHabilitado();
        pack();
        setLocationRelativeTo(propietario);
    }

    /** @return true si se guardo el registro (para que la lista se refresque). */
    public boolean fueGuardado() {
        return guardado;
    }

    private void construir() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6, 6, 6, 6);
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;

        int fila = 0;
        agregarFila(formulario, g, fila++, "Codigo", campoCodigo);
        agregarFila(formulario, g, fila++, "Nombre *", campoNombre);
        agregarFila(formulario, g, fila++, "Categoria", comboCategoria);
        agregarFila(formulario, g, fila++, "Subtipo", comboSubtipo);
        agregarFila(formulario, g, fila++, "Tipo *", comboTipo);
        agregarFila(formulario, g, fila++, "Unidad medida", comboUnidad);
        agregarFila(formulario, g, fila++, "Precio compra", campoPrecioCompra);
        agregarFila(formulario, g, fila++, "Precio venta", campoPrecioVenta);
        agregarFila(formulario, g, fila++, "Inventario", checkInventario);
        agregarFila(formulario, g, fila++, "Stock minimo", campoStockMinimo);
        agregarFila(formulario, g, fila++, "Stock maximo", campoStockMaximo);
        agregarFila(formulario, g, fila++, "Proveedor preferido", comboProveedor);
        agregarFila(formulario, g, fila++, "Modo servicio", comboModo);
        agregarFila(formulario, g, fila, "Porcentaje (%)", campoPorcentaje);

        // El combo de unidad admite texto libre ademas de las sugerencias.
        comboUnidad.setEditable(true);
        // Al cambiar la categoria, se recargan los subtipos coherentes con ella.
        comboCategoria.addActionListener(e -> cargarSubtipos(categoriaSeleccionadaId(), null));
        // Los campos de servicio solo aplican a servicios (sin inventario); se habilitan/ocultan segun el tipo.
        comboTipo.addActionListener(e -> actualizarCamposServicio());
        checkInventario.addActionListener(e -> actualizarCamposServicio());
        comboModo.addActionListener(e -> actualizarCamposServicio());

        JButton botonCancelar = new JButton("Cancelar");
        botonCancelar.addActionListener(e -> dispose());
        JButton botonGuardar = new JButton("Guardar");
        botonGuardar.addActionListener(e -> guardar());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        botones.add(botonCancelar);
        botones.add(botonGuardar);

        setLayout(new BorderLayout());
        add(formulario, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
    }

    private void agregarFila(JPanel panel, GridBagConstraints g, int fila, String etiqueta, JComponent campo) {
        g.gridx = 0;
        g.gridy = fila;
        g.weightx = 0;
        JLabel l = new JLabel(etiqueta);
        l.setFont(new Font(FUENTE, Font.PLAIN, 13));
        panel.add(l, g);

        g.gridx = 1;
        g.weightx = 1;
        panel.add(campo, g);
    }

    /** Carga el selector de categorias: opcion "(Sin categoria)" + categorias activas. */
    private void cargarCategorias() {
        comboCategoria.addItem(SIN_CATEGORIA);
        try {
            List<Categoria> categorias = controller.listarCategorias();
            for (Categoria c : categorias) {
                comboCategoria.addItem(c);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar las categorias:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Carga el selector de proveedores: opcion "(Sin proveedor)" + proveedores activos (Paso F). */
    private void cargarProveedores() {
        comboProveedor.addItem(SIN_PROVEEDOR);
        try {
            for (Tercero p : controller.listarProveedores()) {
                comboProveedor.addItem(p);
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudieron cargar los proveedores:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Habilita el campo de stock maximo solo si el item controla inventario; si no, lo limpia. */
    private void actualizarStockMaximoHabilitado() {
        boolean inventario = checkInventario.isSelected();
        campoStockMaximo.setEnabled(inventario);
        if (!inventario) {
            campoStockMaximo.setText("");
        }
    }

    /** Selecciona en el combo el proveedor cuyo id coincide, o "(Sin proveedor)" si es null o no esta. */
    private void seleccionarProveedor(Integer idProveedor) {
        if (idProveedor == null) {
            comboProveedor.setSelectedItem(SIN_PROVEEDOR);
            return;
        }
        for (int i = 0; i < comboProveedor.getItemCount(); i++) {
            if (comboProveedor.getItemAt(i).getIdTercero() == idProveedor) {
                comboProveedor.setSelectedIndex(i);
                return;
            }
        }
        comboProveedor.setSelectedItem(SIN_PROVEEDOR);
    }

    private void cargarDatos(Item item) {
        campoCodigo.setText(textoSeguro(item.getCodigo()));
        campoNombre.setText(textoSeguro(item.getNombre()));
        seleccionarCategoria(item.getIdCategoria());
        // Al fijar la categoria, el listener recarga los subtipos; ahora se elige el del item.
        seleccionarSubtipo(item.getIdSubtipo());
        comboTipo.setSelectedItem(item.getTipoItem());
        comboUnidad.setSelectedItem(item.getUnidadMedida() == null || item.getUnidadMedida().isEmpty()
                ? "Unidad" : item.getUnidadMedida());
        comboModo.setSelectedItem(item.getModoCalculoServicio() == null
                ? ModoCalculoServicio.FIJO : item.getModoCalculoServicio());
        campoPorcentaje.setText(item.getPorcentajeServicio() == null
                ? "" : item.getPorcentajeServicio().stripTrailingZeros().toPlainString());
        campoPrecioCompra.setText(item.getPrecioCompra().toPlainString());
        campoPrecioVenta.setText(item.getPrecioVenta().toPlainString());
        checkInventario.setSelected(item.isControlaInventario());
        campoStockMinimo.setText(item.getStockMinimo().toPlainString());
        // Paso F: stock maximo (vacio si null) y proveedor preferido.
        campoStockMaximo.setText(item.getStockMaximo() == null
                ? "" : item.getStockMaximo().stripTrailingZeros().toPlainString());
        seleccionarProveedor(item.getIdProveedorPreferido());
    }

    /** Id de la categoria seleccionada (0 si es "(Sin categoria)" o no hay seleccion). */
    private int categoriaSeleccionadaId() {
        Categoria c = (Categoria) comboCategoria.getSelectedItem();
        return (c == null) ? 0 : c.getIdCategoria();
    }

    /**
     * Recarga el combo de subtipos segun la categoria. Siempre incluye "(Sin subtipo)".
     *
     * @param idCategoria        categoria cuyos subtipos cargar (0 = ninguna).
     * @param idSubtipoSeleccion subtipo a preseleccionar, o null para "(Sin subtipo)".
     */
    private void cargarSubtipos(int idCategoria, Integer idSubtipoSeleccion) {
        comboSubtipo.removeAllItems();
        comboSubtipo.addItem(SIN_SUBTIPO);
        if (idCategoria > 0) {
            try {
                for (Subtipo s : controller.listarSubtipos(idCategoria)) {
                    comboSubtipo.addItem(s);
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "No se pudieron cargar los subtipos:\n" + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        seleccionarSubtipo(idSubtipoSeleccion);
    }

    /** Selecciona el subtipo cuyo id coincide, o "(Sin subtipo)" si es null o no esta. */
    private void seleccionarSubtipo(Integer idSubtipo) {
        if (idSubtipo == null) {
            comboSubtipo.setSelectedItem(SIN_SUBTIPO);
            return;
        }
        for (int i = 0; i < comboSubtipo.getItemCount(); i++) {
            if (comboSubtipo.getItemAt(i).getIdSubtipo() == idSubtipo) {
                comboSubtipo.setSelectedIndex(i);
                return;
            }
        }
        comboSubtipo.setSelectedItem(SIN_SUBTIPO);
    }

    /** Selecciona en el combo la categoria cuyo id coincide, o "(Sin categoria)" si es null. */
    private void seleccionarCategoria(Integer idCategoria) {
        if (idCategoria == null) {
            comboCategoria.setSelectedItem(SIN_CATEGORIA);
            return;
        }
        for (int i = 0; i < comboCategoria.getItemCount(); i++) {
            if (comboCategoria.getItemAt(i).getIdCategoria() == idCategoria) {
                comboCategoria.setSelectedIndex(i);
                return;
            }
        }
        comboCategoria.setSelectedItem(SIN_CATEGORIA);
    }

    /**
     * Habilita los campos de servicio solo cuando el item es un servicio (tipo SERVICIO o
     * MANO_OBRA y sin control de inventario). En productos/repuestos/insumos el modo queda FIJO
     * y se deshabilita. El porcentaje solo se habilita en modo PORCENTAJE_REPUESTOS.
     */
    private void actualizarCamposServicio() {
        boolean esServicio = esServicioSeleccionado();
        comboModo.setEnabled(esServicio);
        if (!esServicio && comboModo.getSelectedItem() != ModoCalculoServicio.FIJO) {
            comboModo.setSelectedItem(ModoCalculoServicio.FIJO);
        }
        boolean usaPorcentaje = esServicio
                && comboModo.getSelectedItem() == ModoCalculoServicio.PORCENTAJE_REPUESTOS;
        campoPorcentaje.setEnabled(usaPorcentaje);
        if (!usaPorcentaje) {
            campoPorcentaje.setText("");
        }
    }

    /** Indica si la combinacion tipo/inventario actual corresponde a un servicio. */
    private boolean esServicioSeleccionado() {
        TipoItem t = (TipoItem) comboTipo.getSelectedItem();
        boolean tipoServicio = (t == TipoItem.SERVICIO || t == TipoItem.MANO_OBRA);
        return tipoServicio && !checkInventario.isSelected();
    }

    /** Construye el item con los datos del formulario y lo manda a guardar. */
    private void guardar() {
        try {
            // Si es edicion, conserva el id y el estado originales.
            Item item = (itemEnEdicion != null) ? itemEnEdicion : new Item();
            item.setCodigo(vacioANull(campoCodigo.getText()));
            item.setNombre(campoNombre.getText().trim());

            Categoria categoria = (Categoria) comboCategoria.getSelectedItem();
            item.setIdCategoria((categoria == null || categoria.getIdCategoria() == 0)
                    ? null : categoria.getIdCategoria());

            Subtipo subtipo = (Subtipo) comboSubtipo.getSelectedItem();
            item.setIdSubtipo((subtipo == null || subtipo.getIdSubtipo() == 0)
                    ? null : subtipo.getIdSubtipo());

            item.setTipoItem((TipoItem) comboTipo.getSelectedItem());

            // Unidad de medida (Paso A): texto del combo editable; el service normaliza a "Unidad" si queda vacio.
            Object unidadSel = comboUnidad.isEditable() && comboUnidad.getEditor() != null
                    ? comboUnidad.getEditor().getItem() : comboUnidad.getSelectedItem();
            item.setUnidadMedida(unidadSel == null ? "" : unidadSel.toString());

            // Configuracion de servicio (v0.7.3). Si el campo no aplica, queda en FIJO sin porcentaje.
            item.setModoCalculoServicio((ModoCalculoServicio) comboModo.getSelectedItem());
            if (campoPorcentaje.isEnabled()) {
                String pct = campoPorcentaje.getText().trim();
                item.setPorcentajeServicio(pct.isEmpty() ? null : parsearMonto(pct, "El porcentaje"));
            } else {
                item.setPorcentajeServicio(null);
            }

            item.setPrecioCompra(parsearMonto(campoPrecioCompra.getText(), "El precio de compra"));
            item.setPrecioVenta(parsearMonto(campoPrecioVenta.getText(), "El precio de venta"));
            item.setControlaInventario(checkInventario.isSelected());
            item.setStockMinimo(parsearMonto(campoStockMinimo.getText(), "El stock minimo"));

            // Paso F: stock maximo (vacio = null; el service lo limpia si no controla inventario y valida rangos).
            String maxTexto = campoStockMaximo.getText() == null ? "" : campoStockMaximo.getText().trim();
            item.setStockMaximo(maxTexto.isEmpty() ? null : parsearMonto(maxTexto, "El stock maximo"));
            // Proveedor preferido (id 0 = "(Sin proveedor)" -> null).
            Tercero prov = (Tercero) comboProveedor.getSelectedItem();
            item.setIdProveedorPreferido((prov == null || prov.getIdTercero() == 0)
                    ? null : prov.getIdTercero());

            controller.guardar(item);
            guardado = true;
            dispose();
        } catch (ValidacionException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(),
                    "Datos incompletos", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar:\n" + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Convierte el texto de un campo de monto a BigDecimal.
     * Acepta coma o punto como separador decimal. Un campo vacio equivale a 0.
     *
     * @throws ValidacionException si el texto no es un numero valido.
     */
    private BigDecimal parsearMonto(String texto, String campo) {
        String limpio = texto.trim().replace(",", ".");
        if (limpio.isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            throw new ValidacionException(campo + " debe ser un numero valido.");
        }
    }

    private String textoSeguro(String valor) {
        return valor != null ? valor : "";
    }

    private String vacioANull(String valor) {
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
