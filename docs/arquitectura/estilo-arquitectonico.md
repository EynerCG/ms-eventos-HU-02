# Estilo arquitectónico — ms-eventos

- **Responsable:** Eyner Gómez Quintero
- **Fecha:** 2026-09-13
- **Sprint:** 1
- **Estado:** Preliminar (sujeto a refinamiento en Sprint 2)

---

## 1. Estilo seleccionado

**Arquitectura Limpia (Clean Architecture) dentro de un microservicio claramente delimitado.**

`ms-eventos` es dueño de una sola capacidad de negocio: *el paso de un envío por la red logística*. Registra los eventos, mantiene su historial y decide a qué estado mueve cada evento al envío. No registra envíos ni responde consultas de estado al cliente final — eso pertenece a `ms-envios`.

Internamente el servicio se organiza en cuatro capas con la regla de dependencias apuntando hacia el dominio:

| Capa | Responsabilidad | Depende de |
|---|---|---|
| `domain` | Entidades, value objects y reglas de negocio (`MaquinaEstadosEnvio`) | Nadie. Java puro, sin frameworks |
| `application` | Casos de uso y puertos de entrada/salida | `domain` |
| `infrastructure` | Adaptadores: JPA, cliente REST, outbox, configuración | `application`, `domain` |
| `api` | Controladores REST, DTOs y manejo de errores | `application` |

Ver los diagramas en [diagrama-paquetes-componentes.md](diagrama-paquetes-componentes.md).

## 2. Justificación

El caso de uso central de la HU-02 no es un CRUD: es una **máquina de estados con transiciones válidas e inválidas** (un envío en `REGISTRADO` no puede pasar directo a `ENTREGADO`; un envío ya entregado no admite más eventos; un evento con fecha anterior al último rompe la secuencia). Esa lógica es el activo más valioso del servicio y la que más va a cambiar cuando el negocio agregue nuevos tipos de evento.

Clean Architecture la aísla en `domain/service/MaquinaEstadosEnvio.java`, sin una sola anotación de framework, lo que permite:

- Probar las 18 combinaciones de transiciones con pruebas unitarias puras, sin levantar Spring ni base de datos.
- Cambiar de Supabase a otro PostgreSQL, o de `RestClient` a otro cliente HTTP, sin tocar la regla de negocio.
- Agregar un tipo de evento nuevo modificando solo el enum y el mapa de transiciones (OCP).

La alternativa evaluada y descartada fue la **arquitectura en capas tradicional** (`controller → service → repository`), que acopla la lógica de negocio al framework de persistencia. Ver [ADR-001](../adr/ADR-001-estilo-arquitectonico.md).

## 3. Decisiones técnicas que exige el lineamiento 3.1

### Persistencia

PostgreSQL administrado en Supabase, con un **esquema propio y exclusivo** (`eventos`). El servicio nunca accede a las tablas de otro microservicio por SQL. Migraciones versionadas con Flyway dentro del propio repositorio, de modo que el servicio es dueño de la evolución de su esquema. Ver [ADR-003](../adr/ADR-003-persistencia-esquema-por-servicio.md).

### Manejo de errores

Respuestas uniformes en formato **RFC 7807 Problem Detail** (`application/problem+json`), que Spring Boot 3 provee nativamente, centralizadas en `api/GlobalExceptionHandler.java`.

| Código | Situación | Excepción de dominio |
|---|---|---|
| `400` | Datos obligatorios faltantes o inválidos | `DatoInvalidoException`, `MethodArgumentNotValidException` |
| `404` | El envío no existe | `EnvioNoEncontradoException` |
| `409` | Transición no permitida, envío ya entregado o evento fuera de secuencia | `TransicionNoPermitidaException`, `EnvioYaEntregadoException`, `EventoFueraDeSecuenciaException` |
| `503` | `ms-envios` no responde y el envío no está en la proyección local | `ServicioEnviosNoDisponibleException` |

En los errores de validación se agrega la propiedad `errores`, un mapa campo → mensaje, para que el cliente sepa exactamente qué corregir.

> **Pendiente Sprint 2:** el lineamiento 3.3 pide además los campos `errorCode` y `traceId` en las respuestas de error, y logs estructurados en JSON con correlación por `traceId`. No están implementados aún.

### Versionado de API

Versionado por ruta: todos los endpoints cuelgan de `/api/v1/`. El contrato con `ms-envios` también se consume versionado (`/api/v1/envios/...`). Al introducir cambios incompatibles se publicará `/api/v2/` manteniendo `v1` durante un período de transición.

> **Pendiente Sprint 2:** publicar el contrato OpenAPI/Swagger versionado (lineamiento 3.1) y la documentación de APIs.

### Estrategia de pruebas

Pirámide de pruebas alineada con las capas, 65 pruebas en total:

