# Decisiones técnicas — OPTI - GestorPyme Lite

Este documento resume las principales decisiones técnicas tomadas durante el desarrollo de **OPTI - GestorPyme Lite**.

El objetivo es explicar no solo qué tecnologías usa el proyecto, sino también por qué fueron elegidas y cómo se relacionan con el diseño de una solución administrativa tipo ERP/CRM Lite para pymes, empresas de operación, logística y talleres automotrices.

---

## 1. Java como lenguaje principal

### Decisión

El proyecto se desarrolló en **Java**.

### Justificación

Java permite construir aplicaciones robustas, orientadas a objetos y con buena separación por capas. También es adecuado para proyectos académicos/profesionales donde se busca demostrar dominio de:

- Programación Orientada a Objetos.
- Modelado de entidades de negocio.
- Arquitectura MVC.
- Manejo de excepciones.
- Persistencia con JDBC.
- Pruebas automatizadas.
- Organización modular del código.

### Impacto en el proyecto

Java permitió modelar entidades como:

- Clientes.
- Productos.
- Ventas.
- Pagos.
- Inventario.
- Lotes.
- Vehículos.
- Órdenes de trabajo.

También permitió mantener una estructura clara entre capas de presentación, controladores, servicios, repositorios y modelos de dominio.

---

## 2. Java Swing para la interfaz gráfica

### Decisión

Se usó **Java Swing** para construir la interfaz gráfica de escritorio.

### Justificación

Swing permite desarrollar una aplicación de escritorio sin depender de navegador, servidor o conexión permanente a internet. Esto encaja con el enfoque inicial del sistema:

```text
Aplicación local / offline-first / base SQLite local
```

### Impacto en el proyecto

La interfaz permite trabajar con:

- Formularios.
- Tablas.
- Dashboards.
- Diálogos modales.
- Componentes reutilizables.
- Navegación por módulos.

### Consideración futura

Swing es suficiente para una versión local y académica/profesional. Para una versión comercial multiusuario, el proyecto podría evolucionar hacia:

- Aplicación web.
- API REST.
- Cliente web o escritorio conectado a servidor.

---

## 3. SQLite como base de datos local

### Decisión

Se eligió **SQLite** como motor de base de datos local.

### Justificación

SQLite permite operar sin servidor externo. Es adecuado para una primera versión de escritorio orientada a una pyme o taller que trabaja desde un solo equipo.

Ventajas:

- No requiere instalación de servidor de base de datos.
- Facilita el despliegue local.
- Permite operación offline.
- Es suficiente para un volumen inicial de datos de una pyme.
- Simplifica el desarrollo académico/profesional.

### Impacto en el proyecto

La base local permite mantener datos de:

- Clientes.
- Proveedores.
- Productos.
- Inventario.
- Ventas.
- Compras.
- Pagos.
- Cuentas por cobrar.
- Vehículos.
- Órdenes de trabajo.

### Riesgo identificado

Al ser un archivo local, si se elimina o daña `data/gestorpyme.db`, se pueden perder datos.

### Mitigación implementada

Se agregó funcionalidad de:

- Crear respaldo.
- Restaurar respaldo.
- Advertir al usuario antes de reemplazar la base actual.

---

## 4. Arquitectura MVC por capas

### Decisión

El sistema fue organizado usando una arquitectura por capas basada en MVC:

```text
view -> controller -> service -> repository -> infrastructure.database -> SQLite
```

### Justificación

Esta separación evita mezclar interfaz gráfica, lógica de negocio y acceso a datos.

### Responsabilidad de cada capa

#### View

Contiene la interfaz gráfica construida con Java Swing.

No debe ejecutar SQL ni contener reglas de negocio complejas.

#### Controller

Recibe acciones desde la vista y delega en servicios.

#### Service

Contiene validaciones, reglas de negocio, cálculos y coordinación de operaciones.

#### Repository

Contiene consultas SQL y operaciones de persistencia usando JDBC.

#### Infrastructure

Contiene conexión, inicialización de base de datos, exportación y utilidades de infraestructura.

### Impacto en el proyecto

Esta arquitectura facilita:

- Mantenimiento.
- Pruebas.
- Escalabilidad.
- Lectura del código.
- Evolución futura a API o versión web.

---

## 5. Repositorios como única capa con SQL

