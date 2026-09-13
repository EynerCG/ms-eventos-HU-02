# ADR-001 — Arquitectura Limpia dentro de un microservicio delimitado

| | |
|---|---|
| **Estado** | Aceptada |
| **Prioridad** | Alta |
| **Responsable** | Eyner Gómez Quintero |
| **Fecha** | 2026-09-13 |
| **Sprint** | 1 |
| **Historia relacionada** | HU-02 — Registrar un evento logístico de un envío (work item 58) |

## Contexto

El Sprint 1 de TrackFlow reparte tres historias entre dos microservicios: `ms-envios` (HU-01 registrar envío, HU-03 consultar estado) y `ms-eventos` (HU-02 registrar evento logístico). Este ADR decide cómo se organiza internamente `ms-eventos`.

La HU-02 no es un CRUD. Su valor está en una **máquina de estados con reglas de negocio no triviales**:

- Un envío en `REGISTRADO` solo admite una `RECOGIDA`; no puede saltar a `ENTREGADO`.
- Un envío ya `ENTREGADO` es terminal: no admite más eventos.
- Un evento con fecha anterior al último registrado rompe la secuencia del historial y debe rechazarse.
- Cada tipo de evento determina un estado resultante distinto según el estado de origen.

Ese conjunto de reglas es lo que más va a cambiar cuando el negocio agregue nuevos puntos de la cadena logística, y es lo que el curso de Calidad debe poder probar exhaustivamente. Los lineamientos (sección 3.1) exigen además "mantener una separación explícita mediante arquitectura limpia, hexagonal, por capas, monolito modular o un microservicio claramente delimitado".

## Alternativas consideradas

### A. Arquitectura en capas tradicional (`controller → service → repository`)

Es el patrón del proyecto de referencia del curso de Calidad ([SauceDemo](https://github.com/juanfedevmaster/calidad-de-software/tree/main/SauceDemo)): paquetes `controller`, `service`, `repository`, `model`, `dto`.

- **A favor:** familiar para todo el equipo, menos archivos, arranque más rápido, es lo que la mayoría de tutoriales de Spring Boot enseñan.
- **En contra:** el `service` depende directamente de la implementación concreta del repositorio y las entidades son JPA (dominio anémico). La regla de negocio queda enredada con la persistencia, lo que obliga a levantar contexto de Spring y base de datos para probar una transición de estado. Cambiar de proveedor de persistencia implica tocar la lógica de negocio.

### B. Arquitectura Hexagonal pura (Ports & Adapters de Alistair Cockburn)

- **A favor:** el mismo aislamiento del dominio, con vocabulario de puertos y adaptadores bien establecido.
- **En contra:** en la práctica, para un servicio de este tamaño, la diferencia con Clean Architecture es principalmente de nomenclatura. Clean Architecture añade la separación explícita de *casos de uso* como capa propia, que encaja mejor con el mapeo uno a uno entre historia de usuario y caso de uso que pide la trazabilidad del proyecto.

### C. Arquitectura Limpia dentro de un microservicio delimitado *(elegida)*

Cuatro capas —`domain`, `application`, `infrastructure`, `api`— con la regla de dependencias apuntando hacia adentro, y el servicio acotado a una sola capacidad de negocio.

- **A favor:** aísla la máquina de estados en Java puro; permite probar las reglas sin framework; cada historia de usuario mapea a un caso de uso nombrado; cumple explícitamente el lineamiento 3.1.
- **En contra:** más archivos e indirección; requiere disciplina del equipo para no "atajar" la regla de dependencias; curva de aprendizaje para quien no la conoce.

## Elección

Se adopta la **alternativa C**.

El factor decisivo es la **testabilidad de la regla de negocio**. El curso de Calidad exige cobertura unitaria ≥ 65 % y criterios de aceptación refinados en Gherkin. Con la alternativa A, probar las 18 combinaciones de transiciones válidas e inválidas exigiría levantar Spring y una base de datos en cada caso. Con la alternativa C, `MaquinaEstadosEnvioTest` las cubre todas con JUnit puro en menos de medio segundo.

El segundo factor es el **acoplamiento con `ms-envios`**. Al definir `EnvioEstadoPort` como interfaz en la capa `application`, el núcleo del servicio no sabe que del otro lado hay HTTP. Si Oliver y Julián cambian su contrato, el impacto queda contenido en un único adaptador.

Concreciones de la decisión:

- `domain` y `application` no importan `org.springframework.*` ni `jakarta.persistence.*`.
- Los beans se instancian explícitamente en `infrastructure/config/BeanConfig.java`.
- La frontera transaccional vive en el decorador `RegistrarEventoTransaccional` (infraestructura), no en el caso de uso.

## Consecuencias

**Positivas**

- La regla de negocio se prueba de forma aislada, rápida y determinista; ninguna prueba necesita Supabase ni internet.
- Agregar un tipo de evento nuevo toca dos archivos del dominio; ni el controlador ni el caso de uso cambian (OCP).
- El servicio puede cambiar de base de datos o de cliente HTTP sin tocar el núcleo.
- La estructura de paquetes sirve como documentación: al abrir el repo se ve dónde vive cada responsabilidad.

**Negativas**

- Más archivos que un CRUD equivalente (unas 40 clases de producción para una sola historia).
- El equipo debe conocer la regla de dependencias; un `import` mal puesto rompe el aislamiento sin que el compilador avise.
- El decorador transaccional añade una indirección que puede confundir a quien lea el código por primera vez.

**Mitigaciones**

- Los diagramas de paquetes y componentes ([enlace](../arquitectura/diagrama-paquetes-componentes.md)) documentan visualmente la regla de dependencias.
- Se evaluará para el Sprint 2 agregar una prueba de arquitectura automatizada (ArchUnit) que falle el build si `domain` o `application` importan clases de framework.

## Referencias

- Lineamientos Integrados CodeF@ctory v2.0, secciones 3.1 y 4.2
- [diagrama-paquetes-componentes.md](../arquitectura/diagrama-paquetes-componentes.md)
- [estilo-arquitectonico.md](../arquitectura/estilo-arquitectonico.md)
