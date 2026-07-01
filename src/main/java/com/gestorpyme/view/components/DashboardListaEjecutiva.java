package com.gestorpyme.view.components;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.HashSet;
import java.util.Set;

/**
 * Lista ejecutiva ligera para el Dashboard (Paso L). Capa: view.components.
 *
 * Reemplaza a las tablas tipo hoja de cálculo: muestra un encabezado de columnas y filas limpias con
 * separación suave y fondo alterno, sin rejilla pesada. Pensada para "ventas recientes", "últimos pagos" o
 * el detalle de desgloses. Permite marcar columnas a la derecha (p. ej. montos) y colorear una celda de
 * estado. Solo presentación.
 */
public class DashboardListaEjecutiva extends JPanel {

    private final String[] columnas;
    private final Set<Integer> columnasDerecha = new HashSet<>();
    private int filasAgregadas = 0;

    /** @param columnas títulos de columna (definen el número de celdas por fila). */
    public DashboardListaEjecutiva(String[] columnas) {
        this.columnas = columnas != null ? columnas : new String[0];
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(filaEncabezado());
    }

    /** Marca columnas (por índice) cuyo contenido se alinea a la derecha (montos, cantidades). */
    public void alinearDerecha(int... indices) {
        for (int i : indices) {
            columnasDerecha.add(i);
        }
    }

    /** Agrega una fila de datos. El número de celdas debe coincidir con el de columnas. */
    public void agregarFila(String... celdas) {
        agregarFilaConColor(null, celdas);
    }

    /**
     * Agrega una fila coloreando la última celda (estado) con una pista semántica.
     *
     * @param colorHintUltima pista de color ("EXITO", "ADVERTENCIA", "PELIGRO"…) para la última celda; o null.
     * @param celdas          contenido de las celdas.
     */
    public void agregarFilaConColor(String colorHintUltima, String... celdas) {
        JPanel fila = new JPanel(new GridLayout(1, Math.max(1, columnas.length), 8, 0));
        fila.setOpaque(true);
        fila.setBackground(filasAgregadas % 2 == 0 ? AppPalette.BLANCO : AppPalette.TARJETA_SUAVE);
        fila.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        for (int i = 0; i < columnas.length; i++) {
            String texto = i < celdas.length && celdas[i] != null ? celdas[i] : "";
            JLabel c = new JLabel(texto);
            c.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            boolean ultima = i == columnas.length - 1;
            if (ultima && colorHintUltima != null) {
                c.setForeground(InteractiveBarChartPanel.colorPorHint(colorHintUltima));
                c.setFont(new Font("Segoe UI", Font.BOLD, 12));
            } else {
                c.setForeground(AppPalette.PRIMARIO);
            }
            c.setHorizontalAlignment(columnasDerecha.contains(i) ? JLabel.RIGHT : JLabel.LEFT);
            fila.add(c);
        }
        add(fila);
        filasAgregadas++;
    }

    /** @return número de filas de datos agregadas (sin contar el encabezado). */
    public int getFilas() {
        return filasAgregadas;
    }

    private JPanel filaEncabezado() {
        JPanel head = new JPanel(new GridLayout(1, Math.max(1, columnas.length), 8, 0));
        head.setOpaque(false);
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppPalette.BORDE),
                BorderFactory.createEmptyBorder(2, 8, 6, 8)));
        head.setAlignmentX(LEFT_ALIGNMENT);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        for (int i = 0; i < columnas.length; i++) {
            JLabel c = new JLabel(columnas[i]);
            c.setFont(new Font("Segoe UI", Font.BOLD, 12));
            c.setForeground(AppPalette.TEXTO_SECUNDARIO);
            c.setHorizontalAlignment(columnasDerecha.contains(i) ? JLabel.RIGHT : JLabel.LEFT);
            head.add(c);
        }
        return head;
    }

    /** Envuelve un mensaje de estado vacío con el mismo estilo de la lista. */
    public static JPanel vacia(String mensaje) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        JLabel l = new JLabel(mensaje);
        l.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        l.setForeground(AppPalette.TEXTO_SECUNDARIO);
        l.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(l, BorderLayout.NORTH);
        return p;
    }
}