### Decisión

El SQL se concentra en clases `Repository`.

### Justificación

Esto evita que las vistas o servicios ejecuten consultas directamente.

### Beneficios

- Menos duplicación de consultas.
- Mejor organización.
- Mayor seguridad con `PreparedStatement`.
- Más facilidad para cambiar la base de datos en el futuro.

### Ejemplos de repositorios

- `TerceroRepository`.
- `ItemRepository`.
- `InventarioRepository`.
- `VentaRepository`.
- `OrdenCompraRepository`.
- `OrdenTrabajoRepository`.
- `VehiculoRepository`.

---

## 6. Uso de `PreparedStatement`

### Decisión

Las consultas SQL se realizan mediante `PreparedStatement`.

### Justificación

`PreparedStatement` ayuda a:

- Parametrizar consultas.
- Reducir riesgos de inyección SQL.
- Mejorar claridad del acceso a datos.
- Separar estructura SQL de valores ingresados por el usuario.

---

## 7. Migraciones idempotentes desde Java

### Decisión

Las migraciones del esquema se realizan desde Java de forma aditiva e idempotente.

### Justificación

En una aplicación local con SQLite, es importante que el sistema pueda iniciar y actualizar su esquema sin intervención manual compleja.

### Principios aplicados

- No borrar datos.
- No recrear tablas existentes.
- Crear tablas solo si no existen.
- Agregar columnas solo si faltan.
- Mantener compatibilidad con datos previos.

### Impacto

Esto permitió agregar progresivamente módulos como:

- Compras.
- Recepciones.
- Lotes.
- Dashboard.
- Vehículos.
- Órdenes de trabajo.

---

## 8. `inventario_stock` como fuente autoritativa del disponible

### Decisión

La tabla `inventario_stock` representa el disponible real por producto y bodega.

### Justificación

En sistemas de inventario es necesario tener una fuente clara para consultar existencias actuales.

### Relación con otros módulos

`inventario_stock` se actualiza mediante operaciones como:

- Recepción de mercancía.
- Ventas.
- Ajustes.
- Cierre de órdenes de trabajo.

### Beneficio

Permite consultar rápidamente:

- Stock por bodega.
- Stock bajo.
- Reabastecimiento.
- Disponibilidad para ventas y repuestos.

---

## 9. Kardex para trazabilidad de movimientos

### Decisión

El sistema registra movimientos de inventario en una estructura tipo Kardex.

### Justificación

El Kardex permite explicar por qué cambia el inventario.

### Movimientos relevantes

- Entrada por compra.
- Salida por venta.
- Ajustes manuales.
- Movimientos asociados a lotes.

### Beneficio

Permite auditar operaciones y entender la trazabilidad del inventario.

---

## 10. FEFO para consumo de lotes

### Decisión

Para productos con lote/vencimiento se aplica lógica **FEFO**.

FEFO significa:

```text
First Expired, First Out
```

Es decir, primero se consume el lote que vence antes.

### Justificación

Es una regla útil para negocios con productos perecederos, repuestos con control de lote, insumos, lubricantes, medicamentos, alimentos o productos con vencimiento.

### Impacto

Cuando se realiza una venta o se cierra una orden de trabajo con repuestos, el sistema puede consumir lotes respetando vencimientos.

---

## 11. Ventas como operación transaccional central

### Decisión

La venta es el punto donde se consolidan:

- Cliente.
- Detalle de productos y servicios.
- Inventario.
- Lotes.
- Kardex.
- Pago o cuenta por cobrar.

### Justificación

Evita duplicar operaciones financieras e inventario en varios módulos.

### Impacto

Otros módulos, como órdenes de trabajo, deben delegar en el flujo de venta para ejecutar operaciones reales de salida de inventario, pago o cartera.

---

## 12. Orden de trabajo como documento operativo

### Decisión

La orden de trabajo inicia como documento operativo y no mueve inventario de inmediato.

### Justificación

En un taller automotriz, una OT puede cambiar durante diagnóstico, aprobación o ejecución. Si se descontara inventario al agregar un repuesto, se podrían generar inconsistencias si la OT se edita o cancela.

### Regla aplicada

```text
La OT registra intención operativa.
La venta ejecuta impacto real.
```

### Impacto

La OT permite planear:

