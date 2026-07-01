package com.gestorpyme.view.dashboard;

import com.gestorpyme.controller.DashboardController;
import com.gestorpyme.domain.model.DashboardComparativoMeta;
import com.gestorpyme.domain.model.DashboardMeta;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.components.ProgressBarMetricPanel;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;

/**
 * Panel de metas gerenciales del Dashboard (Paso L · rediseño visual L.1). Capa: view.dashboard.
 *
 * Presenta las metas como un <b>bloque profesional</b> con jerarquía clara y espacio suficiente, separando de
 * forma evidente tres zonas apiladas verticalmente: un subtítulo, un <b>resumen</b> (meta de ventas y utilidad
 * con barras de cumplimiento en filas propias, y comentario con ajuste de línea) y un <b>editor</b> compacto
 * en {@code GridBagLayout} con etiquetas y campos alineados y un botón Guardar visible al final. No usa
 * alturas fijas que recorten: reporta su altura real para que la columna contenedora lo acomode sin
 * solapamientos.
 *
 * No calcula nada: la comparación real vs meta llega resuelta del servicio y el guardado se delega al
 * controlador. Las metas se registran a nivel año/mes (la edición semanal queda diferida). Solo presentación.
 */
public class MetaGerencialPanel extends JPanel {

    private static final int ANCHO_ETIQUETA = 118;

    private final DashboardController controller;
    private final Runnable alGuardar;

    private final JPanel resumenPanel = new JPanel();
    private final JTextField txtMetaVentas = new JTextField();
    private final JTextField txtMetaUtilidad = new JTextField();
    private final JTextField txtMetaMargen = new JTextField();
    private final JTextField txtComentario = new JTextField();

    private int anio;
    private Integer mes;

    /**
     * @param controller controlador del dashboard (lectura/guardado de metas).
     * @param alGuardar  acción a ejecutar tras guardar (p. ej. refrescar el tablero); puede ser null.
     */
    public MetaGerencialPanel(DashboardController controller, Runnable alGuardar) {
        this.controller = controller;
        this.alGuardar = alGuardar;
        setOpaque(false);
        setLayout(new BorderLayout(0, 12));
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        add(crearZonaSuperior(), BorderLayout.NORTH);
        add(crearEditor(), BorderLayout.CENTER);
    }

    /**
     * Refresca el resumen (real vs meta) y carga la meta existente en el editor.
     *
     * @param c    comparación real vs meta del período (puede no tener meta).
     * @param anio año del período actual.
     * @param mes  mes 1..12 o null (año completo).
     */
    public void actualizar(DashboardComparativoMeta c, int anio, Integer mes) {
        this.anio = anio;
        this.mes = mes;
        resumenPanel.removeAll();

        if (c != null && c.isHayMeta()) {
            boolean algo = false;
            if (c.getMetaVentas() != null) {
                resumenPanel.add(filaTexto("Meta ventas", MoneyFormatter.cop(c.getMetaVentas())));
                resumenPanel.add(Box.createRigidArea(new Dimension(0, 2)));
                resumenPanel.add(barra("Cumplimiento ventas", c.getCumplimientoVentas()));
                algo = true;
            }
            if (c.getMetaUtilidad() != null) {
                if (algo) {
                    resumenPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                }
                resumenPanel.add(filaTexto("Meta utilidad", MoneyFormatter.cop(c.getMetaUtilidad())));
                resumenPanel.add(Box.createRigidArea(new Dimension(0, 2)));
                resumenPanel.add(barra("Cumplimiento utilidad", c.getCumplimientoUtilidad()));
                algo = true;
            }
            if (c.getComentario() != null && !c.getComentario().isBlank()) {
                if (algo) {
                    resumenPanel.add(Box.createRigidArea(new Dimension(0, 10)));
                }
                resumenPanel.add(bloqueComentario(c.getComentario()));
            }
            if (!algo) {
                resumenPanel.add(filaTexto("Meta registrada", "sin valores"));
            }
        } else {
            JLabel vacio = new JLabel("Sin meta registrada para este período.");
            vacio.setFont(UiTheme.fuente(Font.ITALIC, 12));
            vacio.setForeground(AppPalette.TEXTO_SECUNDARIO);
            vacio.setAlignmentX(LEFT_ALIGNMENT);
            resumenPanel.add(vacio);
        }
        cargarEnEditor();
        caparAltura(resumenPanel);
        resumenPanel.revalidate();
        resumenPanel.repaint();
    }

