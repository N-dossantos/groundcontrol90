# Diseño — Repo público y demo del frontend en Vercel

| | |
|---|---|
| **Fecha** | 2026-08-25 |
| **Estado** | Aprobado por el usuario en brainstorming, pendiente de plan de implementación |
| **Alcance** | Etapa 1 de dos. La etapa 2 (deploy real en VPS con Caddy + Compose) queda fuera de este documento. |

## 1. Contexto y la restricción que define el diseño

El objetivo del usuario es publicar el repositorio en GitHub como repo público y tener una URL
donde probar la aplicación antes de exponerla al público general.

Vercel **no puede correr este backend**. El stack es Spring Boot 3.2 sobre Java 17, con MySQL,
Flyway y uploads a disco, diseñado para Docker Compose detrás de Caddy en un VPS. Vercel sirve
estáticos y funciones Node/Python/Go: no hay dónde ejecutar el `.jar` ni dónde correr MySQL.

De ahí que el trabajo se parta en dos etapas, decididas con el usuario:

- **Etapa 1 (este documento)**: repo público en GitHub + demo del frontend en Vercel, con el
  backend simulado dentro del propio frontend.
- **Etapa 2 (futura)**: el deploy real y completo en un VPS con Caddy + Compose, tal como ya está
  documentado en el runbook del `README.md`, `docs/superpowers/plans/fase5.md` y
  `checklist-go-live.md`. El servidor
  todavía no está definido; se planifica por separado.

Vercel no participa de la etapa 2. La demo y la aplicación real son destinos distintos.

## 2. Estado de partida verificado

Antes de diseñar se auditó el repositorio, porque hacerlo público expone toda la historia:

- **Sin secretos en los 74 commits.** No hay `.env` versionado (sólo `.env.example`, sin valores),
  ni tokens de Mercado Pago (`APP_USR-`/`TEST-`), ni claves AWS, ni claves privadas. Los
  `jwt.secret` que aparecen en la historia son placeholders de desarrollo, explícitamente marcados
  como tales (`dev-only-secret-key-no-usar-en-produccion...`).
- **Sin artefactos de build versionados**: ni `node_modules/`, ni `dist/`, ni `backend/target/`.
- **`.claude/` y `.superpowers/` no están trackeados**, así que los worktrees de agentes y el
  scratch de SDD no se publican.
- **No existe `LICENSE`**, aunque el README declara licencia MIT.
- **No existe `vercel.json`.**

Tres hallazgos del código del frontend condicionan el diseño:

1. **`request()` (`frontend/src/services/api.js:14`) es un único punto de paso.** Los ~38 endpoints
   que consume el frontend pasan por esa función; la única excepción es `uploadImage()`, que usa
   `fetch` directo porque el body es `FormData`.
2. **El carrito ya es client-side** (`CartContext` + `cartReducer` + `localStorage`). Sólo el
   checkout pega a la API, así que el carrito no necesita simulación.
3. **Los productos sembrados por `DataInitializer` usan URLs de Unsplash**, no imágenes subidas al
   servidor. La demo se ve con fotos reales sin necesitar almacenamiento de archivos.

## 3. Decisión: dónde se enchufa la simulación

Se evaluaron tres opciones y el usuario aprobó la primera.

**Elegida — interceptar dentro de `request()`.** Un branch al principio de `request()` y otro en
`uploadImage()` derivan a un módulo nuevo. Todo lo que viene después —`mapProduct`,
`buildProductPayload`, el manejo de errores, los 38 métodos del objeto `api`— se ejecuta igual que
en producción. La demo ejerce el código real del frontend; lo único simulado es la respuesta HTTP.

**Descartada — interceptar a nivel `fetch`** (MSW o monkeypatch de `window.fetch`). No tocaría
`api.js`, pero suma una dependencia nueva o un parche global frágil, y MSW implica un service
worker: otra pieza que puede fallar en Vercel y que complica el diagnóstico. Sobreingeniería para
una demo estática.