- Servicios.
- Mano de obra.
- Repuestos.
- Diagnóstico.
- Observaciones.

Pero el inventario se descuenta solo al cerrar y facturar.

---

## 13. Cierre de OT delegando en VentaService

### Decisión

Al cerrar una orden de trabajo, el sistema genera una venta delegando en `VentaService`.

### Justificación

`VentaService` ya contiene reglas probadas para:

- Crear venta.
- Descontar inventario.
- Aplicar FEFO.
- Registrar Kardex.
- Registrar pago si es contado.
- Generar cuenta por cobrar si es crédito.

Duplicar esta lógica en `OrdenTrabajoService` sería riesgoso.

### Beneficio

Se evita:

- Doble descuento de inventario.
- Doble registro de Kardex.
- Duplicación de reglas FEFO.
- Inconsistencias entre venta y orden de trabajo.

---

## 14. CSV como primera estrategia de exportación

### Decisión

La exportación inicial se implementó en CSV.

### Justificación

CSV permite generar reportes sin agregar dependencias externas.

Ventajas:

- Compatible con Excel.
- Ligero.
- Fácil de generar.
- Fácil de leer.
- Útil para análisis externo.

### Consideración futura

Excel `.xlsx` y PDF quedan como mejoras futuras porque requieren librerías adicionales y mayor complejidad de formato.

---

## 15. Dashboard general y dashboard gerencial

### Decisión

El sistema separa indicadores operativos y gerenciales.

### Dashboard general

Orientado a operación diaria:

- Ventas.
- Pagos.
- Alertas.
- Stock.
- Lotes.

### Dashboard gerencial

Orientado a análisis ejecutivo:

- Ventas del período.
- Utilidad estimada.
- Margen estimado.
- Inventario.
- Compras.
- Metas.

### Justificación

La operación diaria y la gerencia necesitan distintos niveles de información.

---

## 16. Respaldo y restauración de base de datos

### Decisión

Se agregó funcionalidad para crear y restaurar respaldos desde Configuración.

### Justificación

SQLite trabaja con un archivo local. Si el archivo se pierde, se pierden los datos.

### Beneficio

El usuario puede:

- Crear copias de seguridad.
- Restaurar una copia anterior.
- Proteger información operativa.

### Consideración

Después de restaurar, se recomienda cerrar y abrir nuevamente la aplicación.

---

## 17. Uso de pruebas automatizadas

### Decisión

El proyecto incluye pruebas automatizadas con Maven/JUnit.

### Justificación

Las pruebas permiten validar que cada incremento no rompa módulos existentes.

### Beneficio

El proyecto mantiene una suite verde que valida funcionalidades como:

- Autenticación.
- Inventario.
- Ventas.
- FEFO.
- Compras.
- Dashboard.
- Exportaciones.
- Vehículos.
- Órdenes de trabajo.
- Cierre de OT a venta.

---

## 18. Organización de documentación en `/docs`

### Decisión

La documentación complementaria se ubica en la carpeta `/docs`.

### Justificación

Permite separar el README principal de documentación técnica adicional.

### Documentos incluidos o planeados

- Guía de uso.
- Diagrama de arquitectura.
- Diagrama entidad-relación.
- Decisiones técnicas.
- FAQ.
- Capturas.
- Diagramas.

---

## 19. Decisiones pendientes o futuras

Algunas decisiones quedan para futuras versiones:

- Migrar de SQLite a PostgreSQL para multiusuario.
- Crear API REST.
- Crear versión web.
- Integrar facturación electrónica con DIAN.
- Agregar cuentas por pagar.
- Generar reportes PDF.
- Exportar Excel `.xlsx`.
- Implementar roles y permisos completos.
- Agregar tablero operativo de taller.
- Implementar historial avanzado por vehículo.

---

## 20. Resumen

Las decisiones técnicas del proyecto priorizan:

- Simplicidad inicial.
- Claridad arquitectónica.
- Trazabilidad.
- Operación offline.
- Bajo costo de despliegue.
- Evolución progresiva.
- Separación de responsabilidades.
- Protección de lógica crítica de inventario, ventas y cartera.

OPTI - GestorPyme Lite está diseñado como una base sólida para evolucionar desde una aplicación de escritorio local hacia una solución más completa de tipo ERP/CRM para pymes, logística, operación y talleres automotrices.
