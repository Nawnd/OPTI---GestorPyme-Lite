package com.gestorpyme.view.components;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;

/**
 * Panel de "estado vacio": muestra un mensaje amigable y centrado cuando una
 * seccion del Dashboard no tiene datos que mostrar.
 */
public class EmptyStatePanel extends JPanel {

    /**
     * @param mensaje texto a mostrar (p. ej. "No hay cuentas pendientes.").
     */
    public EmptyStatePanel(String mensaje) {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        JLabel etiqueta = new JLabel(mensaje, JLabel.CENTER);
        etiqueta.setFont(UiTheme.fuente(Font.ITALIC, 13));
        etiqueta.setForeground(UiTheme.TEXTO_TENUE);
        add(etiqueta, BorderLayout.CENTER);
    }
}