**Descartada — una segunda implementación del objeto `api`.** Duplicaría los 38 métodos con sus
mapeos, se desincronizaría al primer cambio, y la demo dejaría de ejercer el código real del
frontend, que es justamente lo que se quiere mostrar.

### Por qué esto no es un stopgap

`AGENTS.md` prohíbe los arreglos provisorios pensados para ser reemplazados. El modo demo no lo es:
es una capacidad permanente del producto. Cuando el VPS exista, la demo sigue teniendo valor —
permite recorrer la aplicación sin crear cuenta y sin tocar datos reales. No se elimina al llegar la
etapa 2; simplemente convive con el deploy real, en otro dominio y con la variable de entorno
apagada del lado del VPS.

## 4. Arquitectura del modo demo

### 4.1 Módulos nuevos

Todo vive bajo `frontend/src/services/demo/`:

| Archivo | Responsabilidad |
|---|---|
| `seed.js` | Datos iniciales portados de `DataInitializer.java`: 4 categorías, 6 productos con variantes de talle y stock, 3 usuarios. Función pura, sin estado. |
| `store.js` | Estado mutable persistido en `localStorage` bajo la key versionada `demo-state-v1`. Si falta o cambió la versión, re-siembra desde `seed.js`. Expone lectura, escritura y reset. |
| `router.js` | Tabla `método + patrón de endpoint → handler`. Resuelve una llamada a una respuesta. Es el único módulo que conoce los endpoints. |
| `handlers/` | Un archivo por área del contrato: `auth.js`, `productos.js`, `pedidos.js`, `pagos.js`, `ventas.js`, `admin.js`, `direcciones.js`. |
| `errors.js` | Construye errores con el mismo shape `{ error, message }` que espera `request()`, para 401, 403, 404 y 409. |

**Contrato**: los handlers devuelven objetos con la forma de los **DTOs del backend**, no con la del
frontend. Es decir `categoriaId`, `categoriaNombre`, `ownerUserNombre`, `variantes[].talle`, etc.
Así `mapProduct` y el resto de `api.js` no se tocan, y el día que la demo apunte a un backend real
el contrato es el mismo.

### 4.2 Alcance funcional

Funciona de punta a punta contra el store:

- Catálogo, búsqueda por nombre, filtros por categoría y por atributos, detalle de producto
- Login con las 3 cuentas sembradas, registro (crea usuario en el store), validate, logout
- Checkout: crea el pedido, descuenta stock de la variante elegida, rechaza si no alcanza
- Mis pedidos, detalle de pedido, cancelar (devuelve el stock reservado)
- Publicar, editar y borrar productos propios (dashboard de vendedor)
- Mis ventas, estadísticas de ventas, cambio de estado de un detalle
- Panel de administración: listado de usuarios, cambio de rol, alta y baja, reportes
- Subida de imagen: `uploadImage` lee el archivo con `FileReader` y devuelve un `data:` URL, así la
  imagen se ve sin ningún servidor de archivos

Se simula de forma explícita:

- **Mercado Pago.** `POST /pagos/pedidos/:id/preferencia` devuelve una URL a la ruta interna que la
  aplicación ya tiene (`/checkout/resultado?status=approved&demo=1`) en lugar de a
  `mercadopago.com`, y
  `GET /pagos/pedidos/:id/estado` responde aprobado. Nadie llega a un checkout real ni puede creer
  que pagó.
- **Emails transaccionales.** No se envían. Ningún endpoint del frontend los dispara directamente,
  así que no hay nada que simular más allá de no romper.

**Autenticación simulada**: el token es un string opaco `demo-token-<userId>`. Los handlers lo leen
para resolver identidad y rol. No se firma ni se valida nada criptográficamente; el modo demo no
tiene ningún dato que proteger.

### 4.3 Activación y aislamiento del build de producción

