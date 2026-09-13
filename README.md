# TrackFlow · ms-eventos

Microservicio de eventos logísticos del sistema de trazabilidad TrackFlow (Caso 6, CodeF@ctory UdeA 2026-2).

Implementa la **HU-02 — Registrar un evento logístico de un envío**: registra lo que le ocurre a un envío a su paso por cada punto de la cadena, mueve su estado y alimenta su historial.

| | |
|---|---|
| Historia | HU-02 (work item 58) |
| Responsables | Eyner Gómez, Juan Daniel Rincón |
| Stack | Java 21 · Spring Boot 3.3 · Maven · PostgreSQL (Supabase) · Flyway |
| Despliegue | Render (Docker) |
| Estilo arquitectónico | Arquitectura Limpia + microservicio delimitado |

## Documentación

| Documento | Contenido |
|---|---|
| [Estilo arquitectónico](docs/arquitectura/estilo-arquitectonico.md) | Justificación del estilo, decisiones técnicas y atributos de calidad |
| [Diagramas de paquetes y componentes](docs/arquitectura/diagrama-paquetes-componentes.md) | Paquetes, componentes con interfaces, contexto y flujo de registro |
| [ADR-001](docs/adr/ADR-001-estilo-arquitectonico.md) | Arquitectura Limpia dentro de un microservicio delimitado |
| [ADR-002](docs/adr/ADR-002-integracion-resiliente-ms-envios.md) | Integración resiliente con ms-envios (outbox + proyección local) |
| [ADR-003](docs/adr/ADR-003-persistencia-esquema-por-servicio.md) | PostgreSQL/Supabase con esquema exclusivo por servicio |
| [Modelo de datos](docs/base-de-datos/README.md) | Diagrama ER, preguntas clave de negocio, modelo lógico y físico |
| [Despliegue en Render](docs/despliegue-render.md) | Paso a paso, variables de entorno y limitaciones del plan gratuito |

## Criterios de aceptación y dónde se cumplen

| Criterio | Implementación | Prueba |
|---|---|---|
| Evento sobre envío existente → queda en el historial con fecha y punto, y el estado pasa al que corresponde | `MaquinaEstadosEnvio` + `RegistrarEventoService` | `MaquinaEstadosEnvioTest`, `RegistrarEventoServiceTest`, `EventoControllerTest` |
| Entrega al destinatario → envío `ENTREGADO` y la entrega es el último movimiento | Transición `ENTREGA` → `ENTREGADO`; historial ordenado por `ocurridoEn` | `RegistrarEventoServiceTest#registraEntrega`, `ValueObjectsTest#historialOrdenadoCronologicamente` |
| Número de seguimiento inexistente → no se registra y se informa | `EnvioNoEncontradoException` → `404` en `GlobalExceptionHandler` | `RegistrarEventoServiceTest#rechazaEnvioInexistente`, `EventoControllerTest#envioInexistente` |

## Catálogo de estados y transiciones

`REGISTRADO` → `EN_TRANSITO` ⇄ `EN_CENTRO_DISTRIBUCION` → `EN_REPARTO` → `ENTREGADO` (terminal), con `ENTREGA_FALLIDA` como desvío que retorna a `EN_REPARTO`.

| Tipo de evento | Estado resultante | Estados de origen permitidos |
|---|---|---|
| `RECOGIDA` | `EN_TRANSITO` | `REGISTRADO` |
| `LLEGADA_CENTRO` | `EN_CENTRO_DISTRIBUCION` | `EN_TRANSITO` |
| `SALIDA_CENTRO` | `EN_TRANSITO` | `EN_CENTRO_DISTRIBUCION` |
| `SALIDA_REPARTO` | `EN_REPARTO` | `EN_TRANSITO`, `EN_CENTRO_DISTRIBUCION`, `ENTREGA_FALLIDA` |
| `INTENTO_ENTREGA_FALLIDO` | `ENTREGA_FALLIDA` | `EN_REPARTO` |
| `ENTREGA` | `ENTREGADO` | `EN_REPARTO` |

Decisiones sobre las preguntas abiertas de la HU, **pendientes de validar con el equipo**:

- Un evento con fecha anterior al último registrado se rechaza con `409`.
- `ENTREGADO` es terminal: cualquier evento posterior se rechaza con `409`.
- Cualquier operador puede registrar eventos (no hay autenticación en el alcance del Sprint 1); el punto queda registrado en el evento.
- La entrega acepta `recibidoPor` como campo **opcional**.

## API

```
POST /api/v1/eventos
GET  /api/v1/envios/{numeroSeguimiento}/eventos
GET  /actuator/health
```

**Registrar un evento**

```json
POST /api/v1/eventos
{
  "numeroSeguimiento": "TRK-SEED-0001",
  "tipoEvento": "RECOGIDA",
  "punto": { "codigo": "CD-MDE", "nombre": "Centro Medellin", "ciudad": "Medellin" },
  "ocurridoEn": "2026-09-13T10:00:00Z",
  "observaciones": "Paquete recogido en origen",
  "recibidoPor": null
}
```

