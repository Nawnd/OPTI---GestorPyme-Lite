-- ============================================================================
-- OPTI - GestorPyme Lite
-- Migracion v0.7.2 - Clasificacion Productos / Servicios (Categoria -> Subtipo)
-- ----------------------------------------------------------------------------
-- IDEMPOTENTE Y SEGURA. No borra ni recrea tablas con datos. No reinicia la base.
--   Parte 1 (estructura): crea la tabla 'subtipos' y su indice (IF NOT EXISTS).
--   La columna items.id_subtipo se agrega desde DatabaseInitializer SOLO si no
--   existe (SQLite no admite ADD COLUMN IF NOT EXISTS).
--   Parte 2 (semillas): inserta categorias base y subtipos con INSERT OR IGNORE,
--   apoyandose en UNIQUE(id_categoria, nombre); no duplica en re-ejecuciones.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Parte 1: estructura
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS subtipos (
    id_subtipo   INTEGER PRIMARY KEY AUTOINCREMENT,
    id_categoria INTEGER NOT NULL,
    nombre       TEXT NOT NULL,
    estado       TEXT NOT NULL DEFAULT 'ACTIVO',
    FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria),
    CHECK (estado IN ('ACTIVO','INACTIVO')),
    UNIQUE (id_categoria, nombre)
);

CREATE INDEX IF NOT EXISTS idx_subtipos_categoria ON subtipos(id_categoria);

-- @@PARTE2_SEMILLAS@@

-- ----------------------------------------------------------------------------
-- Parte 2: semillas idempotentes (categorias base + subtipos)
-- ----------------------------------------------------------------------------
INSERT OR IGNORE INTO categorias (nombre, descripcion, estado) VALUES ('Repuestos', 'Repuestos y partes', 'ACTIVO');
INSERT OR IGNORE INTO categorias (nombre, descripcion, estado) VALUES ('Insumos', 'Insumos y materiales', 'ACTIVO');
INSERT OR IGNORE INTO categorias (nombre, descripcion, estado) VALUES ('Servicios', 'Servicios y mano de obra', 'ACTIVO');

-- Subtipos de Repuestos
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Electrico', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Mecanico', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Carroceria', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Iluminacion', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Suspension', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Frenos', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Motor', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Transmision', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Repuestos';

-- Subtipos de Insumos
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Consumible', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Insumos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Lubricante', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Insumos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Material', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Insumos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Herramienta', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Insumos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Quimico', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Insumos';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Limpieza', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Insumos';

-- Subtipos de Servicios
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Mano de obra', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Servicios';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Diagnostico', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Servicios';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Scanner', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Servicios';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Instalacion', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Servicios';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Reparacion', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Servicios';
INSERT OR IGNORE INTO subtipos (id_categoria, nombre, estado) SELECT c.id_categoria, 'Mantenimiento', 'ACTIVO' FROM categorias c WHERE c.nombre = 'Servicios';
