-- ============================================================================
-- 06_OPTI_GestorPyme_Lite_Migracion_Compras_v1.0.sql   (Paso B)
-- Migracion ADICIONAL al esquema v0.2 (no lo reemplaza).
-- La aplica DatabaseInitializer de forma idempotente:
--   * Parte 1: se ejecuta si la tabla 'ordenes_compra' no existe.
--   * Parte 2: se ejecuta solo si inventario_movimientos aun no admite 'ENTRADA_COMPRA'.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- PARTE 1: tablas nuevas, indices y vista (idempotente con IF NOT EXISTS).
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ordenes_compra (
    id_orden            INTEGER PRIMARY KEY AUTOINCREMENT,
    numero_orden        TEXT NOT NULL UNIQUE,
    id_proveedor        INTEGER NOT NULL,
    fecha_orden         TEXT NOT NULL,
    fecha_estimada      TEXT,
    estado              TEXT NOT NULL DEFAULT 'BORRADOR',
    observaciones       TEXT,
    subtotal            REAL NOT NULL DEFAULT 0,
    total               REAL NOT NULL DEFAULT 0,
    fecha_creacion      TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    fecha_actualizacion TEXT,
    FOREIGN KEY (id_proveedor) REFERENCES terceros(id_tercero),
    CHECK (estado IN ('BORRADOR','EMITIDA','PARCIALMENTE_RECIBIDA','RECIBIDA','CANCELADA'))
);

CREATE TABLE IF NOT EXISTS ordenes_compra_detalles (
    id_detalle          INTEGER PRIMARY KEY AUTOINCREMENT,
    id_orden            INTEGER NOT NULL,
    id_item             INTEGER NOT NULL,
    id_bodega_destino   INTEGER,
    cantidad_solicitada REAL NOT NULL,
    cantidad_recibida   REAL NOT NULL DEFAULT 0,
    precio_unitario     REAL NOT NULL DEFAULT 0,
    subtotal            REAL NOT NULL DEFAULT 0,
    FOREIGN KEY (id_orden) REFERENCES ordenes_compra(id_orden),
    FOREIGN KEY (id_item)  REFERENCES items(id_item),
    FOREIGN KEY (id_bodega_destino) REFERENCES bodegas(id_bodega),
    CHECK (cantidad_solicitada > 0),
    CHECK (cantidad_recibida >= 0),
    CHECK (precio_unitario >= 0)
);

CREATE TABLE IF NOT EXISTS recepciones_mercancia (
    id_recepcion     INTEGER PRIMARY KEY AUTOINCREMENT,
    numero_recepcion TEXT NOT NULL UNIQUE,
    id_orden         INTEGER NOT NULL,
    fecha            TEXT NOT NULL DEFAULT (datetime('now','localtime')),
    observaciones    TEXT,
    id_usuario       INTEGER,
    FOREIGN KEY (id_orden)   REFERENCES ordenes_compra(id_orden),
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario)
);

CREATE TABLE IF NOT EXISTS recepciones_detalles (
    id_detalle_rec    INTEGER PRIMARY KEY AUTOINCREMENT,
    id_recepcion      INTEGER NOT NULL,
    id_detalle_oc     INTEGER NOT NULL,
    id_item           INTEGER NOT NULL,
    id_bodega         INTEGER NOT NULL,
    cantidad_recibida REAL NOT NULL,
    id_lote           INTEGER,
    FOREIGN KEY (id_recepcion)  REFERENCES recepciones_mercancia(id_recepcion),
    FOREIGN KEY (id_detalle_oc) REFERENCES ordenes_compra_detalles(id_detalle),
    FOREIGN KEY (id_item)       REFERENCES items(id_item),
    FOREIGN KEY (id_bodega)     REFERENCES bodegas(id_bodega),
    FOREIGN KEY (id_lote)       REFERENCES lotes(id_lote),
    CHECK (cantidad_recibida > 0)
);

CREATE INDEX IF NOT EXISTS idx_oc_estado ON ordenes_compra(estado);
CREATE INDEX IF NOT EXISTS idx_ocd_orden ON ordenes_compra_detalles(id_orden);
CREATE INDEX IF NOT EXISTS idx_ocd_item ON ordenes_compra_detalles(id_item);
CREATE INDEX IF NOT EXISTS idx_rec_orden ON recepciones_mercancia(id_orden);
CREATE INDEX IF NOT EXISTS idx_recd_recepcion ON recepciones_detalles(id_recepcion);

CREATE VIEW IF NOT EXISTS vw_stock_en_pedido AS
SELECT d.id_item AS id_item,
       SUM(d.cantidad_solicitada - d.cantidad_recibida) AS cantidad_en_pedido
FROM ordenes_compra_detalles d
JOIN ordenes_compra o ON o.id_orden = d.id_orden
WHERE o.estado IN ('EMITIDA','PARCIALMENTE_RECIBIDA')
  AND (d.cantidad_solicitada - d.cantidad_recibida) > 0
GROUP BY d.id_item;

-- @@PARTE2_RECREAR_MOVIMIENTOS@@
-- ----------------------------------------------------------------------------
-- PARTE 2: recrea inventario_movimientos para admitir 'ENTRADA_COMPRA'.
-- SQLite no permite alterar un CHECK; se recrea la tabla copiando TODOS los datos
-- (respaldo) antes de reemplazarla. DatabaseInitializer valida el conteo (antes/despues)
-- dentro de una transaccion y hace rollback si difieren.
-- ----------------------------------------------------------------------------

CREATE TABLE inventario_movimientos_mig (
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
    CHECK (tipo_movimiento IN ('ENTRADA','ENTRADA_COMPRA','SALIDA','AJUSTE_POSITIVO','AJUSTE_NEGATIVO','TRASLADO','SALIDA_VENTA')),
    CHECK (cantidad > 0)
);

INSERT INTO inventario_movimientos_mig (id_movimiento, id_item, id_bodega, tipo_movimiento, cantidad, id_lote, motivo, id_usuario, id_venta, fecha)
SELECT id_movimiento, id_item, id_bodega, tipo_movimiento, cantidad, id_lote, motivo, id_usuario, id_venta, fecha
FROM inventario_movimientos;

DROP TABLE inventario_movimientos;

ALTER TABLE inventario_movimientos_mig RENAME TO inventario_movimientos;
