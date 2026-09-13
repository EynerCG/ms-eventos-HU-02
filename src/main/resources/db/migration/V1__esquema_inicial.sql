CREATE TABLE evento (
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
    recibido_por       VARCHAR(150)
);

CREATE INDEX idx_evento_seguimiento_fecha
    ON evento (numero_seguimiento, ocurrido_en);

CREATE TABLE envio_rastreado (
    numero_seguimiento VARCHAR(30) PRIMARY KEY,
    estado_actual      VARCHAR(40) NOT NULL,
    ultimo_evento_en   TIMESTAMPTZ
);

CREATE TABLE outbox_estado (
    id_evento          UUID         PRIMARY KEY,
    numero_seguimiento VARCHAR(30)  NOT NULL,
    estado             VARCHAR(40)  NOT NULL,
    ocurrido_en        TIMESTAMPTZ  NOT NULL,
    estado_envio       VARCHAR(20)  NOT NULL,
    intentos           INTEGER      NOT NULL,
    proximo_intento_en TIMESTAMPTZ  NOT NULL,
    sincronizado_en    TIMESTAMPTZ,
    ultimo_error       VARCHAR(1000)
);

CREATE INDEX idx_outbox_pendientes
    ON outbox_estado (estado_envio, proximo_intento_en);
