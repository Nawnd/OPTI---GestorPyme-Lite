# Guía de uso — OPTI - GestorPyme Lite

Esta guía explica un flujo básico para probar **OPTI - GestorPyme Lite** desde cero.

El objetivo es mostrar cómo el sistema conecta clientes, productos, inventario, compras, ventas, cartera, dashboards y órdenes de trabajo para talleres automotrices.

---

## 1. Requisitos previos

Antes de ejecutar la aplicación, verificar que el entorno tenga:

- Java 17 o superior.
- Maven instalado.
- Git, si se va a clonar el repositorio.
- Sistema operativo compatible con Java.
- Visual Studio Code, IntelliJ IDEA, NetBeans u otro IDE compatible con Maven.

---

## 2. Ejecutar el proyecto

Desde la raíz del proyecto:

```bash
mvn clean compile
mvn test
mvn exec:java
```

Resultado esperado:

```text
BUILD SUCCESS
Tests run: 317
Failures: 0
Errors: 0
Skipped: 0
```

---

## 3. Flujo básico recomendado

El siguiente flujo permite validar los módulos principales del sistema:

```text
Configuración -> Cliente -> Proveedor -> Producto -> Bodega -> Compra -> Recepción -> Inventario -> Vehículo -> Orden de trabajo -> Venta -> Pago/Cartera -> Dashboard -> Exportación
```

---

## 4. Configurar empresa

Entrar al módulo:

```text
Configuración
```

Registrar información básica:

- Nombre de la empresa.
- Documento o NIT.
- Dirección.
- Teléfono.
- Correo.
- Moneda.
- Mensaje de recibo.

También se puede revisar:

- Preferencias de interfaz.
- Respaldo/restauración de base de datos.
- Información del sistema.

---

## 5. Crear cliente

Entrar al módulo:

```text
Clientes
```

Crear un cliente con datos básicos:

- Nombre.
- Documento.
- Teléfono.
- Correo.
- Dirección.
- Estado activo.

Este cliente podrá usarse después en ventas, cartera, vehículos y órdenes de trabajo.

---

## 6. Crear proveedor

Entrar al módulo:

```text
Proveedores
```

Crear un proveedor con datos básicos:

- Nombre.
- Documento.
- Teléfono.
- Correo.
- Dirección.
- Estado activo.

El proveedor será usado en órdenes de compra.

---

## 7. Crear producto, servicio o repuesto

Entrar al módulo:

```text
Productos / Servicios
```

Crear productos o servicios según el caso.

### Producto inventariable

Ejemplo:

```text
Filtro de aceite
```

Configurar:

- Tipo: Producto.
- Controla inventario: Sí.
- Precio de compra.
- Precio de venta.
- Stock mínimo.
- Unidad de medida.
- Categoría o subtipo si aplica.

### Servicio

Ejemplo:

```text
Cambio de aceite
```

Configurar:

- Tipo: Servicio o Mano de obra.
- Controla inventario: No.
- Precio de venta.
- Modo de cálculo si aplica.

---

## 8. Crear bodega

Entrar al módulo:

```text
Bodegas
```

Crear una bodega, por ejemplo:

```text
Principal
```

La bodega permitirá controlar existencias por ubicación.

---

## 9. Crear orden de compra

Entrar al módulo:

```text
Compras
```

Crear una orden de compra:

- Seleccionar proveedor.
- Agregar productos.
- Indicar cantidades.
- Indicar precios.
- Guardar o emitir la orden.

---

## 10. Recibir mercancía

Desde el módulo de compras, registrar recepción:

- Seleccionar la orden de compra.
- Recibir total o parcialmente.
- Seleccionar bodega destino.
- Asociar lote si aplica.
- Indicar fecha de vencimiento si aplica.

Al recibir mercancía, el sistema debe:

- Aumentar inventario.
- Registrar Kardex de entrada.
- Actualizar estado de la orden.
- Registrar lote si aplica.

---

## 11. Ver inventario

Entrar al módulo:

```text
Inventario
```

Validar:

- Producto.
- Bodega.
- Cantidad disponible.
- Estado de stock.
- Reabastecimiento.
- Conciliación lote vs stock.

