package com.gestorpyme.view.dashboard;

import com.gestorpyme.domain.model.DashboardDrillDownItem;
import com.gestorpyme.view.components.AppPalette;
import com.gestorpyme.view.components.DashboardListaEjecutiva;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;

/**
 * Panel de desglose (drill-down) del Dashboard (Paso L). Capa: view.dashboard.
 *
 * Muestra, bajo demanda, el detalle asociado a una gráfica/indicador: un título, una lista ejecutiva con las
 * filas correspondientes y un botón para cerrarlo. Permanece oculto hasta que se solicita un desglose. Solo
 * presentación; recibe los datos ya resueltos (no ejecuta consultas).
 */
public class DrillDownPanel extends JPanel {

    private final JLabel titulo = new JLabel();
    private final JPanel contenido = new JPanel(new BorderLayout());

    public DrillDownPanel() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setVisible(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppPalette.BORDE),
                BorderFactory.createEmptyBorder(10, 0, 0, 0)));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        titulo.setFont(UiTheme.fuente(Font.BOLD, 14));
        titulo.setForeground(AppPalette.PRIMARIO);
        JButton cerrar = new JButton("Cerrar");
        cerrar.addActionListener(e -> ocultar());
        head.add(titulo, BorderLayout.WEST);
        head.add(cerrar, BorderLayout.EAST);

        contenido.setOpaque(false);
        JScrollPane scroll = new JScrollPane(contenido,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setPreferredSize(new Dimension(100, 220));

        add(head, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Muestra el desglose.
     *
     * @param tituloTexto    título del panel (p. ej. "Ventas del 03/06/2026").
     * @param columnas       encabezados de columna (hasta 4: principal, secundario, valor, estado).
     * @param items          filas a mostrar (pueden venir vacías).
     * @param columnasDerecha índices de columnas alineadas a la derecha (montos/cantidades).
     */
    public void mostrar(String tituloTexto, String[] columnas, List<DashboardDrillDownItem> items,
                        int... columnasDerecha) {
        titulo.setText(tituloTexto);
        contenido.removeAll();
        if (items == null || items.isEmpty()) {
            contenido.add(DashboardListaEjecutiva.vacia("Sin registros para mostrar."), BorderLayout.NORTH);
        } else {
            DashboardListaEjecutiva lista = new DashboardListaEjecutiva(columnas);
            if (columnasDerecha != null) {
                lista.alinearDerecha(columnasDerecha);
            }
            for (DashboardDrillDownItem it : items) {
                lista.agregarFilaConColor(it.getColorHint(), celdasDe(it, columnas.length));
            }
            contenido.add(lista, BorderLayout.NORTH);
        }
        setVisible(true);
        revalidate();
        repaint();
    }

    /** Oculta el panel de desglose. */
    public void ocultar() {
        setVisible(false);
        revalidate();
        repaint();
    }

    /** Mapea los cuatro campos del ítem a las columnas disponibles (principal, secundario, valor, estado). */
    private String[] celdasDe(DashboardDrillDownItem it, int n) {
        String[] todos = {it.getPrincipal(), it.getSecundario(), it.getValor(), it.getEstado()};
        String[] salida = new String[n];
        for (int i = 0; i < n; i++) {
            salida[i] = i < todos.length && todos[i] != null ? todos[i] : "";
        }
        return salida;
    }
}