    /** Zona superior: subtítulo + resumen + separador (todo apilado, con su altura natural). */
    private JComponent crearZonaSuperior() {
        JPanel zona = new JPanel();
        zona.setOpaque(false);
        zona.setLayout(new BoxLayout(zona, BoxLayout.Y_AXIS));

        JLabel subtitulo = new JLabel("Objetivos separados de datos reales");
        subtitulo.setFont(UiTheme.fuente(Font.PLAIN, 11));
        subtitulo.setForeground(AppPalette.TEXTO_SECUNDARIO);
        subtitulo.setAlignmentX(LEFT_ALIGNMENT);

        resumenPanel.setOpaque(false);
        resumenPanel.setLayout(new BoxLayout(resumenPanel, BoxLayout.Y_AXIS));
        resumenPanel.setAlignmentX(LEFT_ALIGNMENT);
        resumenPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));

        zona.add(subtitulo);
        zona.add(resumenPanel);
        zona.add(Box.createRigidArea(new Dimension(0, 12)));
        zona.add(separador());
        return zona;
    }

    /** Editor compacto en GridBagLayout: etiquetas y campos alineados, sin alturas irregulares. */
    private JComponent crearEditor() {
        JPanel editor = new JPanel(new GridBagLayout());
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));

        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, 8, 0);
        JLabel titulo = new JLabel("Registrar / actualizar meta");
        titulo.setFont(UiTheme.fuente(Font.BOLD, 12));
        titulo.setForeground(AppPalette.PRIMARIO);
        editor.add(titulo, g);

        agregarFila(editor, 1, "Meta ventas:", txtMetaVentas);
        agregarFila(editor, 2, "Meta utilidad:", txtMetaUtilidad);
        agregarFila(editor, 3, "Meta margen %:", txtMetaMargen);
        agregarFila(editor, 4, "Comentario:", txtComentario);

        // Botón al final, visible y alineado a la izquierda.
        GridBagConstraints gb = new GridBagConstraints();
        gb.gridx = 0;
        gb.gridy = 5;
        gb.gridwidth = 2;
        gb.anchor = GridBagConstraints.WEST;
        gb.insets = new Insets(10, 0, 0, 0);
        JButton guardar = new JButton("Guardar meta");
        guardar.addActionListener(e -> guardar());
        editor.add(guardar, gb);

        // Fila elástica final: empuja el contenido hacia arriba si sobra altura (evita centrado/estiramiento).
        GridBagConstraints gf = new GridBagConstraints();
        gf.gridx = 0;
        gf.gridy = 6;
        gf.gridwidth = 2;
        gf.weighty = 1.0;
        gf.fill = GridBagConstraints.BOTH;
        editor.add(Box.createGlue(), gf);
        return editor;
    }

    /** Agrega una fila etiqueta + campo al editor con alineación y altura uniforme. */
    private void agregarFila(JPanel editor, int fila, String etiqueta, JTextField campo) {
        GridBagConstraints gl = new GridBagConstraints();
        gl.gridx = 0;
        gl.gridy = fila;
        gl.anchor = GridBagConstraints.WEST;
        gl.insets = new Insets(4, 0, 4, 10);
        JLabel l = new JLabel(etiqueta);
        l.setFont(UiTheme.fuente(Font.PLAIN, 12));
        l.setForeground(AppPalette.TEXTO_SECUNDARIO);
        l.setPreferredSize(new Dimension(ANCHO_ETIQUETA, 24));
        editor.add(l, gl);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 1;
        gc.gridy = fila;
        gc.weightx = 1.0;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.WEST;
        gc.insets = new Insets(4, 0, 4, 0);
        campo.setPreferredSize(new Dimension(120, 26));
        editor.add(campo, gc);
    }

    private JComponent filaTexto(String etiqueta, String valor) {
        JPanel fila = new JPanel(new BorderLayout(8, 0));
        fila.setOpaque(false);
        fila.setAlignmentX(LEFT_ALIGNMENT);
        fila.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel l = new JLabel(etiqueta);
        l.setFont(UiTheme.fuente(Font.PLAIN, 12));
        l.setForeground(AppPalette.TEXTO_SECUNDARIO);
        JLabel v = new JLabel(valor);
        v.setFont(UiTheme.fuente(Font.BOLD, 12));
        v.setForeground(AppPalette.PRIMARIO);
        v.setHorizontalAlignment(JLabel.RIGHT);
        fila.add(l, BorderLayout.WEST);
        fila.add(v, BorderLayout.CENTER);
        return fila;
    }

    /** Barra de cumplimiento en su propia fila (no se mezcla con el texto). */
    private JComponent barra(String etiqueta, Double cumpl) {
        ProgressBarMetricPanel p = new ProgressBarMetricPanel(etiqueta, cumpl, colorCumpl(cumpl));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    /** Comentario con ajuste de línea (no se recorta ni desborda). */
    private JComponent bloqueComentario(String texto) {
        JPanel cont = new JPanel(new BorderLayout(0, 2));
        cont.setOpaque(false);
        cont.setAlignmentX(LEFT_ALIGNMENT);
        JLabel l = new JLabel("Comentario");
        l.setFont(UiTheme.fuente(Font.PLAIN, 12));
        l.setForeground(AppPalette.TEXTO_SECUNDARIO);
        JTextArea ta = new JTextArea(texto);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setEditable(false);
        ta.setFocusable(false);
        ta.setOpaque(false);
        ta.setBorder(null);
        ta.setFont(UiTheme.fuente(Font.PLAIN, 12));
        ta.setForeground(AppPalette.PRIMARIO);
        cont.add(l, BorderLayout.NORTH);
        cont.add(ta, BorderLayout.CENTER);
        return cont;
    }

    private JComponent separador() {
        JSeparator sep = new JSeparator(JSeparator.HORIZONTAL);
        sep.setForeground(AppPalette.BORDE);
        sep.setAlignmentX(LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    /** Fija la altura máxima de un panel vertical a su altura preferida (evita estiramientos del BoxLayout). */
    private void caparAltura(JPanel panel) {
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
    }

    private void cargarEnEditor() {
        try {
            DashboardMeta m = controller.buscarMeta(anio, mes, null);
            txtMetaVentas.setText(m != null && m.getMetaVentas() != null ? m.getMetaVentas().toPlainString() : "");
            txtMetaUtilidad.setText(m != null && m.getMetaUtilidad() != null ? m.getMetaUtilidad().toPlainString() : "");
            txtMetaMargen.setText(m != null && m.getMetaMargen() != null
                    ? m.getMetaMargen().multiply(new BigDecimal("100")).stripTrailingZeros().toPlainString() : "");
            txtComentario.setText(m != null && m.getComentario() != null ? m.getComentario() : "");
        } catch (Exception ignore) {
            // El editor queda como está si no se puede leer la meta.
        }
    }

    private void guardar() {
        try {
            DashboardMeta m = new DashboardMeta();
            m.setAnio(anio);
            m.setMes(mes);
            m.setSemana(null);
            m.setMetaVentas(parse(txtMetaVentas.getText()));
            m.setMetaUtilidad(parse(txtMetaUtilidad.getText()));
            BigDecimal margenPct = parse(txtMetaMargen.getText());
            m.setMetaMargen(margenPct == null ? null : margenPct.divide(new BigDecimal("100")));
            m.setComentario(txtComentario.getText() == null || txtComentario.getText().isBlank()
                    ? null : txtComentario.getText().trim());
            controller.guardarMeta(m);
            if (alGuardar != null) {
                alGuardar.run();
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "No se pudo guardar la meta: " + e.getMessage(),
                    "Metas", JOptionPane.WARNING_MESSAGE);
        }
    }

    private BigDecimal parse(String txt) {
        if (txt == null || txt.isBlank()) {
            return null;
        }
        String limpio = txt.trim().replace(".", "").replace(",", ".").replaceAll("[^0-9.\\-]", "");
        if (limpio.isBlank() || limpio.equals("-") || limpio.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(limpio);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Color colorCumpl(Double cumpl) {
        if (cumpl == null) {
            return AppPalette.NEUTRO;
        }
        if (cumpl >= 1.0) {
            return AppPalette.EXITO;
        }
        return cumpl >= 0.7 ? AppPalette.ADVERTENCIA : AppPalette.PELIGRO;
    }
}
