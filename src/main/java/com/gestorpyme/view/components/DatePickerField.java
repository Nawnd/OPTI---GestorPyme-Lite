package com.gestorpyme.view.components;

import com.gestorpyme.util.DateFormatter;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;

/**
 * Campo de fecha reutilizable que admite DOS formas de ingreso:
 *
 * A. Manual: el usuario escribe la fecha en formato {@code dd/MM/yyyy} (ej. 15/06/2026).
 * B. Visual: un boton junto al campo abre {@link DateSelectorDialog} (picker Dia|Mes|Anio).
 *
 * Conversion de formatos (ver {@link DateFormatter}):
 * - Lo que ve/escribe el usuario: dd/MM/yyyy.
 * - Lo que se entrega a la logica/BD: ISO yyyy-MM-dd o {@link LocalDate}.
 *
 * Reglas:
 * - Permite vacio cuando el campo es opcional (p. ej. filtros de Kardex): devuelve null.
 * - {@link #getFecha()} lanza {@link IllegalArgumentException} si el texto no es una
 *   fecha valida (la vista muestra el mensaje al usuario).
 */
public class DatePickerField extends JPanel {

    private final JTextField campo = new JTextField();
    private final JButton boton = new JButton("\uD83D\uDCC5"); // icono de calendario

    public DatePickerField() {
        super(new BorderLayout(4, 0));
        setOpaque(false);

        campo.setFont(UiTheme.fuenteNormal());
        campo.setToolTipText("Formato dd/MM/yyyy (ej. 15/06/2026). Tambien puede usar el calendario.");

        boton.setToolTipText("Abrir selector de fecha");
        boton.setMargin(new Insets(2, 6, 2, 6));
        boton.setFocusable(false);
        boton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        boton.addActionListener(e -> abrirSelector());

        add(campo, BorderLayout.CENTER);
        add(boton, BorderLayout.EAST);
    }

    /** Abre el selector visual usando la fecha actual del campo (si es valida) como inicial. */
    private void abrirSelector() {
        LocalDate inicial;
        try {
            inicial = DateFormatter.desdeVista(campo.getText());
        } catch (IllegalArgumentException ex) {
            inicial = null; // Si lo escrito no es valido, el selector parte de hoy.
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        LocalDate elegida = DateSelectorDialog.seleccionar(owner, inicial);
        if (elegida != null) {
            setFecha(elegida);
        }
    }

    /**
     * @return la fecha escrita, o {@code null} si el campo esta vacio.
     * @throws IllegalArgumentException si el texto no es una fecha valida (dd/MM/yyyy).
     */
    public LocalDate getFecha() {
        return DateFormatter.desdeVista(campo.getText());
    }

    /**
     * @return la fecha en formato ISO (yyyy-MM-dd) para la BD, o {@code null} si esta vacio.
     * @throws IllegalArgumentException si el texto no es una fecha valida.
     */
    public String getTextoIso() {
        return DateFormatter.aIso(getFecha());
    }

    /** @return true si el campo esta vacio (sin texto). */
    public boolean estaVacio() {
        return campo.getText().trim().isEmpty();
    }

    /**
     * Fija la fecha mostrada (en formato dd/MM/yyyy). Si es null, deja el campo vacio.
     *
     * @param fecha fecha a mostrar o null.
     */
    public void setFecha(LocalDate fecha) {
        campo.setText(DateFormatter.aVista(fecha));
    }

    /**
     * Fija la fecha a partir de un texto ISO (yyyy-MM-dd, con o sin hora).
     *
     * @param textoIso texto ISO o null.
     */
    public void setFechaIso(String textoIso) {
        campo.setText(DateFormatter.isoAVista(textoIso));
    }

    /** Limita el ancho preferido del campo de texto (el boton va aparte). */
    public void setAnchoCampo(int ancho) {
        campo.setPreferredSize(new Dimension(ancho, UiScaleManager.escalar(28)));
    }

    /** @return el texto crudo escrito por el usuario. */
    public String getTexto() {
        return campo.getText().trim();
    }
}
