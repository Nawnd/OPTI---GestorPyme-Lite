# Preguntas frecuentes — OPTI - GestorPyme Lite

Este documento reúne preguntas frecuentes sobre **OPTI - GestorPyme Lite**, su propósito, funcionamiento, alcance actual, arquitectura, instalación, base de datos y evolución futura.

El objetivo es ayudar a docentes, reclutadores, desarrolladores y usuarios interesados a entender rápidamente el proyecto.

---

## 1. ¿Qué es OPTI - GestorPyme Lite?

**OPTI - GestorPyme Lite** es una aplicación administrativa tipo **ERP/CRM Lite** desarrollada en Java para pequeñas y medianas empresas.

Permite gestionar procesos como:

- Clientes y prospectos.
- Proveedores.
- Productos, servicios, repuestos e insumos.
- Bodegas e inventario.
- Compras y recepción de mercancía.
- Ventas de contado y crédito.
- Pagos y cuentas por cobrar.
- Kardex y lotes.
- Dashboards.
- Exportaciones CSV.
- Vehículos y órdenes de trabajo para talleres automotrices.

---

## 2. ¿Cuál es el objetivo principal del proyecto?

El objetivo principal es construir un sistema administrativo funcional, modular y documentado que permita aplicar conocimientos de:

- Java.
- Programación Orientada a Objetos.
- Arquitectura MVC.
- SQL.
- SQLite.
- Java Swing.
- Gestión de inventario.
- Procesos ERP/CRM.
- Documentación técnica.
- Pruebas automatizadas.

También funciona como proyecto de portafolio profesional para demostrar habilidades en desarrollo de software, bases de datos y análisis de procesos empresariales.

---

## 3. ¿El sistema está terminado?

El sistema se encuentra en una etapa de **desarrollo avanzado**.

Cuenta con múltiples módulos funcionales implementados y probados, pero todavía tiene mejoras planeadas, como:

- Cuentas por pagar.
- Dashboard operativo de taller.
- Exportaciones avanzadas.
- Reportes PDF.
- Exportación Excel `.xlsx`.
- Roles y permisos completos.
- Facturación electrónica futura.
- Versión web o multi-PC.

Por esta razón, el proyecto debe entenderse como una solución funcional en evolución.

---

## 4. ¿Por qué se usó Java?

Java fue elegido porque permite construir aplicaciones robustas, orientadas a objetos y con buena separación por capas.

Además, Java permite demostrar conocimientos importantes para roles junior como:

- Modelado de entidades.
- Manejo de clases y paquetes.
- Validaciones de negocio.
- JDBC.
- Pruebas automatizadas.
- Arquitectura MVC.
- Organización de código empresarial.

---

## 5. ¿Por qué se usó Java Swing?

Java Swing fue usado porque permite construir una aplicación de escritorio sin depender de navegador, servidor o conexión constante a internet.

Esto encaja con el enfoque inicial del proyecto:

```text
Aplicación local + SQLite + operación offline-first
```

Swing también facilita crear formularios, tablas, diálogos y dashboards para una aplicación administrativa.

---

## 6. ¿Por qué se usó SQLite?

SQLite fue elegido porque permite trabajar con una base de datos local en archivo, sin necesidad de instalar un servidor externo.

Ventajas para este proyecto:

- Fácil despliegue local.
- Funciona sin internet.
- No requiere servidor de base de datos.
- Es suficiente para una primera versión de escritorio.
- Facilita el desarrollo académico y de portafolio.

---

## 7. ¿El sistema funciona sin internet?

Sí. El sistema fue diseñado como una aplicación **offline-first**.

Esto significa que puede funcionar localmente usando SQLite como base de datos, sin depender de conexión a internet para sus operaciones principales.

---

## 8. ¿Se puede usar en varios computadores al mismo tiempo?

En su estado actual, el sistema está pensado para uso local en un solo equipo.

Una versión multi-PC requeriría una evolución técnica, por ejemplo:

- Migrar de SQLite a PostgreSQL o un motor cliente-servidor.
- Crear una API REST.
- Centralizar la base de datos.
- Implementar control de concurrencia.
- Agregar autenticación y permisos más robustos.

---

## 9. ¿Dónde se guarda la información?

La información se guarda en una base de datos SQLite local.

La ruta esperada del archivo de base de datos es:

```text
data/gestorpyme.db
```

Este archivo no debe subirse a GitHub porque puede contener datos reales o de prueba.

---

