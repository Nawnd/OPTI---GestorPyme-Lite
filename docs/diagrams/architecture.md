# Diagrama de arquitectura — OPTI - GestorPyme Lite

Este documento resume la arquitectura principal de **OPTI - GestorPyme Lite**, un sistema administrativo / ERP / CRM Lite desarrollado en Java, Java Swing, SQLite y Maven.

El proyecto usa una arquitectura por capas basada en MVC, separando interfaz gráfica, controladores, lógica de negocio, acceso a datos e infraestructura de persistencia.

> GitHub puede renderizar diagramas Mermaid directamente dentro de archivos Markdown usando bloques de código con el identificador `mermaid`.

---

## 1. Arquitectura general por capas

```mermaid
flowchart TD
    USER[Usuario final] --> VIEW[View / Java Swing]

    VIEW --> CONTROLLER[Controller]
    CONTROLLER --> SERVICE[Service / Logica de negocio]
    SERVICE --> REPOSITORY[Repository / JDBC]
    REPOSITORY --> DBINFRA[Infrastructure Database]
    DBINFRA --> SQLITE[(SQLite local)]

    SERVICE --> DOMAIN[Domain Model / Enums / Exceptions]
    VIEW --> COMPONENTS[View Components / UI reutilizable]
    SERVICE --> UTIL[Util / Formatters / Helpers]
    SERVICE --> EXPORT[Infrastructure Export / CSV]

    EXPORT --> CSV[(Archivos CSV)]
```

---

## 2. Flujo MVC simplificado

```mermaid
sequenceDiagram
    participant U as Usuario
    participant V as View
    participant C as Controller
    participant S as Service
    participant R as Repository
    participant D as SQLite

    U->>V: Ejecuta accion en la interfaz
    V->>C: Solicita operacion
    C->>S: Delega caso de uso
    S->>S: Valida reglas de negocio
    S->>R: Solicita persistencia o consulta
    R->>D: Ejecuta SQL con PreparedStatement
    D-->>R: Retorna datos
    R-->>S: Retorna modelos
    S-->>C: Retorna resultado
    C-->>V: Actualiza respuesta
    V-->>U: Muestra informacion
```

---

## 3. Responsabilidad de capas

### View

Contiene las interfaces gráficas construidas con Java Swing.

Responsabilidades principales:

- Formularios.
- Tablas.
- Botones.
- Diálogos.
- Paneles tipo dashboard.
- Captura de acciones del usuario.

Regla de arquitectura:

- No ejecutar SQL.
- No concentrar reglas de negocio complejas.

---

### Controller

Actúa como intermediario entre la vista y los servicios.

Responsabilidades principales:

- Recibir acciones desde la interfaz.
- Delegar operaciones a la capa de servicio.
- Evitar que la vista conozca detalles internos de negocio o persistencia.

---

### Service

Contiene la lógica de negocio.

Responsabilidades principales:

- Validaciones.
- Cálculos.
- Reglas empresariales.
- Coordinación de transacciones.
- Flujo de ventas, inventario, compras, cartera, órdenes de trabajo y reportes.

---

### Repository

Contiene el acceso a datos mediante JDBC.

Responsabilidades principales:

- Consultas SQL.
- Inserciones.
- Actualizaciones.
- Lectura de entidades.
- Mapeo entre tablas SQLite y modelos Java.

---

### Infrastructure Database

Contiene la conexión e inicialización de la base de datos.

Responsabilidades principales:

- Conexión SQLite.
- Inicialización del esquema.
- Migraciones idempotentes.
- Preparación de la base local.

---

### Domain

Contiene los modelos, enums y excepciones del negocio.

Ejemplos:

- Cliente / tercero.
- Producto / servicio / repuesto.
- Venta.
- Pago.
- Cuenta por cobrar.
- Vehículo.
- Orden de trabajo.
- Lote.
- Kardex.
- Estados y tipos de operación.

---

### Util

Contiene utilidades transversales.

Ejemplos:

- Formato de dinero.
- Formato de fechas.
- Conversión de valores.
- Hash de contraseñas.
- Parseo numérico.

---

## 4. Flujo operativo de taller

El módulo de taller integra cliente, vehículo, orden de trabajo, venta, inventario, lotes, Kardex y pagos/cartera.

