# Diagramas de paquetes y componentes — ms-eventos

Microservicio de eventos logísticos de TrackFlow (HU-02). Los diagramas reflejan el código tal como está implementado, no un diseño aspiracional.

- **Responsable:** Eyner Gómez Quintero
- **Fecha:** 2026-09-13
- **Sprint:** 1

---

## 1. Diagrama de paquetes

Clean Architecture: las dependencias apuntan **siempre hacia adentro**. El dominio no conoce a nadie; la infraestructura conoce a todos.

```mermaid
graph TD
    subgraph api["api — adaptadores de entrada"]
        EC[EventoController]
        GEH[GlobalExceptionHandler]
        DTO[dto: RegistrarEventoRequest<br/>EventoResponse, HistorialResponse]
    end

    subgraph application["application — casos de uso"]
        PIN[port.in<br/>RegistrarEventoUseCase<br/>ConsultarHistorialUseCase]
        UC[usecase<br/>RegistrarEventoService<br/>ConsultarHistorialService]
        POUT[port.out<br/>EventoRepository<br/>EnvioRastreadoRepository<br/>EnvioEstadoPort<br/>OutboxRepository]
    end

    subgraph domain["domain — reglas de negocio (Java puro)"]
        MODEL[model<br/>Evento, EnvioRastreado<br/>NumeroSeguimiento, PuntoLogistico<br/>EstadoEnvio, TipoEvento, HistorialEnvio]
        DSERV[service<br/>MaquinaEstadosEnvio]
        DEXC[exception<br/>EnvioNoEncontradoException<br/>TransicionNoPermitidaException<br/>EnvioYaEntregadoException<br/>EventoFueraDeSecuenciaException]
    end

    subgraph infrastructure["infrastructure — adaptadores de salida"]
        PERS[persistence<br/>*JpaEntity, *RepositoryAdapter]
        CLI[client<br/>EnvioEstadoRestAdapter]
        OUTB[outbox<br/>OutboxProcessor]
        TX[transaction<br/>RegistrarEventoTransaccional]
        CFG[config<br/>BeanConfig, RestClientConfig]
    end

    api --> application
    application --> domain
    infrastructure --> application
    infrastructure --> domain

    classDef dom fill:#1b4332,stroke:#2d6a4f,color:#fff
    classDef app fill:#14213d,stroke:#1d3557,color:#fff
    classDef inf fill:#3d1f00,stroke:#6b3410,color:#fff
    classDef apiC fill:#3c096c,stroke:#5a189a,color:#fff
    class MODEL,DSERV,DEXC dom
    class PIN,UC,POUT app
    class PERS,CLI,OUTB,TX,CFG inf
    class EC,GEH,DTO apiC
```

**Regla de dependencias verificable:** ninguna clase de `domain` ni de `application` importa `org.springframework.*` ni `jakarta.persistence.*`. Los beans se instancian a mano en `infrastructure/config/BeanConfig.java`, y la frontera transaccional vive en el decorador `RegistrarEventoTransaccional`, no en el caso de uso.

---

## 2. Diagrama de componentes con interfaces

Cada puerto es una interfaz explícita. Los adaptadores son intercambiables sin tocar el núcleo (DIP).

```mermaid
graph LR
    Cliente(["Operador logístico<br/>(Postman / cliente REST)"])
    MSENVIOS(["ms-envios<br/>(HU-01 / HU-03)"])
    SUPA[("Supabase PostgreSQL<br/>esquema: eventos")]

    subgraph MSEVENTOS["ms-eventos"]
        direction TB

        CTRL["EventoController<br/><i>« adaptador entrada »</i>"]

        IUC1{{"« interface »<br/>RegistrarEventoUseCase"}}
        IUC2{{"« interface »<br/>ConsultarHistorialUseCase"}}

        TXD["RegistrarEventoTransaccional<br/><i>« decorador @Transactional »</i>"]
        SVC1["RegistrarEventoService"]
        SVC2["ConsultarHistorialService"]

        MAQ["MaquinaEstadosEnvio<br/><i>« servicio de dominio »</i>"]

        IREPO{{"« interface »<br/>EventoRepository"}}
        IENVR{{"« interface »<br/>EnvioRastreadoRepository"}}
        IOUT{{"« interface »<br/>OutboxRepository"}}
        IEST{{"« interface »<br/>EnvioEstadoPort"}}

        AREPO["EventoRepositoryAdapter"]
        AENVR["EnvioRastreadoRepositoryAdapter"]
        AOUT["OutboxRepositoryAdapter"]
        AEST["EnvioEstadoRestAdapter"]
        PROC["OutboxProcessor<br/><i>« @Scheduled »</i>"]
    end

    Cliente -->|"HTTP REST<br/>POST /api/v1/eventos<br/>GET /api/v1/envios/{n}/eventos"| CTRL
    CTRL --> IUC1
    CTRL --> IUC2
    IUC1 -.implementa.-> TXD
    TXD --> SVC1
    IUC2 -.implementa.-> SVC2

    SVC1 --> MAQ
    SVC1 --> IREPO
    SVC1 --> IENVR
    SVC1 --> IOUT
    SVC1 --> IEST
    SVC2 --> IREPO
    SVC2 --> IENVR

    IREPO -.implementa.-> AREPO
    IENVR -.implementa.-> AENVR
    IOUT -.implementa.-> AOUT
    IEST -.implementa.-> AEST

    PROC --> IOUT
    PROC --> IEST

    AREPO --> SUPA
    AENVR --> SUPA
    AOUT --> SUPA
    AEST -->|"HTTP REST<br/>GET /api/v1/envios/{n}<br/>PATCH /api/v1/envios/{n}/estado"| MSENVIOS
```

