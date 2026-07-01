package com.gestorpyme.view.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Campo de seleccion por busqueda inteligente (reemplaza a un JComboBox largo).
 *
 * Muestra la etiqueta de la entidad elegida (o un texto guia si esta vacio) y un boton
 * "Buscar" que abre {@link EntitySearchDialog}. Tambien permite limpiar la seleccion.
 * Es generico y reutilizable mediante {@link EntitySearchSpec}; no ejecuta SQL.
 *
 * @param <T> tipo de la entidad seleccionable.
 */
public class EntityLookupField<T> extends JPanel {

    private final EntitySearchSpec<T> spec;
    private final JTextField txtDisplay = new JTextField();
    private final JButton btnBuscar = new JButton("Buscar");
    private final JButton btnLimpiar = new JButton("\u00D7"); // x
    private T seleccionado;
    private Runnable alCambiar;

    public EntityLookupField(EntitySearchSpec<T> spec) {
        this.spec = spec;
        setLayout(new BorderLayout(4, 0));
        setOpaque(false);

        txtDisplay.setEditable(false);
        txtDisplay.setColumns(16);
        txtDisplay.setFont(UiTheme.fuenteNormal());
        txtDisplay.setBackground(Color.WHITE);
        txtDisplay.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.BORDE),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        txtDisplay.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        txtDisplay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                abrirBusqueda();
            }
        });

        estilizarBoton(btnBuscar, true);
        estilizarBoton(btnLimpiar, false);
        btnLimpiar.setToolTipText("Limpiar seleccion");
        btnBuscar.addActionListener(e -> abrirBusqueda());
        btnLimpiar.addActionListener(e -> limpiar());

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        acciones.setOpaque(false);
        acciones.add(btnLimpiar);
        acciones.add(btnBuscar);

        add(txtDisplay, BorderLayout.CENTER);
        add(acciones, BorderLayout.EAST);

        mostrarGuia();
    }

    private void estilizarBoton(JButton b, boolean primario) {
        if (primario) {
            UiTheme.botonPrimario(b);
        } else {
            UiTheme.botonSecundario(b);
        }
        b.setMargin(new Insets(2, 8, 2, 8));
        b.setFocusable(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void abrirBusqueda() {
        Window propietario = SwingUtilities.getWindowAncestor(this);
        T elegido = EntitySearchDialog.mostrar(propietario, spec);
        if (elegido != null) {
            setSeleccionado(elegido);
        }
    }

    /** Establece la entidad seleccionada y notifica el cambio. */
    public void setSeleccionado(T entidad) {
        this.seleccionado = entidad;
        refrescar();
        if (alCambiar != null) {
            alCambiar.run();
        }
    }

    /** @return la entidad seleccionada o null. */
    public T getSeleccionado() {
        return seleccionado;
    }

    /** @return true si no hay seleccion. */
    public boolean estaVacio() {
        return seleccionado == null;
    }

    /** Limpia la seleccion. */
    public void limpiar() {
        setSeleccionado(null);
    }

    /** Registra una accion a ejecutar cuando cambia la seleccion. */
    public void setAlCambiar(Runnable alCambiar) {
        this.alCambiar = alCambiar;
    }

    private void refrescar() {
        if (seleccionado == null) {
            mostrarGuia();
        } else {
            txtDisplay.setForeground(UiTheme.TEXTO);
            txtDisplay.setText(spec.aEtiqueta(seleccionado));
            txtDisplay.setToolTipText(txtDisplay.getText());
            txtDisplay.setCaretPosition(0);
        }
    }

    private void mostrarGuia() {
        txtDisplay.setForeground(UiTheme.TEXTO_TENUE);
        txtDisplay.setText(spec.getTextoGuia());
        txtDisplay.setToolTipText(null);
    }
}
