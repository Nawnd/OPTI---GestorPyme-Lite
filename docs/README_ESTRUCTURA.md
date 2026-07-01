# Estructura del proyecto — OPTI - GestorPyme Lite

Esta es la **base técnica inicial sin código**. Solo carpetas y archivos de soporte.
Arquitectura: monolito modular **offline-first** por capas. Paquete raíz: `com.gestorpyme`.

## Carpetas raíz

| Carpeta | Para qué sirve |
|---|---|
| `pom.xml` | Configuración Maven del proyecto (Java 17, dependencias). |
| `README.md` | Descripción, requisitos y comandos del proyecto. |
| `.gitignore` | Archivos/carpetas que Git no debe versionar. |
| `data/` | Base de datos SQLite local en ejecución (`*.db`). No se versiona. |
| `database/` | Script SQL oficial del esquema (fuente para crear la BD). |
| `exports/` | Archivos CSV exportados, organizados por módulo. No se versiona. |
| `logs/` | Registros de la aplicación (`*.log`). No se versiona. |
| `docs/` | Documentación técnica del proyecto. |
| `assets/` | Recursos de diseño fuera del classpath (imágenes, íconos, fuentes). |
| `src/` | Código fuente Maven (`main` y `test`). |

## `exports/` (subcarpetas)

`clientes/`, `prospectos/`, `productos/`, `inventario/`, `ventas/`, `pagos/`, `cartera/` —
destino de los CSV de cada módulo.

## `src/main/java/com/gestorpyme/` (capas)

| Paquete | Responsabilidad |
|---|---|
| `app/` | Punto de entrada / arranque de la aplicación (futura clase `Main`). |
| `config/` | Configuración de la aplicación. |
| `controller/` | Coordina eventos de la UI y llama a los servicios (MVC/MVP en la capa de interfaz). |
| `domain/enums/` | Enumeraciones canónicas del dominio. |
| `domain/exception/` | Excepciones propias del negocio. |
| `domain/model/` | Modelos/entidades del dominio (no dependen de Swing). |
| `infrastructure/database/` | Conexión y acceso técnico a SQLite (JDBC). |
| `infrastructure/export/` | Exportación CSV. |
| `infrastructure/backup/` | Respaldos. |
| `infrastructure/sync/` | Sincronización (futuro). |
| `repository/` | Encapsula JDBC/SQL (acceso a datos). |
| `service/` | Reglas de negocio, validaciones y transacciones. |
| `util/` | Utilidades transversales. |
| `view/` | Vistas Swing (no ejecutan SQL). |

### `view/` (subcarpetas por módulo)

`components/` (componentes reutilizables), `dashboard/`, `empresa/`, `clientes/`,
`productos/`, `inventario/`, `ventas/`, `pagos/`, `reportes/`, `login/`.

## `src/main/resources/` (recursos del classpath)

| Carpeta | Contenido |
|---|---|
| `database/` | Copia del script SQL accesible desde la aplicación. |
| `images/` | Imágenes de la aplicación. |
| `icons/` | Íconos (PNG para Swing). |
| `fonts/` | Tipografías (SF Pro Display; fallback Segoe UI). |
| `styles/` | Definiciones de estilo/paleta de la UI. |

## `src/test/java/com/gestorpyme/`

Espacio para pruebas unitarias (futuro).

---

**Reglas de capa (recordatorio):** las vistas no ejecutan SQL · los controladores coordinan y llaman servicios · los servicios contienen reglas/validaciones/transacciones · los repositorios encapsulan JDBC/SQL · los modelos no dependen de Swing · `PRAGMA foreign_keys = ON` en cada conexión.