| Nivel | Qué cubre | Técnica |
|---|---|---|
| Unitarias de dominio | Transiciones de estado, value objects, reglas del agregado | JUnit 5 puro, sin Spring |
| Unitarias de aplicación | Casos de uso y orquestación | Mockito sobre los puertos |
| Integración de persistencia | Adaptadores JPA y ciclo del outbox | `@DataJpaTest` con H2 en modo PostgreSQL |
| Integración de API | Los 3 criterios de aceptación y los casos de error | `@WebMvcTest` con MockMvc |
| Integración de cliente | Contrato HTTP con `ms-envios` | `MockRestServiceServer` |
| Aceptación manual | Escenarios de la HU contra el servicio desplegado | Colección de Postman en `postman/` |

Ninguna prueba requiere conexión a Supabase ni a internet, de modo que el pipeline puede ejecutarlas de forma determinista.

## 4. Atributos de calidad (lineamiento 4.3)

| Atributo | Cómo lo aborda este servicio | Evidencia |
|---|---|---|
| **Escalabilidad** | El servicio es sin estado: todo el estado vive en PostgreSQL. Puede escalar horizontalmente sin coordinación entre instancias. El `OutboxProcessor` toma lotes acotados (`tamano-lote`, por defecto 50) para no saturar a `ms-envios`. | `application.yml`, `OutboxProcessor` |
| **Rendimiento** | Línea base académica: 200 solicitudes/minuto, respuesta ≤ 30 s. El registro de un evento responde sin esperar a `ms-envios` (la sincronización es asíncrona), y el historial se apoya en el índice `idx_evento_seguimiento_fecha`. | `V1__esquema_inicial.sql` |
| **Disponibilidad** | Sonda `/actuator/health` con probes de liveness y readiness para Render. Un fallo de `ms-envios` degrada la funcionalidad (el estado se sincroniza más tarde) en lugar de tumbar el registro de eventos. | `application.yml`, `OutboxProcessor` |
| **Mantenibilidad** | Responsabilidades separadas por capa, reglas de negocio parametrizadas en un único mapa de transiciones, puertos pequeños y segregados, y documentación viva (ADR + diagramas versionados junto al código). | `MaquinaEstadosEnvio`, `docs/` |
| **Interoperabilidad** | Contrato REST versionado, JSON estándar, errores RFC 7807, y bajo acoplamiento: el único punto de integración es la interfaz `EnvioEstadoPort`, sustituible sin tocar el núcleo. | `EnvioEstadoRestAdapter` |
| **Trazabilidad** | Cada evento guarda `ocurrido_en` (cuándo pasó en la realidad) y `registrado_en` (cuándo lo supo el sistema), más el punto logístico. El `idEvento` (UUID) viaja a `ms-envios` como clave de idempotencia y permite correlacionar ambos lados. | `Evento`, `MensajeOutbox` |

> **Pendiente Sprint 2/3:** observabilidad con Prometheus/Grafana y correlación por `traceId` (lineamiento 7.3).

## 5. Principios de microservicios aplicados

Según las características definidas por AWS (autónomos y especializados):

| Principio | Implementación concreta |
|---|---|
| Autónomo | Repositorio, build, pipeline y servicio en Render propios. Se despliega sin coordinar con `ms-envios`. |
| Especializado | Una sola capacidad de negocio: el paso del envío por la red logística. |
| Base de datos por servicio | Dueño exclusivo del esquema `eventos`. Nunca lee tablas ajenas por SQL. |
| Comunicación por API | Único punto de integración: HTTP/REST contra el contrato publicado de `ms-envios`. |
| Acoplamiento débil | Proyección local `envio_rastreado` + outbox transaccional: sigue aceptando eventos con `ms-envios` caído. |
| Resiliencia | Timeouts explícitos, reintentos con backoff exponencial y límite de intentos antes de marcar `FALLIDO`. |

## 6. Aplicación de SOLID

- **SRP** — `MaquinaEstadosEnvio` solo decide transiciones; `RegistrarEventoService` solo orquesta; los adaptadores solo traducen entre el dominio y el mundo externo.
- **OCP** — un tipo de evento nuevo se agrega con un valor en el enum y una entrada en el mapa de transiciones; ni el caso de uso ni el controlador cambian.
- **LSP** — los adaptadores de persistencia y el cliente REST se sustituyen por dobles en las pruebas sin que el caso de uso se entere.
- **ISP** — cuatro puertos de salida pequeños y separados (`EventoRepository`, `EnvioRastreadoRepository`, `EnvioEstadoPort`, `OutboxRepository`) en vez de un repositorio que lo haga todo.
- **DIP** — `application` define las interfaces e `infrastructure` las implementa; los beans se cablean en `BeanConfig` para que el núcleo no dependa de Spring.

## 7. Brechas conocidas frente a los lineamientos

Declaradas explícitamente para que el equipo decida cuándo abordarlas:

| Brecha | Lineamiento | Sprint objetivo |
|---|---|---|
| Contrato OpenAPI/Swagger publicado | 3.1 | 2 |
| `errorCode` y `traceId` en errores; logs estructurados JSON | 3.3 | 2 |
| Autenticación, RBAC por endpoint | 3.4 | 2 |
| Observabilidad (Prometheus/Grafana) | 7.3 | 3 |
| Nombres de código en inglés | 7.1 | Por decidir con el equipo y el docente |
