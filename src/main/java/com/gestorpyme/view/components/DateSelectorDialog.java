package com.gestorpyme.view.components;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Selector de fecha visual, moderno y horizontal (estilo "picker": Dia | Mes | Anio).
 *
 * Usa tres {@link JSpinner} alineados. Valida fechas reales: al cambiar el mes o el
 * anio, ajusta automaticamente el maximo de dias (evita 31/02, 30/02, etc.).
 *
 * Devuelve un {@link LocalDate} con Aceptar, o {@code null} con Cancelar.
 */
public class DateSelectorDialog extends JDialog {

    private static final String[] MESES = {
            "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
    };

    private final JSpinner spDia;
    private final JSpinner spMes;
    private final JSpinner spAnio;
    private LocalDate resultado;

    private DateSelectorDialog(Window owner, LocalDate inicial) {
        super(owner, "Seleccionar fecha", ModalityType.APPLICATION_MODAL);
        LocalDate base = (inicial != null) ? inicial : LocalDate.now();

        spDia = new JSpinner(new SpinnerNumberModel(base.getDayOfMonth(), 1, 31, 1));
        spMes = new JSpinner(new SpinnerListModel(MESES));
        spMes.setValue(MESES[base.getMonthValue() - 1]);
        spAnio = new JSpinner(new SpinnerNumberModel(base.getYear(), 1900, 2999, 1));
        // El anio se muestra sin separador de miles (2026, no 2.026).
        spAnio.setEditor(new JSpinner.NumberEditor(spAnio, "0000"));

        estilizar(spDia, 60);
        estilizar(spMes, 130);
        estilizar(spAnio, 90);

        // Al cambiar mes o anio, recalcular el maximo de dias del mes.
        spMes.addChangeListener(e -> ajustarDias());
        spAnio.addChangeListener(e -> ajustarDias());

        setContentPane(construir());
        ajustarDias();
        pack();
        setResizable(false);
        setLocationRelativeTo(owner);
    }

    private JPanel construir() {
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setBackground(UiTheme.SUPERFICIE);
        contenedor.setBorder(UiTheme.margen());

        JLabel titulo = new JLabel("Seleccione la fecha");
        titulo.setFont(UiTheme.fuenteSubtitulo());
        titulo.setForeground(UiTheme.PRIMARIO);
        titulo.setBorder(BorderFactory.createEmptyBorder(0, 0, UiTheme.ESPACIO, 0));

        // Picker horizontal: Dia | Mes | Anio, cada uno con su etiqueta encima.
        JPanel picker = new JPanel(new GridLayout(1, 3, UiTheme.ESPACIO, 0));
        picker.setBackground(UiTheme.SUPERFICIE);
        picker.add(columna("Dia", spDia));
        picker.add(columna("Mes", spMes));
        picker.add(columna("Anio", spAnio));

        JButton btnCancelar = UiTheme.botonSecundario(new JButton("Cancelar"));
        btnCancelar.addActionListener(e -> { resultado = null; dispose(); });
        JButton btnAceptar = UiTheme.botonPrimario(new JButton("Aceptar"));
        btnAceptar.addActionListener(e -> aceptar());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, 0));
        botones.setBackground(UiTheme.SUPERFICIE);
        botones.setBorder(BorderFactory.createEmptyBorder(UiTheme.MARGEN, 0, 0, 0));
        botones.add(btnCancelar);
        botones.add(btnAceptar);

        contenedor.add(titulo, BorderLayout.NORTH);
        contenedor.add(picker, BorderLayout.CENTER);
        contenedor.add(botones, BorderLayout.SOUTH);
        return contenedor;
    }

    private JPanel columna(String etiqueta, JSpinner spinner) {
        JPanel col = new JPanel(new BorderLayout(0, 4));
        col.setBackground(UiTheme.SUPERFICIE);
        JLabel l = new JLabel(etiqueta, SwingConstants.CENTER);
        l.setFont(UiTheme.fuenteNegrita());
        l.setForeground(UiTheme.TEXTO);
        col.add(l, BorderLayout.NORTH);
        col.add(spinner, BorderLayout.CENTER);
        return col;
    }

    private void estilizar(JSpinner spinner, int ancho) {
        spinner.setFont(UiTheme.fuente(Font.BOLD, 16));
        spinner.setPreferredSize(new Dimension(ancho, UiScaleManager.escalar(40)));
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) editor).getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        }
    }

    /** Limita el dia al numero de dias del mes/anio seleccionados. */
    private void ajustarDias() {
        int mes = indiceMes() + 1;
        int anio = (int) spAnio.getValue();
        int maxDia = YearMonth.of(anio, mes).lengthOfMonth();
        SpinnerNumberModel modeloDia = (SpinnerNumberModel) spDia.getModel();
        modeloDia.setMaximum(maxDia);
        if ((int) spDia.getValue() > maxDia) {
            spDia.setValue(maxDia);
        }
    }

    private int indiceMes() {
        String mes = (String) spMes.getValue();
        for (int i = 0; i < MESES.length; i++) {
            if (MESES[i].equals(mes)) {
                return i;
            }
        }
        return 0;
    }

    private void aceptar() {
        int dia = (int) spDia.getValue();
        int mes = indiceMes() + 1;
        int anio = (int) spAnio.getValue();
        resultado = LocalDate.of(anio, mes, dia);
        dispose();
    }

    /**
     * Abre el selector y devuelve la fecha elegida.
     *
     * @param owner   ventana padre (para centrar y modalidad).
     * @param inicial fecha inicial a mostrar (si es null, usa hoy).
     * @return la fecha elegida, o {@code null} si se cancelo.
     */
    public static LocalDate seleccionar(Window owner, LocalDate inicial) {
        DateSelectorDialog dialog = new DateSelectorDialog(owner, inicial);
        dialog.setVisible(true);
        return dialog.resultado;
    }
}
