-- =============================================================
-- OPTI - GestorPyme Lite
-- Script SQLite Inicial v0.2
-- Sistema Administrativo CRM/ERP Lite para Pymes
-- Responsable: Jean Sebastián González Mera
-- Fecha: 2026-06-12
-- Motor: SQLite | Arquitectura: Java Swing + Maven + SQLite (offline-first)
-- Fuente de verdad del modelo lógico: 05_OPTI_GestorPyme_Lite_Modelo_Base_Datos_v0.2
-- Enums canónicos (DEC-012). Reemplaza al Script v0.1 (archivado en _Historico_v0.1).
-- Notas de decisiones:
--   DEC-020 (Pendiente): valores monetarios en REAL; evaluar INTEGER (centavos) en v1.0.
--   DEC-018 (Propuesta): numero_venta consecutivo generado desde la capa service.
--   DEC-019 (Propuesta): inventario_stock es la fuente del disponible; lotes informativos.
--   DEC-023/025: las contraseñas se guardan como hash generado desde Java, nunca en texto plano.
-- =============================================================

PRAGMA foreign_keys = ON;

-- =============================================================
-- 1. usuarios
-- =============================================================
CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario      INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre_usuario  TEXT NOT NULL UNIQUE,
    nombre_completo TEXT,
    password_hash   TEXT NOT NULL,
    rol             TEXT NOT NULL DEFAULT 'ADMIN',
    estado          TEXT NOT NULL DEFAULT 'ACTIVO',
    fecha_creacion  TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    CHECK (rol IN ('ADMIN','VENDEDOR','INVENTARIO','CONSULTA')),
    CHECK (estado IN ('ACTIVO','INACTIVO'))
);

-- Usuario administrador inicial. El hash real debe generarse desde Java.
INSERT OR IGNORE INTO usuarios (id_usuario, nombre_usuario, nombre_completo, password_hash, rol, estado)
VALUES (1, 'admin', 'Administrador General', 'PENDIENTE_GENERAR_HASH_DESDE_JAVA', 'ADMIN', 'ACTIVO');

-- =============================================================
-- 2. empresa_configuracion
-- =============================================================
CREATE TABLE IF NOT EXISTS empresa_configuracion (
    id_empresa          INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre_empresa      TEXT NOT NULL,
    documento           TEXT,
    direccion           TEXT,
    telefono            TEXT,
    correo              TEXT,
    moneda              TEXT NOT NULL DEFAULT 'COP',
    mensaje_recibo      TEXT,
    ruta_logo           TEXT,
    fecha_actualizacion TEXT DEFAULT (datetime('now','localtime'))
);

INSERT OR IGNORE INTO empresa_configuracion (id_empresa, nombre_empresa, moneda, mensaje_recibo)
VALUES (1, 'Mi Empresa', 'COP', 'Gracias por su compra');

-- =============================================================
-- 3. terceros
-- =============================================================
CREATE TABLE IF NOT EXISTS terceros (
    id_tercero      INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo_tercero    TEXT NOT NULL,
    nombre          TEXT NOT NULL,
    documento       TEXT,
    telefono        TEXT,
    correo          TEXT,
    direccion       TEXT,
    estado          TEXT NOT NULL DEFAULT 'ACTIVO',
    observaciones   TEXT,
    fecha_creacion  TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    CHECK (tipo_tercero IN ('CLIENTE','PROSPECTO','PROVEEDOR')),
    CHECK (estado IN ('ACTIVO','INACTIVO'))
);

-- =============================================================
-- 4. categorias
-- =============================================================
CREATE TABLE IF NOT EXISTS categorias (
    id_categoria INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre       TEXT NOT NULL UNIQUE,
    descripcion  TEXT,
    estado       TEXT NOT NULL DEFAULT 'ACTIVO',
    CHECK (estado IN ('ACTIVO','INACTIVO'))
);

INSERT OR IGNORE INTO categorias (id_categoria, nombre, descripcion, estado) VALUES
    (1, 'General',   'Categoría general por defecto',     'ACTIVO'),
    (2, 'Servicios', 'Categoría inicial para servicios',  'ACTIVO');

-- =============================================================
-- 5. items
-- =============================================================
CREATE TABLE IF NOT EXISTS items (
    id_item             INTEGER PRIMARY KEY AUTOINCREMENT,
    codigo              TEXT UNIQUE,
    nombre              TEXT NOT NULL,
    id_categoria        INTEGER,
    tipo_item           TEXT NOT NULL,
    precio_compra       REAL NOT NULL DEFAULT 0,
    precio_venta        REAL NOT NULL DEFAULT 0,
    controla_inventario INTEGER NOT NULL DEFAULT 0,
    stock_minimo        REAL NOT NULL DEFAULT 0,
    estado              TEXT NOT NULL DEFAULT 'ACTIVO',
    fecha_creacion      TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria),
    CHECK (tipo_item IN ('PRODUCTO','SERVICIO','INSUMO','REPUESTO','MANO_OBRA')),
    CHECK (controla_inventario IN (0,1)),
    CHECK (precio_compra >= 0),
    CHECK (precio_venta >= 0),
    CHECK (stock_minimo >= 0),
    CHECK (estado IN ('ACTIVO','INACTIVO'))
);

