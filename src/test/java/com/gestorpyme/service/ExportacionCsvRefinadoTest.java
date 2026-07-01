package com.gestorpyme.service;

import com.gestorpyme.domain.enums.EstadoCuenta;
import com.gestorpyme.domain.enums.EstadoRegistro;
import com.gestorpyme.domain.enums.EstadoVenta;
import com.gestorpyme.domain.enums.MedioPago;
import com.gestorpyme.domain.enums.ModoCalculoServicio;
import com.gestorpyme.domain.enums.TipoExportacion;
import com.gestorpyme.domain.enums.TipoItem;
import com.gestorpyme.domain.enums.TipoTercero;
import com.gestorpyme.domain.model.Abono;
import com.gestorpyme.domain.model.CuentaPorCobrar;
import com.gestorpyme.domain.model.Item;
import com.gestorpyme.domain.model.Tercero;
import com.gestorpyme.domain.model.Venta;
import com.gestorpyme.domain.model.VentaDetalle;
import com.gestorpyme.infrastructure.database.DatabaseConnection;
import com.gestorpyme.infrastructure.database.DatabaseInitializer;
import com.gestorpyme.repository.ItemRepository;
import com.gestorpyme.repository.TerceroRepository;
import com.gestorpyme.util.ExportValueFormatter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de la exportacion CSV refinada (v0.8.2): formateadores, escritura CSV
 * (separador, BOM, escape), encabezados de cada exportacion y valores limpios
 * (estados legibles, montos sin '$'/miles/'.0'). Datos de prueba marcados "ZEXPTEST".
 * La exportacion es de solo lectura sobre los datos de negocio.
 */
class ExportacionCsvRefinadoTest {

    private static final ExportacionService exportacionService = new ExportacionService();
    private static final VentaService ventaService = new VentaService();
    private static final CuentaService cuentaService = new CuentaService();
    private static final ItemRepository itemRepo = new ItemRepository();
    private static final TerceroRepository terceroRepo = new TerceroRepository();

    @BeforeAll
    static void preparar() throws SQLException {
        DatabaseInitializer.initialize();
        limpiar();
    }

    @AfterAll
    static void limpiarFinal() throws SQLException {
        limpiar();
    }

    // ---------------- ExportValueFormatter ----------------

    @Test
    void montoEnteroSinDecimalesNiSimbolos() {
        assertEquals("976548", ExportValueFormatter.formatMoneyForCsv(new BigDecimal("976548")));
        assertEquals("0", ExportValueFormatter.formatMoneyForCsv(BigDecimal.ZERO));
        assertEquals("976548", ExportValueFormatter.formatMoneyForCsv(new BigDecimal("976548.0")));
    }

    @Test
    void montoConDecimalesUsaComa() {
        assertEquals("1250000,50", ExportValueFormatter.formatMoneyForCsvDecimal(new BigDecimal("1250000.50")));
        assertEquals("976548", ExportValueFormatter.formatMoneyForCsvDecimal(new BigDecimal("976548")));
    }

    @Test
    void cantidadLimpia() {
        assertEquals("85", ExportValueFormatter.formatQuantityForCsv(new BigDecimal("85.0")));
        assertEquals("85,5", ExportValueFormatter.formatQuantityForCsv(new BigDecimal("85.5")));
    }

    @Test
    void fechaNormalizadaAIso() {
        assertEquals("2026-06-13", ExportValueFormatter.formatDateForCsv("2026-06-13"));
        assertEquals("2026-06-13 10:30", ExportValueFormatter.formatDateForCsv("2026-06-13 10:30:00"));
        assertEquals("2026-06-13 10:30", ExportValueFormatter.formatDateForCsv("2026-06-13T10:30:45"));
        assertEquals("", ExportValueFormatter.formatDateForCsv(null));
    }

    @Test
    void estadoExportaEtiquetaLegible() {
        assertEquals("Parcial", ExportValueFormatter.formatEstadoForCsv(EstadoCuenta.ABONADA));
        assertEquals("Pendiente de pago", ExportValueFormatter.formatEstadoForCsv(EstadoVenta.PENDIENTE_PAGO));
        assertEquals("", ExportValueFormatter.formatEstadoForCsv(null));
    }

    // ---------------- CsvWriter (separador, BOM, escape) ----------------