---

## 12. Crear vehículo

Entrar al módulo:

```text
Vehículos
```

Registrar:

- Cliente.
- Placa.
- Marca.
- Línea/modelo.
- Año.
- Color.
- Kilometraje.
- Observaciones.

Este vehículo podrá usarse en órdenes de trabajo.

---

## 13. Crear orden de trabajo

Entrar al módulo:

```text
Órdenes de trabajo
```

Crear una OT con:

- Cliente.
- Vehículo.
- Kilometraje de ingreso.
- Motivo de ingreso.
- Diagnóstico.
- Observaciones.

Agregar detalles:

### Servicios

Solo deben aparecer ítems tipo:

```text
SERVICIO
MANO_OBRA
```

### Repuestos

Solo deben aparecer productos inventariables:

```text
PRODUCTO con controla_inventario = true
```

La OT inicialmente funciona como documento operativo.

---

## 14. Cerrar y facturar OT

Cuando la orden esté lista, usar:

```text
Cerrar y facturar
```

El cierre debe:

- Crear venta.
- Generar detalle de venta.
- Descontar inventario de repuestos.
- Aplicar FEFO si hay lotes.
- Registrar Kardex de salida.
- Registrar pago si es contado.
- Generar cuenta por cobrar si es crédito.
- Marcar la OT como entregada.
- Asociar la OT con la venta generada.

---

## 15. Registrar pago o revisar cartera

Si la venta fue de contado:

```text
Pagos
```

Validar el pago registrado.

Si la venta fue a crédito:

```text
Cuentas por cobrar
```

Validar:

- Saldo pendiente.
- Estado de la cuenta.
- Registro de abonos si aplica.

---

## 16. Revisar dashboard

Entrar a:

```text
Dashboard
```

Validar indicadores como:

- Ventas.
- Pagos.
- Cartera.
- Inventario.
- Compras.
- Lotes.
- Alertas operativas.
- Indicadores gerenciales.

---

## 17. Exportar CSV

Entrar al módulo:

```text
Exportación CSV
```

Probar exportaciones como:

- Clientes.
- Inventario.
- Kardex.
- Ventas.
- Cartera.
- Compras.
- Recepciones.
- Dashboard gerencial.

Los archivos CSV usan separador `;` y están pensados para abrirse en Excel u otras herramientas de análisis.

---

## 18. Respaldo de base de datos

Entrar a:

```text
Configuración -> Base de datos
```

Usar:

```text
Crear respaldo
```

Esto permite generar una copia del archivo SQLite local.

También existe:

```text
Restaurar respaldo
```

Después de restaurar un respaldo, se recomienda cerrar y abrir nuevamente la aplicación.

---

## 19. Flujo resumido de prueba

```text
1. Configurar empresa
2. Crear cliente
3. Crear proveedor
4. Crear producto inventariable
5. Crear servicio
6. Crear bodega
7. Crear orden de compra
8. Recibir mercancía con lote
9. Revisar inventario
10. Crear vehículo
11. Crear orden de trabajo
12. Agregar servicio y repuesto
13. Cerrar OT y facturar
14. Revisar venta
15. Revisar inventario/Kardex
16. Revisar pago o cartera
17. Revisar dashboard
18. Exportar CSV
19. Crear respaldo
```

---

## 20. Notas importantes

- El sistema funciona inicialmente en modo local/offline.
- La base de datos principal es SQLite.
- No se recomienda subir `data/gestorpyme.db` a GitHub.
- El inventario se descuenta en ventas o al cerrar una orden de trabajo.
- Los repuestos con lote siguen lógica FEFO.
- La utilidad del dashboard es estimada y no reemplaza contabilidad formal.
- La facturación electrónica está planeada como posible evolución futura.

---

## 21. Recomendación para pruebas

Para probar el sistema de forma ordenada, se recomienda crear datos de ejemplo con prefijos claros, por ejemplo:

```text
Cliente Demo
Proveedor Demo
Producto Demo
Bodega Demo
Vehículo Demo
OT Demo
```

Evitar usar datos personales reales en pruebas o publicaciones.