-- =============================================================
-- 6. bodegas
-- =============================================================
CREATE TABLE IF NOT EXISTS bodegas (
    id_bodega      INTEGER PRIMARY KEY AUTOINCREMENT,
    nombre         TEXT NOT NULL UNIQUE,
    ubicacion      TEXT,
    estado         TEXT NOT NULL DEFAULT 'ACTIVO',
    fecha_creacion TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    CHECK (estado IN ('ACTIVO','INACTIVO'))
);

INSERT OR IGNORE INTO bodegas (id_bodega, nombre, ubicacion, estado)
VALUES (1, 'Bodega Principal', 'Ubicación principal del negocio', 'ACTIVO');

-- =============================================================
-- 7. lotes
-- =============================================================
CREATE TABLE IF NOT EXISTS lotes (
    id_lote           INTEGER PRIMARY KEY AUTOINCREMENT,
    id_item           INTEGER NOT NULL,
    id_bodega         INTEGER,
    numero_lote       TEXT NOT NULL,
    cantidad_inicial  REAL NOT NULL DEFAULT 0,
    fecha_ingreso     TEXT NOT NULL DEFAULT (date('now','localtime')),
    fecha_vencimiento TEXT,
    estado            TEXT NOT NULL DEFAULT 'ACTIVO',
    FOREIGN KEY (id_item)   REFERENCES items(id_item),
    FOREIGN KEY (id_bodega) REFERENCES bodegas(id_bodega),
    CHECK (cantidad_inicial >= 0),
    CHECK (estado IN ('ACTIVO','AGOTADO','VENCIDO','INACTIVO'))
);

-- =============================================================
-- 8. inventario_stock  (fuente del disponible - DEC-019)
-- =============================================================
CREATE TABLE IF NOT EXISTS inventario_stock (
    id_stock            INTEGER PRIMARY KEY AUTOINCREMENT,
    id_item             INTEGER NOT NULL,
    id_bodega           INTEGER NOT NULL,
    cantidad            REAL NOT NULL DEFAULT 0,
    fecha_actualizacion TEXT DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (id_item)   REFERENCES items(id_item),
    FOREIGN KEY (id_bodega) REFERENCES bodegas(id_bodega),
    UNIQUE (id_item, id_bodega),
    CHECK (cantidad >= 0)
);

-- =============================================================
-- 9. ventas
-- =============================================================
CREATE TABLE IF NOT EXISTS ventas (
    id_venta      INTEGER PRIMARY KEY AUTOINCREMENT,
    numero_venta  TEXT NOT NULL UNIQUE,
    id_tercero    INTEGER,
    fecha         TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    subtotal      REAL NOT NULL DEFAULT 0,
    descuento     REAL NOT NULL DEFAULT 0,
    total         REAL NOT NULL DEFAULT 0,
    estado        TEXT NOT NULL DEFAULT 'BORRADOR',
    id_usuario    INTEGER,
    observaciones TEXT,
    FOREIGN KEY (id_tercero) REFERENCES terceros(id_tercero),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    CHECK (subtotal >= 0),
    CHECK (descuento >= 0),
    CHECK (total >= 0),
    CHECK (estado IN ('BORRADOR','CONFIRMADA','PAGADA','PENDIENTE_PAGO','ANULADA'))
);

-- =============================================================
-- 10. venta_detalles
-- =============================================================
CREATE TABLE IF NOT EXISTS venta_detalles (
    id_detalle      INTEGER PRIMARY KEY AUTOINCREMENT,
    id_venta        INTEGER NOT NULL,
    id_item         INTEGER NOT NULL,
    cantidad        REAL NOT NULL,
    precio_unitario REAL NOT NULL,
    descuento_linea REAL NOT NULL DEFAULT 0,
    subtotal_linea  REAL NOT NULL,
    FOREIGN KEY (id_venta) REFERENCES ventas(id_venta),
    FOREIGN KEY (id_item)  REFERENCES items(id_item),
    CHECK (cantidad > 0),
    CHECK (precio_unitario >= 0),
    CHECK (descuento_linea >= 0),
    CHECK (subtotal_linea >= 0)
);

