package com.gestorpyme.view.pagos;

import com.gestorpyme.controller.CuentaController;
import com.gestorpyme.controller.PagoController;
import com.gestorpyme.controller.VentaController;
import com.gestorpyme.domain.enums.EstadoVenta;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.Pago;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vista del modulo Pagos: lista las ventas con su total, lo pagado y el saldo, y
 * permite registrar un pago o consultar los pagos de una venta. El saldo se calcula
 * a partir de los datos del servicio. No ejecuta SQL: delega en los controladores.
 */
public class PagosView extends JPanel {

    private final VentaController ventaController = new VentaController();
    private final PagoController pagoController = new PagoController();
    private final CuentaController cuentaController = new CuentaController();

    private final DefaultTableModel modelo;
    private final JTable tabla;
    /** Ventas cargadas, alineadas con las filas de la tabla. */
    private final List<Venta> ventas = new ArrayList<>();
    /** Saldo por fila (total - pagado), para abrir el formulario sin recalcular. */
    private final List<BigDecimal> saldos = new ArrayList<>();

    /** Filtro de busqueda inteligente: id de la venta seleccionada (null = todas). */
    private final EntityLookupField<Venta> lkpBuscar =
            new EntityLookupField<>(SearchSpecs.ventas(ventaController));
    private Integer filtroIdVenta = null;

    public PagosView(Integer idUsuario) {
        setLayout(new BorderLayout(0, UiTheme.ESPACIO));
        setBackground(UiTheme.FONDO);
        setBorder(UiTheme.margen());

        modelo = new DefaultTableModel(
                new Object[]{"Numero", "Fecha", "Cliente", "Total", "Pagado", "Saldo", "Estado"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        TableUtils.estilizar(tabla);
        TableUtils.conScrollHorizontal(tabla);
        TableUtils.anchos(tabla, 110, 100, 220, 110, 110, 110, 140);
        TableUtils.anchosMinimos(tabla, 100, 90, 150, 90, 90, 90, 110);
        TableUtils.alinearDerecha(tabla, 3, 4, 5);

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
        JLabel titulo = new JLabel("Pagos");
        titulo.setFont(UiTheme.fuenteTitulo());
        titulo.setForeground(UiTheme.PRIMARIO);

        JButton btnPago = UiTheme.botonPrimario(new JButton("Registrar pago"));
        btnPago.addActionListener(e -> registrarPago());
        JButton btnVer = UiTheme.botonSecundario(new JButton("Ver pagos"));
        btnVer.addActionListener(e -> verPagos());

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.ESPACIO, 0));
        acciones.setOpaque(false);
        acciones.add(btnVer);
        acciones.add(btnPago);

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

    /** Barra de busqueda inteligente: filtra la tabla por venta (consecutivo o cliente). */
    private JComponent crearFiltro() {
        JPanel filtro = new JPanel(new FlowLayout(FlowLayout.LEFT, UiTheme.ESPACIO, 0));
        filtro.setOpaque(false);
        filtro.add(new JLabel("Buscar venta:"));
        lkpBuscar.setPreferredSize(new Dimension(320, 32));
        lkpBuscar.setAlCambiar(() -> {
            Venta v = lkpBuscar.getSeleccionado();
            filtroIdVenta = (v == null) ? null : v.getIdVenta();
            recargar();
        });
        filtro.add(lkpBuscar);
        JButton btnLimpiar = UiTheme.botonSecundario(new JButton("Ver todas"));
        btnLimpiar.addActionListener(e -> {
            lkpBuscar.limpiar();
            filtroIdVenta = null;
            recargar();
        });
        filtro.add(btnLimpiar);
        return filtro;
    }

    private void recargar() {
        try {
            ventas.clear();
            saldos.clear();
            modelo.setRowCount(0);

            // Mapa idVenta -> cuenta por cobrar. Para ventas a credito, la cuenta es la
            // fuente de verdad del saldo, de modo que Pagos muestre lo mismo que Cuentas.
            Map<Integer, CuentaPorCobrar> cuentasPorVenta = new HashMap<>();
            for (CuentaPorCobrar c : cuentaController.listar()) {
                cuentasPorVenta.put(c.getIdVenta(), c);
            }

            for (Venta v : ventaController.listar()) {
                if (filtroIdVenta != null && v.getIdVenta() != filtroIdVenta) {
                    continue; // filtro de busqueda activo: solo la venta seleccionada
                }
                BigDecimal total = v.getTotal() == null ? BigDecimal.ZERO : v.getTotal();
                BigDecimal pagado;
                BigDecimal saldo;

                CuentaPorCobrar cuenta = cuentasPorVenta.get(v.getIdVenta());
                if (cuenta != null) {
                    // Venta a credito: saldo y pagado provienen de la cuenta por cobrar.
                    pagado = cuenta.getValorPagado() == null ? BigDecimal.ZERO : cuenta.getValorPagado();
                    saldo = cuenta.getSaldoPendiente() == null ? BigDecimal.ZERO : cuenta.getSaldoPendiente();
                } else if (v.getEstado() == EstadoVenta.PAGADA) {
                    // Venta de contado ya pagada (su pago se registro al venderse).
                    pagado = total;
                    saldo = BigDecimal.ZERO;
                } else {
                    // Caso residual sin cuenta: se calcula desde la tabla pagos.
                    pagado = pagoController.totalPagado(v.getIdVenta());
                    saldo = total.subtract(pagado);
                }

                ventas.add(v);
                saldos.add(saldo);
                modelo.addRow(new Object[]{
                        v.getNumeroVenta(),
                        DateFormatter.isoAVista(v.getFecha()),
                        v.getNombreTercero() == null ? "(sin cliente)" : v.getNombreTercero(),
                        MoneyFormatter.cop(total),
                        MoneyFormatter.cop(pagado),
                        MoneyFormatter.cop(saldo),
                        v.getEstado()
                });
            }
        } catch (Exception e) {
            error("No se pudieron cargar las ventas: " + e.getMessage());
        }
    }

    private void registrarPago() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione una venta para registrar el pago.");
            return;
        }
        Venta v = ventas.get(fila);
        BigDecimal saldo = saldos.get(fila);
        if (saldo.compareTo(BigDecimal.ZERO) <= 0) {
            error("La venta ya se encuentra pagada.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        PagoFormDialog dialog = new PagoFormDialog(owner, pagoController, v.getIdVenta(),
                v.getNumeroVenta(), saldo);
        dialog.setVisible(true);
        if (dialog.isGuardado()) {
            recargar();
        }
    }

