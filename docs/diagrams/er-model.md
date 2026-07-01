# Modelo entidad-relación resumido — OPTI - GestorPyme Lite

Este documento presenta un modelo entidad-relación resumido de **OPTI - GestorPyme Lite**.

El objetivo no es listar todos los campos de todas las tablas, sino mostrar las relaciones principales entre módulos: clientes, ventas, inventario, compras, pagos, cartera, lotes, vehículos y órdenes de trabajo.

---

## 1. Vista general del modelo

```mermaid
erDiagram
    TERCEROS ||--o{ VENTAS : realiza
    TERCEROS ||--o{ CUENTAS_POR_COBRAR : tiene
    TERCEROS ||--o{ ORDENES_COMPRA : proveedor
    TERCEROS ||--o{ VEHICULOS : posee
    TERCEROS ||--o{ CRM_SEGUIMIENTOS : registra

    ITEMS ||--o{ VENTA_DETALLES : vendido_en
    ITEMS ||--o{ INVENTARIO_STOCK : stock
    ITEMS ||--o{ INVENTARIO_MOVIMIENTOS : movimientos
    ITEMS ||--o{ LOTES : lotes
    ITEMS ||--o{ ORDENES_COMPRA_DETALLES : comprado_en
    ITEMS ||--o{ ORDEN_TRABAJO_SERVICIOS : servicio
    ITEMS ||--o{ ORDEN_TRABAJO_REPUESTOS : repuesto

    BODEGAS ||--o{ INVENTARIO_STOCK : contiene
    BODEGAS ||--o{ INVENTARIO_MOVIMIENTOS : registra
    BODEGAS ||--o{ LOTES : almacena
    BODEGAS ||--o{ ORDEN_TRABAJO_REPUESTOS : salida

    VENTAS ||--o{ VENTA_DETALLES : incluye
    VENTAS ||--o{ PAGOS : recibe
    VENTAS ||--o{ CUENTAS_POR_COBRAR : genera

    CUENTAS_POR_COBRAR ||--o{ ABONOS_CUENTA : recibe

    ORDENES_COMPRA ||--o{ ORDENES_COMPRA_DETALLES : contiene
    ORDENES_COMPRA ||--o{ RECEPCIONES_MERCANCIA : recibe
    RECEPCIONES_MERCANCIA ||--o{ RECEPCIONES_DETALLES : detalle

    LOTES ||--o{ VENTA_DETALLE_LOTES : consumido_en
    VENTA_DETALLES ||--o{ VENTA_DETALLE_LOTES : consume

    VEHICULOS ||--o{ ORDENES_TRABAJO : genera
    ORDENES_TRABAJO ||--o{ ORDEN_TRABAJO_SERVICIOS : incluye
    ORDENES_TRABAJO ||--o{ ORDEN_TRABAJO_REPUESTOS : incluye
    ORDENES_TRABAJO }o--|| VENTAS : factura
```

---

## 2. Núcleo CRM / terceros

```mermaid
erDiagram
    TERCEROS ||--o{ CRM_SEGUIMIENTOS : tiene
    TERCEROS ||--o{ VENTAS : realiza
    TERCEROS ||--o{ CUENTAS_POR_COBRAR : genera
    TERCEROS ||--o{ VEHICULOS : posee
    TERCEROS ||--o{ ORDENES_COMPRA : proveedor

    TERCEROS {
        int id_tercero PK
        string tipo_tercero
        string nombre
        string documento
        string telefono
        string correo
        string estado
    }

    CRM_SEGUIMIENTOS {
        int id_seguimiento PK
        int id_tercero FK
        string tipo
        string estado
        string descripcion
        string fecha
    }
```

### Explicación

La tabla `terceros` centraliza clientes, prospectos y proveedores. Desde esta tabla se conectan ventas, cartera, compras, CRM y vehículos.

---

## 3. Productos, inventario, bodegas y Kardex

