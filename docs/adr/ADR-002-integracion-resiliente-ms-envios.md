# ADR-002 — Integración resiliente con ms-envios mediante outbox transaccional y proyección local

| | |
|---|---|
| **Estado** | Aceptada |
| **Prioridad** | Alta |
| **Responsable** | Eyner Gómez Quintero |
| **Fecha** | 2026-09-13 |
| **Sprint** | 1 |
| **Historia relacionada** | HU-02 — Registrar un evento logístico de un envío (work item 58) |

## Contexto

El criterio de aceptación principal de la HU-02 dice:

> *Dado un envío identificado por su número de seguimiento, cuando registro un evento indicando el punto y lo ocurrido, entonces el evento queda en el historial del envío con su fecha y su punto **y el estado actual del envío pasa a ser el que corresponde a ese evento**.*

Ese "estado actual del envío" es propiedad de **`ms-envios`**, que es la fuente de verdad del ciclo de vida del envío (HU-01 y HU-03, a cargo de Oliver y Julián). `ms-eventos` necesita, entonces, hacer dos cosas que dependen de otro servicio: **saber si el envío existe y en qué estado va**, y **propagar el cambio de estado**.

Esto plantea un problema clásico de sistemas distribuidos: dos escrituras que deben quedar consistentes (guardar el evento localmente y actualizar el estado remoto) sin poder envolverlas en una sola transacción.

Restricciones adicionales del Sprint 1:

- Al momento de implementar la HU-02, **`ms-envios` todavía no existía ni estaba desplegado**. Depender de él para poder avanzar habría bloqueado el desarrollo y las pruebas.
- El plan de calidad (sección 6.5) previó **datos semilla** justamente para poder probar HU-02 sin esperar el despliegue de HU-01.
- El despliegue es en Render plan gratuito, donde los servicios **se duermen tras 15 minutos sin tráfico** y la primera petición puede tardar ~50 segundos. Un fallo de comunicación no es un caso excepcional: es lo esperado varias veces al día.

## Alternativas consideradas

### A. Llamada REST síncrona bloqueante

`ms-eventos` llama a `ms-envios` dentro del mismo flujo de la petición; si falla, se rechaza el evento con `502`/`503`.

- **A favor:** la más simple de implementar y de razonar. El cliente sabe de inmediato si todo quedó consistente.
- **En contra:** acopla la disponibilidad de los dos servicios: si `ms-envios` está caído o dormido, `ms-eventos` **no puede registrar nada**, aunque el evento sea válido y su propia base de datos esté sana. Se pierde información real del mundo físico (el paquete sí llegó al centro de distribución) por un problema técnico de otro servicio. Además, habría bloqueado el desarrollo durante todo el sprint, porque `ms-envios` no existía.

### B. Base de datos compartida

Ambos servicios leen y escriben la misma tabla de envíos.

- **A favor:** consistencia transaccional inmediata y trivial de implementar.
- **En contra:** destruye la premisa de microservicios. Si Oliver renombra una columna, `ms-eventos` se rompe sin que nadie toque su código y sin que el compilador avise. Impide desplegar y evolucionar los servicios de forma independiente. Descartada de plano: sería un monolito distribuido con el costo operativo de dos servicios y ninguno de sus beneficios. Ver [ADR-003](ADR-003-persistencia-esquema-por-servicio.md).

### C. Mensajería asíncrona con broker (Kafka / RabbitMQ)

- **A favor:** desacoplamiento real, garantías de entrega, patrón estándar de la industria.
- **En contra:** el lineamiento 5.2 es explícito: *"Kafka u otra tecnología especializada solo se incorpora cuando el caso de uso y la capacidad operativa lo justifican"*. Para dos servicios, un sprint de dos semanas y un despliegue en Render gratuito, añadir y operar un broker es desproporcionado.

### D. Outbox transaccional + proyección local *(elegida)*

`ms-eventos` mantiene una copia mínima del estado del envío (`envio_rastreado`) y encola los cambios a propagar en una tabla `outbox_estado`, dentro de la misma transacción que guarda el evento. Un proceso en segundo plano los publica a `ms-envios` con reintentos.