    private void verPagos() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            error("Seleccione una venta para ver sus pagos.");
            return;
        }
        Venta v = ventas.get(fila);
        try {
            List<Pago> pagos = pagoController.listarPorVenta(v.getIdVenta());
            DefaultTableModel md = new DefaultTableModel(
                    new Object[]{"Fecha", "Medio", "Valor", "Referencia"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            for (Pago p : pagos) {
                md.addRow(new Object[]{
                        DateFormatter.isoAVista(p.getFecha()),
                        p.getMedioPago(),
                        MoneyFormatter.cop(p.getValor()),
                        p.getReferencia() == null ? "" : p.getReferencia()});
            }
            JTable t = new JTable(md);
            TableUtils.estilizar(t);
            TableUtils.alinearDerecha(t, 2);
            JScrollPane scroll = new JScrollPane(t);
            scroll.setPreferredSize(new Dimension(520, 280));
            scroll.getViewport().setBackground(Color.WHITE);
            JOptionPane.showMessageDialog(SwingUtilities.getWindowAncestor(this), scroll,
                    "Pagos de la venta " + v.getNumeroVenta(), JOptionPane.PLAIN_MESSAGE);
        } catch (Exception e) {
            error("No se pudieron cargar los pagos: " + e.getMessage());
        }
    }

    private void error(String mensaje) {
        JOptionPane.showMessageDialog(this, mensaje, "Pagos", JOptionPane.ERROR_MESSAGE);
    }
}
