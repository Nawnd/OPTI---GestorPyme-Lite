package com.gestorpyme.view.components;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.Color;
import java.awt.Component;

/**
 * Utilidades para dar a las {@link JTable} una apariencia profesional y consistente,
 * y para evitar que el contenido se vea cortado:
 *
 * - Alto de fila comodo (sensible al zoom).
 * - Encabezado corporativo (#051E22, texto blanco).
 * - Efecto cebra y seleccion clara.
 * - TOOLTIPS automaticos: al pasar el mouse por una celda se ve su texto completo,
 *   aunque la columna sea estrecha (soluciona "Bodega Princi..." o "Canti...").
 * - Anchos preferidos/minimos por columna y modo con scroll horizontal opcional.
 *
 * Clase de utilidades: no se instancia.
 */
public final class TableUtils {

    private TableUtils() {
    }

    /**
     * Aplica el estilo base a una tabla: fuentes, alturas, colores, rejilla,
     * efecto cebra, encabezado corporativo y tooltips automaticos por celda.
     *
     * @param tabla tabla a estilizar.
     */
    public static void estilizar(JTable tabla) {
        tabla.setFont(UiTheme.fuenteNormal());
        tabla.setRowHeight(UiScaleManager.escalar(28));
        tabla.setFillsViewportHeight(true);
        tabla.setShowGrid(true);
        tabla.setGridColor(UiTheme.TABLA_REJILLA);
        tabla.setSelectionBackground(UiTheme.TABLA_SELECCION);
        tabla.setSelectionForeground(UiTheme.TABLA_SELECCION_TEXTO);
        tabla.setShowVerticalLines(true);
        tabla.setIntercellSpacing(new java.awt.Dimension(0, 1));
        tabla.setRowSelectionAllowed(true);
        // Por defecto se reparte el ancho entre columnas (se adapta a la ventana).
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);

        // Encabezado con color corporativo mediante un renderizador propio
        // (mas fiable que setBackground, que algunos Look&Feel ignoran).
        JTableHeader encabezado = tabla.getTableHeader();
        encabezado.setReorderingAllowed(false);
        encabezado.setFont(UiTheme.fuenteNegrita());
        encabezado.setDefaultRenderer(new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel,
                                                           boolean foco, int fila, int col) {
                JLabel etiqueta = (JLabel) super.getTableCellRendererComponent(t, valor, sel, foco, fila, col);
                etiqueta.setBackground(UiTheme.TABLA_ENCABEZADO);
                etiqueta.setForeground(UiTheme.TABLA_ENCABEZADO_TEXTO);
                etiqueta.setFont(UiTheme.fuenteNegrita());
                etiqueta.setOpaque(true);
                etiqueta.setHorizontalAlignment(LEFT);
                etiqueta.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return etiqueta;
            }
        });

        // Celdas: efecto cebra + relleno + TOOLTIP con el texto completo.
        tabla.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel,
                                                           boolean foco, int fila, int col) {
                Component c = super.getTableCellRendererComponent(t, valor, sel, foco, fila, col);
                if (!sel) {
                    c.setBackground(fila % 2 == 0 ? Color.WHITE : UiTheme.TABLA_FILA_ALTERNA);
                    c.setForeground(UiTheme.TEXTO);
                }
                if (c instanceof JLabel) {
                    JLabel etiqueta = (JLabel) c;
                    etiqueta.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 8));
                    // Tooltip = texto completo de la celda (evita perder informacion truncada).
                    etiqueta.setToolTipText(valor == null ? null : valor.toString());
                }
                return c;
            }
        });
    }

    /**
     * Asigna anchos preferidos a las columnas (en pixeles), en orden.
     *
     * @param tabla  tabla destino.
     * @param anchos anchos preferidos por columna (indice 0 en adelante).
     */
    public static void anchos(JTable tabla, int... anchos) {
        TableColumnModel modelo = tabla.getColumnModel();
        int n = Math.min(anchos.length, modelo.getColumnCount());
        for (int i = 0; i < n; i++) {
            modelo.getColumn(i).setPreferredWidth(anchos[i]);
        }
    }

    /**
     * Asigna anchos minimos a las columnas (en pixeles), en orden. Cuando la suma
     * de minimos supera el ancho visible y la tabla esta en modo
     * {@link JTable#AUTO_RESIZE_OFF}, aparece un scroll horizontal en vez de truncar.
     *
     * @param tabla   tabla destino.
     * @param minimos anchos minimos por columna.
     */
    public static void anchosMinimos(JTable tabla, int... minimos) {
        TableColumnModel modelo = tabla.getColumnModel();
        int n = Math.min(minimos.length, modelo.getColumnCount());
        for (int i = 0; i < n; i++) {
            modelo.getColumn(i).setMinWidth(minimos[i]);
        }
    }

    /**
     * Activa el modo con SCROLL HORIZONTAL: las columnas conservan su ancho y, si
     * no caben, el {@link javax.swing.JScrollPane} muestra la barra horizontal.
     * Util para tablas anchas (Kardex, Ventas) en ventanas medianas/pequenas.
     *
     * @param tabla tabla destino.
     */
    public static void conScrollHorizontal(JTable tabla) {
        tabla.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    }

    /**
     * Alinea a la derecha las columnas indicadas (util para cantidades y montos),
     * respetando el efecto cebra y los tooltips.
     *
     * @param tabla    tabla destino.
     * @param columnas indices de columnas a alinear a la derecha.
     */
    public static void alinearDerecha(JTable tabla, int... columnas) {
        DefaultTableCellRenderer derecha = new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel,
                                                           boolean foco, int fila, int col) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, valor, sel, foco, fila, col);
                c.setHorizontalAlignment(RIGHT);
                if (!sel) {
                    c.setBackground(fila % 2 == 0 ? Color.WHITE : UiTheme.TABLA_FILA_ALTERNA);
                    c.setForeground(UiTheme.TEXTO);
                }
                c.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 8, 2, 8));
                c.setToolTipText(valor == null ? null : valor.toString());
                return c;
            }
        };
        TableColumnModel modelo = tabla.getColumnModel();
        for (int col : columnas) {
            if (col >= 0 && col < modelo.getColumnCount()) {
                modelo.getColumn(col).setCellRenderer(derecha);
            }
        }
    }
}