```mermaid
flowchart LR
    CLIENTE[Cliente] --> VEHICULO[Vehiculo]
    VEHICULO --> OT[Orden de Trabajo]
    OT --> SERVICIOS[Servicios / Mano de obra]
    OT --> REPUESTOS[Repuestos / Insumos]
    OT --> CIERRE[Cerrar y facturar]

    CIERRE --> VENTA[Venta]
    VENTA --> INVENTARIO[Inventario]
    INVENTARIO --> FEFO[Lotes / FEFO]
    INVENTARIO --> KARDEX[Kardex]

    VENTA --> PAGO[Pago contado]
    VENTA --> CARTERA[Cuenta por cobrar]
```

---

## 5. Flujo de inventario y lotes

```mermaid
flowchart TD
    COMPRA[Orden de compra] --> RECEPCION[Recepcion de mercancia]
    RECEPCION --> STOCK[Inventario stock]
    RECEPCION --> LOTE[Lote / vencimiento]
    RECEPCION --> KARDEX_ENTRADA[Kardex entrada compra]

    VENTA[Venta / cierre OT] --> STOCK_SALIDA[Descuento de inventario]
    STOCK_SALIDA --> FEFO[Seleccion FEFO]
    FEFO --> LOTE_CONSUMO[Venta detalle lotes]
    STOCK_SALIDA --> KARDEX_SALIDA[Kardex salida venta]
```

---

## 6. Flujo comercial y financiero

```mermaid
flowchart TD
    CLIENTE[Cliente] --> VENTA[Venta]
    VENTA --> DETALLES[Detalle de venta]
    DETALLES --> PRODUCTOS[Productos / Repuestos]
    DETALLES --> SERVICIOS[Servicios]

    VENTA --> TIPO{Tipo de venta}
    TIPO -->|Contado| PAGO[Pago]
    TIPO -->|Credito| CARTERA[Cuenta por cobrar]
    CARTERA --> ABONOS[Abonos]
```

---

## 7. Flujo de compras y recepción

```mermaid
flowchart LR
    PROVEEDOR[Proveedor] --> OC[Orden de compra]
    OC --> DETALLE_OC[Detalle de compra]
    DETALLE_OC --> RECEPCION[Recepcion]
    RECEPCION --> INVENTARIO[Inventario]
    RECEPCION --> LOTES[Lotes]
    RECEPCION --> KARDEX[Kardex entrada]
```

---

## 8. Mapa de módulos principales

```mermaid
mindmap
  root((OPTI - GestorPyme Lite))
    Administracion
      Login
      Configuracion
      Respaldo y restauracion
    CRM
      Clientes
      Prospectos
      Seguimientos
    Inventario
      Productos y servicios
      Bodegas
      Stock
      Kardex
      Lotes FEFO
    Compras
      Ordenes de compra
      Recepcion
      Proveedores
    Ventas
      Contado
      Credito
      Pagos
      Cartera
    Taller
      Vehiculos
      Ordenes de trabajo
      Cierre a venta
    Analitica
      Dashboard general
      Dashboard gerencial
      Exportaciones CSV
```

---

## 9. Decisiones arquitectónicas relevantes

- Arquitectura MVC por capas.
- SQLite como base de datos local offline-first.
- JDBC con `PreparedStatement`.
- Separación entre lógica de negocio y presentación.
- Servicios como capa principal de reglas empresariales.
- Repositorios como única capa con SQL.
- Migraciones idempotentes desde la infraestructura de base de datos.
- Inventario controlado desde operaciones transaccionales.
- FEFO aplicado al consumo de lotes.
- Orden de trabajo como documento operativo antes de convertirse en venta.
- Cierre de orden de trabajo delegando en el flujo existente de ventas.

---

## 10. Estado actual de la arquitectura

La arquitectura soporta módulos de:

- Autenticación.
- Configuración de empresa.
- Clientes, prospectos y proveedores.
- CRM.
- Productos, servicios, repuestos e insumos.
- Bodegas e inventario.
- Kardex.
- Lotes y FEFO.
- Compras y recepción.
- Ventas.
- Pagos.
- Cuentas por cobrar.
- Dashboard general y gerencial.
- Exportaciones CSV.
- Respaldo y restauración.
- Vehículos.
- Órdenes de trabajo.
- Cierre de OT a venta.

---

## 11. Próximas mejoras documentales sugeridas

- Crear diagrama entidad-relación resumido.
- Agregar diagrama del flujo completo de orden de trabajo.
- Agregar guía de uso funcional.
- Agregar decisiones técnicas principales.
- Agregar preguntas frecuentes.
