-- Envios semilla para probar HU-02 sin depender del despliegue de HU-01 (ms-envios).
-- Corresponde a la seccion 6.5 del plan de aseguramiento de calidad.
INSERT INTO envio_rastreado (numero_seguimiento, estado_actual, ultimo_evento_en) VALUES
    ('TRK-SEED-0001', 'REGISTRADO', NULL),
    ('TRK-SEED-0002', 'EN_TRANSITO', NULL),
    ('TRK-SEED-0003', 'EN_REPARTO', NULL),
    ('TRK-SEED-0004', 'ENTREGADO', NULL)
ON CONFLICT (numero_seguimiento) DO NOTHING;
