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