-- =============================================================
-- 11. pagos
-- =============================================================
CREATE TABLE IF NOT EXISTS pagos (
    id_pago       INTEGER PRIMARY KEY AUTOINCREMENT,
    id_venta      INTEGER NOT NULL,
    medio_pago    TEXT NOT NULL,
    valor         REAL NOT NULL,
    fecha         TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    referencia    TEXT,
    observaciones TEXT,
    FOREIGN KEY (id_venta) REFERENCES ventas(id_venta),
    CHECK (medio_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA','CREDITO','MIXTO','OTRO')),
    CHECK (valor > 0)
);

-- =============================================================
-- 12. cuentas_por_cobrar
-- =============================================================
CREATE TABLE IF NOT EXISTS cuentas_por_cobrar (
    id_cuenta         INTEGER PRIMARY KEY AUTOINCREMENT,
    id_venta          INTEGER NOT NULL UNIQUE,
    id_tercero        INTEGER NOT NULL,
    valor_total       REAL NOT NULL,
    valor_pagado      REAL NOT NULL DEFAULT 0,
    saldo_pendiente   REAL NOT NULL,
    fecha_vencimiento TEXT,
    estado            TEXT NOT NULL DEFAULT 'PENDIENTE',
    FOREIGN KEY (id_venta)   REFERENCES ventas(id_venta),
    FOREIGN KEY (id_tercero) REFERENCES terceros(id_tercero),
    CHECK (valor_total >= 0),
    CHECK (valor_pagado >= 0),
    CHECK (saldo_pendiente >= 0),
    CHECK (estado IN ('PENDIENTE','ABONADA','PAGADA','VENCIDA','CANCELADA'))
);

-- =============================================================
-- 13. abonos_cuenta
-- =============================================================
CREATE TABLE IF NOT EXISTS abonos_cuenta (
    id_abono      INTEGER PRIMARY KEY AUTOINCREMENT,
    id_cuenta     INTEGER NOT NULL,
    valor         REAL NOT NULL,
    fecha         TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    medio_pago    TEXT NOT NULL,
    observaciones TEXT,
    FOREIGN KEY (id_cuenta) REFERENCES cuentas_por_cobrar(id_cuenta),
    CHECK (valor > 0),
    CHECK (medio_pago IN ('EFECTIVO','TRANSFERENCIA','TARJETA','OTRO'))
);

-- =============================================================
-- 14. inventario_movimientos  (Kardex - DEC-017)
-- =============================================================
CREATE TABLE IF NOT EXISTS inventario_movimientos (
    id_movimiento   INTEGER PRIMARY KEY AUTOINCREMENT,
    id_item         INTEGER NOT NULL,
    id_bodega       INTEGER NOT NULL,
    tipo_movimiento TEXT NOT NULL,
    cantidad        REAL NOT NULL,
    id_lote         INTEGER,
    motivo          TEXT,
    id_usuario      INTEGER,
    id_venta        INTEGER,
    fecha           TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    FOREIGN KEY (id_item)    REFERENCES items(id_item),
    FOREIGN KEY (id_bodega)  REFERENCES bodegas(id_bodega),
    FOREIGN KEY (id_lote)    REFERENCES lotes(id_lote),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    FOREIGN KEY (id_venta)   REFERENCES ventas(id_venta),
    CHECK (tipo_movimiento IN ('ENTRADA','SALIDA','AJUSTE_POSITIVO','AJUSTE_NEGATIVO','TRASLADO','SALIDA_VENTA')),
    CHECK (cantidad > 0)
);

-- =============================================================
-- 15. crm_seguimientos
-- =============================================================
CREATE TABLE IF NOT EXISTS crm_seguimientos (
    id_seguimiento INTEGER PRIMARY KEY AUTOINCREMENT,
    id_tercero     INTEGER NOT NULL,
    tipo           TEXT NOT NULL,
    descripcion    TEXT NOT NULL,
    fecha          TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    estado         TEXT NOT NULL DEFAULT 'REGISTRADO',
    id_usuario     INTEGER,
    FOREIGN KEY (id_tercero) REFERENCES terceros(id_tercero),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    CHECK (tipo IN ('LLAMADA','NOTA','VISITA','WHATSAPP','CORREO','OTRO')),
    CHECK (estado IN ('REGISTRADO','CERRADO','PENDIENTE'))
);

-- =============================================================
-- 16. exportaciones_log
-- =============================================================
CREATE TABLE IF NOT EXISTS exportaciones_log (
    id_exportacion INTEGER PRIMARY KEY AUTOINCREMENT,
    tipo           TEXT NOT NULL,
    ruta_archivo   TEXT NOT NULL,
    fecha          TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    id_usuario     INTEGER,
    estado         TEXT NOT NULL DEFAULT 'GENERADO',
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario),
    CHECK (tipo IN ('CLIENTES','PROSPECTOS','ITEMS','VENTAS','PAGOS','INVENTARIO','KARDEX','CARTERA','CRM','OTRO')),
    CHECK (estado IN ('GENERADO','ERROR'))
);