### Interfaces expuestas (provided)

| Interfaz | Operación | Consumidor |
|---|---|---|
| `POST /api/v1/eventos` | Registrar un evento logístico y mover el estado del envío | Operador del punto logístico |
| `GET /api/v1/envios/{numeroSeguimiento}/eventos` | Consultar el historial y el estado actual | Operador, Calidad |
| `GET /actuator/health` | Sonda de salud | Render, monitoreo |

### Interfaces consumidas (required)

| Interfaz | Proveedor | Uso |
|---|---|---|
| `GET /api/v1/envios/{numeroSeguimiento}` | `ms-envios` | Hidratar la proyección local cuando el envío aún no se conoce |
| `PATCH /api/v1/envios/{numeroSeguimiento}/estado` | `ms-envios` | Propagar el cambio de estado (asíncrono, vía outbox, con `idEvento` como clave de idempotencia) |

### Puertos internos (interfaces de la capa `application`)

| Puerto | Tipo | Implementación | Archivo |
|---|---|---|---|
| `RegistrarEventoUseCase` | Entrada | `RegistrarEventoTransaccional` → `RegistrarEventoService` | `application/port/in/` |
| `ConsultarHistorialUseCase` | Entrada | `ConsultarHistorialService` | `application/port/in/` |
| `EventoRepository` | Salida | `EventoRepositoryAdapter` (JPA) | `application/port/out/` |
| `EnvioRastreadoRepository` | Salida | `EnvioRastreadoRepositoryAdapter` (JPA) | `application/port/out/` |
| `OutboxRepository` | Salida | `OutboxRepositoryAdapter` (JPA) | `application/port/out/` |
| `EnvioEstadoPort` | Salida | `EnvioEstadoRestAdapter` (HTTP) | `application/port/out/` |

---

## 3. Vista de contexto (C4 nivel 1)

Ubica a `ms-eventos` dentro de la solución TrackFlow.

```mermaid
graph TB
    OP(["Operador de punto logístico"])
    CLI(["Cliente / destinatario"])

    subgraph TF["TrackFlow"]
        MSE["ms-eventos<br/><i>HU-02: eventos e historial</i>"]
        MSN["ms-envios<br/><i>HU-01, HU-03: ciclo de vida del envío</i>"]
    end

    DB[("Supabase PostgreSQL<br/>esquema eventos | esquema envios")]

    OP -->|registra eventos| MSE
    OP -->|registra envíos| MSN
    CLI -->|consulta estado| MSN
    MSE -->|REST: consulta y actualiza estado| MSN
    MSE -->|JDBC: solo su esquema| DB
    MSN -->|JDBC: solo su esquema| DB
```

**Regla dura:** cada microservicio accede **únicamente a su propio esquema**. `ms-eventos` nunca lee las tablas de `ms-envios` por SQL; toda información ajena entra por su API REST. Ver [ADR-003](../adr/ADR-003-persistencia-esquema-por-servicio.md).

---

## 4. Flujo de registro de un evento

```mermaid
sequenceDiagram
    actor OP as Operador
    participant C as EventoController
    participant S as RegistrarEventoService
    participant M as MaquinaEstadosEnvio
    participant DB as Supabase (esquema eventos)
    participant P as OutboxProcessor
    participant E as ms-envios

    OP->>C: POST /api/v1/eventos
    C->>S: registrar(comando)
    S->>DB: buscar envío en proyección local

    alt no está en la proyección local
        S->>E: GET /api/v1/envios/{n}
        alt 404
            S-->>OP: 404 Envío no encontrado
        else 200
            E-->>S: estado actual
        end
    end

    S->>M: siguienteEstado(estadoActual, tipoEvento)
    alt transición inválida o envío entregado
        M-->>OP: 409 Evento no aplicable
    end

    Note over S,DB: Una sola transacción
    S->>DB: guardar evento + estado local + fila outbox
    S-->>OP: 201 Created

    Note over P,E: Asíncrono, cada 5 s
    P->>DB: leer pendientes del outbox
    P->>E: PATCH /api/v1/envios/{n}/estado
    alt éxito
        P->>DB: marcar ENVIADO
    else fallo
        P->>DB: reprogramar con backoff exponencial
    end
```

El punto clave: el `201` se devuelve **antes** de hablar con `ms-envios`. Si ese servicio está caído, el evento igual queda registrado y la sincronización se recupera sola. Ver [ADR-002](../adr/ADR-002-integracion-resiliente-ms-envios.md).