| Código | Cuándo |
|---|---|
| `201` | Evento registrado |
| `400` | Faltan datos obligatorios o tienen formato inválido |
| `404` | El envío no existe |
| `409` | Transición no permitida, envío ya entregado, o evento fuera de secuencia |
| `503` | ms-envios no responde y el envío aún no está en la proyección local |

Los errores usan el formato RFC 7807 (`application/problem+json`).

## Arquitectura

Clean Architecture con las dependencias apuntando siempre hacia adentro: `api` → `application` → `domain`. `infrastructure` implementa los puertos que define `application`.

```
domain/          Entidades, value objects y la maquina de estados. Java puro, sin frameworks.
application/     Puertos (in/out) y casos de uso. Sin anotaciones de Spring.
infrastructure/  Adaptadores: JPA, cliente REST a ms-envios, outbox, configuracion.
api/             Controladores REST y manejo de errores.
```

### Aplicación de SOLID

- **SRP** — `MaquinaEstadosEnvio` solo decide transiciones; `RegistrarEventoService` solo orquesta el caso de uso; los adaptadores solo traducen entre el dominio y el mundo externo.
- **OCP** — agregar un tipo de evento es añadir un valor al enum y una entrada en el mapa de transiciones, sin tocar el caso de uso ni el controlador.
- **LSP** — los adaptadores de persistencia y el cliente REST se sustituyen por dobles en las pruebas sin que el caso de uso se entere.
- **ISP** — puertos de salida pequeños y separados (`EventoRepository`, `EnvioRastreadoRepository`, `EnvioEstadoPort`, `OutboxRepository`) en vez de un repositorio que lo haga todo.
- **DIP** — `application` define las interfaces y `infrastructure` las implementa. El dominio no conoce JPA, Spring ni HTTP; los beans se cablean en `BeanConfig`.

La frontera transaccional vive en `RegistrarEventoTransaccional`, un decorador de infraestructura, para que la capa de aplicación no tenga que depender de Spring.

### Principios de microservicios

| Principio | Cómo se cumple |
|---|---|
| Autónomo | Repositorio, build, pipeline y servicio en Render propios. Se despliega sin tocar ms-envios. |
| Especializado | Una sola capacidad: el paso del envío por la red logística. |
| Base de datos por servicio | Dueño exclusivo del esquema `eventos`. **Nunca lee las tablas de ms-envios por SQL.** |
| API bien definida | Único punto de integración: HTTP/REST contra el contrato publicado de ms-envios. |
| Acoplamiento débil | Proyección local `envio_rastreado` + outbox: sigue registrando eventos con ms-envios caído. |
| Resiliencia | El fallo de ms-envios degrada la funcionalidad (sincroniza más tarde), no tumba el registro. |

### Integración con ms-envios

ms-eventos mantiene una proyección local del envío. Al registrar un evento:

1. Busca el envío en la proyección local; si no está, lo hidrata consultando a ms-envios. Si tampoco existe allá → `404`.
2. La máquina de estados valida la transición contra el estado local.
3. Evento, nuevo estado local y fila del outbox se persisten **en una sola transacción**.
4. `OutboxProcessor` publica el cambio a ms-envios de forma asíncrona, con reintentos y backoff exponencial. El `idEvento` va como clave de idempotencia.

**Contrato esperado de ms-envios** (por confirmar con Oliver y Julián):

```
GET   /api/v1/envios/{numeroSeguimiento}       200 → { numeroSeguimiento, estado }   404 → no existe
PATCH /api/v1/envios/{numeroSeguimiento}/estado
      { "estado": "...", "ocurridoEn": "...", "idEvento": "..." }
```

## Cómo ejecutarlo

Requiere JDK 21 y Maven 3.9+.

```bash
mvn clean verify
```

Las pruebas no necesitan base de datos: usan H2 en memoria.

Para levantarlo contra Supabase:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/postgres \
SPRING_DATASOURCE_USERNAME=<usuario> \
SPRING_DATASOURCE_PASSWORD=<clave> \
MS_ENVIOS_BASE_URL=https://trackflow-ms-envios.onrender.com \
mvn spring-boot:run
```

Flyway crea el esquema `eventos` y carga los envíos semilla (`TRK-SEED-0001` a `TRK-SEED-0004`), que permiten probar la HU-02 sin esperar el despliegue de HU-01.

## Pruebas

Suite de pruebas unitarias y de integración (dominio, casos de uso, controladores, persistencia, cliente REST y outbox).

La colección de Postman con los escenarios de éxito y error está en [`postman/`](postman/). Apunta la variable `baseUrl` a local o a Render.

## Despliegue en Render

Web Service tipo Docker, definido en `render.yaml`. Health check en `/actuator/health`.

Variables de entorno requeridas: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `MS_ENVIOS_BASE_URL`.

> En el plan gratuito Render duerme el servicio tras 15 minutos sin tráfico: la primera petición puede tardar ~50 segundos. Tenerlo en cuenta al probar con Postman y al hacer la demo del sprint review.