En `api.js`:

```js
const DEMO = import.meta.env.VITE_DEMO_MODE === 'true'
```

Vite sustituye `import.meta.env.VITE_DEMO_MODE` en tiempo de build. En el build del VPS la variable
no existe, `DEMO` queda como constante `false`, y Rollup elimina tanto el branch como el `import()`
dinámico del router. **El código de la demo no viaja al bundle de producción, ni siquiera como chunk
separado.** Esto es un requisito verificable, no una expectativa: ver sección 7.

El router se carga con `import()` dinámico, no estático, precisamente para que esa eliminación sea
posible.

### 4.4 Señalización al usuario

Un banner fijo, visible en todas las pantallas mientras el modo demo esté activo:

> **Modo demo** — datos de ejemplo, sin backend real. Ningún pago es real.

No es negociable. La aplicación se presenta como un ecommerce; sin el banner, una demo pública que
acepta "pagos" es engañosa.

## 5. Configuración de Vercel

- **`frontend/vercel.json`**: rewrite de `/(.*)` a `/index.html`. Sin eso, entrar directo a una ruta
  como `/product/3` devuelve 404, porque React Router rutea del lado del cliente. Más headers de
  seguridad básicos (`X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`).
- **Proyecto**: Root Directory `frontend`, framework Vite, build `npm run build`, output `dist`.
- **Variable de entorno**: `VITE_DEMO_MODE=true` en Production y Preview.
- **Dominio**: el `*.vercel.app` que asigna Vercel. Sin dominio propio en esta etapa.
- `.gitignore` ya incluye `.vercel`.

## 6. Publicación del repositorio

- El usuario crea el repositorio **vacío** en github.com —sin README ni `.gitignore`, para evitar un
  conflicto en el primer push— y pasa la URL.
- Se agrega el remote y se pushea **`main` con la historia completa**, ya auditada en la sección 2.
- **No se publican** las ramas `fase3-notificaciones-legal` ni `fase5-auditoria-pre-deploy` (ya
  mergeadas en `main`), ni las ramas `worktree-agent-*`.
- Se agrega **`LICENSE`** con el texto MIT, que el README ya promete.
- Se agrega al README una sección con el link a la demo, qué se puede probar ahí y qué está
  simulado, y la aclaración de que el deploy real es el del VPS.

## 7. Verificación

Ninguna afirmación de "listo" sin estas comprobaciones:

1. `npm run build` en `frontend/` **sin** `VITE_DEMO_MODE`, seguido de un grep sobre `dist/` con un
   string único del seed (por ejemplo `BOCA-2026-TIT`). **Debe no encontrar nada**: prueba de que el
   mock no viaja a producción.
2. `npm run build` **con** `VITE_DEMO_MODE=true` y el mismo grep. Debe encontrarlo.
3. `npm run lint`. Los `no-unused-vars` de imports usados en JSX son ruido preexistente en todo el
   repo y no cuentan como regresión; cualquier warning nuevo de otra clase, sí.
4. Recorrido manual de la demo ya desplegada en Vercel: `/` → `/product/:id` → agregar al carrito →
   `/cart` → checkout → `/checkout/resultado` → `/orders` → cancelar → `/dashboard/products` →
   `/admin`.
5. Verificación de que una ruta profunda (`/product/3`) cargada directamente por URL no da 404.

El backend no se modifica en esta etapa, así que la suite de Maven no forma parte del criterio de
aceptación. Se corre igual una vez, para confirmar que nada se rompió por accidente.

## 8. Fuera de alcance

- El deploy en VPS, Caddy, DNS, TLS y secretos de producción (etapa 2).
- Cualquier cambio en el backend Java.
- Dominio propio, tanto para la demo como para producción.
- Credenciales reales de Mercado Pago y el flujo de pago real.
- Reemplazar el mock por un backend desplegado en un PaaS: se evaluó y el usuario lo descartó.
