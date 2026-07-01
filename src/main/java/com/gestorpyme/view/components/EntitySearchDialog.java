package com.gestorpyme.view.components;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialogo modal de busqueda inteligente y seleccion de una entidad.
 *
 * Muestra un campo de texto que filtra (con un pequeno retardo para no recargar en
 * cada tecla), una tabla de resultados y botones Seleccionar/Cancelar. Permite elegir
 * con mouse (doble clic) o teclado (flechas + Enter), limpiar y ver un mensaje cuando
 * no hay resultados. No ejecuta SQL: la busqueda la provee {@link EntitySearchSpec}
 * (que llama a la capa de controlador).
 *
 * @param <T> tipo de la entidad buscada.
 */
public class EntitySearchDialog<T> extends JDialog {

    private final EntitySearchSpec<T> spec;
    private final JTextField txtBuscar = new JTextField();
    private final DefaultTableModel modelo;
    private final JTable tabla;
    private final JLabel lblEstado = new JLabel(" ");
    private final Timer temporizador;

    private List<T> resultados = new ArrayList<>();
    private T seleccionado;

    private EntitySearchDialog(Window propietario, EntitySearchSpec<T> spec) {
        super(propietario, spec.getTitulo(), ModalityType.APPLICATION_MODAL);
        this.spec = spec;

        modelo = new DefaultTableModel(spec.getColumnas(), 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        TableUtils.estilizar(tabla);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Retardo de 200 ms: filtra cuando el usuario deja de escribir.
        temporizador = new Timer(200, e -> ejecutarBusqueda());
        temporizador.setRepeats(false);

        construir();
        registrarEventos();

        ejecutarBusqueda(); // carga inicial (primeros resultados)
        setSize(560, 440);
        setLocationRelativeTo(propietario);
    }

    /**
     * Abre el dialogo y devuelve la entidad seleccionada (o null si se cancela).
     *
     * @param propietario ventana padre (para centrar y modalidad).
     * @param spec        configuracion de busqueda.
     * @param <T>         tipo de la entidad.
     * @return la entidad elegida o null.
     */
    public static <T> T mostrar(Window propietario, EntitySearchSpec<T> spec) {
        EntitySearchDialog<T> d = new EntitySearchDialog<>(propietario, spec);
        d.setVisible(true);
        return d.seleccionado;
    }

    private void construir() {
        JPanel raiz = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        raiz.setBackground(UiTheme.FONDO);
        raiz.setBorder(UiTheme.margen());

        JPanel norte = new JPanel(new BorderLayout(0, 4));
        norte.setOpaque(false);
        JLabel guia = new JLabel(spec.getTextoGuia());
        guia.setFont(UiTheme.fuentePequena());
        guia.setForeground(UiTheme.TEXTO_TENUE);
        txtBuscar.setFont(UiTheme.fuenteNormal());
        txtBuscar.putClientProperty("JTextField.placeholderText", spec.getTextoGuia());
        norte.add(guia, BorderLayout.NORTH);
        norte.add(txtBuscar, BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);

        JPanel sur = new JPanel(new BorderLayout());
        sur.setOpaque(false);
        lblEstado.setFont(UiTheme.fuentePequena());
        lblEstado.setForeground(UiTheme.TEXTO_TENUE);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, 0));
        botones.setOpaque(false);
        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        JButton btnSeleccionar = UiTheme.botonPrimario(new JButton("Seleccionar"));
        btnCancelar.addActionListener(e -> { seleccionado = null; dispose(); });
        btnSeleccionar.addActionListener(e -> confirmar());
        botones.add(btnCancelar);
        botones.add(btnSeleccionar);
        sur.add(lblEstado, BorderLayout.WEST);
        sur.add(botones, BorderLayout.EAST);

        raiz.add(norte, BorderLayout.NORTH);
        raiz.add(scroll, BorderLayout.CENTER);
        raiz.add(sur, BorderLayout.SOUTH);
        setContentPane(raiz);
    }

    private void registrarEventos() {
        // Filtrado al escribir (con retardo).
        txtBuscar.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { temporizador.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { temporizador.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { temporizador.restart(); }
        });

        // Doble clic selecciona.
        tabla.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && tabla.getSelectedRow() >= 0) {
                    confirmar();
                }
            }
        });

        // Teclado desde el campo de busqueda: flechas mueven la tabla, Enter selecciona.
        txtBuscar.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                int filas = tabla.getRowCount();
                if (filas == 0) {
                    return;
                }
                int sel = tabla.getSelectedRow();
                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    mover(Math.min(sel + 1, filas - 1));
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    mover(Math.max(sel - 1, 0));
                    e.consume();
                } else if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    confirmar();
                }
            }
        });

        // Esc cierra cancelando.
        getRootPane().registerKeyboardAction(e -> { seleccionado = null; dispose(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void mover(int fila) {
        if (fila >= 0 && fila < tabla.getRowCount()) {
            tabla.setRowSelectionInterval(fila, fila);
            tabla.scrollRectToVisible(tabla.getCellRect(fila, 0, true));
        }
    }

    /** Ejecuta la busqueda con el texto actual y refresca la tabla. */
    private void ejecutarBusqueda() {
        modelo.setRowCount(0);
        try {
            resultados = spec.getBuscador().buscar(txtBuscar.getText());
            if (resultados == null) {
                resultados = new ArrayList<>();
            }
            for (T entidad : resultados) {
                modelo.addRow(spec.aFila(entidad));
            }
            if (resultados.isEmpty()) {
                lblEstado.setText("No se encontraron resultados.");
            } else {
                lblEstado.setText(resultados.size() + (resultados.size() == 1
                        ? " resultado" : " resultados"));
                tabla.setRowSelectionInterval(0, 0);
            }
        } catch (Exception ex) {
            resultados = new ArrayList<>();
            lblEstado.setText("Error al buscar: " + ex.getMessage());
            lblEstado.setForeground(AppPalette.PELIGRO);
        }
    }

    /** Toma la fila seleccionada como resultado y cierra. */
    private void confirmar() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= resultados.size()) {
            lblEstado.setText("Seleccione una fila valida.");
            return;
        }
        seleccionado = resultados.get(fila);
        dispose();
    }
}