```mermaid
erDiagram
    ITEMS ||--o{ INVENTARIO_STOCK : tiene
    ITEMS ||--o{ INVENTARIO_MOVIMIENTOS : registra
    ITEMS ||--o{ LOTES : controla
    BODEGAS ||--o{ INVENTARIO_STOCK : contiene
    BODEGAS ||--o{ INVENTARIO_MOVIMIENTOS : origen_destino
    BODEGAS ||--o{ LOTES : almacena

    ITEMS {
        int id_item PK
        string codigo
        string nombre
        string tipo_item
        decimal precio_compra
        decimal precio_venta
        boolean controla_inventario
        decimal stock_minimo
        string estado
    }

    BODEGAS {
        int id_bodega PK
        string nombre
        string ubicacion
        string estado
    }

    INVENTARIO_STOCK {
        int id_stock PK
        int id_item FK
        int id_bodega FK
        decimal cantidad
    }

    INVENTARIO_MOVIMIENTOS {
        int id_movimiento PK
        int id_item FK
        int id_bodega FK
        string tipo_movimiento
        decimal cantidad
        string motivo
        string fecha
    }

    LOTES {
        int id_lote PK
        int id_item FK
        int id_bodega FK
        string numero_lote
        string fecha_vencimiento
        decimal cantidad_inicial
        decimal cantidad_disponible
        string estado
    }
```

### Explicación

`inventario_stock` representa la existencia disponible por ítem y bodega. `inventario_movimientos` funciona como Kardex. `lotes` permite trazabilidad por vencimiento y consumo FEFO.

---

## 4. Ventas, pagos y cartera

```mermaid
erDiagram
    TERCEROS ||--o{ VENTAS : cliente
    VENTAS ||--o{ VENTA_DETALLES : contiene
    ITEMS ||--o{ VENTA_DETALLES : vendido
    VENTAS ||--o{ PAGOS : recibe
    VENTAS ||--o{ CUENTAS_POR_COBRAR : genera
    CUENTAS_POR_COBRAR ||--o{ ABONOS_CUENTA : recibe

    VENTAS {
        int id_venta PK
        int id_tercero FK
        string numero_venta
        string fecha
        decimal subtotal
        decimal descuento
        decimal total
        string estado
    }

    VENTA_DETALLES {
        int id_detalle PK
        int id_venta FK
        int id_item FK
        int id_bodega_salida FK
        decimal cantidad
        decimal precio_unitario
        decimal subtotal_linea
    }

    PAGOS {
        int id_pago PK
        int id_venta FK
        string medio_pago
        decimal valor
        string fecha
    }

    CUENTAS_POR_COBRAR {
        int id_cuenta PK
        int id_venta FK
        int id_tercero FK
        decimal valor_total
        decimal valor_pagado
        decimal saldo_pendiente
        string estado
    }

    ABONOS_CUENTA {
        int id_abono PK
        int id_cuenta FK
        decimal valor
        string medio_pago
        string fecha
    }
```

### Explicación

Una venta puede ser de contado o crédito. Si es contado, genera pago. Si es crédito, genera cuenta por cobrar y posteriormente puede recibir abonos.

---

## 5. Lotes y consumo FEFO

```mermaid
erDiagram
    LOTES ||--o{ VENTA_DETALLE_LOTES : consumido_en
    VENTA_DETALLES ||--o{ VENTA_DETALLE_LOTES : consume
    ITEMS ||--o{ LOTES : tiene

    LOTES {
        int id_lote PK
        int id_item FK
        int id_bodega FK
        string numero_lote
        string fecha_vencimiento
        decimal cantidad_disponible
        string estado
    }

    VENTA_DETALLES {
        int id_detalle PK
        int id_venta FK
        int id_item FK
        decimal cantidad
    }

    VENTA_DETALLE_LOTES {
        int id_detalle_lote PK
        int id_detalle FK
        int id_lote FK
        decimal cantidad
    }
```

### Explicación

Cuando una venta consume productos con lote, el sistema registra qué lote fue usado. La lógica FEFO prioriza los lotes con vencimiento más próximo.

---

## 6. Compras y recepción

