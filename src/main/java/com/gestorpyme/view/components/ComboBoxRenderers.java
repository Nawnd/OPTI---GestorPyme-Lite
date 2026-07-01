package com.gestorpyme.view.components;

import com.gestorpyme.domain.model.Bodega;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import java.awt.Component;

/**
 * Renderizadores reutilizables para {@link javax.swing.JComboBox}.
 *
 * Su proposito es SEPARAR la representacion visual de la logica del modelo:
 * aunque un modelo tuviera un {@code toString()} tecnico, en pantalla el usuario
 * siempre vera un texto limpio y amigable.
 *
 * Convencion de "centinela": un elemento con id 0 (p. ej. "(Todos)" / "(Sin bodega)")
 * se muestra solo con su nombre, sin codigo.
 *
 * Clase de utilidades: no se instancia.
 */
public final class ComboBoxRenderers {

    private ComboBoxRenderers() {
    }

    /**
     * Devuelve un renderizador que muestra un texto amigable para los modelos
     * conocidos (Item, Bodega, Tercero) y, para cualquier otro valor (enums,
     * cadenas), su {@code toString()} habitual.
     *
     * Formato:
     * - Item: "codigo - nombre" (o solo nombre si no tiene codigo o es centinela).
     * - Bodega y Tercero: su nombre.
     *
     * @return un renderizador listo para {@code combo.setRenderer(...)}.
     */
    public static DefaultListCellRenderer amigable() {
        return new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                                                          boolean seleccionado, boolean foco) {
                super.getListCellRendererComponent(lista, valor, indice, seleccionado, foco);
                setText(textoDe(valor));
                return this;
            }
        };
    }

    /** Calcula el texto a mostrar para un valor segun su tipo. */
    private static String textoDe(Object valor) {
        if (valor == null) {
            return "";
        }
        if (valor instanceof Item) {
            Item item = (Item) valor;
            String codigo = item.getCodigo();
            // Centinela (id 0) o item sin codigo: solo el nombre.
            if (item.getIdItem() == 0 || codigo == null || codigo.trim().isEmpty()) {
                return item.getNombre();
            }
            return codigo + " - " + item.getNombre();
        }
        if (valor instanceof Bodega) {
            return ((Bodega) valor).getNombre();
        }
        if (valor instanceof Tercero) {
            return ((Tercero) valor).getNombre();
        }
        // Enums y cadenas ya tienen una representacion adecuada.
        return valor.toString();
    }
}