-- =============================================================
-- ÍNDICES RECOMENDADOS
-- =============================================================
CREATE INDEX IF NOT EXISTS idx_terceros_nombre      ON terceros(nombre);
CREATE INDEX IF NOT EXISTS idx_terceros_documento   ON terceros(documento);
CREATE INDEX IF NOT EXISTS idx_terceros_telefono    ON terceros(telefono);
CREATE INDEX IF NOT EXISTS idx_terceros_tipo        ON terceros(tipo_tercero);

CREATE INDEX IF NOT EXISTS idx_items_nombre         ON items(nombre);
CREATE INDEX IF NOT EXISTS idx_items_tipo           ON items(tipo_item);
CREATE INDEX IF NOT EXISTS idx_items_categoria      ON items(id_categoria);

CREATE INDEX IF NOT EXISTS idx_stock_item           ON inventario_stock(id_item);
CREATE INDEX IF NOT EXISTS idx_stock_bodega         ON inventario_stock(id_bodega);

CREATE INDEX IF NOT EXISTS idx_mov_item             ON inventario_movimientos(id_item);
CREATE INDEX IF NOT EXISTS idx_mov_fecha            ON inventario_movimientos(fecha);
CREATE INDEX IF NOT EXISTS idx_mov_tipo             ON inventario_movimientos(tipo_movimiento);

CREATE INDEX IF NOT EXISTS idx_lotes_item           ON lotes(id_item);
CREATE INDEX IF NOT EXISTS idx_lotes_vencimiento    ON lotes(fecha_vencimiento);

CREATE INDEX IF NOT EXISTS idx_ventas_fecha         ON ventas(fecha);
CREATE INDEX IF NOT EXISTS idx_ventas_tercero       ON ventas(id_tercero);
CREATE INDEX IF NOT EXISTS idx_ventas_estado        ON ventas(estado);

CREATE INDEX IF NOT EXISTS idx_detalles_venta       ON venta_detalles(id_venta);
CREATE INDEX IF NOT EXISTS idx_detalles_item        ON venta_detalles(id_item);

CREATE INDEX IF NOT EXISTS idx_pagos_venta          ON pagos(id_venta);
CREATE INDEX IF NOT EXISTS idx_pagos_fecha          ON pagos(fecha);

CREATE INDEX IF NOT EXISTS idx_cuentas_tercero      ON cuentas_por_cobrar(id_tercero);
CREATE INDEX IF NOT EXISTS idx_cuentas_estado       ON cuentas_por_cobrar(estado);
CREATE INDEX IF NOT EXISTS idx_cuentas_vencimiento  ON cuentas_por_cobrar(fecha_vencimiento);

CREATE INDEX IF NOT EXISTS idx_abonos_cuenta        ON abonos_cuenta(id_cuenta);
CREATE INDEX IF NOT EXISTS idx_crm_tercero          ON crm_seguimientos(id_tercero);
CREATE INDEX IF NOT EXISTS idx_crm_fecha            ON crm_seguimientos(fecha);

-- =============================================================
-- VISTAS ÚTILES PARA DASHBOARD / CONSULTA
-- =============================================================
CREATE VIEW IF NOT EXISTS vw_stock_bajo AS
SELECT i.id_item, i.codigo, i.nombre, i.tipo_item,
       b.nombre AS bodega, s.cantidad, i.stock_minimo
FROM inventario_stock s
INNER JOIN items   i ON i.id_item   = s.id_item
INNER JOIN bodegas b ON b.id_bodega = s.id_bodega
WHERE i.controla_inventario = 1
  AND i.estado = 'ACTIVO'
  AND s.cantidad <= i.stock_minimo;

CREATE VIEW IF NOT EXISTS vw_cuentas_pendientes AS
SELECT c.id_cuenta, c.id_venta, t.nombre AS cliente,
       c.valor_total, c.valor_pagado, c.saldo_pendiente,
       c.fecha_vencimiento, c.estado
FROM cuentas_por_cobrar c
INNER JOIN terceros t ON t.id_tercero = c.id_tercero
WHERE c.estado IN ('PENDIENTE','ABONADA','VENCIDA');

CREATE VIEW IF NOT EXISTS vw_ventas_resumen AS
SELECT v.id_venta, v.numero_venta, v.fecha,
       COALESCE(t.nombre, 'Cliente no registrado') AS cliente,
       v.subtotal, v.descuento, v.total, v.estado
FROM ventas v
LEFT JOIN terceros t ON t.id_tercero = v.id_tercero;

-- =============================================================
-- FIN DEL SCRIPT v0.2
-- =============================================================