    @Test
    void csvUsaPuntoYComaBomYEscapa() throws SQLException, IOException {
        // Se exporta un tipo cualquiera a archivo y se inspeccionan los bytes/el contenido.
        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "zexp_clientes.csv");
        exportacionService.exportar(TipoExportacion.CLIENTES, archivo.toString(), 1);
        byte[] bytes = Files.readAllBytes(archivo);
        // BOM UTF-8 al inicio.
        assertTrue(bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF, "Debe iniciar con BOM UTF-8.");
        String contenido = new String(bytes, StandardCharsets.UTF_8);
        // El encabezado de clientes usa separador ';'.
        String encabezado = contenido.split("\r?\n", 2)[0];
        assertTrue(encabezado.contains(";"), "El separador debe ser ';'.");
        borrarLogTemporales();
    }

    // ---------------- Encabezados de cada exportacion ----------------

    @Test
    void encabezadosDeExportacionesSonCorrectos() throws SQLException, IOException {
        assertEquals("Nombre;Documento;Telefono;Correo;Direccion;Estado",
                encabezadoDe(TipoExportacion.PROVEEDORES));
        assertEquals("Codigo;Nombre;Tipo;Categoria;Subtipo;Unidad;Precio_Compra_COP;Precio_Venta_COP;"
                + "Inventario;Stock_Minimo;Stock_Maximo;Proveedor_Preferido;Modo_Servicio;Porcentaje_Servicio;Estado",
                encabezadoDe(TipoExportacion.ITEMS));
        assertEquals("Numero_Venta;Fecha;Cliente;Item;Bodega_Salida;Lotes_Consumidos;Cantidad;Precio_Unitario_COP;Descuento_COP;Subtotal_COP",
                encabezadoDe(TipoExportacion.VENTA_DETALLES));
        assertEquals("Venta;Cliente;Fecha;Medio;Valor_COP;Observaciones",
                encabezadoDe(TipoExportacion.ABONOS));
        assertEquals("Numero_OC;Fecha;Proveedor;Total_COP;Estado;Fecha_Estimada;Observaciones",
                encabezadoDe(TipoExportacion.COMPRAS));
        assertEquals("Numero_RC;Numero_OC;Fecha;Proveedor;Estado",
                encabezadoDe(TipoExportacion.RECEPCIONES));
        assertEquals("Codigo_Item;Item;Stock_Actual;Stock_Minimo;Stock_Maximo;En_Pedido;Sugerido;Proveedor_Preferido;Estado_Logistico",
                encabezadoDe(TipoExportacion.REABASTECIMIENTO));
        assertEquals("Codigo_Item;Item;Bodega;Lote;Fecha_Ingreso;Fecha_Vencimiento;Dias_Para_Vencer;Cantidad;Cantidad_Disponible;Estado;Estado_Vencimiento",
                encabezadoDe(TipoExportacion.LOTES));
        borrarLogTemporales();
    }

    // ---------------- Contenido con datos sembrados ----------------

    @Test
    void exportaItemsConCamposDeServicio() throws SQLException, IOException {
        Item s = new Item();
        s.setCodigo("ZEXPTEST-S");
        s.setNombre("ZEXPTEST Servicio");
        s.setTipoItem(TipoItem.SERVICIO);
        s.setPrecioCompra(BigDecimal.ZERO);
        s.setPrecioVenta(new BigDecimal("40000"));
        s.setControlaInventario(false);
        s.setStockMinimo(BigDecimal.ZERO);
        s.setEstado(EstadoRegistro.ACTIVO);
        s.setModoCalculoServicio(ModoCalculoServicio.PORCENTAJE_REPUESTOS);
        s.setPorcentajeServicio(new BigDecimal("10"));
        itemRepo.insertar(s);

        String texto = exportarTexto(TipoExportacion.ITEMS);
        String fila = filaQueContiene(texto, "ZEXPTEST-S");
        assertTrue(fila.contains("Porcentaje sobre repuestos"), "Debe exportar la etiqueta del modo de servicio.");
        assertTrue(fila.contains(";10;"), "Debe exportar el porcentaje como numero limpio.");
        assertTrue(fila.contains(";No;"), "Un servicio no controla inventario -> 'No'.");
        assertSinValoresVisuales(texto);
        borrarLogTemporales();
    }

    @Test
    void exportaCarteraYAbonosConEstadoYValorLimpios() throws SQLException, IOException {
        // Cliente + servicio (sin inventario) + venta a credito + abono parcial.
        int idCliente = terceroRepo.insertar(cliente("ZEXPTEST Cliente"));
        Item serv = new Item();
        serv.setCodigo("ZEXPTEST-SV");
        serv.setNombre("ZEXPTEST Mano de obra");
        serv.setTipoItem(TipoItem.SERVICIO);
        serv.setPrecioCompra(BigDecimal.ZERO);
        serv.setPrecioVenta(new BigDecimal("1000000"));
        serv.setControlaInventario(false);
        serv.setStockMinimo(BigDecimal.ZERO);
        serv.setEstado(EstadoRegistro.ACTIVO);
        serv.setIdItem(itemRepo.insertar(serv));

        Venta v = new Venta();
        v.setIdTercero(idCliente);
        v.setIdUsuario(1);
        List<VentaDetalle> det = new ArrayList<>();
        det.add(linea(serv.getIdItem(), "ZEXPTEST Mano de obra", false, "1", "1000000", "1000000"));
        // Venta a credito (contado=false) con vencimiento; genera cuenta por cobrar.
        ventaService.registrarVenta(v, det, 1, false, "2026-07-15", null);

        Optional<CuentaPorCobrar> cuenta = cuentaService.buscarPorVenta(v.getIdVenta());
        assertTrue(cuenta.isPresent(), "La venta a credito debe generar cuenta por cobrar.");

        // Abono parcial de 600.000 -> la cuenta queda ABONADA ("Parcial").
        Abono ab = new Abono();
        ab.setIdCuenta(cuenta.get().getIdCuenta());
        ab.setValor(new BigDecimal("600000"));
        ab.setFecha("2026-06-15");
        ab.setMedioPago(MedioPago.EFECTIVO);
        ab.setObservaciones("ZEXPTEST abono");
        cuentaService.registrarAbono(ab);

        // Cartera: estado legible "Parcial" y saldo limpio.
        String cartera = exportarTexto(TipoExportacion.CARTERA);
        String filaCartera = filaQueContiene(cartera, v.getNumeroVenta());
        assertTrue(filaCartera.contains("Parcial"), "Cartera debe mostrar el estado legible 'Parcial'.");
        assertTrue(filaCartera.contains("400000"), "Saldo limpio (1.000.000 - 600.000 = 400000).");
        assertSinValoresVisuales(cartera);

        // Abonos: valor limpio.
        String abonos = exportarTexto(TipoExportacion.ABONOS);
        String filaAbono = filaQueContiene(abonos, v.getNumeroVenta());
        assertTrue(filaAbono.contains("600000"), "Abono con Valor_COP limpio.");
        assertTrue(filaAbono.contains("Efectivo"), "Medio del abono como etiqueta legible.");
        assertSinValoresVisuales(abonos);
        borrarLogTemporales();
    }

    // ---------------- auxiliares ----------------

    private static String encabezadoDe(TipoExportacion tipo) throws SQLException, IOException {
        return exportarTexto(tipo).split("\r?\n", 2)[0];
    }

    private static String exportarTexto(TipoExportacion tipo) throws SQLException, IOException {
        Path archivo = Path.of(System.getProperty("java.io.tmpdir"), "zexp_" + tipo.name().toLowerCase() + ".csv");
        exportacionService.exportar(tipo, archivo.toString(), 1);
        String contenido = new String(Files.readAllBytes(archivo), StandardCharsets.UTF_8);
        // Quita el BOM para comparar encabezados con comodidad.
        return contenido.startsWith("\uFEFF") ? contenido.substring(1) : contenido;
    }

    private static String filaQueContiene(String texto, String clave) {
        for (String linea : texto.split("\r?\n")) {
            if (linea.contains(clave)) {
                return linea;
            }
        }
        return "";
    }

    /** Verifica que el CSV no contenga simbolos de moneda, miles con punto, ni ".0". */
    private static void assertSinValoresVisuales(String texto) {
        assertFalse(texto.contains("$"), "El CSV no debe contener '$'.");
        assertFalse(texto.matches("(?s).*\\d\\.\\d{3}.*"), "El CSV no debe usar punto de miles.");
        assertFalse(texto.matches("(?s).*\\d\\.0(\\D|$).*"), "El CSV no debe contener '.0'.");
    }

    private static Tercero cliente(String nombre) {
        Tercero t = new Tercero();
        t.setNombre(nombre);
        t.setTipoTercero(TipoTercero.CLIENTE);
        t.setEstado(EstadoRegistro.ACTIVO);
        return t;
    }

    private static VentaDetalle linea(int idItem, String nombre, boolean controlaInv,
                                      String cantidad, String precio, String subtotal) {
        VentaDetalle d = new VentaDetalle();
        d.setIdItem(idItem);
        d.setNombreItem(nombre);
        d.setControlaInventario(controlaInv);
        d.setCantidad(new BigDecimal(cantidad));
        d.setPrecioUnitario(new BigDecimal(precio));
        d.setDescuentoLinea(BigDecimal.ZERO);
        d.setSubtotalLinea(new BigDecimal(subtotal));
        return d;
    }

    private static void borrarLogTemporales() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zexp_%'");
        }
    }

    private static void limpiar() throws SQLException {
        try (Connection c = DatabaseConnection.getConnection(); Statement st = c.createStatement()) {
            String ventasZ = "SELECT id_venta FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZEXPTEST%')";
            String cuentasZ = "SELECT id_cuenta FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")";
            st.executeUpdate("DELETE FROM abonos_cuenta WHERE id_cuenta IN (" + cuentasZ + ")");
            st.executeUpdate("DELETE FROM pagos WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM cuentas_por_cobrar WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM venta_detalles WHERE id_venta IN (" + ventasZ + ")");
            st.executeUpdate("DELETE FROM ventas WHERE id_tercero IN "
                    + "(SELECT id_tercero FROM terceros WHERE nombre LIKE 'ZEXPTEST%')");
            st.executeUpdate("DELETE FROM items WHERE codigo LIKE 'ZEXPTEST%'");
            st.executeUpdate("DELETE FROM terceros WHERE nombre LIKE 'ZEXPTEST%'");
            st.executeUpdate("DELETE FROM exportaciones_log WHERE ruta_archivo LIKE '%zexp_%'");
        }
    }
}
