-- =====================================================================
-- TrackFlow - Modelo fisico inicial (Sprint 1)
-- Motor: PostgreSQL 15+ / Supabase
-- Responsable: Eyner Gomez Quintero
-- Fecha: 2026-09-13
--
-- Ejecutable sobre una base vacia. Crea dos esquemas aislados, uno por
-- microservicio, sin llaves foraneas cruzadas entre ellos (ver ADR-003).
--
-- Esquema "envios"  -> propiedad de ms-envios  (HU-01, HU-03)
-- Esquema "eventos" -> propiedad de ms-eventos (HU-02)  [IMPLEMENTADO]
-- =====================================================================

BEGIN;

-- =====================================================================
-- ESQUEMA: envios   (propuesta para ms-envios)
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS envios;

-- ---------------------------------------------------------------------
-- persona: remitentes y destinatarios
-- ---------------------------------------------------------------------
CREATE TABLE envios.persona (
    id_persona      BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_completo VARCHAR(150) NOT NULL,
    documento       VARCHAR(30)  NOT NULL,
    telefono        VARCHAR(30),
    correo          VARCHAR(150),
    direccion       VARCHAR(200) NOT NULL,
    ciudad          VARCHAR(100) NOT NULL,
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_persona_documento     UNIQUE (documento),
    CONSTRAINT ck_persona_nombre        CHECK (LENGTH(TRIM(nombre_completo)) >= 3),
    CONSTRAINT ck_persona_correo        CHECK (correo IS NULL OR correo ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT ck_persona_contacto      CHECK (telefono IS NOT NULL OR correo IS NOT NULL)
);

COMMENT ON TABLE  envios.persona            IS 'Remitente o destinatario de un envio';
COMMENT ON CONSTRAINT ck_persona_contacto ON envios.persona
    IS 'Regla de negocio HU-01: debe existir al menos un medio de contacto';

CREATE INDEX idx_persona_documento ON envios.persona (documento);
CREATE INDEX idx_persona_ciudad    ON envios.persona (ciudad);

-- ---------------------------------------------------------------------
-- envio: el paquete y su estado actual (fuente de verdad)
-- ---------------------------------------------------------------------
CREATE TABLE envios.envio (
    id_envio           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero_seguimiento VARCHAR(30)  NOT NULL,
    id_remitente       BIGINT       NOT NULL,
    id_destinatario    BIGINT       NOT NULL,
    estado_actual      VARCHAR(30)  NOT NULL DEFAULT 'REGISTRADO',
    peso_kg            NUMERIC(8,3),
    descripcion        VARCHAR(300),
    registrado_en      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    actualizado_en     TIMESTAMPTZ,

    CONSTRAINT uq_envio_numero_seguimiento UNIQUE (numero_seguimiento),

    CONSTRAINT fk_envio_remitente
        FOREIGN KEY (id_remitente)    REFERENCES envios.persona (id_persona) ON DELETE RESTRICT,
    CONSTRAINT fk_envio_destinatario
        FOREIGN KEY (id_destinatario) REFERENCES envios.persona (id_persona) ON DELETE RESTRICT,

    CONSTRAINT ck_envio_numero_formato CHECK (numero_seguimiento ~ '^[A-Z0-9-]{6,30}$'),
    CONSTRAINT ck_envio_personas_distintas CHECK (id_remitente <> id_destinatario),
    CONSTRAINT ck_envio_peso CHECK (peso_kg IS NULL OR peso_kg > 0),
    CONSTRAINT ck_envio_estado CHECK (estado_actual IN (
        'REGISTRADO', 'EN_TRANSITO', 'EN_CENTRO_DISTRIBUCION',
        'EN_REPARTO', 'ENTREGA_FALLIDA', 'ENTREGADO'
    )),
    CONSTRAINT ck_envio_fechas CHECK (actualizado_en IS NULL OR actualizado_en >= registrado_en)
);

COMMENT ON TABLE  envios.envio                    IS 'Envio registrado. Fuente de verdad del estado de cara al cliente';
COMMENT ON COLUMN envios.envio.numero_seguimiento IS 'Identificador publico unico, generado en HU-01';

CREATE INDEX idx_envio_estado      ON envios.envio (estado_actual);
CREATE INDEX idx_envio_remitente   ON envios.envio (id_remitente);
CREATE INDEX idx_envio_destinatario ON envios.envio (id_destinatario);
CREATE INDEX idx_envio_registrado  ON envios.envio (registrado_en);


-- =====================================================================
-- ESQUEMA: eventos   (ms-eventos - IMPLEMENTADO)
-- Corresponde a las migraciones Flyway V1__esquema_inicial.sql
-- y V2__datos_semilla.sql del repositorio de ms-eventos.
-- =====================================================================

CREATE SCHEMA IF NOT EXISTS eventos;

-- ---------------------------------------------------------------------
-- envio_rastreado: proyeccion local del estado del envio.
-- Permite que ms-eventos opere sin depender de ms-envios (ver ADR-002).
-- ---------------------------------------------------------------------
CREATE TABLE eventos.envio_rastreado (
    numero_seguimiento VARCHAR(30) PRIMARY KEY,
    estado_actual      VARCHAR(40) NOT NULL,
    ultimo_evento_en   TIMESTAMPTZ,

    CONSTRAINT ck_rastreado_numero_formato CHECK (numero_seguimiento ~ '^[A-Z0-9-]{6,30}$'),
    CONSTRAINT ck_rastreado_estado CHECK (estado_actual IN (
        'REGISTRADO', 'EN_TRANSITO', 'EN_CENTRO_DISTRIBUCION',
        'EN_REPARTO', 'ENTREGA_FALLIDA', 'ENTREGADO'
    ))
);

COMMENT ON TABLE eventos.envio_rastreado
    IS 'Proyeccion local del envio. No es la fuente de verdad: se hidrata desde la API de ms-envios';

CREATE INDEX idx_envio_rastreado_pendientes
    ON eventos.envio_rastreado (estado_actual, ultimo_evento_en);

-- ---------------------------------------------------------------------
-- evento: historial de movimientos del envio
-- ---------------------------------------------------------------------
CREATE TABLE eventos.evento (
    id                 UUID         PRIMARY KEY,
    numero_seguimiento VARCHAR(30)  NOT NULL,
    tipo_evento        VARCHAR(40)  NOT NULL,
    estado_resultante  VARCHAR(40)  NOT NULL,
    punto_codigo       VARCHAR(40)  NOT NULL,
    punto_nombre       VARCHAR(150) NOT NULL,
    punto_ciudad       VARCHAR(100),
    ocurrido_en        TIMESTAMPTZ  NOT NULL,
    registrado_en      TIMESTAMPTZ  NOT NULL,
    observaciones      VARCHAR(500),
    recibido_por       VARCHAR(150),

    CONSTRAINT fk_evento_envio_rastreado
        FOREIGN KEY (numero_seguimiento)
        REFERENCES eventos.envio_rastreado (numero_seguimiento) ON DELETE RESTRICT,

    CONSTRAINT ck_evento_tipo CHECK (tipo_evento IN (
        'RECOGIDA', 'LLEGADA_CENTRO', 'SALIDA_CENTRO',
        'SALIDA_REPARTO', 'INTENTO_ENTREGA_FALLIDO', 'ENTREGA'
    )),
    CONSTRAINT ck_evento_estado CHECK (estado_resultante IN (
        'REGISTRADO', 'EN_TRANSITO', 'EN_CENTRO_DISTRIBUCION',
        'EN_REPARTO', 'ENTREGA_FALLIDA', 'ENTREGADO'
    )),
    CONSTRAINT ck_evento_recibido_por CHECK (
        recibido_por IS NULL OR tipo_evento = 'ENTREGA'
    )
);

COMMENT ON COLUMN eventos.evento.ocurrido_en   IS 'Cuando ocurrio el hecho en la realidad';
COMMENT ON COLUMN eventos.evento.registrado_en IS 'Cuando el sistema lo supo. Permite auditar desfases';
COMMENT ON CONSTRAINT ck_evento_recibido_por ON eventos.evento
    IS 'Solo el evento de ENTREGA admite constancia de quien recibio';

-- Resuelve la consulta de historial (P1), la mas frecuente del servicio
CREATE INDEX idx_evento_seguimiento_fecha
    ON eventos.evento (numero_seguimiento, ocurrido_en);

CREATE INDEX idx_evento_punto ON eventos.evento (punto_codigo);
CREATE INDEX idx_evento_tipo  ON eventos.evento (tipo_evento);

-- ---------------------------------------------------------------------
-- outbox_estado: cola de cambios pendientes de propagar a ms-envios.
-- Patron Transactional Outbox (ver ADR-002).
-- ---------------------------------------------------------------------
CREATE TABLE eventos.outbox_estado (
    id_evento          UUID          PRIMARY KEY,
    numero_seguimiento VARCHAR(30)   NOT NULL,
    estado             VARCHAR(40)   NOT NULL,
    ocurrido_en        TIMESTAMPTZ   NOT NULL,
    estado_envio       VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    intentos           INTEGER       NOT NULL DEFAULT 0,
    proximo_intento_en TIMESTAMPTZ   NOT NULL,
    sincronizado_en    TIMESTAMPTZ,
    ultimo_error       VARCHAR(1000),

    CONSTRAINT fk_outbox_evento
        FOREIGN KEY (id_evento) REFERENCES eventos.evento (id) ON DELETE CASCADE,

    CONSTRAINT ck_outbox_estado_envio CHECK (estado_envio IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
    CONSTRAINT ck_outbox_intentos     CHECK (intentos >= 0),
    CONSTRAINT ck_outbox_sincronizado CHECK (
        (estado_envio = 'ENVIADO' AND sincronizado_en IS NOT NULL)
        OR (estado_envio <> 'ENVIADO' AND sincronizado_en IS NULL)
    )
);

COMMENT ON TABLE eventos.outbox_estado
    IS 'Cambios de estado pendientes de propagar a ms-envios. id_evento es la clave de idempotencia';

-- El OutboxProcessor consulta pendientes vencidos cada 5 segundos
CREATE INDEX idx_outbox_pendientes
    ON eventos.outbox_estado (estado_envio, proximo_intento_en);


-- =====================================================================
-- DATOS SEMILLA
-- Permiten probar HU-02 sin depender del despliegue de HU-01
-- (plan de aseguramiento de calidad, seccion 6.5)
-- =====================================================================

INSERT INTO eventos.envio_rastreado (numero_seguimiento, estado_actual, ultimo_evento_en) VALUES
    ('TRK-SEED-0001', 'REGISTRADO',  NULL),
    ('TRK-SEED-0002', 'EN_TRANSITO', NULL),
    ('TRK-SEED-0003', 'EN_REPARTO',  NULL),
    ('TRK-SEED-0004', 'ENTREGADO',   NULL)
ON CONFLICT (numero_seguimiento) DO NOTHING;

COMMIT;