## 10. ¿Qué pasa si se borra la base de datos?

Si se borra `data/gestorpyme.db`, se pierde la información almacenada localmente.

Por eso el sistema incluye funcionalidad de:

- Crear respaldo.
- Restaurar respaldo.

Se recomienda realizar copias de seguridad periódicas, especialmente antes de cambios importantes o pruebas extensas.

---

## 11. ¿El proyecto incluye autenticación?

Sí. El sistema incluye login y autenticación básica de usuarios.

El login permite controlar el acceso inicial a la aplicación. La evolución futura puede incluir permisos por módulo, roles más detallados y seguridad avanzada.

---

## 12. ¿El sistema tiene roles y permisos?

El proyecto contempla la estructura base para roles, pero la aplicación de permisos avanzados por pantalla o acción queda como mejora futura.

En versiones posteriores se podría implementar:

- Administrador.
- Vendedor.
- Auxiliar de inventario.
- Técnico de taller.
- Gerencia.
- Solo lectura.

---

## 13. ¿Qué módulos están implementados?

Entre los módulos implementados se encuentran:

- Login.
- Configuración de empresa.
- Clientes y prospectos.
- Proveedores.
- CRM.
- Productos y servicios.
- Bodegas.
- Inventario.
- Kardex.
- Lotes.
- Compras.
- Recepción de mercancía.
- Ventas.
- Pagos.
- Cuentas por cobrar.
- Dashboard general.
- Dashboard gerencial.
- Exportaciones CSV.
- Respaldo/restauración.
- Vehículos.
- Órdenes de trabajo.
- Cierre de orden de trabajo a venta.

---

## 14. ¿Qué es el Kardex?

El Kardex es el historial de movimientos de inventario.

Permite revisar entradas, salidas y ajustes asociados a productos.

Ejemplos de movimientos:

- Entrada por compra.
- Salida por venta.
- Ajuste de inventario.
- Salida generada al cerrar una orden de trabajo.

El Kardex ayuda a mantener trazabilidad y auditoría operativa.

---

## 15. ¿Qué es FEFO?

FEFO significa:

```text
First Expired, First Out
```

Es decir:

```text
Primero vence, primero sale
```

Esta regla permite consumir primero los lotes con fecha de vencimiento más cercana.

Es útil para productos con vencimiento, insumos, lubricantes, alimentos, medicamentos o repuestos que requieran trazabilidad por lote.

---

## 16. ¿La orden de trabajo descuenta inventario apenas se crea?

No.

La orden de trabajo funciona primero como documento operativo.

Esto significa que permite registrar:

- Cliente.
- Vehículo.
- Diagnóstico.
- Servicios.
- Repuestos planeados.
- Observaciones.

El inventario se descuenta únicamente cuando la OT se cierra y se factura.

---

## 17. ¿Qué pasa cuando se cierra una orden de trabajo?

Cuando se cierra y factura una orden de trabajo, el sistema:

- Crea una venta.
- Genera detalle de venta.
- Descuenta inventario de repuestos.
- Aplica FEFO si hay lotes.
- Registra Kardex de salida.
- Registra pago si es venta de contado.
- Genera cuenta por cobrar si es venta a crédito.
- Marca la OT como entregada.
- Relaciona la OT con la venta generada.

---

## 18. ¿El sistema maneja ventas a crédito?

Sí. El sistema permite registrar ventas a crédito.

Cuando una venta es a crédito, se genera una cuenta por cobrar con saldo pendiente.

Luego se pueden registrar abonos para disminuir el saldo.

---

## 19. ¿El sistema maneja pagos?

Sí. El sistema incluye módulo de pagos.

Los pagos pueden asociarse a ventas de contado o a procesos de cartera, según el flujo implementado.

---

## 20. ¿El sistema maneja cuentas por pagar?

Actualmente el sistema maneja cuentas por cobrar, pero las **cuentas por pagar** están planeadas como una mejora futura.

Ese módulo permitiría controlar:

- Obligaciones con proveedores.
- Facturas de compra pendientes.
- Abonos a proveedores.
- Estados de pago.
- Vencimientos.

---

## 21. ¿El sistema tiene facturación electrónica?

No. La facturación electrónica no está implementada en esta versión.

Para Colombia, una versión futura debería estudiar integración con la DIAN y cumplir los requisitos legales correspondientes.

---

## 22. ¿El sistema genera reportes?

Sí. El sistema genera exportaciones en formato CSV.

Entre los reportes posibles se encuentran:

