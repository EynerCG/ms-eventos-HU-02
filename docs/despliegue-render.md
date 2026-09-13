# Despliegue en Render — ms-eventos

- **Responsable:** Eyner Gómez Quintero
- **Fecha:** 2026-09-13
- **Entregable:** Sprint 1 — "despliegue inicial funcional" (lineamientos 3.7)

El servicio se despliega como **Web Service tipo Docker**, usando el `Dockerfile` multi-etapa de la raíz del proyecto. La definición declarativa está en [`render.yaml`](../render.yaml).

---

## 1. Prerrequisitos

- Cuenta en [render.com](https://render.com) (el plan gratuito basta).
- El repositorio del microservicio publicado en GitHub.
- Credenciales de la base de datos de Supabase, tomadas del **Session Pooler** (ver sección 3).

## 2. Crear el Web Service

1. En el dashboard de Render: **New** → **Web Service**.
2. **Connect a repository** → autorizar GitHub y seleccionar el repositorio de `ms-eventos`.
3. Configurar:

| Campo | Valor |
|---|---|
| **Name** | `trackflow-ms-eventos` |
| **Language / Runtime** | `Docker` |
| **Branch** | `main` |
| **Dockerfile Path** | `./Dockerfile` |
| **Instance Type** | `Free` |
| **Health Check Path** | `/actuator/health` |

> Si el microservicio queda dentro de una subcarpeta del repositorio (por ejemplo `ms-eventos/`), hay que fijar además **Root Directory** en esa carpeta.

## 3. Variables de entorno

En la sección **Environment** del servicio, agregar las cuatro variables:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://aws-0-<región>.pooler.supabase.com:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` | `postgres.<project-ref>` |
| `SPRING_DATASOURCE_PASSWORD` | La contraseña de la base de datos |
| `MS_ENVIOS_BASE_URL` | La URL pública de `ms-envios` en Render |

> **Crítico — no usar la conexión directa.** El connection string directo de Supabase (`db.<project-ref>.supabase.co`) resuelve **solo por IPv6**, y falla desde redes IPv4 con un timeout silencioso de HikariCP (el log solo repite `HikariPool-1 - Starting...` sin error claro). Hay que usar el **Session pooler**: en el panel de Supabase, botón **Connect** → **Método de conexión** → **Agrupador de sesiones**. El host cambia a `aws-0-<región>.pooler.supabase.com` y el usuario pasa de `postgres` a `postgres.<project-ref>`.
>
> Se elige *Session pooler* y no *Transaction pooler* porque este último no conserva el estado de sesión que Hibernate necesita.

Nunca versionar estas credenciales: Render las inyecta en tiempo de ejecución y `render.yaml` las declara con `sync: false` justamente para que no viajen en el repositorio.

## 4. Desplegar y verificar

1. **Create Web Service**. El primer build tarda varios minutos (compila con Maven dentro del contenedor).
2. Al terminar, Render entrega una URL del tipo `https://trackflow-ms-eventos.onrender.com`.
3. Verificar la sonda de salud:

```bash
curl https://trackflow-ms-eventos.onrender.com/actuator/health
```

Debe responder:

```json
{"status":"UP","groups":["liveness","readiness"]}
```

4. En los logs de Render debe verse que Flyway aplicó las migraciones:

```
Successfully applied 3 migrations to schema "eventos"
```

## 5. Probar los criterios de aceptación contra el entorno desplegado

Importar [`postman/TrackFlow-HU02-ms-eventos.postman_collection.json`](../postman/TrackFlow-HU02-ms-eventos.postman_collection.json) y cambiar la variable `baseUrl` de la colección a la URL de Render.

Los envíos semilla (`TRK-SEED-0001` a `TRK-SEED-0004`) ya quedan cargados por la migración `V2`, así que la HU-02 se puede demostrar sin que `ms-envios` esté desplegado.

## 6. Limitaciones del plan gratuito

| Limitación | Impacto | Mitigación |
|---|---|---|
| El servicio **se duerme tras 15 minutos** sin tráfico | La primera petición puede tardar ~50 s y Postman puede reportar timeout | Hacer una petición de calentamiento a `/actuator/health` **antes** de la demostración del sprint review |
| 512 MB de RAM | Suficiente para este servicio | El `Dockerfile` fija `-XX:MaxRAMPercentage=75` para que la JVM respete el límite del contenedor |
| Sin IP fija de salida | Irrelevante aquí: Supabase no restringe por IP en el plan usado | — |

El arranque en frío también afecta la comunicación entre microservicios: si `ms-envios` está dormido, el primer `PATCH` del `OutboxProcessor` fallará por timeout. No es un problema: el outbox reintenta con backoff exponencial y sincroniza en cuanto el otro servicio despierta (ver [ADR-002](adr/ADR-002-integracion-resiliente-ms-envios.md)).

## 7. Despliegue automático

`render.yaml` declara `autoDeploy: true`: cada push a `main` dispara un nuevo despliegue. Para el Sprint 2, cuando exista el pipeline de GitHub Actions, conviene condicionar el despliegue a que las pruebas pasen primero (lineamiento 7.2: *"La integración se bloquea si falla la compilación, las pruebas obligatorias..."*).