- **A favor:** el registro del evento nunca depende de la disponibilidad de otro servicio; la consistencia eventual se garantiza sin broker; no hay escritura dual sin red de seguridad.
- **En contra:** consistencia eventual (hay una ventana donde ambos servicios discrepan); más código y dos tablas adicionales; requiere que `ms-envios` implemente idempotencia.

## Elección

Se adopta la **alternativa D**.

El flujo queda así:

1. Al registrar un evento, se busca el envío en la **proyección local** `envio_rastreado`. Si no está, se consulta a `ms-envios` por REST y se hidrata. Si tampoco existe allá → `404` (cumple el criterio de aceptación 3).
2. `MaquinaEstadosEnvio` valida la transición contra el estado de la proyección local.
3. En **una sola transacción** se persisten: el evento, el nuevo estado local y una fila en `outbox_estado`.
4. Se responde `201` al cliente **sin esperar a `ms-envios`**.
5. `OutboxProcessor`, cada 5 segundos, toma las filas pendientes y llama `PATCH /api/v1/envios/{n}/estado`. Si tiene éxito, marca `ENVIADO`; si falla, reprograma con **backoff exponencial** hasta agotar 8 intentos y marcar `FALLIDO`.

El `idEvento` (UUID generado en el dominio) viaja en cada `PATCH` como **clave de idempotencia**, para que un reintento tras un timeout no aplique el cambio dos veces del lado de `ms-envios`.

Contrato acordado a confirmar con Oliver y Julián:

```
GET   /api/v1/envios/{numeroSeguimiento}         200 → { numeroSeguimiento, estado }   404 → no existe
PATCH /api/v1/envios/{numeroSeguimiento}/estado  { estado, ocurridoEn, idEvento }
```

## Consecuencias

**Positivas**

- El registro de eventos sigue funcionando con `ms-envios` caído o dormido, que es el escenario normal en Render gratuito.
- El desarrollo de la HU-02 avanzó completo sin que `ms-envios` existiera, usando datos semilla en `envio_rastreado`.
- No hay escritura dual sin garantías: evento y mensaje a publicar se confirman juntos o no se confirma ninguno.
- La tabla `outbox_estado` es evidencia auditable de qué se sincronizó, cuándo y con cuántos intentos.
- Si más adelante el equipo adopta un broker, solo cambia el `OutboxProcessor`; el caso de uso no se entera.

**Negativas**

- **Consistencia eventual:** existe una ventana (segundos, o más si `ms-envios` está caído) donde `ms-eventos` ya movió el estado y `ms-envios` todavía no. Una consulta por HU-03 en ese intervalo devuelve el estado anterior.
- Complejidad adicional: dos tablas y un proceso programado que no existirían con la alternativa A.
- **Dependencia externa:** `ms-envios` debe implementar idempotencia sobre `idEvento`. Si no lo hace, un reintento puede duplicar efectos. **Esto debe acordarse explícitamente con Oliver y Julián.**
- Un mensaje que agota los 8 reintentos queda en `FALLIDO` y **requiere intervención manual**; no hay aún un mecanismo de alerta ni de reproceso.

**Riesgos aceptados y mitigaciones**

| Riesgo | Mitigación |
|---|---|
| Divergencia de estado entre servicios | La proyección local es de solo lectura para decisiones; `ms-envios` sigue siendo la fuente de verdad de cara al cliente (HU-03) |
| Mensajes en `FALLIDO` sin vigilancia | Sprint 2: alerta y endpoint de reproceso. Hoy son consultables por SQL en `outbox_estado` |
| `ms-envios` sin idempotencia | Punto explícito a cerrar en la reunión de integración con Juan Pablo |

## Referencias

- Lineamientos Integrados CodeF@ctory v2.0, secciones 4.3 (disponibilidad, interoperabilidad) y 5.2
- Patrón *Transactional Outbox* (Chris Richardson, microservices.io)
- [AWS — ¿Qué son los microservicios?](https://aws.amazon.com/es/microservices/) (característica "autónomos")
- [ADR-003](ADR-003-persistencia-esquema-por-servicio.md)
