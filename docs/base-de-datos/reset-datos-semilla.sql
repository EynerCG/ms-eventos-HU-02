-- Restaura los datos semilla del esquema "eventos" a su estado inicial.
--
-- Para que sirve: la coleccion de Postman de la HU-02 consume los envios semilla
-- (un envio que ya fue entregado no admite mas eventos). Ejecuta este script antes
-- de cada corrida completa de la coleccion para que las pruebas sean repetibles.
--
-- Donde ejecutarlo: Supabase -> SQL Editor, o cualquier cliente psql.
-- ADVERTENCIA: borra todos los eventos de los envios TRK-SEED-*. No lo ejecutes
-- sobre datos que quieras conservar.

BEGIN;

-- El outbox se limpia solo por ON DELETE CASCADE al borrar los eventos.
DELETE FROM eventos.evento
WHERE  numero_seguimiento LIKE 'TRK-SEED-%';

UPDATE eventos.envio_rastreado
SET    estado_actual = datos.estado,
       ultimo_evento_en = NULL
FROM  (VALUES
          ('TRK-SEED-0001', 'REGISTRADO'),
          ('TRK-SEED-0002', 'EN_TRANSITO'),
          ('TRK-SEED-0003', 'EN_REPARTO'),
          ('TRK-SEED-0004', 'ENTREGADO')
      ) AS datos(numero, estado)
WHERE  eventos.envio_rastreado.numero_seguimiento = datos.numero;

-- Por si algun envio semilla fue borrado del entorno.
INSERT INTO eventos.envio_rastreado (numero_seguimiento, estado_actual, ultimo_evento_en) VALUES
    ('TRK-SEED-0001', 'REGISTRADO',  NULL),
    ('TRK-SEED-0002', 'EN_TRANSITO', NULL),
    ('TRK-SEED-0003', 'EN_REPARTO',  NULL),
    ('TRK-SEED-0004', 'ENTREGADO',   NULL)
ON CONFLICT (numero_seguimiento) DO NOTHING;

COMMIT;

-- Verificacion: deben quedar los 4 envios en su estado inicial y sin eventos.
SELECT numero_seguimiento, estado_actual, ultimo_evento_en
FROM   eventos.envio_rastreado
WHERE  numero_seguimiento LIKE 'TRK-SEED-%'
ORDER  BY numero_seguimiento;
