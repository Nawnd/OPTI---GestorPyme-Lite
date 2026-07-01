package com.gestorpyme.view.reportes;

import com.gestorpyme.controller.ExportacionController;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.view.components.ComboBoxRenderers;
import com.gestorpyme.view.components.UiTheme;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Vista del modulo Exportacion CSV. Permite elegir que exportar y la ubicacion del
 * archivo (JFileChooser). La exportacion se ejecuta en segundo plano con
 * {@link SwingWorker} para no bloquear la interfaz. Registra el resultado mediante
 * el servicio. No ejecuta SQL.
 */
public class ExportacionView extends JPanel {

    private final ExportacionController exportacionController = new ExportacionController();
    private final Integer idUsuario;

    private final JComboBox<TipoExportacion> cmbTipo = new JComboBox<>(TipoExportacion.values());
    private final JButton btnExportar = UiTheme.botonPrimario(new JButton("Exportar a CSV"));
    private final JLabel lblEstado = new JLabel(" ");

    public ExportacionView(Integer idUsuario) {
        this.idUsuario = idUsuario;
        setLayout(new BorderLayout());
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        JLabel titulo = new JLabel("Exportacion CSV");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 0, UiTheme.MARGEN, 0));

        JPanel tarjeta = new JPanel(new GridBagLayout());
        tarjeta.setBackground(UiTheme.SUPERFICIE);
        tarjeta.setBorder(UiTheme.tarjeta());
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(8, 8, 8, 8);
        g.anchor = GridBagConstraints.WEST;

        JLabel l = new JLabel("Datos a exportar:");
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        cmbTipo.setRenderer(ComboBoxRenderers.amigable());
        cmbTipo.setPreferredSize(new Dimension(260, 28));

        g.gridx = 0; g.gridy = 0; tarjeta.add(l, g);
        g.gridx = 1; tarjeta.add(cmbTipo, g);
        g.gridx = 2; tarjeta.add(btnExportar, g);

        JLabel hint = new JLabel("Se genera un CSV (separador ';', UTF-8) compatible con Excel en espanol.");
        hint.setFont(UiTheme.fuentePequena());
        hint.setForeground(UiTheme.TEXTO_TENUE);
        g.gridx = 0; g.gridy = 1; g.gridwidth = 3; tarjeta.add(hint, g);

        lblEstado.setFont(UiTheme.fuenteNormal());
        lblEstado.setForeground(UiTheme.TEXTO_TENUE);
        g.gridy = 2; tarjeta.add(lblEstado, g);

        JPanel norte = new JPanel(new BorderLayout());
        norte.setOpaque(false);
        norte.add(titulo, BorderLayout.NORTH);
        norte.add(tarjeta, BorderLayout.CENTER);

        add(norte, BorderLayout.NORTH);

        btnExportar.addActionListener(e -> exportar());
    }

    private void exportar() {
        TipoExportacion tipo = (TipoExportacion) cmbTipo.getSelectedItem();
        if (tipo == null) {
            return;
        }

        // Nombre sugerido: <TIPO>_<timestamp>.csv
        String marca = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String sugerido = tipo.name().toLowerCase() + "_" + marca + ".csv";

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Guardar exportacion CSV");
        chooser.setSelectedFile(new File(sugerido));
        chooser.setFileFilter(new FileNameExtensionFilter("Archivos CSV (*.csv)", "csv"));
        int opcion = chooser.showSaveDialog(this);
        if (opcion != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = chooser.getSelectedFile();
        String ruta = destino.getAbsolutePath();
        if (!ruta.toLowerCase().endsWith(".csv")) {
            ruta += ".csv";
        }

        ejecutarExportacion(tipo, ruta);
    }

    /** Ejecuta la exportacion en segundo plano (SwingWorker) para no congelar la UI. */
    private void ejecutarExportacion(TipoExportacion tipo, String ruta) {
        btnExportar.setEnabled(false);
        lblEstado.setText("Exportando...");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                exportacionController.exportar(tipo, ruta, idUsuario);
                return null;
            }

            @Override
            protected void done() {
                btnExportar.setEnabled(true);
                try {
                    get(); // propaga cualquier excepcion ocurrida en segundo plano
                    lblEstado.setText("Archivo generado: " + ruta);
                    JOptionPane.showMessageDialog(ExportacionView.this,
                            "Exportacion generada correctamente en:\n" + ruta,
                            "Exportacion CSV", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    lblEstado.setText("Error al exportar.");
                    Throwable causa = (e.getCause() != null) ? e.getCause() : e;
                    JOptionPane.showMessageDialog(ExportacionView.this,
                            "No se pudo exportar:\n" + causa.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