```mermaid
erDiagram
    TERCEROS ||--o{ ORDENES_COMPRA : proveedor
    ORDENES_COMPRA ||--o{ ORDENES_COMPRA_DETALLES : contiene
    ITEMS ||--o{ ORDENES_COMPRA_DETALLES : comprado
    ORDENES_COMPRA ||--o{ RECEPCIONES_MERCANCIA : recibe
    RECEPCIONES_MERCANCIA ||--o{ RECEPCIONES_DETALLES : contiene
    ITEMS ||--o{ RECEPCIONES_DETALLES : recibido
    BODEGAS ||--o{ RECEPCIONES_DETALLES : destino
    LOTES ||--o{ RECEPCIONES_DETALLES : asociado

    ORDENES_COMPRA {
        int id_orden_compra PK
        int id_proveedor FK
        string numero_orden
        string fecha
        decimal total
        string estado
    }

    ORDENES_COMPRA_DETALLES {
        int id_detalle PK
        int id_orden_compra FK
        int id_item FK
        decimal cantidad
        decimal precio_unitario
    }

    RECEPCIONES_MERCANCIA {
        int id_recepcion PK
        int id_orden_compra FK
        string numero_recepcion
        string fecha
    }

    RECEPCIONES_DETALLES {
        int id_recepcion_detalle PK
        int id_recepcion FK
        int id_item FK
        int id_bodega FK
        int id_lote FK
        decimal cantidad_recibida
    }
```

### Explicación

Las órdenes de compra permiten solicitar mercancía a proveedores. La recepción actualiza inventario, puede crear o asociar lotes y genera movimientos de Kardex de entrada.

---

## 7. Vehículos y órdenes de trabajo

```mermaid
erDiagram
    TERCEROS ||--o{ VEHICULOS : posee
    VEHICULOS ||--o{ ORDENES_TRABAJO : genera
    TERCEROS ||--o{ ORDENES_TRABAJO : cliente
    ORDENES_TRABAJO ||--o{ ORDEN_TRABAJO_SERVICIOS : incluye
    ORDENES_TRABAJO ||--o{ ORDEN_TRABAJO_REPUESTOS : incluye
    ITEMS ||--o{ ORDEN_TRABAJO_SERVICIOS : servicio
    ITEMS ||--o{ ORDEN_TRABAJO_REPUESTOS : repuesto
    BODEGAS ||--o{ ORDEN_TRABAJO_REPUESTOS : salida
    ORDENES_TRABAJO }o--|| VENTAS : genera

    VEHICULOS {
        int id_vehiculo PK
        int id_tercero FK
        string placa
        string marca
        string linea
        int anio
        string color
        decimal kilometraje
        string estado
    }

    ORDENES_TRABAJO {
        int id_orden_trabajo PK
        string numero_ot
        int id_tercero FK
        int id_vehiculo FK
        int id_venta FK
        string estado
        string motivo_ingreso
        string diagnostico
        decimal total
    }

    ORDEN_TRABAJO_SERVICIOS {
        int id_ot_servicio PK
        int id_orden_trabajo FK
        int id_item FK
        decimal cantidad
        decimal precio_unitario
        decimal subtotal
    }

    ORDEN_TRABAJO_REPUESTOS {
        int id_ot_repuesto PK
        int id_orden_trabajo FK
        int id_item FK
        int id_bodega_salida FK
        decimal cantidad
        decimal precio_unitario
        decimal subtotal
    }
```

### Explicación

El módulo de taller permite relacionar cliente, vehículo, servicios, repuestos y venta. La orden de trabajo inicia como documento operativo y al cerrarse se convierte en una venta real.

---

## 8. Consideraciones del modelo

- `terceros` centraliza clientes, prospectos y proveedores.
- `items` centraliza productos, servicios, repuestos e insumos.
- `inventario_stock` representa el disponible por bodega.
- `inventario_movimientos` funciona como Kardex.
- `lotes` permite trazabilidad FEFO.
- `ventas` integra productos, servicios, pagos y cartera.
- `ordenes_trabajo` conecta operación de taller con ventas e inventario.
- `ordenes_compra` y `recepciones` alimentan inventario y lotes.
- El sistema usa SQLite local con migraciones aditivas e idempotentes.

---

## 9. Próximas mejoras del modelo

- Agregar cuentas por pagar.
- Agregar historial avanzado por vehículo.
- Agregar exportación específica de órdenes de trabajo.
- Agregar dashboard operativo de taller.
- Agregar facturación electrónica en una futura versión.