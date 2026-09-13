# ADR-003 — PostgreSQL en Supabase con esquema exclusivo por servicio y migraciones Flyway

| | |
|---|---|
| **Estado** | Aceptada |
| **Prioridad** | Media |
| **Responsable** | Eyner Gómez Quintero |
| **Fecha** | 2026-09-13 |
| **Sprint** | 1 |
| **Historia relacionada** | HU-02 — Registrar un evento logístico de un envío (work item 58) |

## Contexto

TrackFlow se construye como dos microservicios (`ms-envios` y `ms-eventos`) que comparten un mismo proyecto de Supabase, porque el plan gratuito y el alcance académico no justifican dos instancias separadas de base de datos.

Eso plantea la pregunta de **cómo se reparte el espacio de datos** entre servicios que deben poder desplegarse y evolucionar de forma independiente, y **quién es dueño de la evolución del esquema** (el equipo de Bases de Datos administra el proyecto de Supabase, pero cada servicio necesita crear y versionar sus propias tablas sin bloquearse esperando a otro frente).

Los lineamientos aplicables:

- 1.3: *"PostgreSQL; Supabase o Neon como opción de servicio administrado"*.
- 5.3: *"Definir respaldo, restauración, **migraciones versionadas** y pruebas de recuperación"*.
- 3.1: exige microservicios claramente delimitados.

## Alternativas consideradas

### A. Esquema `public` compartido entre ambos servicios

Todas las tablas juntas, ambos servicios leyendo y escribiendo donde necesiten.

- **A favor:** lo más simple; permite joins directos entre envíos y eventos; una sola consulta resuelve el historial completo con el estado.
- **En contra:** es **acoplamiento por base de datos**, el peor tipo en microservicios porque es invisible en el código. Si `ms-envios` renombra una columna, `ms-eventos` se rompe en tiempo de ejecución sin que el compilador ni las pruebas avisen. Ninguno de los dos puede migrar su esquema sin coordinar con el otro. En la práctica convierte la solución en un monolito distribuido.

### B. Dos proyectos de Supabase independientes (una base de datos por servicio)

- **A favor:** el aislamiento más fuerte posible; es la forma canónica del patrón *database per service*.
- **En contra:** duplica la administración (dos proyectos, dos juegos de credenciales, dos respaldos) para un proyecto académico de dos semanas; el equipo de Bases de Datos tendría que mantener dos entornos; el plan gratuito de Supabase limita el número de proyectos activos y los pausa por inactividad.

### C. Un proyecto de Supabase, un esquema exclusivo por servicio *(elegida)*

`ms-eventos` es dueño exclusivo del esquema `eventos`; `ms-envios` del suyo. Ningún servicio accede al esquema del otro por SQL.

- **A favor:** conserva el aislamiento lógico y la independencia de despliegue, con el costo operativo de un solo proyecto; PostgreSQL garantiza la separación por permisos a nivel de esquema.
- **En contra:** el aislamiento es por convención y permisos, no físico: un desarrollador con credenciales amplias *puede* hacer un join entre esquemas y romper la regla sin que nada lo impida.

### D. Gestión del esquema por el equipo de Bases de Datos mediante scripts manuales

- **A favor:** centraliza el modelado en quienes tienen la responsabilidad académica del curso de Bases de Datos.
- **En contra:** bloquea a Arquitectura: cada cambio de tabla exige coordinar con otro frente. Además, incumple el lineamiento 5.3 sobre migraciones versionadas, porque los scripts sueltos no dejan traza de qué versión está aplicada en cada entorno.

## Elección

Se adopta la **alternativa C** para el reparto de datos, combinada con **Flyway** para las migraciones.

Concreciones:

1. **Esquema `eventos`**, propiedad exclusiva de `ms-eventos`, con tres tablas: `evento` (historial), `envio_rastreado` (proyección local, ver [ADR-002](ADR-002-integracion-resiliente-ms-envios.md)) y `outbox_estado` (cola de sincronización).
2. **Regla dura:** `ms-eventos` nunca ejecuta SQL contra tablas de otro servicio. Todo dato ajeno entra por la API REST de su dueño.
3. **Migraciones Flyway versionadas dentro del propio repositorio** (`src/main/resources/db/migration/`), de modo que el servicio crea y evoluciona su esquema al arrancar, en cualquier entorno, sin intervención manual.
4. **Datos semilla** (`V2__datos_semilla.sql`) con cuatro envíos de prueba en distintos estados, para que Calidad pueda probar la HU-02 sin depender del despliegue de HU-01.
5. **Conexión por Session Pooler** de Supabase (`aws-0-<región>.pooler.supabase.com`), no por conexión directa: la directa resuelve solo por IPv6 y falla desde redes IPv4, como se comprobó en pruebas.

## Consecuencias

**Positivas**

- Cada servicio evoluciona su modelo sin coordinar con el otro ni pedir permiso al equipo de Bases de Datos para cada cambio.
- Las migraciones quedan versionadas junto al código: cualquiera que clone el repo y apunte a una base vacía obtiene el esquema correcto y reproducible.
- Los datos semilla desbloquearon las pruebas de la HU-02 antes de que `ms-envios` existiera.
- Los permisos de PostgreSQL a nivel de esquema permiten hacer cumplir técnicamente el aislamiento, otorgando a cada servicio una cuenta con mínimo privilegio (lineamiento 5.3).

**Negativas**

- **No se pueden hacer joins entre envíos y eventos.** Consultas que en un monolito serían un `JOIN` trivial ahora requieren dos llamadas o una proyección local. Es el costo consciente de la independencia.
- El aislamiento depende de disciplina y de permisos bien configurados; nada impide técnicamente un join entre esquemas si las credenciales son amplias.
- Un fallo del proyecto de Supabase afecta a ambos servicios: no hay aislamiento de disponibilidad, solo lógico.
- Si el equipo de Bases de Datos entrega su propio `schema.sql`, hay que conciliarlo con las migraciones Flyway para evitar dos fuentes de verdad del modelo.

**Acciones pendientes**

| Acción | Responsable |
|---|---|
| Solicitar al equipo de BD un usuario con permisos limitados al esquema `eventos` (hoy se usa `postgres`, que viola el mínimo privilegio) | Eyner |
| Acordar que `schema.sql` del entregable de BD y las migraciones Flyway no se contradigan | Eyner + equipo de BD |
| Definir política de respaldo y restauración (lineamiento 5.3) | Equipo de BD |

## Referencias

- Lineamientos Integrados CodeF@ctory v2.0, secciones 1.3, 5.2 y 5.3
- Patrón *Database per Service* (microservices.io)
- [ADR-002](ADR-002-integracion-resiliente-ms-envios.md)
- [Modelo de datos](../base-de-datos/README.md)
