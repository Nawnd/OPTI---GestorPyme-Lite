package com.gestorpyme.view.cuentas;

import com.gestorpyme.controller.CuentaController;
import com.gestorpyme.controller.TerceroController;
import com.gestorpyme.controller.VentaController;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.util.DateFormatter;
import com.gestorpyme.util.MoneyFormatter;
import com.gestorpyme.view.components.TableUtils;
import com.gestorpyme.view.components.UiTheme;
import com.gestorpyme.view.components.EntityLookupField;
import com.gestorpyme.view.components.SearchSpecs;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Vista del modulo Cuentas por cobrar: lista las ventas a credito con su saldo y
 * estado, y permite registrar abonos o consultarlos. El saldo proviene del
 * servicio/repositorio (valor_total - valor_pagado). No ejecuta SQL.
 */
public class CuentasPorCobrarView extends JPanel {

    private final CuentaController cuentaController = new CuentaController();
    private final TerceroController terceroController = new TerceroController();
    private final VentaController ventaController = new VentaController();

    private final DefaultTableModel modelo;
    private final JTable tabla;
    /** Cuentas cargadas, alineadas con las filas de la tabla. */
    private final List<CuentaPorCobrar> cuentas = new ArrayList<>();

    /** Busqueda inteligente: filtra cartera por cliente o por consecutivo de venta. */
    private final EntityLookupField<Tercero> lkpCliente =
            new EntityLookupField<>(SearchSpecs.clientes(terceroController));
    private final EntityLookupField<Venta> lkpVenta =
            new EntityLookupField<>(SearchSpecs.ventas(ventaController));
    private Integer filtroIdTercero = null;
    private Integer filtroIdVenta = null;

    public CuentasPorCobrarView(Integer idUsuario) {
        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        modelo = new DefaultTableModel(
                new Object[]{"Venta", "Cliente", "Total", "Pagado", "Saldo", "Vence", "Estado"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        TableUtils.estilizar(tabla);
        TableUtils.conScrollHorizontal(tabla);
        TableUtils.anchos(tabla, 110, 220, 110, 110, 110, 110, 120);
        TableUtils.anchosMinimos(tabla, 100, 150, 90, 90, 90, 90, 100);
        TableUtils.alinearDerecha(tabla, 2, 3, 4);

        add(crearNorte(), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(tabla,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDE));
        scroll.getViewport().setBackground(Color.WHITE);
        add(scroll, BorderLayout.CENTER);

        recargar();
    }

    private JComponent crearBarra() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setOpaque(false);
        JLabel titulo = new JLabel("Cuentas por cobrar");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);

        JButton btnAbono = UiTheme.botonPrimario(new JButton("Registrar abono"));
        btnAbono.addActionListener(e -> registrarAbono());
        JButton btnVer = UiTheme.botonSecundario(new JButton("Ver abonos"));
        btnVer.addActionListener(e -> verAbonos());

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, 0));
        acciones.setOpaque(false);
        acciones.add(btnVer);
        acciones.add(btnAbono);

        barra.add(titulo, BorderLayout.WEST);
        barra.add(acciones, BorderLayout.EAST);
        return barra;
    }

    /** Apila la barra de titulo/acciones y la barra de busqueda. */
    private JComponent crearNorte() {
        JPanel norte = new JPanel(new BorderLayout(0, UiTheme.ESPACIO));
        norte.setOpaque(false);
        norte.add(crearBarra(), BorderLayout.NORTH);
        norte.add(crearFiltro(), BorderLayout.SOUTH);
        return norte;
    }

    /** Barra de busqueda inteligente: filtra por cliente o por consecutivo de venta. */
    private JComponent crearFiltro() {
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        filtro.setOpaque(false);

        filtro.add(new JLabel("Cliente:"));
        lkpCliente.setPreferredSize(new Dimension(240, 32));
        lkpCliente.setAlCambiar(() -> {
            Tercero t = lkpCliente.getSeleccionado();
            filtroIdTercero = (t == null) ? null : t.getIdTercero();
            recargar();
        });
        filtro.add(lkpCliente);

        filtro.add(new JLabel("Venta:"));
        lkpVenta.setPreferredSize(new Dimension(240, 32));
        lkpVenta.setAlCambiar(() -> {
            Venta v = lkpVenta.getSeleccionado();
            filtroIdVenta = (v == null) ? null : v.getIdVenta();
            recargar();
        });
        filtro.add(lkpVenta);

        JButton btnLimpiar = UiTheme.botonSecundario(new JButton("Ver todas"));
        btnLimpiar.addActionListener(e -> {
            lkpCliente.limpiar();
            lkpVenta.limpiar();
            filtroIdTercero = null;
            filtroIdVenta = null;
            recargar();
        });
        filtro.add(btnLimpiar);
        return filtro;
    }

    private void recargar() {
        try {
            cuentas.clear();
            modelo.setRowCount(0);
            for (CuentaPorCobrar c : cuentaController.listar()) {
                if (filtroIdTercero != null && c.getIdTercero() != filtroIdTercero) {
                    continue; // filtro por cliente
                }
                if (filtroIdVenta != null && c.getIdVenta() != filtroIdVenta) {
                    continue; // filtro por venta
                }
                cuentas.add(c);
                modelo.addRow(new Object[]{
                        c.getNumeroVenta(),
                        c.getNombreTercero(),
                        MoneyFormatter.cop(c.getValorTotal()),
                        MoneyFormatter.cop(c.getValorPagado()),
                        MoneyFormatter.cop(c.getSaldoPendiente()),
                        DateFormatter.isoAVista(c.getFechaVencimiento()),
                        c.getEstado()
                });
            }
        } catch (Exception e) {
            error("No se pudieron cargar las cuentas por cobrar: " + e.getMessage());
        }
    }

    private void registrarAbono() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione una cuenta para registrar el abono.");
            return;
        }
        CuentaPorCobrar c = cuentas.get(fila);
        if (c.getSaldoPendiente().compareTo(BigDecimal.ZERO) <= 0) {
            error("Esta cuenta ya esta totalmente pagada.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        AbonoFormDialog dialog = new AbonoFormDialog(owner, cuentaController, c.getIdCuenta(),
                c.getNumeroVenta(), c.getSaldoPendiente());
        dialog.setVisible(true);
        if (dialog.isGuardado()) {
            recargar();
        }
    }

    private void verAbonos() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione una cuenta para ver sus abonos.");
            return;
        }
        CuentaPorCobrar c = cuentas.get(fila);
        try {
            List<Abono> abonos = cuentaController.listarAbonos(c.getIdCuenta());
            DefaultTableModel md = new DefaultTableModel(
                    new Object[]{"Fecha", "Medio", "Valor", "Observaciones"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            for (Abono a : abonos) {
                md.addRow(new Object[]{
                        DateFormatter.isoAVista(a.getFecha()),
                        a.getMedioPago(),
                        MoneyFormatter.cop(a.getValor()),
                        a.getObservaciones() == null ? "" : a.getObservaciones()});
            }
            JTable t = new JTable(md);
            TableUtils.estilizar(t);
            TableUtils.alinearDerecha(t, 2);
            JScrollPane scroll = new JScrollPane(t);
            scroll.setPreferredSize(new Dimension(540, 280));
            scroll.getViewport().setBackground(Color.WHITE);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), scroll,
                    "Abonos de la venta " + c.getNumeroVenta(), JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            error("No se pudieron cargar los abonos: " + e.getMessage());
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Cuentas por cobrar", JOptionPane.ERROR_MESSAGE);
    }
}