- Clientes.
- Inventario.
- Kardex.
- Ventas.
- Pagos.
- Cartera.
- Compras.
- Recepciones.
- Dashboard gerencial.

---

## 23. ¿Por qué CSV y no Excel o PDF?

CSV fue elegido como primera estrategia porque:

- No requiere dependencias externas.
- Es liviano.
- Se puede abrir en Excel.
- Es fácil de generar.
- Es útil para análisis de datos.

Excel `.xlsx` y PDF están considerados como mejoras futuras.

---

## 24. ¿Qué significa que la utilidad del dashboard sea estimada?

Significa que los cálculos de utilidad o margen se usan como indicador de gestión, pero no reemplazan un sistema contable formal.

Para una utilidad contable exacta se requeriría implementar costeo más avanzado, como promedio ponderado u otros métodos contables.

---

## 25. ¿El sistema sirve para talleres automotrices?

Sí. El sistema incluye funcionalidades orientadas a talleres automotrices:

- Registro de vehículos por cliente.
- Órdenes de trabajo.
- Servicios y mano de obra.
- Repuestos inventariables.
- Cierre de OT a venta.
- Relación entre cliente, vehículo, venta, inventario y cartera.

---

## 26. ¿El sistema sirve para empresas de logística u operación?

Sí, especialmente en procesos como:

- Control de inventario.
- Bodegas.
- Compras.
- Recepción de mercancía.
- Kardex.
- Lotes.
- Reabastecimiento.
- Dashboard operativo.
- Exportaciones para análisis.

Aun así, funcionalidades avanzadas como rutas logísticas, despacho, transporte o picking no están implementadas todavía.

---

## 27. ¿Qué archivos no deben subirse a GitHub?

No se deben subir archivos generados o sensibles como:

```text
target/
data/gestorpyme.db
*.db
*.sqlite
*.log
```

La base de datos local puede contener información de prueba o datos reales, por lo que debe mantenerse fuera del repositorio.

---

## 28. ¿Cómo se ejecuta el proyecto?

Desde la raíz del proyecto:

```bash
mvn clean compile
mvn test
mvn exec:java
```

Si se ejecutó `mvn clean`, es recomendable compilar antes de ejecutar.

---

## 29. ¿Qué hago si `mvn exec:java` no encuentra la clase principal?

Si aparece un error como:

```text
ClassNotFoundException: com.gestorpyme.app.Main
```

se debe compilar primero:

```bash
mvn clean compile
mvn exec:java
```

También se debe verificar que exista la clase principal en:

```text
src/main/java/com/gestorpyme/app/Main.java
```

---

## 30. ¿Qué demuestra este proyecto en un portafolio profesional?

Este proyecto demuestra habilidades en:

- Java.
- POO.
- MVC.
- SQL.
- SQLite.
- Maven.
- Java Swing.
- Diseño de interfaces.
- Lógica de negocio.
- Gestión de inventario.
- Procesos ERP/CRM.
- Pruebas automatizadas.
- Documentación técnica.
- Organización de código.
- Análisis de procesos operativos.

---

## 31. ¿Cuál es el estado actual del proyecto?

El proyecto se encuentra en desarrollo avanzado.

Tiene múltiples módulos implementados y una base sólida para continuar evolucionando.

Las mejoras futuras incluyen:

- Cuentas por pagar.
- Dashboard operativo de taller.
- Historial avanzado por vehículo.
- Reportes PDF.
- Exportación Excel.
- Facturación electrónica.
- API REST.
- Versión web.
- Multi-PC.
- PostgreSQL.

---

## 32. ¿Cómo puedo contribuir o continuar el proyecto?

Una forma recomendada de continuar es seguir una metodología por pasos:

```text
Diagnóstico -> Diseño -> Implementación -> Pruebas -> Documentación -> Validación
```

Antes de agregar una funcionalidad nueva, se recomienda revisar:

- Arquitectura actual.
- Dependencias entre módulos.
- Riesgos sobre inventario, ventas y pagos.
- Pruebas existentes.
- Documentación técnica.

---

## 33. ¿Cuál es el próximo módulo recomendado?

Algunas mejoras recomendadas son:

- Cuentas por pagar.
- Historial por vehículo.
- Exportación de órdenes de trabajo.
- Dashboard operativo de taller.
- Mejoras visuales en Configuración.
- Reportes avanzados.
- Roles y permisos.

La prioridad debe definirse según necesidades reales de operación, logística y talleres automotrices.
