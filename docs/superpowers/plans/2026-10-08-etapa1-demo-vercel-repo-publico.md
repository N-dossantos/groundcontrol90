# Etapa 1 — Repo público y demo en Vercel: Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar el repositorio en GitHub y desplegar en Vercel una demo del frontend que funcione de punta a punta contra un backend simulado dentro del navegador, sin que ese código llegue al build de producción del VPS.

**Architecture:** `request()` y `uploadImage()` de `frontend/src/services/api.js` derivan, cuando `__DEMO_MODE__` es `true`, a un router en `frontend/src/services/demo/` que devuelve un `Response` real con DTOs del backend; todo el resto de `api.js` (mapeos, manejo de errores) corre igual que en producción. El estado vive en `localStorage` (`demo-state-v1`) y se siembra con los datos de `DataInitializer.java`. `__DEMO_MODE__` es un `define` de Vite que vale el literal `false` sin `VITE_DEMO_MODE=true`, y Rollup elimina el código de la demo del bundle.

**Tech Stack:** React 18, Vite 4.5, React Router 6.30, Vitest 0.34.6 (sólo dev), Vercel (hosting estático), GitHub.

**Spec:** `docs/superpowers/specs/2026-08-25-demo-vercel-repo-publico-design.md`

## Global Constraints

