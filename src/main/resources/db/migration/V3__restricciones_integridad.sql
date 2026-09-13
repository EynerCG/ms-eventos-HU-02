-- Alinea el esquema con el modelo fisico documentado en docs/base-de-datos/schema.sql:
-- integridad referencial, catalogos cerrados y reglas de negocio que la base debe garantizar
-- aunque falle la capa de aplicacion.

ALTER TABLE envio_rastreado
    ADD CONSTRAINT ck_rastreado_numero_formato
        CHECK (numero_seguimiento ~ '^[A-Z0-9-]{6,30}$'),
    ADD CONSTRAINT ck_rastreado_estado
        CHECK (estado_actual IN (
            'REGISTRADO', 'EN_TRANSITO', 'EN_CENTRO_DISTRIBUCION',
            'EN_REPARTO', 'ENTREGA_FALLIDA', 'ENTREGADO'
        ));

CREATE INDEX idx_envio_rastreado_pendientes
    ON envio_rastreado (estado_actual, ultimo_evento_en);

ALTER TABLE evento
    ADD CONSTRAINT fk_evento_envio_rastreado
        FOREIGN KEY (numero_seguimiento)
        REFERENCES envio_rastreado (numero_seguimiento) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_evento_tipo
        CHECK (tipo_evento IN (
            'RECOGIDA', 'LLEGADA_CENTRO', 'SALIDA_CENTRO',
            'SALIDA_REPARTO', 'INTENTO_ENTREGA_FALLIDO', 'ENTREGA'
        )),
    ADD CONSTRAINT ck_evento_estado
        CHECK (estado_resultante IN (
            'REGISTRADO', 'EN_TRANSITO', 'EN_CENTRO_DISTRIBUCION',
            'EN_REPARTO', 'ENTREGA_FALLIDA', 'ENTREGADO'
        )),
    ADD CONSTRAINT ck_evento_recibido_por
        CHECK (recibido_por IS NULL OR tipo_evento = 'ENTREGA');

CREATE INDEX idx_evento_punto ON evento (punto_codigo);
CREATE INDEX idx_evento_tipo  ON evento (tipo_evento);

ALTER TABLE outbox_estado
    ADD CONSTRAINT fk_outbox_evento
        FOREIGN KEY (id_evento) REFERENCES evento (id) ON DELETE CASCADE,
    ADD CONSTRAINT ck_outbox_estado_envio
        CHECK (estado_envio IN ('PENDIENTE', 'ENVIADO', 'FALLIDO')),
    ADD CONSTRAINT ck_outbox_intentos
        CHECK (intentos >= 0),
    ADD CONSTRAINT ck_outbox_sincronizado
        CHECK (
            (estado_envio = 'ENVIADO' AND sincronizado_en IS NOT NULL)
            OR (estado_envio <> 'ENVIADO' AND sincronizado_en IS NULL)
        );

COMMENT ON TABLE evento IS 'Historial de movimientos del envio por la red logistica';
COMMENT ON COLUMN evento.ocurrido_en IS 'Cuando ocurrio el hecho en la realidad';
COMMENT ON COLUMN evento.registrado_en IS 'Cuando el sistema lo supo. Permite auditar desfases';
COMMENT ON TABLE envio_rastreado IS 'Proyeccion local del envio. La fuente de verdad es ms-envios';
COMMENT ON TABLE outbox_estado IS 'Cambios de estado pendientes de propagar. id_evento es clave de idempotencia';