- Código, comentarios, nombres de dominio y mensajes de commit **en español**. Commits con prefijo convencional (`feat:`, `fix:`, `test:`, `ci:`, `docs:`, `build:`).
- Todo commit termina con la línea `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (en los comandos va como segundo `-m`).
- **No se modifica nada en `backend/`.**
- **Nunca** una URL absoluta con `localhost` en el frontend.
- Los handlers de la demo devuelven objetos con la forma de los **DTOs del backend** (`categoriaId`, `categoriaNombre`, `ownerUserNombre`, `variantes[].talle`, `estadoItem`, …), nunca con los nombres del frontend. `mapProduct` y el resto de `api.js` no se tocan para acomodar la demo.
- Los errores de la demo tienen el shape del `GlobalExceptionHandler`: `{ status, error, message }`.
- Key de `localStorage` del estado: `demo-state-v1`.
- Texto del banner, literal: **Modo demo** — datos de ejemplo, sin backend real. Ningún pago es real.
- Sin `VITE_DEMO_MODE=true`, `dist/` **no contiene** `BOCA-2026-TIT`, `Modo demo` ni `demo-token-`. Con la variable, sí.
- Sin dependencias de runtime nuevas. La única dependencia nueva es `vitest@0.34.6` en `devDependencies` (la última que acepta Vite 4; verificado que corre con Vite 4.5.14 en Node 26).
- `npm run lint` con **0 errors**. Los warnings `no-unused-vars` de identificadores usados sólo en JSX son ruido preexistente y no cuentan.
- Al publicar se pushea **sólo `main`**. Ni las ramas `fase*`, ni las `worktree-agent-*`.
- Los comandos de frontend se corren desde `frontend/`; los de git, desde la raíz del repo.

## Desvíos respecto de la spec (y por qué)

1. **`__DEMO_MODE__` (define de Vite) en lugar de `import.meta.env.VITE_DEMO_MODE`.** La spec da por hecho que con `import.meta.env` Rollup elimina el `import()` dinámico. **No es así en Vite 4.5:** se probó en un proyecto mínimo y el chunk `router-*.js` se emite igual en `dist/` sin la variable. Con `define: { __DEMO_MODE__: JSON.stringify(process.env.VITE_DEMO_MODE === "true") }` el chunk desaparece, tanto con el import dinámico como con un import estático usado sólo en `__DEMO_MODE__ && …`. El requisito de la spec (§4.3) se mantiene; cambia el mecanismo.
2. **`initPoint` = `/checkout/resultado?pedidoId=<id>`.** La URL de la spec (`?status=approved&demo=1`) no lleva `pedidoId`, y `CheckoutResultado.jsx` sin ese parámetro muestra "Falta el número de pedido".
3. **Cuatro arreglos en el frontend real (Tasks 1 y 2).** Sin ellos el recorrido de la §7 falla, y también fallan en producción:
   - `request()` explota con `response.json()` en las respuestas **204** sin body: borrar un producto lo borra pero muestra "Error al eliminar el producto".
   - `request()` muestra `errorData.error` (el título genérico: "Unauthorized", "Bad Request") antes que `errorData.message` (el detalle: "Credenciales inválidas").
   - `validateToken` manda un **GET** a `/auth/validate`, que en `AuthController` es `@PostMapping`: la validación siempre falla y la sesión guardada se borra.
   - `ProtectedRoute` redirige a `/login` en el primer render, antes de que termine la restauración de la sesión (que es asíncrona). Cualquier carga completa de una ruta protegida expulsa al usuario: un link directo a `/product/3`, o la vuelta del pago a `/checkout/resultado`, que llega con `window.location.href`.
4. **Pedidos sembrados.** Además de los datos de `DataInitializer`, el seed trae tres pedidos (uno entregado, uno confirmado y uno pendiente). Si no, la demo arranca con Mis ventas y los reportes vacíos, y el paso "cancelar" de la §7 queda imposible: el pago simulado confirma el pedido apenas se crea, y sólo se cancelan los `PENDIENTE`.
5. **Sólo los endpoints que alguna pantalla usa.** Nadie llama a `/direcciones`, a `/productos/filtrar` ni a los listados de pedidos o ventas del admin (se verificó con un grep de `api.*(` en `pages/`, `components/` y `context/`). Esos endpoints no se simulan y responden **501** con un mensaje claro. No hay `handlers/direcciones.js`.
6. **Módulos extra y renombrados:** `respuestas.js` reemplaza a `errors.js` (además de los errores construye las respuestas OK). Se suman `dto.js` (DTOs compartidos entre handlers), `sesion.js` (token → usuario), `fechas.js` (fechas con el formato de `LocalDateTime`) e `imagenes.js` (subida simulada). La imagen se lee con `file.arrayBuffer()` + `btoa` en vez de `FileReader`: el resultado es el mismo `data:` URL y así se puede testear en Node.
7. **Banner con "Reiniciar demo" y cuentas de prueba en el login.** Si no, quien entra no sabe con qué credenciales loguearse, y quien deja la demo inutilizable (borra todos los productos o se elimina a sí mismo como admin) no tiene forma de volver al estado inicial. El banner va arriba de todo, en el flujo normal de la página, y no `position: fixed`, para no tapar el header.
8. **La demo respeta `categoriaId` al crear o editar un producto.** El backend real **lo ignora**: la entidad `Producto` no mapea `categoriaId`, así que un producto creado desde la UI queda sin categoría, y uno editado la pierde. Es un bug del backend, fuera del alcance de esta etapa (ver "Hallazgos fuera de alcance"). La demo implementa el contrato que espera `buildProductPayload`.
9. **Vitest + CI del frontend.** El frontend no tenía tests. La lógica de la demo (stock, transiciones de estado, permisos) los necesita, y sin CI se pudren. Un workflow nuevo `frontend-ci.yml` los corre y se suma como gate de `build-and-push.yml`.

## Hallazgos fuera de alcance (para el backlog)

- **Backend:** `ProductoController` recibe la entidad `Producto` y descarta `categoriaId` (Jackson ignora propiedades desconocidas). Los productos creados desde la UI no tienen categoría, y editar uno se la borra.
- **Frontend:** el modo registro de `Login.jsx` no manda `aceptaTerminos`, así que el backend lo rechaza siempre con 400. El camino que funciona es `/register`.
- **Frontend:** `getProduct`, `updateProduct` y `deleteProduct` buscan `'404'` dentro del mensaje de error, pero el backend nunca lo incluye, así que esa rama no se ejecuta nunca.

## Review Focus

Los casos que la spec implica pero ningún paso de la §7 cubre, en orden de probabilidad:

1. **Recarga completa de una ruta protegida** (link directo, F5 o la vuelta del pago con `window.location.href`): la sesión se mantiene y el usuario queda en esa ruta, no en `/login`. Lo fijan los tests de la Task 2 y la verificación en Vercel de la Task 13.
2. **Estado de la demo corrupto o ausente en `localStorage`** (JSON roto, key borrada a mano): se vuelve a sembrar y la app sigue funcionando. Nada de pantalla en blanco. Test en la Task 3.
3. **Un pedido que pide más stock del disponible, aunque esté repartido en dos líneas del mismo talle:** se rechaza **sin descontar nada** y sin crear el pedido, igual que el rollback transaccional del backend. Test en la Task 6.
4. **Una imagen que no entra en `localStorage`:** mensaje claro (límite de 1 MB, o "almacenamiento lleno"), y el estado guardado queda intacto. Tests en las Tasks 3 y 9.
5. **Token de un usuario que ya no existe** (el admin lo eliminó) **y demo inutilizable:** el token inválido da 401 limpio (la sesión se cierra en vez de romper pantallas), y "Reiniciar demo" devuelve todo al estado inicial. Test en la Task 4; banner en la Task 9.

---

## Mapa de archivos

| Archivo | Acción | Responsabilidad |
|---|---|---|
| `frontend/package.json` | Modificar | script `test`, devDependency `vitest` |
| `frontend/vite.config.js` | Modificar | `define` de `__DEMO_MODE__` |
| `frontend/eslint.config.mjs` | Modificar | global `__DEMO_MODE__` |
| `frontend/src/services/api.js` | Modificar | arreglos de `request()`/`validateToken`, branch demo |
| `frontend/src/services/api.test.js` | Crear | tests de `request()` y `validateToken` |
| `frontend/src/context/sesionGuardada.js` | Crear | leer la sesión guardada en storage |
| `frontend/src/context/sesionGuardada.test.js` | Crear | tests |
| `frontend/src/reducers/authReducer.js` | Modificar | flag `restoring` |
| `frontend/src/reducers/authReducer.test.js` | Crear | tests |
| `frontend/src/context/AuthContext.jsx` | Modificar | arranca en `restoring` si hay sesión guardada |
| `frontend/src/components/ProtectedRoute.jsx` | Modificar | espera la restauración |
| `frontend/src/test/storageEnMemoria.js` | Crear | `localStorage` falso para tests |
| `frontend/src/test/llamarDemo.js` | Crear | helper para llamar al router desde los tests |
| `frontend/src/services/demo/fechas.js` | Crear | fechas con formato `LocalDateTime` |
| `frontend/src/services/demo/respuestas.js` | Crear | `DemoError` y helpers de respuesta |
| `frontend/src/services/demo/seed.js` | Crear | estado inicial (puro) |
| `frontend/src/services/demo/store.js` | Crear | cargar/guardar/reiniciar en `localStorage`, `nextId` |
| `frontend/src/services/demo/sesion.js` | Crear | token ↔ usuario |
| `frontend/src/services/demo/dto.js` | Crear | entidades del store → DTOs del backend |
| `frontend/src/services/demo/router.js` | Crear | tabla de endpoints, acceso, persistencia, `Response` |
| `frontend/src/services/demo/handlers/auth.js` | Crear | login, registro, validate |
| `frontend/src/services/demo/handlers/productos.js` | Crear | catálogo y ABM de productos |
| `frontend/src/services/demo/handlers/categorias.js` | Crear | listado de categorías |
| `frontend/src/services/demo/handlers/pedidos.js` | Crear | checkout, mis pedidos, cancelar |
| `frontend/src/services/demo/handlers/pagos.js` | Crear | Mercado Pago simulado |
| `frontend/src/services/demo/handlers/ventas.js` | Crear | ventas del vendedor y transiciones |
| `frontend/src/services/demo/handlers/admin.js` | Crear | usuarios y reportes |
| `frontend/src/services/demo/imagenes.js` | Crear | subida simulada (`data:` URL) |
| `frontend/src/services/demo/*.test.js`, `handlers/*.test.js` | Crear | tests |
| `frontend/src/components/demo/DemoBanner.jsx` | Crear | banner + "Reiniciar demo" |
| `frontend/src/components/demo/DemoCuentas.jsx` | Crear | cuentas de prueba en el login |
| `frontend/src/App.jsx` | Modificar | monta el banner |
| `frontend/src/pages/Login.jsx` | Modificar | monta las cuentas de prueba |
| `frontend/vercel.json` | Crear | rewrite SPA + headers |
| `.github/workflows/frontend-ci.yml` | Crear | tests + build del frontend |
| `.github/workflows/build-and-push.yml` | Modificar | gate también por el frontend |
| `LICENSE` | Crear | MIT |
| `README.md` | Modificar | demo, tests del frontend, CI |

---

### Task 1: Vitest, CI del frontend y arreglos de `request()` / `validateToken`

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/src/services/api.js` (`request()`, `validateToken`)
- Create: `frontend/src/services/api.test.js`
- Create: `.github/workflows/frontend-ci.yml`
- Modify: `.github/workflows/build-and-push.yml`
- Modify: `README.md` (secciones CI/CD y Testing)

**Interfaces:**
- Produces: `npm test` (corre `vitest run`). `request()` devuelve `null` para un 204 y lanza `Error(message || error || "HTTP error! status: N")`. Las tasks siguientes dependen de las dos cosas: el router de la demo devuelve 204 en los DELETE, y sus errores llegan a la UI por `message`.

- [ ] **Step 1: Instalar dependencias y vitest**

`frontend/node_modules` está incompleto: hay que reinstalar.

```bash
cd frontend
npm ci
npm install -D vitest@0.34.6
```

En `frontend/package.json`, agregar el script `test` a `scripts` (dejar los demás como están):

```json
    "test": "vitest run",
```

- [ ] **Step 2: Escribir los tests que fallan**

Crear `frontend/src/services/api.test.js`:

```js
import { describe, it, expect, vi, afterEach } from "vitest"
import { api } from "./api"

const respuesta = (status, body) =>
  new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

// api.js lee el token de localStorage/sessionStorage, que en Node no existen.
const sinSesion = () => {
  const vacio = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
  vi.stubGlobal("localStorage", vacio)
  vi.stubGlobal("sessionStorage", vacio)
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("request()", () => {
  it("acepta una respuesta 204 sin body como éxito", async () => {
    sinSesion()
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(204)))

    await expect(api.deleteProduct(3)).resolves.toBe(true)
  })

  it("muestra el detalle (message) antes que el título genérico (error)", async () => {
    sinSesion()
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(respuesta(401, { error: "Unauthorized", message: "Credenciales inválidas" })),
    )

    await expect(api.login("x@test.com", "malpass")).rejects.toThrow("Credenciales inválidas")
  })

  it("usa error cuando el backend no manda message (AdminController)", async () => {
    sinSesion()
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(respuesta(409, { error: "El email ya está registrado" })))

    await expect(api.createUser({ email: "a@test.com" })).rejects.toThrow("El email ya está registrado")
  })
})

describe("api.validateToken", () => {
  it("valida con POST, el método que expone AuthController", async () => {
    sinSesion()
    const fetchMock = vi.fn().mockResolvedValue(
      respuesta(200, { id: 2, email: "user1@test.com", username: "user1", nombre: "User", apellido: "One", role: "USER" }),
    )
    vi.stubGlobal("fetch", fetchMock)

    await api.validateToken("tok")

    const [, config] = fetchMock.mock.calls[0]
    expect(config.method).toBe("POST")
    expect(config.headers.Authorization).toBe("Bearer tok")
  })
})
```

- [ ] **Step 3: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: 4 tests, **3 FAIL**: el de 204 (`SyntaxError`/JSON), el de `message` (recibe "Unauthorized") y el de `validateToken` (`method` es `undefined`). El de AdminController ya pasa.

- [ ] **Step 4: Implementar**

En `frontend/src/services/api.js`, dentro de `request()`, reemplazar:

```js
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      throw new Error(errorData.error || errorData.message || `HTTP error! status: ${response.status}`)
    }
    
    return await response.json()
```

por:

```js
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}))
      // `message` trae el detalle en castellano ("Credenciales inválidas"); `error` es el
      // título genérico del GlobalExceptionHandler ("Unauthorized"). Algunos endpoints
      // (AdminController) sólo mandan `error`, por eso queda como segunda opción.
      throw new Error(errorData.message || errorData.error || `HTTP error! status: ${response.status}`)
    }

    // Los DELETE responden 204 sin body: response.json() fallaría sobre una operación exitosa.
    if (response.status === 204) {
      return null
    }

    return await response.json()
```

En `validateToken`, reemplazar:

```js
      const response = await request('/auth/validate', {
        headers: {
```

por:

```js
      // AuthController expone /auth/validate sólo como POST: con el GET por defecto de
      // fetch la validación fallaba siempre y la sesión guardada se borraba en cada recarga.
      const response = await request('/auth/validate', {
        method: 'POST',
        headers: {
```

- [ ] **Step 5: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: `Tests  4 passed (4)`

- [ ] **Step 6: CI del frontend**

Crear `.github/workflows/frontend-ci.yml`:

```yaml
name: Frontend CI

on:
  pull_request:
    branches: [main]
  # Igual que backend-ci.yml: en main lo invoca build-and-push.yml como gate, para no
  # correr la suite dos veces sobre el mismo commit.
  workflow_call:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Instalar dependencias
        working-directory: frontend
        run: npm ci

      - name: Tests
        working-directory: frontend
        run: npm test

      - name: Build
        working-directory: frontend
        run: npm run build
```

En `.github/workflows/build-and-push.yml`, reemplazar:

```yaml
  test:
    uses: ./.github/workflows/backend-ci.yml

  build-and-push:
    needs: test
```

por:

```yaml
  test:
    uses: ./.github/workflows/backend-ci.yml

  test-frontend:
    uses: ./.github/workflows/frontend-ci.yml

  build-and-push:
    needs: [test, test-frontend]
```

- [ ] **Step 7: README**

En `README.md`, en la tabla de la sección `## ⚙️ CI/CD`, agregar debajo de la fila de `backend-ci.yml`:

```markdown
| `.github/workflows/frontend-ci.yml` | PR a `main`, y como gate de `build-and-push` | Corre los tests del frontend (`npm test`) y verifica que el build compile |
```

y reemplazar la oración `El \`needs: test\` es lo que impide` por `El \`needs: [test, test-frontend]\` es lo que impide`.

En la sección `## 🧪 Testing`, debajo del párrafo que empieza con "Cubre servicios, controllers…", agregar:

````markdown
```bash
cd frontend
npm test       # Vitest: api.js, sesión y el backend simulado del modo demo
```
````

- [ ] **Step 8: Lint y build**

Run: `npm run lint 2>&1 | tail -3 && npm run build 2>&1 | tail -3`
Expected: el resumen de eslint con `0 errors`; build `✓ built in …`.

- [ ] **Step 9: Commit**

```bash
cd ..
git add frontend/package.json frontend/package-lock.json frontend/src/services/api.js frontend/src/services/api.test.js .github/workflows/frontend-ci.yml .github/workflows/build-and-push.yml README.md
git commit -m "fix: leer el detalle de los errores, aceptar respuestas 204 y validar el token con POST" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: No expulsar al usuario al recargar una ruta protegida

**Files:**
- Create: `frontend/src/context/sesionGuardada.js`
- Create: `frontend/src/context/sesionGuardada.test.js`
- Modify: `frontend/src/reducers/authReducer.js`
- Create: `frontend/src/reducers/authReducer.test.js`
- Modify: `frontend/src/context/AuthContext.jsx` (efecto de restauración y `useReducer`)
- Modify: `frontend/src/components/ProtectedRoute.jsx`

**Interfaces:**
- Consumes: `api.validateToken(token)` con POST (Task 1).
- Produces: `leerSesionGuardada(): { token: string, user: string, storageType: "localStorage" | "sessionStorage" } | null`. `useAuth()` expone además `restoring: boolean`. Acciones del reducer: `AUTH_RESTORE` (pone `restoring: false`) y `AUTH_RESTORE_FAILED` (vuelve al estado inicial).

- [ ] **Step 1: Escribir los tests que fallan**

Crear `frontend/src/context/sesionGuardada.test.js`:

```js
import { describe, it, expect, vi, afterEach } from "vitest"
import { leerSesionGuardada } from "./sesionGuardada"

const storageCon = (datos) => ({ getItem: (k) => datos[k] ?? null })

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("leerSesionGuardada", () => {
  it("prefiere la sesión de localStorage (recordarme)", () => {
    vi.stubGlobal("localStorage", storageCon({ auth_token: "a", auth_user: "{}" }))
    vi.stubGlobal("sessionStorage", storageCon({ auth_token: "b", auth_user: "{}" }))

    expect(leerSesionGuardada()).toEqual({ token: "a", user: "{}", storageType: "localStorage" })
  })

  it("cae en sessionStorage si localStorage no tiene sesión", () => {
    vi.stubGlobal("localStorage", storageCon({}))
    vi.stubGlobal("sessionStorage", storageCon({ auth_token: "b", auth_user: "{}" }))

    expect(leerSesionGuardada()).toEqual({ token: "b", user: "{}", storageType: "sessionStorage" })
  })

  it("no cuenta como sesión un token sin usuario", () => {
    vi.stubGlobal("localStorage", storageCon({ auth_token: "a" }))
    vi.stubGlobal("sessionStorage", storageCon({}))

    expect(leerSesionGuardada()).toBeNull()
  })
})
```

Crear `frontend/src/reducers/authReducer.test.js`:

```js
import { describe, it, expect } from "vitest"
import { authReducer, authInitialState } from "./authReducer"

const restaurando = { ...authInitialState, restoring: true }

describe("authReducer", () => {
  it("arranca sin restauración en curso", () => {
    expect(authInitialState.restoring).toBe(false)
  })

  it("AUTH_RESTORE termina la restauración con el usuario logueado", () => {
    const estado = authReducer(restaurando, {
      type: "AUTH_RESTORE",
      payload: { token: "t", user: { id: 2 }, storageType: "localStorage" },
    })

    expect(estado).toMatchObject({ restoring: false, isAuthenticated: true, token: "t", user: { id: 2 } })
  })

  it("AUTH_RESTORE_FAILED termina la restauración sin sesión", () => {
    const estado = authReducer(restaurando, { type: "AUTH_RESTORE_FAILED" })

    expect(estado).toEqual(authInitialState)
  })
})
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `cd frontend && npm test`
Expected: FAIL. `sesionGuardada.test.js` no resuelve el import, y `authReducer.test.js` falla en `restoring` (es `undefined`) y en `AUTH_RESTORE_FAILED` (el reducer devuelve el estado sin cambios).

- [ ] **Step 3: Implementar**

Crear `frontend/src/context/sesionGuardada.js`:

```js
// La sesión puede estar en localStorage ("recordarme") o en sessionStorage. Sólo cuenta
// si están el token y el usuario juntos: AuthContext guarda y borra siempre los dos.
export const leerSesionGuardada = () => {
  const storages = [
    [localStorage, "localStorage"],
    [sessionStorage, "sessionStorage"],
  ]
  for (const [storage, storageType] of storages) {
    const token = storage.getItem("auth_token")
    const user = storage.getItem("auth_user")
    if (token && user) {
      return { token, user, storageType }
    }
  }
  return null
}
```

En `frontend/src/reducers/authReducer.js`, agregar a `authInitialState`, después de `storageType`:

```js
  // true mientras se valida una sesión guardada al cargar la app (ver AuthContext)
  restoring: false,
```

En el `case "AUTH_RESTORE"`, agregar `restoring: false,` a lo que devuelve, y sumar un case nuevo antes del `default`:

```js
    case "AUTH_RESTORE_FAILED":
      return {
        ...authInitialState,
      }
```

En `frontend/src/context/AuthContext.jsx`:

1. Agregar el import `import { leerSesionGuardada } from "./sesionGuardada"`.
2. Reemplazar `const [state, dispatch] = useReducer(authReducer, authInitialState)` por:

```js
  // Si hay una sesión guardada, la app arranca "restaurando": ProtectedRoute espera a que
  // se valide el token en vez de mandar a /login en el primer render. Sin esto, cualquier
  // carga completa de una ruta protegida (un link directo, F5, o la vuelta del pago a
  // /checkout/resultado) expulsaba al usuario aunque su sesión fuera válida.
  const [state, dispatch] = useReducer(authReducer, authInitialState, (inicial) => ({
    ...inicial,
    restoring: leerSesionGuardada() !== null,
  }))
```

3. Reemplazar el `useEffect` de restauración entero (desde `// Restore session on app load` hasta su `}, [])`) por:

```js
  // Restore session on app load
  useEffect(() => {
    const sesion = leerSesionGuardada()
    if (!sesion) return

    api.validateToken(sesion.token)
      .then((validatedUser) => {
        dispatch({
          type: "AUTH_RESTORE",
          payload: {
            token: sesion.token,
            user: {
              id: validatedUser.id,
              username: validatedUser.username,
              email: validatedUser.email,
              firstName: validatedUser.firstName,
              lastName: validatedUser.lastName,
              role: validatedUser.role || 'user'
            },
            storageType: sesion.storageType,
          },
        })
      })
      .catch(() => {
        // Token inválido o expirado
        localStorage.removeItem("auth_token")
        localStorage.removeItem("auth_user")
        sessionStorage.removeItem("auth_token")
        sessionStorage.removeItem("auth_user")
        dispatch({ type: "AUTH_RESTORE_FAILED" })
      })
  }, [])
```

Reemplazar `frontend/src/components/ProtectedRoute.jsx` entero por:

```jsx
import { Navigate } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import LoadingSpinner from "./LoadingSpinner"
const ProtectedRoute = ({ children }) => {
  const { isAuthenticated, restoring } = useAuth()
  // Mientras se valida una sesión guardada todavía no se sabe si el usuario está logueado:
  // redirigir ya a /login lo expulsaría en cada recarga.
  if (restoring) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <LoadingSpinner size="lg" />
      </div>
    )
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }
  return children
}
export default ProtectedRoute
```

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: `Tests  10 passed (10)`

- [ ] **Step 5: Verificación manual contra el backend real**

```bash
cd ../backend && mvn spring-boot:run     # terminal 1, :8081
cd frontend && npm run dev               # terminal 2, :5173
```

En `http://localhost:5173`, loguearse con `user1@test.com` / `user123`, **sin** tildar "recordarme". Ir a `/orders` y apretar F5: tiene que mostrar un spinner un instante y quedarse en `/orders`. Repetir con "recordarme" tildado. Después abrir `http://localhost:5173/product/3` en la misma pestaña: tiene que mostrar el producto, no `/login`.

- [ ] **Step 6: Lint y commit**

Run: `npm run lint 2>&1 | tail -3` → `0 errors`.

```bash
cd ..
git add frontend/src/context/sesionGuardada.js frontend/src/context/sesionGuardada.test.js frontend/src/reducers/authReducer.js frontend/src/reducers/authReducer.test.js frontend/src/context/AuthContext.jsx frontend/src/components/ProtectedRoute.jsx
git commit -m "fix: no expulsar al usuario al recargar una ruta protegida" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Estado de la demo: seed y store

**Files:**
- Create: `frontend/src/services/demo/fechas.js`
- Create: `frontend/src/services/demo/respuestas.js`
- Create: `frontend/src/services/demo/seed.js`
- Create: `frontend/src/services/demo/store.js`
- Create: `frontend/src/test/storageEnMemoria.js`
- Create: `frontend/src/services/demo/seed.test.js`
- Create: `frontend/src/services/demo/store.test.js`

**Interfaces:**
- Produces:
  - `fechaLocal(fecha?: Date): string` → `"YYYY-MM-DDTHH:mm:ss"` en hora local. `restarDias(fecha: Date, dias: number): Date`.
  - `class DemoError extends Error { status: number; body: { status, error, message } }`. Helpers: `demoError(status, error, message)`, `noAutorizado(message)` (401 "Unauthorized"), `prohibido(message)` (403 "Forbidden"), `noEncontrado(message)` (404 "Not Found"), `pedidoInvalido(message)` (400 "Bad Request"), `conflicto(message)` (409 "Conflict"). Respuestas OK: `ok(body)` → `{ status: 200, body }`, `creado(body)` → 201, `sinContenido()` → `{ status: 204, body: null }`.
  - `crearSeed(ahora?: Date): Estado`.
  - `cargar(): Estado`, `guardar(estado): void` (lanza `DemoError` 507 si `localStorage` no tiene espacio), `reiniciar(): void`, `nextId(estado, coleccion): number`, `CLAVE_ESTADO = "demo-state-v1"`.
  - Forma del `Estado`, que usan todos los handlers:

```
{
  seq: { categorias, usuarios, productos, variantes, pedidos, detalles, pagos },   // próximo id libre
  categorias: [{ id, nombre, descripcion, createdAt, updatedAt }],
  usuarios:   [{ id, username, email, password, nombre, apellido, role: "ADMIN"|"USER", createdAt, updatedAt }],
  productos:  [{ id, name, description, price, club, liga, temporada, tipo: "CAMISETA"|"SHORT", images: string[], categoriaId, ownerUserId, createdAt, updatedAt }],
  variantes:  [{ id, productoId, talle, stock, sku }],
  pedidos:    [{ id, usuarioId, estado, direccionEnvio, notas, total, createdAt, updatedAt }],
  detalles:   [{ id, pedidoId, productoId, productoVarianteId, talle, productoNombre, productoImagen, cantidad, precioUnitario, vendedorId, estadoItem }],
  pagos:      [{ id, pedidoId, preferenceId, estado: "PENDING"|"APPROVED", createdAt }],
}
```

  - Ids sembrados que usan los tests: usuarios `1` admin, `2` user1, `3` testuser. Productos `1` Boca (dueño 2), `2` River (2), `3` Short Boca (3), `4` Real Madrid (2), `5` Argentina (3), `6` Retro 1986 (3). Variantes `1..23` en orden de producto y talle: `1` Boca S (6), `2` Boca M (10), `14` Real Madrid L (7), `18` Argentina M (15), `23` Retro XL (2). Pedidos: `1` testuser, Boca M ×1, `ENTREGADO`, hace 12 días. `2` testuser, Real Madrid L ×1, `CONFIRMADO`, hace 3 días. `3` user1, Argentina M ×2, `PENDIENTE`, hace 1 hora.

- [ ] **Step 1: Helper de tests**

Crear `frontend/src/test/storageEnMemoria.js`:

```js
// localStorage en memoria para los tests, que corren en Node sin DOM.
// `llena` simula la cuota excedida: setItem lanza como lo haría el navegador.
export const crearStorageEnMemoria = () => {
  const datos = new Map()
  const storage = {
    llena: false,
    getItem: (k) => (datos.has(k) ? datos.get(k) : null),
    setItem: (k, v) => {
      if (storage.llena) throw new Error("QuotaExceededError")
      datos.set(k, String(v))
    },
    removeItem: (k) => {
      datos.delete(k)
    },
  }
  return storage
}
```

- [ ] **Step 2: Escribir los tests que fallan**

Crear `frontend/src/services/demo/seed.test.js`:

```js
import { describe, it, expect } from "vitest"
import { crearSeed } from "./seed"

const AHORA = new Date(2026, 9, 8, 15, 30, 0) // 8/10/2026 15:30 hora local

describe("crearSeed", () => {
  const estado = crearSeed(AHORA)

  it("porta los datos de DataInitializer", () => {
    expect(estado.categorias.map((c) => c.nombre)).toEqual([
      "Clubes Argentinos",
      "Clubes Europeos",
      "Selecciones",
      "Retro",
    ])
    expect(estado.usuarios.map((u) => [u.id, u.email, u.role])).toEqual([
      [1, "admin@test.com", "ADMIN"],
      [2, "user1@test.com", "USER"],
      [3, "test@test.com", "USER"],
    ])
    expect(estado.productos).toHaveLength(6)
    expect(estado.variantes).toHaveLength(23)
  })

  it("respeta talles, stock y sku por variante", () => {
    const porId = (id) => estado.variantes.find((v) => v.id === id)
    expect(porId(1)).toEqual({ id: 1, productoId: 1, talle: "S", stock: 6, sku: "BOCA-2026-TIT-S" })
    expect(porId(2)).toMatchObject({ productoId: 1, talle: "M", stock: 10 })
    expect(porId(14)).toMatchObject({ productoId: 4, talle: "L", stock: 7, sku: "RMA-2026-TIT-L" })
    expect(porId(18)).toMatchObject({ productoId: 5, talle: "M", stock: 15 })
    expect(porId(23)).toMatchObject({ productoId: 6, talle: "XL", stock: 2, sku: "ARG-1986-RETRO-XL" })
  })

  it("siembra tres pedidos con snapshot del producto y fechas relativas a ahora", () => {
    expect(estado.pedidos.map((p) => [p.id, p.usuarioId, p.estado, p.total])).toEqual([
      [1, 3, "ENTREGADO", 45000],
      [2, 3, "CONFIRMADO", 78000],
      [3, 2, "PENDIENTE", 178000],
    ])
    expect(estado.pedidos[0].createdAt).toBe("2026-09-26T15:30:00")
    expect(estado.pedidos[2].createdAt).toBe("2026-10-08T14:30:00")
    expect(estado.detalles[2]).toMatchObject({
      pedidoId: 3,
      productoVarianteId: 18,
      talle: "M",
      productoNombre: "Camiseta Selección Argentina 2026",
      cantidad: 2,
      precioUnitario: 89000,
      vendedorId: 3,
      estadoItem: "PENDIENTE",
    })
  })

  it("deja los contadores de ids en el siguiente libre", () => {
    expect(estado.seq).toEqual({
      categorias: 5,
      usuarios: 4,
      productos: 7,
      variantes: 24,
      pedidos: 4,
      detalles: 4,
      pagos: 3,
    })
  })
})
```

Crear `frontend/src/services/demo/store.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { cargar, guardar, reiniciar, nextId, CLAVE_ESTADO } from "./store"
import { DemoError } from "./respuestas"
import { crearStorageEnMemoria } from "../../test/storageEnMemoria"

let storage

beforeEach(() => {
  storage = crearStorageEnMemoria()
  vi.stubGlobal("localStorage", storage)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("store de la demo", () => {
  it("siembra y persiste en la primera carga", () => {
    const estado = cargar()

    expect(estado.productos).toHaveLength(6)
    expect(JSON.parse(storage.getItem(CLAVE_ESTADO)).productos).toHaveLength(6)
  })

  it("devuelve lo guardado en las cargas siguientes", () => {
    const estado = cargar()
    estado.variantes[0].stock = 1
    guardar(estado)

    expect(cargar().variantes[0].stock).toBe(1)
  })

  it("vuelve a sembrar si el estado guardado está corrupto", () => {
    storage.setItem(CLAVE_ESTADO, "{esto no es json")

    expect(cargar().productos).toHaveLength(6)
  })

  it("reiniciar descarta los cambios", () => {
    const estado = cargar()
    estado.productos = []
    guardar(estado)

    reiniciar()

    expect(cargar().productos).toHaveLength(6)
  })

  it("con el storage lleno lanza un 507 claro y no pisa lo guardado", () => {
    const estado = cargar()
    estado.productos = []
    storage.llena = true

    expect(() => guardar(estado)).toThrow(DemoError)
    try {
      guardar(estado)
    } catch (e) {
      expect(e.status).toBe(507)
      expect(e.message).toContain("Reiniciar demo")
    }
    storage.llena = false
    expect(cargar().productos).toHaveLength(6)
  })

  it("nextId entrega el siguiente id y avanza el contador", () => {
    const estado = cargar()

    expect(nextId(estado, "productos")).toBe(7)
    expect(nextId(estado, "productos")).toBe(8)
    expect(estado.seq.productos).toBe(9)
  })
})
```

- [ ] **Step 3: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: FAIL en `seed.test.js` y `store.test.js`, porque los módulos no existen.

- [ ] **Step 4: Implementar `fechas.js` y `respuestas.js`**

Crear `frontend/src/services/demo/fechas.js`:

```js
// El backend serializa LocalDateTime sin zona ("2026-08-25T14:30:00"). Se arma con los
// componentes locales y no con toISOString(), que daría la hora UTC: el frontend la
// interpretaría como local y mostraría todo corrido tres horas.
const dosDigitos = (n) => String(n).padStart(2, "0")

export const fechaLocal = (fecha = new Date()) =>
  `${fecha.getFullYear()}-${dosDigitos(fecha.getMonth() + 1)}-${dosDigitos(fecha.getDate())}` +
  `T${dosDigitos(fecha.getHours())}:${dosDigitos(fecha.getMinutes())}:${dosDigitos(fecha.getSeconds())}`

// Días de calendario, no 24 h fijas: si en el medio hay un cambio de horario (en la zona
// del navegador del visitante), restar milisegundos corre la hora.
export const restarDias = (fecha, dias) => {
  const resultado = new Date(fecha)
  resultado.setDate(resultado.getDate() - dias)
  return resultado
}
```

Crear `frontend/src/services/demo/respuestas.js`:

```js
// Errores con el mismo shape que GlobalExceptionHandler, para que request() los procese
// por el mismo camino que en producción.
export class DemoError extends Error {
  constructor(status, error, message) {
    super(message)
    this.status = status
    this.body = { status, error, message }
  }
}

export const demoError = (status, error, message) => new DemoError(status, error, message)
export const noAutorizado = (message) => demoError(401, "Unauthorized", message)
export const prohibido = (message) => demoError(403, "Forbidden", message)
export const noEncontrado = (message) => demoError(404, "Not Found", message)
export const pedidoInvalido = (message) => demoError(400, "Bad Request", message)
export const conflicto = (message) => demoError(409, "Conflict", message)

// Lo que devuelve un handler cuando termina bien: el router lo convierte en un Response.
export const ok = (body) => ({ status: 200, body })
export const creado = (body) => ({ status: 201, body })
export const sinContenido = () => ({ status: 204, body: null })
```

- [ ] **Step 5: Implementar `seed.js`**

Crear `frontend/src/services/demo/seed.js`:

```js
import { fechaLocal, restarDias } from "./fechas"

// Portado de DataInitializer.java: mismos usuarios, categorías, productos, talles y stock.
// Las ids son posicionales (como las asignaría la base vacía) y los tests dependen de ellas.
const CATEGORIAS = [
  ["Clubes Argentinos", "Camisetas y shorts de clubes del fútbol argentino"],
  ["Clubes Europeos", "Camisetas y shorts de clubes de las principales ligas europeas"],
  ["Selecciones", "Camisetas y shorts de selecciones nacionales"],
  ["Retro", "Reediciones de camisetas históricas"],
]

const USUARIOS = [
  { username: "admin", email: "admin@test.com", password: "admin123", nombre: "Admin", apellido: "User", role: "ADMIN" },
  { username: "user1", email: "user1@test.com", password: "user123", nombre: "User", apellido: "One", role: "USER" },
  { username: "testuser", email: "test@test.com", password: "test123", nombre: "Test", apellido: "User", role: "USER" },
]

const PRODUCTOS = [
  {
    name: "Camiseta Titular Boca Juniors 2026",
    description: "Camiseta titular oficial temporada 2026, tela liviana con tecnología de secado rápido",
    price: 45000, club: "Boca Juniors", liga: "Liga Profesional Argentina", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 1, ownerUserId: 2,
    imagen: "https://images.unsplash.com/photo-1517466787929-bc90951d0974?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL"], stocks: [6, 10, 8, 4], skuBase: "BOCA-2026-TIT",
  },
  {
    name: "Camiseta Titular River Plate 2026",
    description: "Camiseta titular oficial temporada 2026 con la banda roja clásica",
    price: 45000, club: "River Plate", liga: "Liga Profesional Argentina", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 1, ownerUserId: 2,
    imagen: "https://images.unsplash.com/photo-1580087433295-ab2600c1030e?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL"], stocks: [5, 12, 9, 3], skuBase: "RIVER-2026-TIT",
  },
  {
    name: "Short Titular Boca Juniors 2026",
    description: "Short titular oficial temporada 2026, cintura elástica con cordón ajustable",
    price: 22000, club: "Boca Juniors", liga: "Liga Profesional Argentina", temporada: "2026",
    tipo: "SHORT", categoriaId: 1, ownerUserId: 3,
    imagen: "https://images.unsplash.com/photo-1562183241-b937e95585b6?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L"], stocks: [7, 11, 6], skuBase: "BOCA-2026-SHORT",
  },
  {
    name: "Camiseta Titular Real Madrid 2026",
    description: "Camiseta titular blanca temporada 2026, corte atlético",
    price: 78000, club: "Real Madrid", liga: "LaLiga", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 2, ownerUserId: 2,
    imagen: "https://images.unsplash.com/photo-1577212017184-80cc0da11082?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL", "XXL"], stocks: [4, 8, 7, 5, 2], skuBase: "RMA-2026-TIT",
  },
  {
    name: "Camiseta Selección Argentina 2026",
    description: "Camiseta titular de la selección argentina, edición con las tres estrellas",
    price: 89000, club: "Selección Argentina", liga: "Selecciones", temporada: "2026",
    tipo: "CAMISETA", categoriaId: 3, ownerUserId: 3,
    imagen: "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&h=600&fit=crop&crop=center",
    talles: ["S", "M", "L", "XL"], stocks: [10, 15, 12, 6], skuBase: "ARG-2026-TIT",
  },
  {
    name: "Camiseta Retro Argentina 1986",
    description: "Reedición de la camiseta campeona del mundo en México 1986",
    price: 65000, club: "Selección Argentina", liga: "Selecciones", temporada: "1986",
    tipo: "CAMISETA", categoriaId: 4, ownerUserId: 3,
    imagen: "https://images.unsplash.com/photo-1571945153237-4929e783af4a?w=800&h=600&fit=crop&crop=center",
    talles: ["M", "L", "XL"], stocks: [3, 5, 2], skuBase: "ARG-1986-RETRO",
  },
]

// Estado inicial de la demo. Además de lo que siembra DataInitializer trae tres pedidos,
// para que Mis pedidos, Mis ventas y los reportes no arranquen vacíos y haya un pedido
// PENDIENTE para probar la cancelación (el pago simulado confirma al instante los nuevos).
// Las fechas son relativas a `ahora` para que caigan dentro del reporte de 30 días.
export const crearSeed = (ahora = new Date()) => {
  const hoy = fechaLocal(ahora)

  const categorias = CATEGORIAS.map(([nombre, descripcion], i) => ({
    id: i + 1, nombre, descripcion, createdAt: hoy, updatedAt: null,
  }))

  const usuarios = USUARIOS.map((u, i) => ({ id: i + 1, ...u, createdAt: hoy, updatedAt: null }))

  const productos = []
  const variantes = []
  PRODUCTOS.forEach((p, i) => {
    const productoId = i + 1
    productos.push({
      id: productoId, name: p.name, description: p.description, price: p.price, club: p.club,
      liga: p.liga, temporada: p.temporada, tipo: p.tipo, images: [p.imagen],
      categoriaId: p.categoriaId, ownerUserId: p.ownerUserId, createdAt: hoy, updatedAt: null,
    })
    p.talles.forEach((talle, j) => {
      variantes.push({ id: variantes.length + 1, productoId, talle, stock: p.stocks[j], sku: `${p.skuBase}-${talle}` })
    })
  })

  const detalle = (id, pedidoId, varianteId, cantidad, estadoItem) => {
    const variante = variantes.find((v) => v.id === varianteId)
    const producto = productos.find((p) => p.id === variante.productoId)
    return {
      id, pedidoId, productoId: producto.id, productoVarianteId: variante.id, talle: variante.talle,
      productoNombre: producto.name, productoImagen: producto.images[0], cantidad,
      precioUnitario: producto.price, vendedorId: producto.ownerUserId, estadoItem,
    }
  }

  const detalles = [
    detalle(1, 1, 2, 1, "ENTREGADO"),   // Boca M, vende user1
    detalle(2, 2, 14, 1, "CONFIRMADO"), // Real Madrid L, vende user1
    detalle(3, 3, 18, 2, "PENDIENTE"),  // Argentina M, vende testuser
  ]

  const totalDe = (pedidoId) =>
    detalles.filter((d) => d.pedidoId === pedidoId).reduce((suma, d) => suma + d.precioUnitario * d.cantidad, 0)

  const pedido = (id, usuarioId, estado, fecha, direccionEnvio) => ({
    id, usuarioId, estado, direccionEnvio, notas: "", total: totalDe(id), createdAt: fechaLocal(fecha), updatedAt: null,
  })

  const pedidos = [
    pedido(1, 3, "ENTREGADO", restarDias(ahora, 12), "Av. Corrientes 1234, CABA"),
    pedido(2, 3, "CONFIRMADO", restarDias(ahora, 3), "Av. Corrientes 1234, CABA"),
    pedido(3, 2, "PENDIENTE", new Date(ahora.getTime() - 60 * 60 * 1000), "Bv. Oroño 850, Rosario"),
  ]

  const pagos = [
    { id: 1, pedidoId: 1, preferenceId: "demo-pref-1", estado: "APPROVED", createdAt: pedidos[0].createdAt },
    { id: 2, pedidoId: 2, preferenceId: "demo-pref-2", estado: "APPROVED", createdAt: pedidos[1].createdAt },
  ]

  return {
    seq: {
      categorias: categorias.length + 1,
      usuarios: usuarios.length + 1,
      productos: productos.length + 1,
      variantes: variantes.length + 1,
      pedidos: pedidos.length + 1,
      detalles: detalles.length + 1,
      pagos: pagos.length + 1,
    },
    categorias, usuarios, productos, variantes, pedidos, detalles, pagos,
  }
}
```

- [ ] **Step 6: Implementar `store.js`**

Crear `frontend/src/services/demo/store.js`:

```js
import { crearSeed } from "./seed"
import { demoError } from "./respuestas"

// Estado de la demo en el localStorage del visitante. La key lleva versión: si un deploy
// cambia la forma del estado, se sube la versión y los visitantes arrancan de un seed
// nuevo en vez de cargar datos con otra forma.
export const CLAVE_ESTADO = "demo-state-v1"

export const guardar = (estado) => {
  try {
    localStorage.setItem(CLAVE_ESTADO, JSON.stringify(estado))
  } catch {
    // Casi siempre es la cuota de localStorage (~5 MB), que se llena con imágenes subidas.
    throw demoError(
      507,
      "Insufficient Storage",
      'El almacenamiento de la demo está lleno. Usá "Reiniciar demo" para empezar de nuevo.',
    )
  }
}

// Cada llamada devuelve una copia nueva parseada del storage: el router muta esa copia y
// sólo la guarda si el handler terminó bien.
export const cargar = () => {
  try {
    const guardado = localStorage.getItem(CLAVE_ESTADO)
    if (guardado) return JSON.parse(guardado)
  } catch {
    // JSON corrupto (lo editaron a mano, o quedó a medio escribir): se vuelve a sembrar.
  }
  const inicial = crearSeed()
  guardar(inicial)
  return inicial
}

export const reiniciar = () => {
  localStorage.removeItem(CLAVE_ESTADO)
}

export const nextId = (estado, coleccion) => {
  const id = estado.seq[coleccion]
  estado.seq[coleccion] = id + 1
  return id
}
```

- [ ] **Step 7: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: `Tests  20 passed (20)`

- [ ] **Step 8: Commit**

```bash
cd ..
git add frontend/src/services/demo frontend/src/test/storageEnMemoria.js
git commit -m "feat: estado del modo demo sembrado desde los datos de DataInitializer" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Router de la demo, autenticación y enganche en `api.js`

**Files:**
- Create: `frontend/src/services/demo/sesion.js`
- Create: `frontend/src/services/demo/dto.js`
- Create: `frontend/src/services/demo/router.js`
- Create: `frontend/src/services/demo/handlers/auth.js`
- Create: `frontend/src/test/llamarDemo.js`
- Create: `frontend/src/services/demo/router.test.js`
- Create: `frontend/src/services/demo/handlers/auth.test.js`
- Modify: `frontend/vite.config.js`
- Modify: `frontend/eslint.config.mjs`
- Modify: `frontend/src/services/api.js` (constante `DEMO`, `fetchApi`, `request()`)

**Interfaces:**
- Consumes: `cargar`, `guardar`, `nextId` (store). `DemoError`, helpers de `respuestas.js`. `fechaLocal`.
- Produces:
  - `responder(endpoint: string, config: RequestInit): Promise<Response>`. `endpoint` es lo mismo que recibe `request()` (por ejemplo `"/productos/buscar?nombre=boca"`) y `config` trae `method`, `headers.Authorization` y `body` (JSON string).
  - Contrato de un handler: `(ctx) => { status, body }`, con `ctx = { state, params, query, body, token, usuario }`. `params` son strings y `usuario` es la entidad del store (o `null` en las rutas públicas). El router persiste `state` sólo si el handler no lanzó.
  - Fila de la tabla: `[metodo, patron, acceso, handler]`, con `acceso` igual a `"publico" | "usuario" | "admin"`. El orden importa: la primera ruta que matchea gana.
  - `tokenPara(usuario): string` → `"demo-token-<id>"`. `usuarioActual(state, token)` lanza 401 si el token no corresponde a ningún usuario.
  - `usuarioDTO(usuario)` → `{ id, username, nombre, apellido, email, role, createdAt, updatedAt }`, sin `password`.
  - Helper de tests: `llamar(metodo, endpoint, { body?, token? }) → Promise<{ status, body }>`, `TOKENS = { admin: "demo-token-1", user1: "demo-token-2", testuser: "demo-token-3" }`, `usarStorageEnMemoria()`.
  - Global de build: `__DEMO_MODE__: boolean`.

- [ ] **Step 1: Helper de tests**

Crear `frontend/src/test/llamarDemo.js`:

```js
import { vi } from "vitest"
import { responder } from "../services/demo/router"
import { crearStorageEnMemoria } from "./storageEnMemoria"

export const TOKENS = { admin: "demo-token-1", user1: "demo-token-2", testuser: "demo-token-3" }

export const usarStorageEnMemoria = () => {
  vi.stubGlobal("localStorage", crearStorageEnMemoria())
}

// Llama al router con el mismo `config` que armaría request() en api.js.
export const llamar = async (metodo, endpoint, { body, token } = {}) => {
  const res = await responder(endpoint, {
    method: metodo,
    headers: { "Content-Type": "application/json", ...(token && { Authorization: `Bearer ${token}` }) },
    ...(body !== undefined && { body: JSON.stringify(body) }),
  })
  return { status: res.status, body: res.status === 204 ? null : await res.json() }
}
```

- [ ] **Step 2: Escribir los tests que fallan**

Crear `frontend/src/services/demo/router.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria } from "../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

describe("router de la demo", () => {
  it("responde 501 con un mensaje claro a un endpoint que la demo no simula", async () => {
    const res = await llamar("GET", "/direcciones", { token: "demo-token-2" })

    expect(res.status).toBe(501)
    expect(res.body.message).toBe("GET /direcciones no está disponible en el modo demo")
  })

  it("responde 501 si el método no coincide aunque la ruta exista", async () => {
    const res = await llamar("GET", "/auth/login")

    expect(res.status).toBe(501)
  })
})
```

Crear `frontend/src/services/demo/handlers/auth.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const registro = {
  username: "nuevo",
  email: "nuevo@test.com",
  password: "clave-larga",
  nombre: "Nueva",
  apellido: "Cuenta",
  aceptaTerminos: true,
}

describe("auth de la demo", () => {
  it("loguea por email y devuelve token y UsuarioDTO sin password", async () => {
    const res = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "user1@test.com", password: "user123" },
    })

    expect(res.status).toBe(200)
    expect(res.body.token).toBe(TOKENS.user1)
    expect(res.body.user).toEqual({
      id: 2,
      username: "user1",
      nombre: "User",
      apellido: "One",
      email: "user1@test.com",
      role: "USER",
      createdAt: expect.any(String),
      updatedAt: null,
    })
  })

  it("loguea también por username", async () => {
    const res = await llamar("POST", "/auth/login", { body: { emailOrUsername: "admin", password: "admin123" } })

    expect(res.body.user.role).toBe("ADMIN")
  })

  it("rechaza una contraseña incorrecta con 401", async () => {
    const res = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "user1@test.com", password: "otra" },
    })

    expect(res.status).toBe(401)
    expect(res.body).toEqual({ status: 401, error: "Unauthorized", message: "Credenciales inválidas" })
  })

  it("registra un usuario USER que después puede loguearse", async () => {
    const res = await llamar("POST", "/auth/register", { body: registro })

    expect(res.status).toBe(200)
    expect(res.body.user).toMatchObject({ id: 4, role: "USER", email: "nuevo@test.com" })
    const login = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "nuevo@test.com", password: "clave-larga" },
    })
    expect(login.status).toBe(200)
  })

  it("rechaza un email ya registrado con 409", async () => {
    const res = await llamar("POST", "/auth/register", { body: { ...registro, email: "user1@test.com" } })

    expect(res.status).toBe(409)
    expect(res.body.message).toBe("El email 'user1@test.com' ya existe")
  })

  it("exige aceptar los términos", async () => {
    const res = await llamar("POST", "/auth/register", { body: { ...registro, aceptaTerminos: false } })

    expect(res.status).toBe(400)
  })

  it("exige una contraseña de al menos 8 caracteres", async () => {
    const res = await llamar("POST", "/auth/register", { body: { ...registro, password: "corta" } })

    expect(res.status).toBe(400)
  })

  it("validate devuelve el usuario del token", async () => {
    const res = await llamar("POST", "/auth/validate", { token: TOKENS.testuser })

    expect(res.status).toBe(200)
    expect(res.body).toMatchObject({ id: 3, username: "testuser" })
  })

  it("validate rechaza con 401 el token de un usuario que ya no existe", async () => {
    const res = await llamar("POST", "/auth/validate", { token: "demo-token-99" })

    expect(res.status).toBe(401)
  })

  it("validate rechaza con 401 un pedido sin token", async () => {
    const res = await llamar("POST", "/auth/validate")

    expect(res.status).toBe(401)
  })
})
```

- [ ] **Step 3: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: FAIL. `llamarDemo.js` no resuelve `../services/demo/router`.

- [ ] **Step 4: Implementar `sesion.js`, `dto.js` y `handlers/auth.js`**

Crear `frontend/src/services/demo/sesion.js`:

```js
import { noAutorizado } from "./respuestas"

// El token de la demo es "demo-token-<id>". No se firma nada porque la demo no tiene
// ningún dato que proteger: sólo hace falta saber quién es el usuario.
export const tokenPara = (usuario) => `demo-token-${usuario.id}`

export const usuarioActual = (state, token) => {
  const id = Number((token || "").replace(/^demo-token-/, ""))
  const usuario = state.usuarios.find((u) => u.id === id)
  if (!token || !usuario) {
    throw noAutorizado("Token expirado o inválido")
  }
  return usuario
}
```

Crear `frontend/src/services/demo/dto.js`:

```js
// Convierte las entidades del store en los DTOs que devuelve el backend real
// (backend/src/main/java/com/ecommerce/dto/). api.js los mapea igual que en producción.

export const usuarioDTO = (u) => ({
  id: u.id,
  username: u.username,
  nombre: u.nombre,
  apellido: u.apellido,
  email: u.email,
  role: u.role,
  createdAt: u.createdAt,
  updatedAt: u.updatedAt,
})
```

Crear `frontend/src/services/demo/handlers/auth.js`:

```js
import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { usuarioDTO } from "../dto"
import { tokenPara, usuarioActual } from "../sesion"
import { ok, demoError, noAutorizado, conflicto } from "../respuestas"

// Mismo orden que UsuarioService.findByEmailOrUsername: primero por email, después por username.
export const login = ({ state, body }) => {
  const identificador = body?.emailOrUsername
  const usuario =
    state.usuarios.find((u) => u.email === identificador) ||
    state.usuarios.find((u) => u.username === identificador)
  if (!usuario || usuario.password !== body?.password) {
    throw noAutorizado("Credenciales inválidas")
  }
  return ok({ token: tokenPara(usuario), user: usuarioDTO(usuario) })
}

const validacion = (campo, mensaje) => demoError(400, "Validation Failed", `Errores de validación: ${campo}: ${mensaje}`)

export const registrar = ({ state, body }) => {
  if (body?.aceptaTerminos !== true) {
    throw validacion("aceptaTerminos", "Debés aceptar los Términos y Condiciones y la Política de Privacidad")
  }
  if (!body.password || body.password.length < 8) {
    throw validacion("password", "La contraseña debe tener entre 8 y 100 caracteres")
  }
  if (state.usuarios.some((u) => u.email === body.email)) {
    throw conflicto(`El email '${body.email}' ya existe`)
  }
  if (state.usuarios.some((u) => u.username === body.username)) {
    throw conflicto(`El username '${body.username}' ya existe`)
  }

  const usuario = {
    id: nextId(state, "usuarios"),
    username: body.username,
    email: body.email,
    password: body.password,
    nombre: body.nombre,
    apellido: body.apellido,
    role: "USER",
    createdAt: fechaLocal(),
    updatedAt: null,
  }
  state.usuarios.push(usuario)
  return ok({ token: tokenPara(usuario), user: usuarioDTO(usuario) })
}

export const validar = ({ state, token }) => ok(usuarioDTO(usuarioActual(state, token)))
```

- [ ] **Step 5: Implementar `router.js`**

Crear `frontend/src/services/demo/router.js`:

```js
import { cargar, guardar } from "./store"
import { usuarioActual } from "./sesion"
import { DemoError, demoError, prohibido } from "./respuestas"
import * as auth from "./handlers/auth"

// Endpoints que simula la demo: sólo los que usa alguna pantalla. `acceso` replica las
// reglas de SecurityConfig y los @PreAuthorize: "publico" no mira el token, "usuario"
// exige uno válido y "admin" además rol ADMIN. Gana la primera fila que matchea, así que
// las rutas literales van antes que las que tienen :parametro en la misma posición.
const RUTAS = [
  ["POST", "/auth/login", "publico", auth.login],
  ["POST", "/auth/register", "publico", auth.registrar],
  ["POST", "/auth/validate", "publico", auth.validar],
]

const TABLA = RUTAS.map(([metodo, patron, acceso, handler]) => ({
  metodo,
  regex: new RegExp(`^${patron.replace(/:[a-zA-Z]+/g, "([^/]+)")}$`),
  nombres: (patron.match(/:[a-zA-Z]+/g) || []).map((n) => n.slice(1)),
  acceso,
  handler,
}))

const respuestaJson = (status, body) =>
  new Response(status === 204 ? null : JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  })

// Devuelve un Response real, así request() en api.js recorre el mismo camino que con fetch.
export const responder = async (endpoint, config = {}) => {
  const metodo = (config.method || "GET").toUpperCase()
  const [path, queryString = ""] = endpoint.split("?")
  const query = Object.fromEntries(new URLSearchParams(queryString))
  const token = (config.headers?.Authorization || "").replace(/^Bearer /, "") || null
  const body = config.body ? JSON.parse(config.body) : undefined

  try {
    const ruta = TABLA.find((r) => r.metodo === metodo && r.regex.test(path))
    if (!ruta) {
      throw demoError(501, "Not Implemented", `${metodo} ${path} no está disponible en el modo demo`)
    }
    const valores = path.match(ruta.regex).slice(1)
    const params = Object.fromEntries(ruta.nombres.map((nombre, i) => [nombre, decodeURIComponent(valores[i])]))

    const state = cargar()
    const usuario = ruta.acceso === "publico" ? null : usuarioActual(state, token)
    if (ruta.acceso === "admin" && usuario.role !== "ADMIN") {
      throw prohibido("No tenés permiso para acceder a este recurso")
    }

    const resultado = ruta.handler({ state, params, query, body, token, usuario })
    // Se persiste sólo si el handler terminó bien. Si lanzó a mitad de camino (por
    // ejemplo, stock insuficiente en el segundo item de un pedido), lo que alcanzó a
    // mutar se descarta entero, igual que el rollback de la transacción en el backend.
    guardar(state)
    return respuestaJson(resultado.status, resultado.body)
  } catch (e) {
    if (e instanceof DemoError) return respuestaJson(e.status, e.body)
    throw e
  }
}
```

- [ ] **Step 6: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: `Tests  32 passed (32)`

- [ ] **Step 7: Enganchar la demo en `api.js` con eliminación garantizada en producción**

En `frontend/vite.config.js`, agregar dentro de `defineConfig({ … })`, después de `plugins: [react()],`:

```js
  // Ver services/api.js. Tiene que ser un literal en el build: así Rollup elimina el código
  // de la demo cuando VITE_DEMO_MODE no está seteada (build del VPS). Con import.meta.env
  // no alcanza: Vite 4 emite igual el chunk del import() dinámico.
  define: {
    __DEMO_MODE__: JSON.stringify(process.env.VITE_DEMO_MODE === "true"),
  },
```

En `frontend/eslint.config.mjs`, agregar en `globals`, después de `document: "readonly",`:

```js
        __DEMO_MODE__: "readonly",
```

En `frontend/src/services/api.js`, debajo de `const API_BASE_URL = "/api"`, agregar:

```js
// Modo demo (deploy de Vercel, sin backend): las respuestas salen de un backend simulado
// en el navegador (services/demo/). __DEMO_MODE__ es un literal que fija vite.config.js:
// en el build del VPS vale false, Rollup elimina los branches y los import() de abajo, y
// el código de la demo no llega a dist/ ni como chunk separado.
const DEMO = __DEMO_MODE__

const fetchApi = async (endpoint, config) => {
  if (DEMO) {
    const { responder } = await import("./demo/router")
    return responder(endpoint, config)
  }
  return fetch(`${API_BASE_URL}${endpoint}`, config)
}
```

En `request()`, borrar la línea `const url = \`${API_BASE_URL}${endpoint}\`` y reemplazar `const response = await fetch(url, config)` por:

```js
    const response = await fetchApi(endpoint, config)
```

- [ ] **Step 8: Verificar que la demo no viaja al build de producción**

Run (desde `frontend/`):

```bash
rm -rf dist && npm run build >/dev/null && grep -rl "demo-token-" dist || echo "OK: sin código de demo"
rm -rf dist && VITE_DEMO_MODE=true npm run build >/dev/null && grep -rl "demo-token-" dist
```

Expected: la primera línea imprime `OK: sin código de demo`. La segunda lista un archivo `dist/assets/router-*.js`.

- [ ] **Step 9: Verificación manual en dev**

Run: `VITE_DEMO_MODE=true npm run dev` (sin el backend corriendo). En `http://localhost:5173/login`, loguearse con `user1@test.com` / `user123`: entra a `/`. El catálogo muestra un error con "no está disponible en el modo demo", porque los productos llegan en la Task 5. Probar también una contraseña incorrecta: el toast dice "Credenciales inválidas".

- [ ] **Step 10: Lint, tests y commit**

Run: `npm run lint 2>&1 | tail -3 && npm test 2>&1 | tail -4` → `0 errors` y todos los tests pasan.

```bash
cd ..
git add frontend/vite.config.js frontend/eslint.config.mjs frontend/src/services/api.js frontend/src/services/demo frontend/src/test/llamarDemo.js
git commit -m "feat: router del modo demo con autenticación simulada, excluido del build de producción" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Catálogo y productos

**Files:**
- Modify: `frontend/src/services/demo/dto.js` (agregar `categoriaDTO` y `productoDTO`)
- Create: `frontend/src/services/demo/handlers/productos.js`
- Create: `frontend/src/services/demo/handlers/categorias.js`
- Modify: `frontend/src/services/demo/router.js` (imports y `RUTAS`)
- Create: `frontend/src/services/demo/handlers/productos.test.js`

**Interfaces:**
- Consumes: contrato de handler y `llamar`/`TOKENS` (Task 4). `nextId`, `fechaLocal`, helpers de `respuestas.js`.
- Produces: `productoDTO(state, producto)` → forma de `ProductoDTO.java` (`variantes: [{ id, talle, stock, sku }]`, `stockTotal`, `categoriaId`, `categoriaNombre`, `ownerUserId`, `ownerUserNombre`, …). `categoriaDTO(categoria)`. La Task 6 usa `productoDTO` sólo indirectamente.

- [ ] **Step 1: Escribir los tests que fallan**

Crear `frontend/src/services/demo/handlers/productos.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const payload = (cambios = {}) => ({
  producto: {
    name: "Camiseta Suplente Boca 2026",
    description: "Suplente amarilla",
    price: 47000,
    club: "Boca Juniors",
    liga: "Liga Profesional Argentina",
    temporada: "2026",
    tipo: "CAMISETA",
    images: ["https://example.com/a.jpg"],
    categoriaId: 1,
    ...cambios,
  },
  variantes: [
    { talle: "M", stock: 5, sku: "BOCA-SUP-M" },
    { talle: "L", stock: 3, sku: "BOCA-SUP-L" },
  ],
})

describe("catálogo de la demo", () => {
  it("lista los productos con la forma de ProductoDTO", async () => {
    const res = await llamar("GET", "/productos")

    expect(res.status).toBe(200)
    expect(res.body).toHaveLength(6)
    expect(res.body[0]).toMatchObject({
      id: 1,
      name: "Camiseta Titular Boca Juniors 2026",
      price: 45000,
      tipo: "CAMISETA",
      stockTotal: 28,
      categoriaId: 1,
      categoriaNombre: "Clubes Argentinos",
      ownerUserId: 2,
      ownerUserNombre: "User",
    })
    expect(res.body[0].variantes[0]).toEqual({ id: 1, talle: "S", stock: 6, sku: "BOCA-2026-TIT-S" })
  })

  it("busca por nombre sin distinguir mayúsculas", async () => {
    const res = await llamar("GET", "/productos/buscar?nombre=BOCA")

    expect(res.body.map((p) => p.id)).toEqual([1, 3])
  })

  it("una búsqueda vacía devuelve todo", async () => {
    const res = await llamar("GET", "/productos/buscar?nombre=")

    expect(res.body).toHaveLength(6)
  })

  it("filtra por categoría", async () => {
    const res = await llamar("GET", "/productos/categoria/4")

    expect(res.body.map((p) => p.name)).toEqual(["Camiseta Retro Argentina 1986"])
  })

  it("devuelve 404 para un producto inexistente", async () => {
    const res = await llamar("GET", "/productos/999")

    expect(res.status).toBe(404)
    expect(res.body.message).toBe("Producto con ID 999 no encontrado")
  })

  it("lista las categorías con la forma de CategoriaDTO", async () => {
    const res = await llamar("GET", "/categorias")

    expect(res.body[0]).toMatchObject({ id: 1, nombre: "Clubes Argentinos" })
  })
})

describe("ABM de productos en la demo", () => {
  it("crea un producto a nombre del usuario logueado", async () => {
    const res = await llamar("POST", "/productos", { body: payload(), token: TOKENS.user1 })

    expect(res.status).toBe(201)
    expect(res.body).toMatchObject({ id: 7, ownerUserId: 2, categoriaNombre: "Clubes Argentinos", stockTotal: 8 })
    expect(res.body.variantes.map((v) => [v.id, v.talle])).toEqual([[24, "M"], [25, "L"]])
  })

  it("exige estar logueado para crear", async () => {
    const res = await llamar("POST", "/productos", { body: payload() })

    expect(res.status).toBe(401)
  })

  it("no deja editar el producto de otro vendedor", async () => {
    const res = await llamar("PUT", "/productos/3", { body: payload(), token: TOKENS.user1 })

    expect(res.status).toBe(403)
    expect(res.body.message).toBe("No tenés permiso sobre este producto")
  })

  it("el admin puede editar cualquier producto", async () => {
    const res = await llamar("PUT", "/productos/3", { body: payload({ name: "Editado por admin" }), token: TOKENS.admin })

    expect(res.status).toBe(200)
    expect(res.body.name).toBe("Editado por admin")
    expect(res.body.ownerUserId).toBe(3)
  })

  it("al editar conserva el id de los talles que siguen y borra los que se quitan sin ventas", async () => {
    // Producto 2 (River, de user1): talles S(5) M(6) L(7) XL(8), sin ventas
    const body = {
      ...payload(),
      variantes: [
        { talle: "M", stock: 1, sku: "RIVER-2026-TIT-M" },
        { talle: "XXL", stock: 2, sku: "RIVER-2026-TIT-XXL" },
      ],
    }
    const res = await llamar("PUT", "/productos/2", { body, token: TOKENS.user1 })

    expect(res.status).toBe(200)
    expect(res.body.variantes).toEqual([
      { id: 6, talle: "M", stock: 1, sku: "RIVER-2026-TIT-M" },
      { id: 24, talle: "XXL", stock: 2, sku: "RIVER-2026-TIT-XXL" },
    ])
  })

  it("no deja quitar un talle que ya tiene ventas", async () => {
    // Producto 1 (Boca): el talle M (variante 2) está en el pedido sembrado 1
    const body = { ...payload(), variantes: [{ talle: "S", stock: 6, sku: "BOCA-2026-TIT-S" }] }
    const res = await llamar("PUT", "/productos/1", { body, token: TOKENS.user1 })

    expect(res.status).toBe(400)
    expect(res.body.message).toContain("No se puede eliminar el talle M")
    const producto = await llamar("GET", "/productos/1")
    expect(producto.body.variantes).toHaveLength(4)
  })

  it("borra un producto propio junto con sus talles", async () => {
    const res = await llamar("DELETE", "/productos/2", { token: TOKENS.user1 })

    expect(res.status).toBe(204)
    expect((await llamar("GET", "/productos/2")).status).toBe(404)
    expect((await llamar("GET", "/productos")).body).toHaveLength(5)
  })

  it("no deja borrar el producto de otro vendedor", async () => {
    const res = await llamar("DELETE", "/productos/5", { token: TOKENS.user1 })

    expect(res.status).toBe(403)
  })
})
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: FAIL. Todos responden 501, porque las rutas no existen todavía.

- [ ] **Step 3: Implementar los DTOs**

Agregar al final de `frontend/src/services/demo/dto.js`:

```js
export const categoriaDTO = (c) => ({
  id: c.id,
  nombre: c.nombre,
  descripcion: c.descripcion,
  createdAt: c.createdAt,
  updatedAt: c.updatedAt,
})

// stockTotal es derivado, igual que en ProductoDTO: el stock vive en cada talle.
export const productoDTO = (state, p) => {
  const variantes = state.variantes
    .filter((v) => v.productoId === p.id)
    .map(({ id, talle, stock, sku }) => ({ id, talle, stock, sku }))
  const categoria = state.categorias.find((c) => c.id === p.categoriaId)
  const owner = state.usuarios.find((u) => u.id === p.ownerUserId)
  return {
    id: p.id,
    name: p.name,
    description: p.description,
    price: p.price,
    club: p.club,
    liga: p.liga,
    temporada: p.temporada,
    tipo: p.tipo,
    variantes,
    stockTotal: variantes.reduce((suma, v) => suma + v.stock, 0),
    images: p.images,
    categoriaId: categoria ? categoria.id : null,
    categoriaNombre: categoria ? categoria.nombre : null,
    ownerUserId: p.ownerUserId,
    ownerUserNombre: owner ? owner.nombre : null,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  }
}
```

- [ ] **Step 4: Implementar los handlers**

Crear `frontend/src/services/demo/handlers/categorias.js`:

```js
import { categoriaDTO } from "../dto"
import { ok } from "../respuestas"

export const listar = ({ state }) => ok(state.categorias.map(categoriaDTO))
```

Crear `frontend/src/services/demo/handlers/productos.js`:

```js
import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { productoDTO } from "../dto"
import { ok, creado, sinContenido, demoError, noEncontrado, prohibido } from "../respuestas"

const buscarProducto = (state, id) => {
  const producto = state.productos.find((p) => p.id === Number(id))
  if (!producto) throw noEncontrado(`Producto con ID ${id} no encontrado`)
  return producto
}

// Mismo criterio que ProductoService.verificarPropiedad: el dueño o un admin.
const verificarPropiedad = (producto, usuario) => {
  if (usuario.role !== "ADMIN" && producto.ownerUserId !== usuario.id) {
    throw prohibido("No tenés permiso sobre este producto")
  }
}

// El backend real descarta categoriaId (la entidad Producto no lo mapea). La demo
// implementa el contrato que arma buildProductPayload en api.js.
const aplicarCampos = (producto, datos) => {
  producto.name = datos.name
  producto.description = datos.description
  producto.price = Number(datos.price)
  producto.club = datos.club
  producto.liga = datos.liga
  producto.temporada = datos.temporada
  producto.tipo = datos.tipo
  producto.images = datos.images || []
  producto.categoriaId = datos.categoriaId ?? null
}

// Igual que ProductoService.reemplazarVariantes: se aparean por talle y se actualizan en
// su lugar, porque el id de la variante es lo que referencian los pedidos y los carritos.
const reemplazarVariantes = (state, producto, deseadas = []) => {
  const talles = new Set(deseadas.map((v) => v.talle))
  const aQuitar = state.variantes.filter((v) => v.productoId === producto.id && !talles.has(v.talle))
  for (const variante of aQuitar) {
    if (state.detalles.some((d) => d.productoVarianteId === variante.id)) {
      throw demoError(
        400,
        "Validation Error",
        `No se puede eliminar el talle ${variante.talle} porque ya tiene ventas. Poné su stock en 0 para dejar de ofrecerlo.`,
      )
    }
  }
  state.variantes = state.variantes.filter((v) => !aQuitar.includes(v))

  for (const dto of deseadas) {
    const existente = state.variantes.find((v) => v.productoId === producto.id && v.talle === dto.talle)
    if (existente) {
      existente.stock = Number(dto.stock)
      existente.sku = dto.sku
    } else {
      state.variantes.push({
        id: nextId(state, "variantes"),
        productoId: producto.id,
        talle: dto.talle,
        stock: Number(dto.stock),
        sku: dto.sku,
      })
    }
  }
}

const aDTOs = (state, productos) => productos.map((p) => productoDTO(state, p))

export const listar = ({ state }) => ok(aDTOs(state, state.productos))

export const buscar = ({ state, query }) => {
  const nombre = (query.nombre || "").trim().toLowerCase()
  const encontrados = nombre ? state.productos.filter((p) => p.name.toLowerCase().includes(nombre)) : state.productos
  return ok(aDTOs(state, encontrados))
}

export const porCategoria = ({ state, params }) =>
  ok(aDTOs(state, state.productos.filter((p) => p.categoriaId === Number(params.categoryId))))

export const obtener = ({ state, params }) => ok(productoDTO(state, buscarProducto(state, params.id)))

export const crear = ({ state, body, usuario }) => {
  const producto = { id: nextId(state, "productos"), ownerUserId: usuario.id, createdAt: fechaLocal(), updatedAt: null }
  aplicarCampos(producto, body.producto)
  state.productos.push(producto)
  reemplazarVariantes(state, producto, body.variantes)
  return creado(productoDTO(state, producto))
}

export const actualizar = ({ state, params, body, usuario }) => {
  const producto = buscarProducto(state, params.id)
  verificarPropiedad(producto, usuario)
  aplicarCampos(producto, body.producto)
  producto.updatedAt = fechaLocal()
  reemplazarVariantes(state, producto, body.variantes)
  return ok(productoDTO(state, producto))
}

export const eliminar = ({ state, params, usuario }) => {
  const producto = buscarProducto(state, params.id)
  verificarPropiedad(producto, usuario)
  state.productos = state.productos.filter((p) => p.id !== producto.id)
  state.variantes = state.variantes.filter((v) => v.productoId !== producto.id)
  return sinContenido()
}
```

- [ ] **Step 5: Registrar las rutas**

En `frontend/src/services/demo/router.js`, agregar los imports debajo de `import * as auth from "./handlers/auth"`:

```js
import * as productos from "./handlers/productos"
import * as categorias from "./handlers/categorias"
```

y reemplazar `RUTAS` por:

```js
const RUTAS = [
  ["POST", "/auth/login", "publico", auth.login],
  ["POST", "/auth/register", "publico", auth.registrar],
  ["POST", "/auth/validate", "publico", auth.validar],

  ["GET", "/productos", "publico", productos.listar],
  ["GET", "/productos/buscar", "publico", productos.buscar],
  ["GET", "/productos/categoria/:categoryId", "publico", productos.porCategoria],
  ["GET", "/productos/:id", "publico", productos.obtener],
  ["POST", "/productos", "usuario", productos.crear],
  ["PUT", "/productos/:id", "usuario", productos.actualizar],
  ["DELETE", "/productos/:id", "usuario", productos.eliminar],

  ["GET", "/categorias", "publico", categorias.listar],
]
```

- [ ] **Step 6: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: todos pasan (`Tests  46 passed (46)`).

- [ ] **Step 7: Verificación manual**

Con `VITE_DEMO_MODE=true npm run dev`, logueado como `user1@test.com`, revisar:
- El catálogo muestra los 6 productos con fotos.
- La búsqueda "river" y el filtro por categoría funcionan.
- `/product/1` muestra los talles.
- En `/dashboard/products` aparecen los productos de user1. Crear uno nuevo con categoría, editarlo y borrarlo: el toast dice "eliminado exitosamente", sin error.

- [ ] **Step 8: Commit**

```bash
cd ..
git add frontend/src/services/demo
git commit -m "feat: catálogo y ABM de productos en el modo demo" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Checkout, pedidos y pago simulado

**Files:**
- Modify: `frontend/src/services/demo/dto.js` (agregar `detalleDTO` y `pedidoDTO`)
- Create: `frontend/src/services/demo/handlers/pedidos.js`
- Create: `frontend/src/services/demo/handlers/pagos.js`
- Modify: `frontend/src/services/demo/router.js`
- Create: `frontend/src/services/demo/handlers/pedidos.test.js`
- Create: `frontend/src/services/demo/handlers/pagos.test.js`

**Interfaces:**
- Consumes: contrato de handler, `llamar`/`TOKENS`, `nextId`, `fechaLocal`, helpers de `respuestas.js`.
- Produces: `buscarPedido(state, id)` (404 si no existe), `devolverStock(state, detalle)` (suma `cantidad` a la variante si todavía existe) y `pedidoDTO(state, pedido)` con `items: DetallePedidoDTO[]`. Las Tasks 7 y 8 usan `devolverStock`, `pedidoDTO` y el flujo de pago.

- [ ] **Step 1: Escribir los tests que fallan**

Crear `frontend/src/services/demo/handlers/pedidos.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const stockDe = async (productoId, talle) => {
  const res = await llamar("GET", `/productos/${productoId}`)
  return res.body.variantes.find((v) => v.talle === talle).stock
}

const pedir = (items, token = TOKENS.testuser) =>
  llamar("POST", "/pedidos", { body: { items, direccionEnvio: "Calle 1", notas: "" }, token })

describe("checkout en la demo", () => {
  it("crea el pedido PENDIENTE y descuenta el stock del talle", async () => {
    const res = await pedir([{ productoVarianteId: 1, cantidad: 2 }]) // Boca S, stock 6

    expect(res.status).toBe(201)
    expect(res.body).toMatchObject({ id: 4, usuarioId: 3, estado: "PENDIENTE", total: 90000 })
    expect(res.body.items[0]).toMatchObject({
      productoVarianteId: 1,
      talle: "S",
      cantidad: 2,
      precioUnitario: 45000,
      subtotal: 90000,
      vendedorId: 2,
      vendedorNombre: "User One",
      estadoItem: "PENDIENTE",
    })
    expect(await stockDe(1, "S")).toBe(4)
  })

  it("rechaza un item sin stock suficiente sin descontar nada", async () => {
    const res = await pedir([{ productoVarianteId: 23, cantidad: 3 }]) // Retro XL, stock 2

    expect(res.status).toBe(400)
    expect(res.body.message).toBe(
      "Stock insuficiente para Camiseta Retro Argentina 1986 (talle XL). Disponible: 2, Solicitado: 3",
    )
    expect(await stockDe(6, "XL")).toBe(2)
  })

  it("rechaza entero un pedido que supera el stock repartido en dos líneas del mismo talle", async () => {
    const res = await pedir([
      { productoVarianteId: 1, cantidad: 1 },  // Boca S, alcanza
      { productoVarianteId: 23, cantidad: 1 }, // Retro XL: 2 → 1
      { productoVarianteId: 23, cantidad: 2 }, // Retro XL: pide 2, quedan 1
    ])

    expect(res.status).toBe(400)
    expect(await stockDe(1, "S")).toBe(6)
    expect(await stockDe(6, "XL")).toBe(2)
    expect((await llamar("GET", "/pedidos/mis-pedidos", { token: TOKENS.testuser })).body).toHaveLength(2)
  })

  it("devuelve 404 si la variante no existe", async () => {
    const res = await pedir([{ productoVarianteId: 999, cantidad: 1 }])

    expect(res.status).toBe(404)
  })

  it("rechaza un pedido sin items", async () => {
    const res = await pedir([])

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("El pedido debe tener al menos un producto")
  })
})

describe("pedidos en la demo", () => {
  it("mis pedidos trae sólo los propios, del más nuevo al más viejo", async () => {
    const res = await llamar("GET", "/pedidos/mis-pedidos", { token: TOKENS.testuser })

    expect(res.body.map((p) => p.id)).toEqual([2, 1])
  })

  it("no deja ver el pedido de otro usuario", async () => {
    const res = await llamar("GET", "/pedidos/3", { token: TOKENS.testuser })

    expect(res.status).toBe(403)
  })

  it("el admin ve cualquier pedido", async () => {
    const res = await llamar("GET", "/pedidos/3", { token: TOKENS.admin })

    expect(res.status).toBe(200)
    expect(res.body.usuarioEmail).toBe("user1@test.com")
  })

  it("cancelar un pedido PENDIENTE devuelve el stock", async () => {
    const res = await llamar("PUT", "/pedidos/3/cancelar", { token: TOKENS.user1 })

    expect(res.status).toBe(200)
    expect(res.body.estado).toBe("CANCELADO_COMPRADOR")
    expect(res.body.items[0].estadoItem).toBe("CANCELADO_COMPRADOR")
    expect(await stockDe(5, "M")).toBe(17)
  })

  it("no deja cancelar un pedido que ya no está PENDIENTE", async () => {
    const res = await llamar("PUT", "/pedidos/2/cancelar", { token: TOKENS.testuser })

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("Solo se pueden cancelar pedidos en estado PENDIENTE")
  })

  it("no deja cancelar el pedido de otro usuario", async () => {
    const res = await llamar("PUT", "/pedidos/3/cancelar", { token: TOKENS.testuser })

    expect(res.status).toBe(400)
    expect(await stockDe(5, "M")).toBe(15)
  })
})
```

Crear `frontend/src/services/demo/handlers/pagos.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const crearPedido = async () => {
  const res = await llamar("POST", "/pedidos", {
    body: { items: [{ productoVarianteId: 2, cantidad: 1 }], direccionEnvio: "Calle 1", notas: "" },
    token: TOKENS.testuser,
  })
  return res.body.id
}

describe("Mercado Pago simulado", () => {
  it("la preferencia lleva a la pantalla de resultado del propio sitio", async () => {
    const id = await crearPedido()

    const res = await llamar("POST", `/pagos/pedidos/${id}/preferencia`, { token: TOKENS.testuser })

    expect(res.status).toBe(200)
    expect(res.body).toEqual({ preferenceId: "demo-pref-3", initPoint: `/checkout/resultado?pedidoId=${id}` })
  })

  it("la consulta de estado aprueba el pago y confirma el pedido y sus items", async () => {
    const id = await crearPedido()
    await llamar("POST", `/pagos/pedidos/${id}/preferencia`, { token: TOKENS.testuser })

    const estado = await llamar("GET", `/pagos/pedidos/${id}/estado`, { token: TOKENS.testuser })

    expect(estado.body).toEqual({ estadoPago: "APPROVED", estadoPedido: "CONFIRMADO" })
    const pedido = await llamar("GET", `/pedidos/${id}`, { token: TOKENS.testuser })
    expect(pedido.body.estado).toBe("CONFIRMADO")
    expect(pedido.body.items[0].estadoItem).toBe("CONFIRMADO")
  })

  it("consultar el estado otra vez no cambia nada (idempotente, como el webhook)", async () => {
    const id = await crearPedido()
    await llamar("POST", `/pagos/pedidos/${id}/preferencia`, { token: TOKENS.testuser })
    await llamar("GET", `/pagos/pedidos/${id}/estado`, { token: TOKENS.testuser })

    const segunda = await llamar("GET", `/pagos/pedidos/${id}/estado`, { token: TOKENS.testuser })

    expect(segunda.body).toEqual({ estadoPago: "APPROVED", estadoPedido: "CONFIRMADO" })
  })

  it("no deja pagar el pedido de otro usuario", async () => {
    const res = await llamar("POST", "/pagos/pedidos/3/preferencia", { token: TOKENS.testuser })

    expect(res.status).toBe(403)
    expect(res.body.message).toBe("No tenés permiso sobre este pedido")
  })
})
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: FAIL. Las rutas de pedidos y pagos dan 501.

- [ ] **Step 3: Implementar los DTOs**

Agregar al final de `frontend/src/services/demo/dto.js`:

```js
const nombreCompleto = (u) => (u ? `${u.nombre} ${u.apellido}` : null)

export const detalleDTO = (state, d) => {
  const vendedor = state.usuarios.find((u) => u.id === d.vendedorId)
  return {
    id: d.id,
    productoId: d.productoId,
    productoVarianteId: d.productoVarianteId,
    talle: d.talle,
    productoNombre: d.productoNombre,
    productoImagen: d.productoImagen,
    cantidad: d.cantidad,
    precioUnitario: d.precioUnitario,
    subtotal: d.precioUnitario * d.cantidad,
    vendedorId: vendedor ? vendedor.id : null,
    vendedorNombre: nombreCompleto(vendedor),
    vendedorEmail: vendedor ? vendedor.email : null,
    estadoItem: d.estadoItem,
  }
}

export const pedidoDTO = (state, p) => {
  const usuario = state.usuarios.find((u) => u.id === p.usuarioId)
  return {
    id: p.id,
    usuarioId: usuario ? usuario.id : null,
    usuarioNombre: usuario ? usuario.nombre : null,
    usuarioEmail: usuario ? usuario.email : null,
    items: state.detalles.filter((d) => d.pedidoId === p.id).map((d) => detalleDTO(state, d)),
    total: p.total,
    estado: p.estado,
    direccionEnvio: p.direccionEnvio,
    notas: p.notas,
    createdAt: p.createdAt,
    updatedAt: p.updatedAt,
  }
}
```

- [ ] **Step 4: Implementar `handlers/pedidos.js`**

Crear `frontend/src/services/demo/handlers/pedidos.js`:

```js
import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { pedidoDTO } from "../dto"
import { ok, creado, demoError, noEncontrado, prohibido, pedidoInvalido } from "../respuestas"

export const buscarPedido = (state, id) => {
  const pedido = state.pedidos.find((p) => p.id === Number(id))
  if (!pedido) throw noEncontrado(`Pedido con ID ${id} no encontrado`)
  return pedido
}

// Si la variante ya no existe (se borró el producto), no hay a dónde devolver el stock.
export const devolverStock = (state, detalle) => {
  const variante = state.variantes.find((v) => v.id === detalle.productoVarianteId)
  if (variante) variante.stock += detalle.cantidad
}

const masNuevoPrimero = (a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id

export const misPedidos = ({ state, usuario }) =>
  ok(
    state.pedidos
      .filter((p) => p.usuarioId === usuario.id)
      .sort(masNuevoPrimero)
      .map((p) => pedidoDTO(state, p)),
  )

export const obtener = ({ state, params, usuario }) => {
  const pedido = buscarPedido(state, params.id)
  if (pedido.usuarioId !== usuario.id && usuario.role !== "ADMIN") {
    throw prohibido("No tienes permiso para ver este pedido")
  }
  return ok(pedidoDTO(state, pedido))
}

// Igual que PedidoService.crearPedido: valida y descuenta item por item. Si un item no
// tiene stock se lanza a mitad de camino, y el router descarta lo ya descontado.
export const crear = ({ state, body, usuario }) => {
  const items = body?.items || []
  if (items.length === 0) throw pedidoInvalido("El pedido debe tener al menos un producto")

  const pedido = {
    id: nextId(state, "pedidos"),
    usuarioId: usuario.id,
    estado: "PENDIENTE",
    direccionEnvio: body.direccionEnvio,
    notas: body.notas,
    total: 0,
    createdAt: fechaLocal(),
    updatedAt: null,
  }

  for (const item of items) {
    const variante = state.variantes.find((v) => v.id === Number(item.productoVarianteId))
    if (!variante) throw noEncontrado(`Producto con ID ${item.productoVarianteId} no encontrado`)
    const producto = state.productos.find((p) => p.id === variante.productoId)
    if (variante.stock < item.cantidad) {
      throw demoError(
        400,
        "Stock Insuficiente",
        `Stock insuficiente para ${producto.name} (talle ${variante.talle}). Disponible: ${variante.stock}, Solicitado: ${item.cantidad}`,
      )
    }
    state.detalles.push({
      id: nextId(state, "detalles"),
      pedidoId: pedido.id,
      productoId: producto.id,
      productoVarianteId: variante.id,
      talle: variante.talle,
      productoNombre: producto.name,
      productoImagen: producto.images?.[0] ?? null,
      cantidad: item.cantidad,
      precioUnitario: producto.price,
      vendedorId: producto.ownerUserId,
      estadoItem: "PENDIENTE",
    })
    variante.stock -= item.cantidad
    pedido.total += producto.price * item.cantidad
  }

  state.pedidos.push(pedido)
  return creado(pedidoDTO(state, pedido))
}

export const cancelar = ({ state, params, usuario }) => {
  const pedido = buscarPedido(state, params.id)
  if (pedido.usuarioId !== usuario.id) throw pedidoInvalido("No tienes permiso para cancelar este pedido")
  if (pedido.estado !== "PENDIENTE") throw pedidoInvalido("Solo se pueden cancelar pedidos en estado PENDIENTE")

  for (const detalle of state.detalles.filter((d) => d.pedidoId === pedido.id && d.estadoItem === "PENDIENTE")) {
    devolverStock(state, detalle)
    detalle.estadoItem = "CANCELADO_COMPRADOR"
  }
  pedido.estado = "CANCELADO_COMPRADOR"
  pedido.updatedAt = fechaLocal()
  return ok(pedidoDTO(state, pedido))
}
```

- [ ] **Step 5: Implementar `handlers/pagos.js`**

Crear `frontend/src/services/demo/handlers/pagos.js`:

```js
import { nextId } from "../store"
import { fechaLocal } from "../fechas"
import { buscarPedido } from "./pedidos"
import { ok, demoError, prohibido } from "../respuestas"

const pedidoPropio = (state, pedidoId, usuario) => {
  const pedido = buscarPedido(state, pedidoId)
  if (pedido.usuarioId !== usuario.id) throw prohibido("No tenés permiso sobre este pedido")
  return pedido
}

// En vez del checkout hosteado de Mercado Pago, la "pasarela" es la pantalla de resultado
// del propio sitio: nadie llega a mercadopago.com ni puede creer que pagó de verdad.
export const crearPreferencia = ({ state, params, usuario }) => {
  const pedido = pedidoPropio(state, params.pedidoId, usuario)
  const id = nextId(state, "pagos")
  const pago = { id, pedidoId: pedido.id, preferenceId: `demo-pref-${id}`, estado: "PENDING", createdAt: fechaLocal() }
  state.pagos.push(pago)
  return ok({ preferenceId: pago.preferenceId, initPoint: `/checkout/resultado?pedidoId=${pedido.id}` })
}

// Hace las veces del webhook: la primera consulta acredita el pago y confirma el pedido,
// con la misma lógica idempotente que PedidoService.confirmarPago.
export const estado = ({ state, params, usuario }) => {
  const pedido = pedidoPropio(state, params.pedidoId, usuario)
  const pago = state.pagos.filter((p) => p.pedidoId === pedido.id).sort((a, b) => b.id - a.id)[0]
  if (!pago) throw demoError(500, "Internal Server Error", "Ocurrió un error inesperado")

  if (pago.estado === "PENDING" && pedido.estado === "PENDIENTE") {
    pago.estado = "APPROVED"
    for (const detalle of state.detalles.filter((d) => d.pedidoId === pedido.id && d.estadoItem === "PENDIENTE")) {
      detalle.estadoItem = "CONFIRMADO"
    }
    pedido.estado = "CONFIRMADO"
    pedido.updatedAt = fechaLocal()
  }
  return ok({ estadoPago: pago.estado, estadoPedido: pedido.estado })
}
```

- [ ] **Step 6: Registrar las rutas**

En `frontend/src/services/demo/router.js`, agregar los imports:

```js
import * as pedidos from "./handlers/pedidos"
import * as pagos from "./handlers/pagos"
```

y agregar al final de `RUTAS`, después de la fila de categorías:

```js

  ["GET", "/pedidos/mis-pedidos", "usuario", pedidos.misPedidos],
  ["GET", "/pedidos/:id", "usuario", pedidos.obtener],
  ["POST", "/pedidos", "usuario", pedidos.crear],
  ["PUT", "/pedidos/:id/cancelar", "usuario", pedidos.cancelar],

  ["POST", "/pagos/pedidos/:pedidoId/preferencia", "usuario", pagos.crearPreferencia],
  ["GET", "/pagos/pedidos/:pedidoId/estado", "usuario", pagos.estado],
```

- [ ] **Step 7: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: todos pasan (`Tests  61 passed (61)`).

- [ ] **Step 8: Verificación manual del checkout**

Con `VITE_DEMO_MODE=true npm run dev`, logueado como `test@test.com`:
1. Agregar Boca talle M al carrito y finalizar la compra. La página recarga en `/checkout/resultado?pedidoId=4`, sigue logueada (Task 2) y muestra "¡Pago aprobado!".
2. "Ver mi pedido" muestra el pedido 4 `CONFIRMADO`, y en `/product/1` el stock de M bajó de 10 a 9.
3. Desloguearse, entrar como `user1@test.com`, ir a `/orders` y cancelar el pedido 3. El toast confirma y el pedido queda cancelado.

- [ ] **Step 9: Commit**

```bash
cd ..
git add frontend/src/services/demo
git commit -m "feat: checkout, pedidos y pago simulado de Mercado Pago en el modo demo" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Ventas del vendedor

**Files:**
- Modify: `frontend/src/services/demo/dto.js` (agregar `ventaDTO`)
- Create: `frontend/src/services/demo/handlers/ventas.js`
- Modify: `frontend/src/services/demo/router.js`
- Create: `frontend/src/services/demo/handlers/ventas.test.js`

**Interfaces:**
- Consumes: `devolverStock` (`handlers/pedidos.js`), contrato de handler, `llamar`/`TOKENS`.
- Produces: `ventaDTO(state, detalle)` con la forma de `VentaDTO.java`. Rutas `GET /ventas/mis-ventas`, `GET /ventas/estadisticas` y `PUT /ventas/:detalleId/estado?estado=X`.

- [ ] **Step 1: Escribir los tests que fallan**

Crear `frontend/src/services/demo/handlers/ventas.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const mover = (detalleId, estado, token = TOKENS.user1) =>
  llamar("PUT", `/ventas/${detalleId}/estado?estado=${estado}`, { token })

describe("ventas del vendedor en la demo", () => {
  it("lista sólo las ventas propias con la forma de VentaDTO, de la más nueva a la más vieja", async () => {
    const res = await llamar("GET", "/ventas/mis-ventas", { token: TOKENS.user1 })

    expect(res.body.map((v) => v.detalleId)).toEqual([2, 1])
    expect(res.body[0]).toMatchObject({
      pedidoId: 2,
      productoNombre: "Camiseta Titular Real Madrid 2026",
      talle: "L",
      estadoItem: "CONFIRMADO",
      compradorId: 3,
      compradorNombre: "Test User",
      compradorEmail: "test@test.com",
      direccionEnvio: "Av. Corrientes 1234, CABA",
    })
  })

  it("cuenta las ventas por estado", async () => {
    const res = await llamar("GET", "/ventas/estadisticas", { token: TOKENS.user1 })

    expect(res.body).toEqual({
      totalVentas: 2,
      ventasPendientes: 0,
      ventasConfirmadas: 1,
      ventasEnviadas: 0,
      ventasEntregadas: 1,
      ventasCanceladas: 0,
    })
  })

  it("avanza un item por las transiciones válidas y deriva el estado del pedido", async () => {
    expect((await mover(2, "PREPARANDO")).status).toBe(200)
    const enviado = await mover(2, "ENVIADO")

    expect(enviado.body.estadoItem).toBe("ENVIADO")
    const pedido = await llamar("GET", "/pedidos/2", { token: TOKENS.testuser })
    expect(pedido.body.estado).toBe("ENVIADO")
  })

  it("rechaza una transición inválida", async () => {
    const res = await mover(2, "ENTREGADO")

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("Transición de estado inválida desde CONFIRMADO")
  })

  it("al cancelar el vendedor devuelve el stock y el pedido queda cancelado", async () => {
    const res = await mover(3, "CANCELADO_VENDEDOR", TOKENS.testuser)

    expect(res.body.estadoItem).toBe("CANCELADO_VENDEDOR")
    const producto = await llamar("GET", "/productos/5")
    expect(producto.body.variantes.find((v) => v.talle === "M").stock).toBe(17)
    const pedido = await llamar("GET", "/pedidos/3", { token: TOKENS.user1 })
    expect(pedido.body.estado).toBe("CANCELADO_COMPRADOR")
  })

  it("no deja mover un item cancelado", async () => {
    await mover(3, "CANCELADO_VENDEDOR", TOKENS.testuser)

    const res = await mover(3, "CONFIRMADO", TOKENS.testuser)

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("No se puede cambiar el estado de un item cancelado o devuelto")
  })

  it("no deja mover el item de otro vendedor", async () => {
    const res = await mover(3, "CONFIRMADO", TOKENS.user1)

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("No tienes permiso para modificar este item")
  })

  it("rechaza un estado que no existe", async () => {
    const res = await mover(2, "VOLANDO")

    expect(res.status).toBe(400)
    expect(res.body.message).toBe("Estado inválido: VOLANDO")
  })
})
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: FAIL. Las rutas de ventas dan 501.

- [ ] **Step 3: Implementar `ventaDTO`**

Agregar al final de `frontend/src/services/demo/dto.js`:

```js
export const ventaDTO = (state, d) => {
  const pedido = state.pedidos.find((p) => p.id === d.pedidoId)
  const comprador = pedido ? state.usuarios.find((u) => u.id === pedido.usuarioId) : null
  return {
    detalleId: d.id,
    productoId: d.productoId,
    productoNombre: d.productoNombre,
    talle: d.talle,
    productoImagen: d.productoImagen,
    cantidad: d.cantidad,
    precioUnitario: d.precioUnitario,
    subtotal: d.precioUnitario * d.cantidad,
    estadoItem: d.estadoItem,
    pedidoId: pedido ? pedido.id : null,
    fechaPedido: pedido ? pedido.createdAt : null,
    compradorId: comprador ? comprador.id : null,
    compradorNombre: nombreCompleto(comprador),
    compradorEmail: comprador ? comprador.email : null,
    direccionEnvio: pedido ? pedido.direccionEnvio : null,
  }
}
```

- [ ] **Step 4: Implementar `handlers/ventas.js`**

Crear `frontend/src/services/demo/handlers/ventas.js`:

```js
import { fechaLocal } from "../fechas"
import { ventaDTO } from "../dto"
import { devolverStock } from "./pedidos"
import { ok, pedidoInvalido } from "../respuestas"

const ESTADOS = [
  "PENDIENTE", "CONFIRMADO", "PREPARANDO", "ENVIADO", "EN_TRANSITO", "ENTREGADO", "CANCELADO",
  "CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR", "DEVOLUCION_SOLICITADA", "DEVUELTO", "PAGO_RECHAZADO",
]

// Copia de PedidoService.validarTransicionEstado. Un estado que no figura acá ni en
// FINALES no restringe la transición, igual que en el backend.
const TRANSICIONES = {
  PENDIENTE: ["CONFIRMADO", "CANCELADO_VENDEDOR", "CANCELADO_COMPRADOR"],
  CONFIRMADO: ["PREPARANDO", "CANCELADO_VENDEDOR"],
  PREPARANDO: ["ENVIADO"],
  ENVIADO: ["EN_TRANSITO", "ENTREGADO"],
  EN_TRANSITO: ["ENTREGADO"],
  ENTREGADO: ["DEVOLUCION_SOLICITADA"],
  DEVOLUCION_SOLICITADA: ["DEVUELTO"],
}
const FINALES = ["CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR", "DEVUELTO"]

const validarTransicion = (actual, nuevo) => {
  if (FINALES.includes(actual)) {
    throw pedidoInvalido("No se puede cambiar el estado de un item cancelado o devuelto")
  }
  const permitidos = TRANSICIONES[actual]
  if (permitidos && !permitidos.includes(nuevo)) {
    throw pedidoInvalido(`Transición de estado inválida desde ${actual}`)
  }
}

// Copia de PedidoService.actualizarEstadoPedidoGeneral: el estado del pedido se deriva de
// los de sus items, nunca se setea a mano.
const estadoDerivado = (items) => {
  const cuantos = (...estados) => items.filter((i) => estados.includes(i.estadoItem)).length
  if (cuantos("CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR") > 0) return "CANCELADO_COMPRADOR"
  if (cuantos("ENTREGADO") === items.length) return "ENTREGADO"
  if (cuantos("EN_TRANSITO", "ENVIADO") > 0) return "ENVIADO"
  if (cuantos("CONFIRMADO", "PREPARANDO") > 0) return "CONFIRMADO"
  if (cuantos("PENDIENTE") === items.length) return "PENDIENTE"
  return "CONFIRMADO"
}

const fechaDelPedido = (state, d) => state.pedidos.find((p) => p.id === d.pedidoId)?.createdAt || ""

const ventasDe = (state, usuario) =>
  state.detalles
    .filter((d) => d.vendedorId === usuario.id)
    .sort((a, b) => fechaDelPedido(state, b).localeCompare(fechaDelPedido(state, a)) || b.id - a.id)

export const misVentas = ({ state, usuario }) => ok(ventasDe(state, usuario).map((d) => ventaDTO(state, d)))

export const estadisticas = ({ state, usuario }) => {
  const ventas = ventasDe(state, usuario)
  const cuantas = (...estados) => ventas.filter((v) => estados.includes(v.estadoItem)).length
  return ok({
    totalVentas: ventas.length,
    ventasPendientes: cuantas("PENDIENTE"),
    ventasConfirmadas: cuantas("CONFIRMADO"),
    ventasEnviadas: cuantas("ENVIADO", "EN_TRANSITO"),
    ventasEntregadas: cuantas("ENTREGADO"),
    ventasCanceladas: cuantas("CANCELADO", "CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR"),
  })
}

export const actualizarEstado = ({ state, params, query, usuario }) => {
  const nuevo = (query.estado || "").toUpperCase()
  if (!ESTADOS.includes(nuevo)) throw pedidoInvalido(`Estado inválido: ${query.estado}`)

  const detalle = state.detalles.find((d) => d.id === Number(params.detalleId))
  if (!detalle) throw pedidoInvalido("Detalle de pedido no encontrado")
  if (detalle.vendedorId !== usuario.id) throw pedidoInvalido("No tienes permiso para modificar este item")

  validarTransicion(detalle.estadoItem, nuevo)
  if (nuevo === "CANCELADO_VENDEDOR") devolverStock(state, detalle)
  detalle.estadoItem = nuevo

  const pedido = state.pedidos.find((p) => p.id === detalle.pedidoId)
  const derivado = estadoDerivado(state.detalles.filter((d) => d.pedidoId === pedido.id))
  if (pedido.estado !== derivado) {
    pedido.estado = derivado
    pedido.updatedAt = fechaLocal()
  }
  return ok(ventaDTO(state, detalle))
}
```

- [ ] **Step 5: Registrar las rutas**

En `frontend/src/services/demo/router.js`, agregar el import `import * as ventas from "./handlers/ventas"` y, al final de `RUTAS`:

```js

  ["GET", "/ventas/mis-ventas", "usuario", ventas.misVentas],
  ["GET", "/ventas/estadisticas", "usuario", ventas.estadisticas],
  ["PUT", "/ventas/:detalleId/estado", "usuario", ventas.actualizarEstado],
```

- [ ] **Step 6: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: todos pasan (`Tests  69 passed (69)`).

- [ ] **Step 7: Verificación manual**

Con `VITE_DEMO_MODE=true npm run dev`, logueado como `user1@test.com`, entrar a `/sales`:
- Muestra 2 ventas y las tarjetas de estadísticas.
- Avanzar la del Real Madrid a "Preparando" y después a "Enviado".
- Como `test@test.com`, `/orders/2` muestra el pedido enviado.

- [ ] **Step 8: Commit**

```bash
cd ..
git add frontend/src/services/demo
git commit -m "feat: ventas del vendedor y transiciones de estado en el modo demo" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Panel de administración y reportes

**Files:**
- Create: `frontend/src/services/demo/handlers/admin.js`
- Modify: `frontend/src/services/demo/router.js`
- Create: `frontend/src/services/demo/handlers/admin.test.js`

**Interfaces:**
- Consumes: `usuarioDTO`, `nextId`, `fechaLocal`, `restarDias`, helpers de `respuestas.js`, `llamar`/`TOKENS`.
- Produces: rutas `"admin"` para `/admin/usuarios*` y `/pedidos/admin/reportes`.

- [ ] **Step 1: Escribir los tests que fallan**

Crear `frontend/src/services/demo/handlers/admin.test.js`:

```js
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { llamar, usarStorageEnMemoria, TOKENS } from "../../../test/llamarDemo"

beforeEach(usarStorageEnMemoria)
afterEach(() => {
  vi.unstubAllGlobals()
})

const nuevo = {
  username: "vendedor2",
  email: "vendedor2@test.com",
  password: "clave123",
  nombre: "Vende",
  apellido: "Dor",
  role: "USER",
}

describe("administración de usuarios en la demo", () => {
  it("exige rol ADMIN", async () => {
    const res = await llamar("GET", "/admin/usuarios", { token: TOKENS.user1 })

    expect(res.status).toBe(403)
  })

  it("lista los usuarios sin contraseñas", async () => {
    const res = await llamar("GET", "/admin/usuarios", { token: TOKENS.admin })

    expect(res.body).toHaveLength(3)
    expect(res.body.every((u) => !("password" in u))).toBe(true)
  })

  it("cuenta usuarios por rol", async () => {
    const res = await llamar("GET", "/admin/usuarios/estadisticas", { token: TOKENS.admin })

    expect(res.body).toEqual({ totalUsuarios: 3, adminUsuarios: 1, userUsuarios: 2 })
  })

  it("crea un usuario que después puede loguearse", async () => {
    const res = await llamar("POST", "/admin/usuarios", { body: nuevo, token: TOKENS.admin })

    expect(res.status).toBe(201)
    expect(res.body).toMatchObject({ id: 4, username: "vendedor2", role: "USER" })
    const login = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "vendedor2", password: "clave123" },
    })
    expect(login.status).toBe(200)
  })

  it("rechaza un email repetido con 409", async () => {
    const res = await llamar("POST", "/admin/usuarios", {
      body: { ...nuevo, email: "user1@test.com" },
      token: TOKENS.admin,
    })

    expect(res.status).toBe(409)
    expect(res.body.message).toBe("El email ya está registrado")
  })

  it("actualiza los datos y cambia la contraseña sólo si viene", async () => {
    const res = await llamar("PUT", "/admin/usuarios/2", {
      body: { username: "user1", email: "user1@test.com", nombre: "Usuario", apellido: "Uno", role: "USER" },
      token: TOKENS.admin,
    })

    expect(res.body.nombre).toBe("Usuario")
    const login = await llamar("POST", "/auth/login", {
      body: { emailOrUsername: "user1@test.com", password: "user123" },
    })
    expect(login.status).toBe(200)
  })

  it("cambia el rol y rechaza uno inválido", async () => {
    const ok = await llamar("PUT", "/admin/usuarios/2/rol", { body: { role: "ADMIN" }, token: TOKENS.admin })
    const mal = await llamar("PUT", "/admin/usuarios/2/rol", { body: { role: "JEFE" }, token: TOKENS.admin })

    expect(ok.body.role).toBe("ADMIN")
    expect(mal.status).toBe(400)
    expect(mal.body.message).toBe("Rol inválido. Debe ser USER o ADMIN")
  })

  it("elimina un usuario y su token deja de valer", async () => {
    const res = await llamar("DELETE", "/admin/usuarios/3", { token: TOKENS.admin })

    expect(res.status).toBe(200)
    expect(res.body.message).toBe("Usuario eliminado exitosamente")
    expect((await llamar("POST", "/auth/validate", { token: TOKENS.testuser })).status).toBe(401)
  })

  it("devuelve 404 al tocar un usuario inexistente", async () => {
    const res = await llamar("DELETE", "/admin/usuarios/99", { token: TOKENS.admin })

    expect(res.status).toBe(404)
  })
})

describe("reporte de ventas en la demo", () => {
  it("por defecto cubre los últimos 30 días y sólo cuenta pedidos pagados", async () => {
    const res = await llamar("GET", "/pedidos/admin/reportes?", { token: TOKENS.admin })

    expect(res.body).toMatchObject({ ventasTotales: 123000, ticketPromedio: 61500, cantidadPedidos: 2 })
    expect(res.body.productosMasVendidos).toHaveLength(2)
    expect(res.body.productosMasVendidos.map((p) => p.nombre).sort()).toEqual([
      "Camiseta Titular Boca Juniors 2026",
      "Camiseta Titular Real Madrid 2026",
    ])
  })

  it("respeta el rango de fechas pedido", async () => {
    const res = await llamar("GET", "/pedidos/admin/reportes?desde=2000-01-01T00:00:00&hasta=2000-12-31T23:59:59", {
      token: TOKENS.admin,
    })

    expect(res.body).toEqual({ ventasTotales: 0, ticketPromedio: 0, cantidadPedidos: 0, productosMasVendidos: [] })
  })

  it("suma una compra recién pagada", async () => {
    const pedido = await llamar("POST", "/pedidos", {
      body: { items: [{ productoVarianteId: 2, cantidad: 2 }], direccionEnvio: "Calle 1", notas: "" },
      token: TOKENS.testuser,
    })
    await llamar("POST", `/pagos/pedidos/${pedido.body.id}/preferencia`, { token: TOKENS.testuser })
    await llamar("GET", `/pagos/pedidos/${pedido.body.id}/estado`, { token: TOKENS.testuser })

    const res = await llamar("GET", "/pedidos/admin/reportes?", { token: TOKENS.admin })

    expect(res.body.cantidadPedidos).toBe(3)
    expect(res.body.productosMasVendidos[0]).toEqual({ nombre: "Camiseta Titular Boca Juniors 2026", cantidadVendida: 3 })
  })

  it("exige rol ADMIN", async () => {
    const res = await llamar("GET", "/pedidos/admin/reportes?", { token: TOKENS.user1 })

    expect(res.status).toBe(403)
  })
})
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `npm test`
Expected: FAIL. Las rutas de admin dan 501.

- [ ] **Step 3: Implementar `handlers/admin.js`**

Crear `frontend/src/services/demo/handlers/admin.js`:

```js
import { nextId } from "../store"
import { fechaLocal, restarDias } from "../fechas"
import { usuarioDTO } from "../dto"
import { ok, creado, conflicto, noEncontrado, pedidoInvalido } from "../respuestas"

const ROLES = ["USER", "ADMIN"]

// Copia de PedidoService.ESTADOS_NO_VENTA: pedidos que no representan plata facturada.
const ESTADOS_NO_VENTA = ["PENDIENTE", "PAGO_RECHAZADO", "CANCELADO", "CANCELADO_COMPRADOR", "CANCELADO_VENDEDOR", "DEVUELTO"]

const buscarUsuario = (state, id) => {
  const usuario = state.usuarios.find((u) => u.id === Number(id))
  if (!usuario) throw noEncontrado(`Usuario con ID ${id} no encontrado`)
  return usuario
}

export const listarUsuarios = ({ state }) => ok(state.usuarios.map(usuarioDTO))

export const estadisticasUsuarios = ({ state }) =>
  ok({
    totalUsuarios: state.usuarios.length,
    adminUsuarios: state.usuarios.filter((u) => u.role === "ADMIN").length,
    userUsuarios: state.usuarios.filter((u) => u.role === "USER").length,
  })

export const crearUsuario = ({ state, body }) => {
  if (state.usuarios.some((u) => u.email === body.email)) throw conflicto("El email ya está registrado")
  if (state.usuarios.some((u) => u.username === body.username)) throw conflicto("El nombre de usuario ya está registrado")
  const usuario = {
    id: nextId(state, "usuarios"),
    username: body.username,
    email: body.email,
    password: body.password,
    nombre: body.nombre,
    apellido: body.apellido,
    role: body.role || "USER",
    createdAt: fechaLocal(),
    updatedAt: null,
  }
  state.usuarios.push(usuario)
  return creado(usuarioDTO(usuario))
}

// Igual que AdminController.updateUsuario: sólo se pisan los campos que vienen.
export const actualizarUsuario = ({ state, params, body }) => {
  const usuario = buscarUsuario(state, params.id)
  if (body.email && body.email !== usuario.email) {
    if (state.usuarios.some((u) => u.email === body.email)) throw conflicto("El email ya está registrado")
    usuario.email = body.email
  }
  if (body.username && body.username !== usuario.username) {
    if (state.usuarios.some((u) => u.username === body.username)) throw conflicto("El nombre de usuario ya está registrado")
    usuario.username = body.username
  }
  if (body.nombre) usuario.nombre = body.nombre
  if (body.apellido) usuario.apellido = body.apellido
  if (body.password) usuario.password = body.password
  if (body.role) usuario.role = body.role
  usuario.updatedAt = fechaLocal()
  return ok(usuarioDTO(usuario))
}

export const cambiarRol = ({ state, params, body }) => {
  const usuario = buscarUsuario(state, params.id)
  const rol = (body?.role || "").toUpperCase()
  if (!ROLES.includes(rol)) throw pedidoInvalido("Rol inválido. Debe ser USER o ADMIN")
  usuario.role = rol
  usuario.updatedAt = fechaLocal()
  return ok(usuarioDTO(usuario))
}

export const eliminarUsuario = ({ state, params }) => {
  const usuario = buscarUsuario(state, params.id)
  state.usuarios = state.usuarios.filter((u) => u.id !== usuario.id)
  return ok({ message: "Usuario eliminado exitosamente" })
}

// Las fechas de la demo son strings "YYYY-MM-DDTHH:mm:ss": comparan bien como texto.
export const reporteVentas = ({ state, query }) => {
  const ahora = new Date()
  const desde = query.desde || fechaLocal(restarDias(ahora, 30))
  const hasta = query.hasta || fechaLocal(ahora)

  const pagados = state.pedidos.filter(
    (p) => p.createdAt >= desde && p.createdAt <= hasta && !ESTADOS_NO_VENTA.includes(p.estado),
  )
  const ventasTotales = pagados.reduce((suma, p) => suma + p.total, 0)
  const ticketPromedio = pagados.length === 0 ? 0 : Math.round((ventasTotales / pagados.length) * 100) / 100

  const vendidos = new Map()
  for (const d of state.detalles.filter((d) => pagados.some((p) => p.id === d.pedidoId))) {
    vendidos.set(d.productoNombre, (vendidos.get(d.productoNombre) || 0) + d.cantidad)
  }
  const productosMasVendidos = [...vendidos]
    .map(([nombre, cantidadVendida]) => ({ nombre, cantidadVendida }))
    .sort((a, b) => b.cantidadVendida - a.cantidadVendida)

  return ok({ ventasTotales, ticketPromedio, cantidadPedidos: pagados.length, productosMasVendidos })
}
```

- [ ] **Step 4: Registrar las rutas**

En `frontend/src/services/demo/router.js`, agregar el import `import * as admin from "./handlers/admin"` y, al final de `RUTAS`:

```js

  ["GET", "/admin/usuarios", "admin", admin.listarUsuarios],
  ["GET", "/admin/usuarios/estadisticas", "admin", admin.estadisticasUsuarios],
  ["POST", "/admin/usuarios", "admin", admin.crearUsuario],
  ["PUT", "/admin/usuarios/:id", "admin", admin.actualizarUsuario],
  ["PUT", "/admin/usuarios/:id/rol", "admin", admin.cambiarRol],
  ["DELETE", "/admin/usuarios/:id", "admin", admin.eliminarUsuario],
  ["GET", "/pedidos/admin/reportes", "admin", admin.reporteVentas],
```

- [ ] **Step 5: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: todos pasan (`Tests  82 passed (82)`).

- [ ] **Step 6: Verificación manual**

Con `VITE_DEMO_MODE=true npm run dev`, logueado como `admin@test.com`:
- `/admin` lista los 3 usuarios con sus estadísticas.
- Crear un usuario, cambiarle el rol y borrarlo.
- `/admin/reportes` muestra $123.000 en ventas totales y los dos productos más vendidos.

- [ ] **Step 7: Commit**

```bash
cd ..
git add frontend/src/services/demo
git commit -m "feat: panel de administración y reportes de ventas en el modo demo" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Imágenes, banner, cuentas de prueba y "Reiniciar demo"

**Files:**
- Create: `frontend/src/services/demo/imagenes.js`
- Create: `frontend/src/services/demo/imagenes.test.js`
- Modify: `frontend/src/services/api.js` (`uploadImage`)
- Create: `frontend/src/components/demo/DemoBanner.jsx`
- Create: `frontend/src/components/demo/DemoCuentas.jsx`
- Modify: `frontend/src/App.jsx`
- Modify: `frontend/src/pages/Login.jsx`

**Interfaces:**
- Consumes: `reiniciar()` (store), `DEMO` / `__DEMO_MODE__` (Task 4).
- Produces: `leerImagenDemo(file: File): Promise<string>` (`data:` URL), `TAMANIO_MAXIMO_DEMO = 1048576`. Componentes `<DemoBanner />` y `<DemoCuentas onElegir={(email, password) => void} />`.

- [ ] **Step 1: Escribir el test que falla**

Crear `frontend/src/services/demo/imagenes.test.js`:

```js
import { describe, it, expect } from "vitest"
import { leerImagenDemo, TAMANIO_MAXIMO_DEMO } from "./imagenes"

describe("subida de imágenes en la demo", () => {
  it("devuelve la imagen como data: URL", async () => {
    const file = new File([new Uint8Array([137, 80, 78, 71])], "foto.png", { type: "image/png" })

    expect(await leerImagenDemo(file)).toBe("data:image/png;base64,iVBORw==")
  })

  it("rechaza con un mensaje claro una imagen que no entraría en localStorage", async () => {
    const file = new File([new Uint8Array(TAMANIO_MAXIMO_DEMO + 1)], "grande.jpg", { type: "image/jpeg" })

    await expect(leerImagenDemo(file)).rejects.toThrow('En el modo demo las imágenes no pueden superar 1 MB ("grande.jpg" pesa 1.0 MB).')
  })
})
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `npm test`
Expected: FAIL porque `./imagenes` no existe.

- [ ] **Step 3: Implementar `imagenes.js` y engancharlo**

Crear `frontend/src/services/demo/imagenes.js`:

```js
// En la demo no hay servidor de archivos: la imagen se guarda como data: URL dentro del
// estado en localStorage. El límite es más bajo que los 5 MB del backend porque todo
// localStorage ronda los 5 MB y el base64 agrega un tercio.
export const TAMANIO_MAXIMO_DEMO = 1024 * 1024

export const leerImagenDemo = async (file) => {
  if (file.size > TAMANIO_MAXIMO_DEMO) {
    const megas = (file.size / 1024 / 1024).toFixed(1)
    throw new Error(`En el modo demo las imágenes no pueden superar 1 MB ("${file.name}" pesa ${megas} MB).`)
  }
  const bytes = new Uint8Array(await file.arrayBuffer())
  let binario = ""
  for (let i = 0; i < bytes.length; i++) {
    binario += String.fromCharCode(bytes[i])
  }
  return `data:${file.type};base64,${btoa(binario)}`
}
```

En `frontend/src/services/api.js`, al principio de `uploadImage(file)`, antes de `const formData = new FormData()`:

```js
    if (DEMO) {
      const { leerImagenDemo } = await import("./demo/imagenes")
      return leerImagenDemo(file)
    }

```

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run: `npm test`
Expected: todos pasan (`Tests  84 passed (84)`).

- [ ] **Step 5: Banner y cuentas de prueba**

Crear `frontend/src/components/demo/DemoBanner.jsx`:

```jsx
import { reiniciar } from "../../services/demo/store"

// Un ecommerce que acepta "pagos" sin avisar que es una demo sería engañoso: el banner
// aparece en todas las pantallas, login incluido, mientras el modo demo esté activo.
const DemoBanner = () => {
  // Vuelve todo al estado inicial: datos de la demo, sesión y carrito (el carrito puede
  // apuntar a talles que el reinicio hace desaparecer).
  const reiniciarDemo = () => {
    reiniciar()
    for (const storage of [localStorage, sessionStorage]) {
      storage.removeItem("auth_token")
      storage.removeItem("auth_user")
    }
    localStorage.removeItem("cart_items")
    window.location.assign("/login")
  }

  return (
    <div className="bg-amber-400 text-amber-950 text-sm px-4 py-2 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-center">
      <span>
        <strong>Modo demo</strong> — datos de ejemplo, sin backend real. Ningún pago es real.
      </span>
      <button type="button" onClick={reiniciarDemo} className="underline font-medium">
        Reiniciar demo
      </button>
    </div>
  )
}

export default DemoBanner
```

Crear `frontend/src/components/demo/DemoCuentas.jsx`:

```jsx
// Cuentas sembradas de la demo (seed.js): sin esto, quien entra no tiene cómo loguearse.
const CUENTAS = [
  { rol: "Compra y vende", email: "user1@test.com", password: "user123" },
  { rol: "Vende", email: "test@test.com", password: "test123" },
  { rol: "Administra", email: "admin@test.com", password: "admin123" },
]

const DemoCuentas = ({ onElegir }) => (
  <div className="rounded-md border border-amber-300 bg-amber-50 dark:bg-gray-800 dark:border-amber-700 p-4 text-sm">
    <p className="font-medium text-gray-900 dark:text-white mb-2">Cuentas de prueba</p>
    <ul className="space-y-1">
      {CUENTAS.map((cuenta) => (
        <li key={cuenta.email}>
          <button
            type="button"
            onClick={() => onElegir(cuenta.email, cuenta.password)}
            className="text-left text-blue-700 dark:text-blue-400 hover:underline"
          >
            {cuenta.email} / {cuenta.password}
          </button>
          <span className="text-gray-600 dark:text-gray-400"> — {cuenta.rol}</span>
        </li>
      ))}
    </ul>
  </div>
)

export default DemoCuentas
```

En `frontend/src/App.jsx`, agregar el import `import DemoBanner from "./components/demo/DemoBanner"` y reemplazar `<AppRouter />` por:

```jsx
              {__DEMO_MODE__ && <DemoBanner />}
              <AppRouter />
```

En `frontend/src/pages/Login.jsx`, agregar el import `import DemoCuentas from "../components/demo/DemoCuentas"`. Justo después del `</h2>` del título ("Iniciar Sesión" / "Crear Cuenta"), agregar:

```jsx
          {__DEMO_MODE__ && isLoginMode && (
            <div className="mt-4">
              <DemoCuentas onElegir={(email, password) => setFormData((prev) => ({ ...prev, email, password }))} />
            </div>
          )}
```

- [ ] **Step 6: Verificar el aislamiento del build (§7.1 y §7.2 de la spec)**

Run (desde `frontend/`):

```bash
rm -rf dist && npm run build >/dev/null && (grep -rlE "BOCA-2026-TIT|Modo demo|demo-token-|Cuentas de prueba" dist || echo "OK: build de producción sin demo")
rm -rf dist && VITE_DEMO_MODE=true npm run build >/dev/null && grep -rlE "BOCA-2026-TIT" dist && grep -rl "Modo demo" dist
```

Expected: la primera línea imprime `OK: build de producción sin demo`. La segunda lista al menos un archivo por cada grep. Si la primera encuentra algo, **parar**: algo importa la demo por fuera de un branch `__DEMO_MODE__`/`DEMO`.

- [ ] **Step 7: Verificación manual**

Con `VITE_DEMO_MODE=true npm run dev`:
1. `/login` muestra el banner y las cuentas de prueba. Un clic en una cuenta completa el formulario.
2. Logueado como `user1@test.com`, crear un producto subiendo una imagen chica (menos de 1 MB). Se ve en el catálogo después de recargar.
3. Probar con una imagen de más de 1 MB: aparece el mensaje "En el modo demo las imágenes no pueden superar 1 MB…".
4. Borrar todos los productos propios y después "Reiniciar demo": vuelve a `/login` y, al entrar, están los 6 productos.
5. Sin la variable (`npm run dev` a secas, con el backend corriendo) no hay banner ni cuentas de prueba.

- [ ] **Step 8: Lint, tests y commit**

Run: `npm run lint 2>&1 | tail -3 && npm test 2>&1 | tail -4` → `0 errors`, todos los tests pasan.

```bash
cd ..
git add frontend/src/services/demo/imagenes.js frontend/src/services/demo/imagenes.test.js frontend/src/services/api.js frontend/src/components/demo frontend/src/App.jsx frontend/src/pages/Login.jsx
git commit -m "feat: banner del modo demo, cuentas de prueba, reinicio y subida de imágenes simulada" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: `vercel.json`, LICENSE y verificación final local

**Files:**
- Create: `frontend/vercel.json`
- Create: `LICENSE`

**Interfaces:**
- Produces: la configuración que la Task 12 usa en Vercel.

- [ ] **Step 1: `vercel.json`**

Crear `frontend/vercel.json`:

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "framework": "vite",
  "buildCommand": "npm run build",
  "outputDirectory": "dist",
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }],
  "headers": [
    {
      "source": "/(.*)",
      "headers": [
        { "key": "X-Content-Type-Options", "value": "nosniff" },
        { "key": "Referrer-Policy", "value": "strict-origin-when-cross-origin" },
        { "key": "X-Frame-Options", "value": "DENY" }
      ]
    }
  ]
}
```

El rewrite es lo que evita el 404 al entrar directo a `/product/3`: React Router rutea del lado del cliente, y Vercel sirve primero los archivos que existen (`/assets/*`) y recién después aplica el rewrite.

Run: `node -e "JSON.parse(require('fs').readFileSync('frontend/vercel.json','utf8')); console.log('JSON válido')"` (desde la raíz)
Expected: `JSON válido`

- [ ] **Step 2: LICENSE**

Crear `LICENSE` en la raíz:

```
MIT License

Copyright (c) 2026 Contribuidores de TPO-Ecommerce

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- [ ] **Step 3: Suite del backend (confirmar que nada se rompió por accidente)**

Run:

```bash
cd backend && rm -rf target/surefire-reports && mvn -B test 2>&1 | grep -E "Tests run:|BUILD" | tail -3
```

Expected: `Tests run: 156, Failures: 0, Errors: 0` y `BUILD SUCCESS`.

- [ ] **Step 4: Frontend completo**

Run (desde `frontend/`): `npm test 2>&1 | tail -4 && npm run lint 2>&1 | tail -3 && npm run build 2>&1 | tail -2`
Expected: todos los tests pasan, `0 errors`, build OK.

- [ ] **Step 5: Commit**

```bash
cd ..
git add frontend/vercel.json LICENSE
git commit -m "build: configuración de Vercel para la demo y licencia MIT" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Publicar el repositorio en GitHub

> Acción hacia afuera e irreversible (el contenido queda público y puede quedar cacheado). **No avanzar sin la confirmación explícita del usuario en el Step 2.**

**Files:** ninguno, o `CLAUDE.md` si el usuario decide versionarlo (Step 1).

- [ ] **Step 1: Árbol limpio y decisión sobre `CLAUDE.md`**

Run: `git status --short`
Expected: sólo `?? CLAUDE.md`, que estaba sin trackear desde antes de este plan.

Preguntarle al usuario si `CLAUDE.md` se publica. Si dice que sí, sumarle a la sección "Comandos", en el bloque de frontend:

```bash
npm test               # vitest (api.js, sesión y backend simulado de la demo)
VITE_DEMO_MODE=true npm run dev   # modo demo: backend simulado en el navegador, sin :8081
```

y agregar debajo de "Un solo origen, dos apps" este párrafo:

```markdown
### Modo demo

Con `VITE_DEMO_MODE=true` (sólo en el deploy de Vercel), `request()` y `uploadImage()`
derivan a `frontend/src/services/demo/`: un router que devuelve `Response` reales con los
DTOs del backend, sobre un estado en `localStorage` sembrado como `DataInitializer`. El flag
llega como el literal `__DEMO_MODE__` (`define` en `vite.config.js`), no por
`import.meta.env`: con esto último Vite 4 emite igual el chunk de la demo en el build del
VPS. Un endpoint nuevo que use una pantalla necesita su fila en `RUTAS` de `router.js`.
```

Después commitear: `git add CLAUDE.md && git commit -m "docs: agregar CLAUDE.md con la guía del repo" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"`. Si dice que no, dejarlo sin trackear.

- [ ] **Step 2: Pedir el repositorio vacío**

Pedirle al usuario que cree el repositorio público en github.com **vacío** (sin README, sin `.gitignore`, sin licencia) y que pase la URL. Avisarle que el primer push a `main` dispara `build-and-push.yml`, que corre los dos CI y publica las imágenes en `ghcr.io`. Ese es el ejercicio del gate de CI que la Fase 5 dejó pendiente, en su camino feliz.

- [ ] **Step 3: Remote y push sólo de `main`**

```bash
git remote add origin <URL que pasó el usuario>
git push -u origin main
```

- [ ] **Step 4: Verificar que sólo se publicó `main`**

Run: `git ls-remote --heads origin`
Expected: una sola línea, `refs/heads/main`.

- [ ] **Step 5: Verificar CI**

Pedirle al usuario que abra la pestaña Actions del repositorio, o leerla si hay acceso. Tienen que pasar "Backend CI", "Frontend CI" y después "build-and-push". Si algo falla, diagnosticar con superpowers:systematic-debugging antes de seguir.

---

### Task 12: Deploy en Vercel, README y recorrido de la demo

> Requiere la cuenta de Vercel del usuario. La configuración del proyecto la hace el usuario en el dashboard. El executor guía y verifica.

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Crear el proyecto en Vercel**

Pedirle al usuario que, en vercel.com → Add New → Project, importe el repositorio de GitHub con esta configuración:
- **Root Directory:** `frontend`
- **Framework Preset:** Vite (lo toma de `vercel.json`)
- **Environment Variables:** `VITE_DEMO_MODE` = `true`, tildada en **Production** y en **Preview**
- Deploy.

Que pase la URL `https://<proyecto>.vercel.app` que asigna Vercel.

- [ ] **Step 2: Verificar el rewrite y los headers (§7.5)**

Run: `curl -sI https://<proyecto>.vercel.app/product/3 | grep -iE "^HTTP|x-frame-options|x-content-type-options|referrer-policy"`
Expected: `HTTP/2 200` y los tres headers.

Run: `curl -s https://<proyecto>.vercel.app/ | grep -o 'assets/index-[^"]*\.js' | head -1`. Después, con ese path: `curl -s https://<proyecto>.vercel.app/<path> | grep -c "Modo demo"`
Expected: un número mayor que 0, es decir, el deploy se construyó con la variable.

- [ ] **Step 3: Recorrido manual (§7.4), con foco en las recargas completas**

En una ventana de incógnito sobre `https://<proyecto>.vercel.app`:
1. `/` redirige a `/login`, que muestra el banner y las cuentas de prueba. Entrar con `test@test.com`.
2. `/` → `/product/1` → agregar talle M al carrito → `/cart` → finalizar compra.
3. La recarga cae en `/checkout/resultado?pedidoId=4`, **sigue logueado** y muestra "¡Pago aprobado!". → "Ver mi pedido": `CONFIRMADO`.
4. F5 sobre `/orders/4`: sigue en `/orders/4`.
5. Salir, entrar con `user1@test.com` → `/orders` → cancelar el pedido 3 → queda cancelado.
6. `/dashboard/products` → crear un producto con imagen chica → editarlo → borrarlo, sin toasts de error.
7. `/sales` → avanzar una venta.
8. Salir, entrar con `admin@test.com` → `/admin` (usuarios) y `/admin/reportes` (ventas del pedido 4 incluidas).
9. Pegar `https://<proyecto>.vercel.app/product/3` en una pestaña nueva: muestra el producto (no 404 ni `/login`).
10. "Reiniciar demo": vuelve a `/login` con los datos iniciales.

Anotar cualquier desvío y diagnosticarlo (superpowers:systematic-debugging) antes de seguir.

- [ ] **Step 4: README con la demo**

En `README.md`, insertar esta sección entre el párrafo de introducción (el que termina en "…automático.") y `## 🚀 Inicio Rápido`:

```markdown
## 🧪 Demo online

**https://<proyecto>.vercel.app**: el frontend corriendo en Vercel con el backend
simulado dentro del navegador. No necesita crear cuenta.

- **Cuentas:** `user1@test.com` / `user123` (compra y vende), `test@test.com` / `test123`
  (vende), `admin@test.com` / `admin123` (administra). También podés registrarte.
- **Funciona de punta a punta:** catálogo y búsqueda, carrito, checkout con descuento de
  stock por talle, mis pedidos y cancelación, publicar y editar productos (con imágenes de
  hasta 1 MB), mis ventas y cambio de estado, panel de administración y reportes.
- **Simulado:** Mercado Pago (el pago se aprueba al instante, sin salir del sitio) y los
  emails (no se envían). Los datos viven en el `localStorage` de tu navegador. "Reiniciar
  demo", en el banner, los vuelve al estado inicial.
- El deploy real (backend Spring Boot + MySQL detrás de Caddy) es el del VPS, descripto en
  [Deploy a Producción](#-deploy-a-producción). El código de la demo no viaja en ese build.
```

En la sección `## 🧪 Testing`, en el bloque de frontend que agregó la Task 1, no hay que cambiar nada.

- [ ] **Step 5: Commit y push**

```bash
git add README.md
git commit -m "docs: link y alcance de la demo online en el README" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push
```

Vercel redespliega solo con el push. Run: `curl -sI https://<proyecto>.vercel.app/ | head -1` → `HTTP/2 200`.

- [ ] **Step 6: Registrar el resultado**

Agregar al final de este plan una sección `## ✅ Resultado de la ejecución (<fecha>)`, con el mismo formato que la de `fase5.md`: conteo de tests (backend y frontend), tabla de verificaciones corridas con su resultado, desvíos y lo que quede pendiente (por ejemplo, la CSP del VPS, que sigue siendo de la etapa 2). Commit `docs: registrar el resultado de la ejecución de la etapa 1` y push.

---

## ✅ Resultado de la ejecución (2026-10-08)

Ejecutado inline (executing-plans), sin subagentes. Repo: https://github.com/N-dossantos/groundcontrol90 — Demo: https://groundcontrol-zeta.vercel.app

**Tests:** frontend 0 → 84 (Vitest), backend 156, 0 failures (local y en GitHub Actions).

| Verificación | Resultado |
|---|---|
| §7.1 build sin `VITE_DEMO_MODE`: `dist/` sin `BOCA-2026-TIT`, `Modo demo`, `demo-token-`, `Cuentas de prueba`, `demo-state-v1` | ✅ |
| §7.2 build con la variable: aparecen (`index-*.js` y chunk `router-*.js`) | ✅ |
| §7.3 `npm run lint` | ✅ 0 errors (warnings: ruido `no-unused-vars` de JSX) |
| §7.5 `/product/3` directo en Vercel: 200 + `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy` | ✅ |
| Deploy de Vercel construido con la demo (mismo hash `index-29750abe.js` que el build local con la variable) | ✅ |
| §7.4 recorrido: banner y cuentas en `/login`; producto → carrito → checkout → `/checkout/resultado?pedidoId=4` sigue logueado y "¡Pago aprobado!"; F5 en `/orders/4`; cancelar pedido 3 (stock 15 → 17); borrar producto propio sin error (204); `/sales` → Preparando; `/admin` y `/admin/reportes` ($168.000, 3 pedidos); "Reiniciar demo" | ✅ (ver desvíos) |
| CI en GitHub: Backend CI, Frontend CI y build-and-push con imágenes en ghcr.io | ✅ (al segundo intento, ver desvíos) |
| Sólo `main` publicado (`git ls-remote --heads origin`) | ✅ |

**Desvíos:**

- El primer push hizo fallar el Backend CI (18 errores en `setUp`, `NULL not allowed for column "PRODUCTO_ID"`). Causa: `PedidoConcurrenciaTest` y `EmailServiceSinSesionTest` commitean datos y sólo limpiaban antes de cada test; con el contexto de los controllers en caché, su `usuarioRepository.deleteAll()` fallaba según el orden de las clases (en macOS no se daba). Arreglado en `009e370` con un `@AfterEach`, sólo en código de test; reproducido con `-Dsurefire.runOrder=random`. El gate cumplió su función: con los tests rotos no publicó imágenes.
- El recorrido con sesión se hizo sobre el mismo build servido en `localhost` (`vite preview`), no sobre el dominio de Vercel; sin sesión (banner, cuentas, redirección de rutas profundas) se verificó en Vercel.
- `CLAUDE.md` queda fuera del repo por decisión del usuario.
- Se quitó de `Login.jsx` el recuadro "Credenciales de prueba" del commit base: no dependía de `__DEMO_MODE__` y aparecía también en el build de producción del VPS, donde esas cuentas no existen. En la demo lo reemplazan las cuentas de prueba del modo demo.

**Pendiente:**

- Lo de "Hallazgos fuera de alcance" sigue abierto (backend ignora `categoriaId`, registro desde `Login.jsx` sin `aceptaTerminos`, ramas `'404'` muertas en `api.js`).
- La CSP y el resto del deploy real siguen siendo de la etapa 2.
