# Fase 5 — Auditoría pre-deploy: seguridad, consistencia y provisioning del VPS

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Cerrar los hallazgos de la última pasada completa sobre la app antes de subir a git y desplegar. Cuatro de ellos son bloqueantes: dos rompen la aplicación en producción y dos la dejan comprometida desde el primer arranque.

**Alcance de la auditoría:** backend (Spring Boot), frontend (React + Vite), Docker y `docker-compose`, Caddy y nginx, workflows de CI, `.gitignore`, y la documentación de deploy. Se corrió la suite completa del backend como línea de base.

**Estado de la línea de base (verificado):** `mvn -B test` → **136 tests, 0 failures, 0 errors, 0 skipped**, con `target/surefire-reports` borrado antes de correr para no leer reportes viejos. Ningún hallazgo de este documento surge de un test roto: la suite pasa y aun así la app no funciona en producción.

## Global Constraints

- Depende de las Fases 0-4 aplicadas: Dockerfiles, Caddy con TLS, Flyway, CI/CD a `ghcr.io` y backups.
- Los arreglos bloqueantes (Tasks 1-4) son chicos y acotados: ninguno requiere rediseño, todos son cambios localizados en archivos que ya existen.
- Ningún secreto se commitea. Los secretos de producción se generan **en el servidor**, no se reutilizan de desarrollo.
- Después de cada task, la suite del backend tiene que seguir en verde: `cd backend && mvn -B test`.

---

## 🔴 Bloqueantes

### Task 1: El frontend apunta a `localhost:8081` en producción

**Severidad:** Bloqueante — la app no funciona en producción, sin más.

**Files:**
- Modify: `frontend/src/services/api.js`
- Modify: `frontend/src/constants/index.js`

**El problema:**

`frontend/src/services/api.js:4`:
```js
const API_BASE_URL = "http://localhost:8081/api"
```

Duplicado en `frontend/src/constants/index.js:2`. No hay ni un `import.meta.env` en todo `frontend/` (verificado con grep sobre `src/`, `index.html`, `vite.config.js` y `Dockerfile`).

La build de producción corre en el navegador del **visitante**, así que `localhost:8081` apunta a la máquina del comprador, no al servidor. Toda llamada a la API falla.

**El fix:** usar una ruta relativa `/api`. El `Caddyfile` ya rutea `/api/*` al backend en el mismo dominio, así que además desaparece el problema de CORS por completo (mismo origen).

- [x] **Step 1: Reemplazar la base URL en `api.js`**

```js
// El frontend se sirve detrás del mismo dominio que la API (Caddy rutea /api/* al
// backend), así que la ruta relativa funciona igual en dev y en prod. Una URL
// absoluta con localhost apuntaría a la máquina del visitante, no al servidor.
const API_BASE_URL = "/api"
```

- [x] **Step 2: Eliminar la constante duplicada de `constants/index.js`**

`API_BASE_URL` en `constants/index.js:2` no la importa nadie (el único consumidor, `api.js:1`, importa sólo `ERROR_MESSAGES` y `TOKEN_PREFIX`). Borrarla en vez de dejar dos fuentes de verdad divergentes.

- [x] **Step 3: Actualizar el mensaje de error de `api.js:36`**

Menciona `http://localhost:8081` en un texto que ve el usuario final en producción. Reemplazar por algo neutro: `"No se puede conectar con el servidor. Intentá de nuevo en unos minutos."`

- [x] **Step 4: Configurar el proxy de Vite para desarrollo**

Con la ruta relativa, `npm run dev` necesita que Vite haga proxy al backend. En `frontend/vite.config.js`:

```js
server: {
  port: 5173,
  proxy: {
    "/api": "http://localhost:8081",
  },
},
```

**Verificación:**
- `cd frontend && npm run build` → PASS.
- `npm run dev` con el backend levantado → el catálogo carga y el login funciona.
- `grep -rn "localhost:8081" frontend/src/` → sin resultados.

---

### Task 2: Se siembra `admin@test.com` / `admin123` en producción

**Severidad:** Bloqueante — la tienda real nace con un admin de credenciales públicas.

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/initializer/DataInitializer.java`
- Modify: `README.md`

**El problema:**

`DataInitializer.java:70-77` es un `CommandLineRunner` **sin `@Profile`**, así que corre también con el perfil `prod`. Su única guarda es:

```java
if (categoriaRepository.count() == 0) {
    initializeData();
}
```

Que es exactamente el estado de la base en el primer arranque del VPS. Resultado en producción:

- Un usuario `ADMIN` con `admin@test.com` / `admin123` (`DataInitializer.java:70-77`), con acceso a todo `/api/admin/**` — gestión de usuarios, cambio de roles, borrado de cuentas.
- Dos usuarios más con contraseñas triviales (`user123`, `test123`).
- Seis productos demo de Boca, River, Real Madrid y la Selección en el catálogo real.

Las credenciales están además publicadas en `README.md:45-48`, así que no hace falta ni adivinarlas.

- [x] **Step 1: Restringir el seeder al perfil `dev`**

```java
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {
```

Con el import `org.springframework.context.annotation.Profile`. Los datos de arranque de producción los provee Flyway (`db/migration/`), que es el mecanismo correcto y ya está en uso.

- [x] **Step 2: Aclarar en el README que las credenciales son sólo de desarrollo**

Cambiar el encabezado de `README.md:45` de "Credenciales de Prueba" a algo inequívoco:

```markdown
### Credenciales de desarrollo (perfil `dev` únicamente):
> Estas cuentas las crea `DataInitializer`, que sólo corre con el perfil `dev`.
> En producción la base arranca vacía: el primer admin se crea a mano (ver Task 2 de fase5).
```

- [x] **Step 3: Documentar cómo crear el primer admin en producción**

Sin el seeder, producción arranca sin ningún admin. El procedimiento es: registrarse por la UI como usuario normal y después promover esa cuenta con SQL, una sola vez:

```bash
docker exec -it ecommerce-mysql mysql -u root -p ecommerce_db \
  -e "UPDATE usuarios SET role='ADMIN' WHERE email='tu-email-real@dominio.com';"
```

Agregarlo al `README.md` en la sección de deploy y como ítem del checklist de go-live.

**Verificación:**
- `cd backend && mvn -B test` → 136 tests en verde (ningún test depende del seeder).
- Levantar con `SPRING_PROFILES_ACTIVE=prod` contra una base vacía → arranca sin crear usuarios ni productos.
- Levantar con `dev` → los datos de ejemplo siguen apareciendo.

---

### Task 3: MySQL publicado al host en producción

**Severidad:** Bloqueante — expone la base de datos a Internet.

**Files:**
- Modify: `docker-compose.prod.yml`

**El problema:**

`docker-compose.prod.yml:14-15`:
```yaml
ports:
  - "${MYSQL_PORT:-3307}:3306"
```

En un VPS con IP pública eso deja MySQL accesible desde Internet. Y el detalle que lo vuelve peligroso: **`ufw` no protege de esto**. Docker inserta sus propias reglas en la cadena `DOCKER-USER` y en la tabla `nat`, que se evalúan antes que las de ufw. Un `ufw status` mostrando el puerto cerrado da una falsa sensación de seguridad.

El backend le llega a MySQL por la red interna `ecommerce-network` usando el hostname `mysql-db`. No necesita el puerto publicado para nada.

- [x] **Step 1: Eliminar la publicación del puerto**

Borrar las dos líneas de `ports:` del servicio `mysql-db` en `docker-compose.prod.yml`. Dejar el comentario que explica por qué no están, siguiendo el patrón que ya usan `backend` y `frontend` en ese mismo archivo:

```yaml
    # Sin ports: la base sólo es accesible dentro de ecommerce-network. Publicar el
    # puerto la expondría a Internet — y ufw no lo impediría, porque Docker escribe
    # sus reglas en DOCKER-USER/nat, que se evalúan antes.
```

- [x] **Step 2 (opcional): acceso administrativo por túnel SSH**

Si hace falta conectarse con un cliente de SQL, no republicar el puerto: usar un túnel, que no abre nada al exterior.

```bash
ssh -L 3307:localhost:3306 deploy@tu-dominio.com
# y en el server, si el puerto no está publicado, entrar por el contenedor:
docker exec -it ecommerce-mysql mysql -u root -p
```

**Verificación:**
- `docker compose -f docker-compose.prod.yml config | grep -A3 mysql-db` → sin `ports`.
- Desde afuera del VPS: `nc -zv tu-dominio.com 3307` → connection refused.
- La app sigue funcionando (el backend conecta por la red interna).

---

### Task 4: IDOR — cualquier usuario logueado edita o borra productos ajenos

**Severidad:** Bloqueante — Broken Access Control (OWASP A01).

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/ProductoController.java`
- Modify: `backend/src/main/java/com/ecommerce/service/ProductoService.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/CategoriaController.java`
- Create: `backend/src/test/java/com/ecommerce/controller/ProductoOwnershipSecurityTest.java`

**El problema:**

Tres capas y ninguna verifica quién es el dueño del producto:

| Capa | Archivo | Qué hace |
|---|---|---|
| Autorización HTTP | `SecurityConfig.java:102-103` | `PUT`/`DELETE /api/productos/**` sólo exigen `.authenticated()` |
| Controller | `ProductoController.java:72-92` | No recibe ni consulta el usuario autenticado |
| Service | `ProductoService.java:80,162` | `actualizarProducto`/`eliminarProducto` no reciben `ownerUserId` |

Siendo un marketplace multi-vendedor (`Role.USER` = "usuario regular que puede comprar y vender"), cualquier cuenta registrada puede reescribir el precio de cualquier producto, vaciarle el stock o borrar el catálogo entero.

El mismo agujero, con menor impacto, en `CategoriaController.java:51-88`: `POST`/`PUT`/`DELETE` sin chequeo de rol, así que cualquier usuario crea, renombra o borra categorías.

**El patrón correcto ya existe en el repo.** `PedidoController.java:71-73`, `PagoController.java:60-62,83-85` y `DireccionController.java:71` verifican pertenencia y tiran `ForbiddenException`. Sólo falta replicarlo.

- [x] **Step 1: Pasar el usuario autenticado desde el controller**

En `ProductoController.java`, agregar `@AuthenticationPrincipal Usuario usuario` a `actualizarProducto` y `eliminarProducto`, y propagarlo al service (ya se hace así en `crearProducto`, línea 61).

- [x] **Step 2: Verificar propiedad en el service**

En `ProductoService.actualizarProducto` y `eliminarProducto`, recibir el `usuario` y comparar contra `producto.getOwnerUser().getId()` antes de mutar, permitiendo también al `ADMIN`:

```java
// El dueño del producto o un admin. Sin este chequeo cualquier cuenta registrada
// puede reescribir el precio o borrar el producto de otro vendedor.
private void verificarPropiedad(Producto producto, Usuario usuario) {
    boolean esDueño = producto.getOwnerUser().getId().equals(usuario.getId());
    boolean esAdmin = usuario.getRole() == Role.ADMIN;
    if (!esDueño && !esAdmin) {
        throw new ForbiddenException("No tenés permiso sobre este producto");
    }
}
```

`ForbiddenException` ya está mapeada a 403 en `GlobalExceptionHandler.java:114-116`.

- [x] **Step 3: Exigir rol ADMIN en las categorías**

Las categorías son taxonomía global de la tienda, no algo por vendedor. En `SecurityConfig.java:104-106`, cambiar `.authenticated()` por `.hasRole("ADMIN")` en el `POST`/`PUT`/`DELETE` de `/api/categorias`, y agregar `@PreAuthorize("hasRole('ADMIN')")` en los métodos de `CategoriaController` para tener doble capa — igual que `AdminController`, que ya usa las dos.

- [x] **Step 4: Tests de regresión**

Crear `ProductoOwnershipSecurityTest` cubriendo:
- Usuario A crea producto → usuario B hace `PUT` → **403**.
- Usuario A crea producto → usuario B hace `DELETE` → **403**.
- Usuario A edita y borra su propio producto → **200 / 204**.
- Un `ADMIN` edita el producto de otro → **200**.
- Usuario sin rol admin hace `POST /api/categorias` → **403**.

Seguir el patrón de `PedidoControllerSecurityTest` y `VentasControllerSecurityTest`, que ya existen.

**Verificación:**
- `cd backend && mvn -B test` → los 136 tests previos siguen verdes + los nuevos.

---

## 🟠 Altos

### Task 5: `lombok edge-SNAPSHOT` sin pinear, fuera de Maven Central

**Files:** `backend/pom.xml`

`pom.xml:18` declara `<lombok.version>edge-SNAPSHOT</lombok.version>` y `pom.xml:21-26` agrega el repositorio `https://projectlombok.org/edge-releases`.

Un SNAPSHOT no pineado, servido fuera de Maven Central, se re-resuelve en cada build de CI y en cada `docker build`. Dos consecuencias: los builds no son reproducibles (el mismo commit puede producir binarios distintos), y hay una dependencia de build que puede cambiar de contenido sin que cambie nada en el repo.

- [x] Eliminar la property `lombok.version` y el bloque `<repositories>`, dejando que Spring Boot 3.2.0 gestione la versión de Lombok desde su BOM (1.18.30, compatible con Java 17).
- [x] Quitar `<version>${lombok.version}</version>` del `annotationProcessorPaths` (`pom.xml:132`).
- [x] Verificar: `cd backend && mvn -B clean test` → 136 tests en verde y sin descargas desde `projectlombok.org`.

### Task 6: Logging DEBUG y `show-sql` activos en producción

**Files:** `backend/src/main/resources/application.properties`, `application-prod.properties`

`application.properties:9-10` fija `logging.level.com.ecommerce=DEBUG` y `logging.level.org.springframework.security=DEBUG` en el archivo **base**, así que aplica también al perfil `prod`. Sumado a `application-prod.properties:33` (`spring.jpa.show-sql=true`) y `:36` (`format_sql=true`), producción vuelca todas las queries SQL y el detalle interno de la cadena de autenticación a los logs.

- [x] Bajar el logging base a `INFO`.
- [x] Mover los `DEBUG` a `application-dev.properties`, donde sí son útiles.
- [x] Poner `spring.jpa.show-sql=false` y `format_sql=false` en `application-prod.properties`.

### Task 7: CI publica imágenes sin depender de los tests

**Files:** `.github/workflows/build-and-push.yml`

`backend-ci.yml` y `build-and-push.yml` se disparan los dos con `push` a `main` y corren **en paralelo**. Un commit que rompe los tests igual publica `ghcr.io/.../ecommerce-backend:latest`, y el `docker compose pull` del VPS se lo baja.

- [x] Extraer el job de tests a un workflow reutilizable, o duplicar el step de test como job `test` dentro de `build-and-push.yml` y agregar `needs: test` al job `build-and-push`.
- [x] Verificar con un commit que rompa un test a propósito en una rama: la imagen no debe publicarse.

### Task 8: Faltan headers de seguridad HTTP

**Files:** `Caddyfile`, `frontend/nginx.conf`

Ni Caddy ni nginx emiten `Strict-Transport-Security`, `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors` ni CSP.

- [x] Agregar un bloque `header` en el `Caddyfile`, que es el punto por donde pasa todo el tráfico:

```caddyfile
{$DOMAIN} {
    header {
        Strict-Transport-Security "max-age=31536000; includeSubDomains"
        X-Content-Type-Options "nosniff"
        Referrer-Policy "strict-origin-when-cross-origin"
        Content-Security-Policy "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'"
        -Server
    }
    ...
}
```

- [x] Validar la CSP en el navegador antes de dar la task por cerrada: las imágenes de productos vienen de dominios externos (Unsplash en los datos de ejemplo) y la consola avisa si algo queda bloqueado.

### Task 9: `axios` no se usa y arrastra 10 advisories

**Files:** `frontend/package.json`, `frontend/package-lock.json`

`axios` figura en `dependencies` pero **no se importa en ningún archivo** de `frontend/src/` (verificado con grep). Todo el cliente HTTP es `fetch` nativo, en `api.js`. `npm audit --omit=dev` reporta 6 vulnerabilidades en dependencias de producción y todas cuelgan de axios y su árbol (`follow-redirects`, `form-data`).

- [x] `cd frontend && npm uninstall axios`
- [x] `npm audit --omit=dev` → debe quedar limpio.
- [x] `npm run build` → PASS.

### Task 10: Spring Boot 3.2.0 desactualizado

**Files:** `backend/pom.xml`

`pom.xml:8` fija Spring Boot 3.2.0, de noviembre de 2023.

- [x] Actualizar al último parche de la línea 3.2.x (cambio de bajo riesgo) o evaluar 3.3.x/3.4.x.
- [x] `mvn -B test` después del bump: 136 tests en verde antes de mergear.

---

## 🟡 Medios

### Task 11: Las imágenes que sube el vendedor se pierden al recargar

`frontend/src/components/ImageUploader.jsx:12` hace `URL.createObjectURL(file)` y guarda esa URL como la imagen del producto. Una URL `blob:` sólo es válida en la pestaña que la creó: se persiste en la base y queda rota apenas el usuario recarga o entra desde otro dispositivo.

No hay endpoint de upload ni storage de archivos en el backend, así que no es un bug de una línea.

- [x] Decidir la estrategia: endpoint de upload con volumen en el VPS, o un servicio externo (S3-compatible, Cloudinary).
- [x] Mientras tanto, como mitigación inmediata: restringir el formulario a URLs de imagen pegadas a mano, que es lo que efectivamente funciona hoy.

### Task 12: `docker-compose.yml` de desarrollo no levanta

`docker-compose.yml:39` usa `SPRING_PROFILES_ACTIVE=docker`, pero `application-docker.properties` **no existe** (sólo hay `application.properties`, `-dev` y `-prod`). Sin ese perfil no se definen `jwt.secret` ni `cors.allowed-origins`, así que `JwtUtil.init()` (`JwtUtil.java:26-29`) tira `IllegalStateException` y el backend no arranca.

Es el "Método 1: Docker (Recomendado)" del README, o sea el primer camino que prueba alguien que clona el repo.

- [x] Cambiar el perfil a `dev`, o crear `application-docker.properties`.
- [x] Verificar de punta a punta: `docker compose up -d --build` y confirmar que los tres servicios quedan healthy.

### Task 13: Endurecimientos menores

- [x] **Contenedores como root.** Ni `backend/Dockerfile` ni `frontend/Dockerfile` declaran `USER`. Agregar un usuario sin privilegios en la etapa final de cada uno.
- [x] **Password de 6 caracteres.** `RegisterRequestDTO.java:28` pide mínimo 6 sin requisitos de complejidad. Subir a 8+ y validar contra las contraseñas más comunes.
- [x] **Buckets del rate limiter sin evicción.** `AuthRateLimitFilter.java:23` es un `ConcurrentHashMap` que crece una entrada por IP y nunca las libera. Usar un cache con expiración.
- [x] **JWT de 24 h sin revocación.** `JwtUtil.java:19`. Un token robado sirve un día entero y cambiar la contraseña no lo invalida. Evaluar access token corto + refresh token.
- [x] **`verifyServerCertificate=false`** en la URL de MySQL (`docker-compose.prod.yml:37`): pide SSL pero no valida el certificado. Aceptable en una red Docker privada; documentarlo como decisión consciente.

---

## 📁 Task 14: Gitignore

Verificado con `git check-ignore`. Hoy no hay secretos ni `node_modules` trackeados — 209 archivos, todos legítimos — pero hay agujeros abiertos:

```
frontend/node_modules/   → NO IGNORADO   ⚠️
frontend/dist/           → NO IGNORADO   ⚠️
dist/  (raíz)            → NO IGNORADO   ⚠️
.claude/                 → NO IGNORADO   ⚠️  (worktrees = copias completas del repo)
```

La causa es que `/node_modules` y `/build` están **anclados a la raíz** por la barra inicial, así que no cubren nada dentro de `frontend/`. Un `git add .` sube el árbol entero de dependencias.

- [x] Agregar al `.gitignore` de la raíz:

```gitignore
node_modules/
dist/
.claude/
```

- [x] Borrar restos trackeados de la etapa anterior del proyecto:
  - `src/` en la raíz — 5 archivos JSX duplicados de `frontend/src/` (`context/AuthContext.jsx`, `context/CartContext.jsx`, `context/ToastContext.jsx`, `reducers/authReducer.js`, `reducers/cartReducer.js`).
  - `backend-legacy/db.json` — la base de json-server del prototipo.

- [x] Verificar: `git status --short` limpio y `git check-ignore -q frontend/node_modules/x && echo OK`.

**Ya está bien resuelto:** `.env*` con excepción de `!.env.example`, `backend/target/`, `.DS_Store`, `*.pem`.

---

## 📄 Task 15: Inconsistencias en el README

- [x] `README.md:68` dice **Java 24**; el `pom.xml:17` es Java 17.
- [x] `README.md:97` y `README.md:107` linkean **`docs/DOCKER.md`, que no existe** (en `docs/` sólo están `DOCUMENTACION-COMPLETA.md` y `superpowers/`).
- [x] `README.md:280` dice "5 categorías (Electrónicos, Ropa, Hogar, Deportes, Libros)" — son **4** y de fútbol (Clubes Argentinos, Clubes Europeos, Selecciones, Retro). Y "7 productos" cuando `DataInitializer` crea **6**.
- [x] `README.md:356` deja sin tildar "Pasarela de pagos" y "Deploy a producción"; los dos están hechos (Fases 2 y 4).
- [x] `README.md:31` levanta MySQL en el puerto **3308**; el `docker-compose.yml` usa **3307** por defecto.
- [x] `README.md:45-48` publica las credenciales del seeder (ver Task 2).
- [x] El árbol de estructura (`README.md:83-99`) no menciona `scripts/`, `Caddyfile` ni `docker-compose.prod.yml`.
- [x] `frontend/src/constants/index.js` conserva `CATEGORY_IDS` (Electronics, Clothing, Home, Sports, Books) y `DEFAULT_USER_IDS` del proyecto genérico original, sin relación con el dominio de camisetas.

---

## ✅ Lo que está sólido

No hace falta tocarlo, y conviene no romperlo al aplicar lo de arriba:

- **JWT bien implementado.** HS512, secreto de mínimo 64 bytes validado en `@PostConstruct` (`JwtUtil.java:24-36`), falla al arranque si falta o es corto. Nada de defaults inseguros en prod.
- **Webhook de Mercado Pago.** HMAC-SHA256 + `MessageDigest.isEqual` para comparación en tiempo constante (`MercadoPagoSignatureValidator.java:51-53`), y el `external_reference` del pago como fuente de verdad del cobro en vez del redirect del comprador. Bien resuelto.
- **`X-Forwarded-For`.** `Caddyfile:7` usa `header_up X-Forwarded-For {remote_host}`, que *reemplaza* en vez de anexar, y `application-prod.properties:12` fija `forward-headers-strategy=framework`. Sin eso el rate limiter usaría un único bucket compartido, o el cliente podría falsear su IP. Es un detalle que casi siempre se pasa por alto.
- **Flyway con `ddl-auto=validate`** en producción: el esquema está versionado y Hibernate no lo modifica.
- **Ownership sí verificado** en `PedidoController`, `PagoController` y `DireccionController` — el patrón que le falta a productos.
- **`AdminController`** con doble capa: matcher por URL en `SecurityConfig.java:98` más `@PreAuthorize` en cada método.
- **Script de backup** (`scripts/backup-mysql.sh`): escribe a `.tmp`, valida con `gzip -t` antes de renombrar, rota por antigüedad, sube offsite y usa `MYSQL_PWD` para no filtrar la contraseña en la lista de procesos.
- **`.env.example`** completo, comentado y sin valores reales; el compose falla con `:?` y un mensaje claro si falta cualquier variable.
- **136 tests** cubriendo servicios, controllers, seguridad, concurrencia de stock y expiración de pedidos.

---

## 🖥️ Task 16: Provisioning del VPS, paso a paso

Reemplaza la sección "Deploy a Producción" del README (`README.md:165-222`), agregando el hardening que le falta.

### 1. Provisionar

Ubuntu 22.04 o 24.04 LTS, **mínimo 2 GB de RAM** — conviven MySQL, la JVM del backend, nginx y Caddy. Con 1 GB el backend muere por OOM durante el arranque de Hibernate.

### 2. Usuario no-root y SSH por clave

```bash
# como root, la primera vez
adduser deploy && usermod -aG sudo deploy
rsync --archive --chown=deploy:deploy ~/.ssh /home/deploy
```

En `/etc/ssh/sshd_config`:
```
PermitRootLogin no
PasswordAuthentication no
```
```bash
sudo systemctl restart ssh
```

> ⚠️ Antes de cerrar la sesión actual, abrí **otra terminal** y confirmá que entrás como `deploy`. Con la sesión vieja abierta, un error se arregla; si la cerraste, perdiste el acceso al servidor.

### 3. Firewall y actualizaciones automáticas

```bash
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow OpenSSH
sudo ufw allow 80
sudo ufw allow 443
sudo ufw enable

sudo apt install -y unattended-upgrades
sudo dpkg-reconfigure -plow unattended-upgrades
```

Recordar la Task 3: **ufw no filtra los puertos que publica Docker**. La protección real es no publicarlos.

### 4. Docker

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker deploy
# cerrar sesión y volver a entrar para que el grupo tome efecto
docker compose version
```

### 5. DNS

Registro `A` apuntando a la IP pública del VPS. Verificar **antes** de seguir:

```bash
dig +short tu-dominio.com
```

Caddy no puede emitir el certificado si el dominio todavía no resuelve, y Let's Encrypt aplica rate limits: cinco fallos consecutivos bloquean el dominio por una hora.

### 6. Repo y `.env`

```bash
git clone <url-del-repo> ecommerce && cd ecommerce
cp .env.example .env
chmod 600 .env        # contiene el access token de Mercado Pago y la clave SMTP
nano .env
```

Generar los secretos **en el servidor**, no reutilizar los de desarrollo:

```bash
openssl rand -base64 64   # JWT_SECRET (mínimo 64 bytes, si no el backend no arranca)
openssl rand -base64 32   # MYSQL_PASSWORD
openssl rand -base64 32   # MYSQL_ROOT_PASSWORD
```

Dos pares que tienen que coincidir y es fácil desincronizar:
- `SPRING_DATASOURCE_PASSWORD` = `MYSQL_PASSWORD`
- `SPRING_DATASOURCE_USERNAME` = `MYSQL_USER`

Credenciales de Mercado Pago **de producción**: las de test empiezan con `TEST-`.

### 7. Levantar el stack

```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps        # los 4 en running/healthy
docker compose -f docker-compose.prod.yml logs -f backend
```

Si el backend reinicia en loop, casi siempre es una variable faltante o un `JWT_SECRET` de menos de 64 bytes — el log lo dice explícitamente.

### 8. Verificar

```bash
curl -I https://tu-dominio.com                      # HTTP/2 200 con certificado válido

curl -i -X POST https://tu-dominio.com/api/pagos/webhook \
  -H "Content-Type: application/json" -d '{}'       # 401 = llegó al backend y la firma se rechazó
```

Un timeout o un 502 en el webhook significan que el proxy o el backend no están bien. El `Content-Type` no es opcional: sin él la respuesta es 415 y no prueba nada sobre la firma.

### 9. Primer admin

La base arranca vacía (Task 2). Registrarse por la UI con el email real y promover esa cuenta, una sola vez:

```bash
docker exec -it ecommerce-mysql mysql -u root -p ecommerce_db \
  -e "UPDATE usuarios SET role='ADMIN' WHERE email='tu-email-real@dominio.com';"
```

### 10. Webhook en el panel de Mercado Pago

Configurar la notification URL como `https://tu-dominio.com/api/pagos/webhook` y copiar el secret que genera el panel a `MERCADOPAGO_WEBHOOK_SECRET` en el `.env`. Reiniciar el backend después de cambiarlo.

### 11. Backups

```bash
set -a && source .env && set +a
./scripts/backup-mysql.sh        # probar a mano antes de automatizar

crontab -e
# 0 3 * * * cd /home/deploy/ecommerce && set -a && source .env && set +a && ./scripts/backup-mysql.sh >> /var/log/ecommerce-backup.log 2>&1
```

Configurar `BACKUP_S3_BUCKET`: un backup que vive en el mismo disco que la base no protege contra perder el servidor.

### 12. Actualizaciones posteriores

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

### 13. Go-live

Correr completo `docs/superpowers/plans/checklist-go-live.md`, que valida el flujo de pago real de punta a punta.

---

## Resumen de verificación de la Fase 5

Antes de dar la fase por cerrada:

- [x] `cd backend && rm -rf target/surefire-reports && mvn -B test` → 136+ tests, 0 failures, 0 errors.
- [x] `cd frontend && npm run build` → PASS.
- [x] `cd frontend && npm audit --omit=dev` → sin vulnerabilidades.
- [x] `grep -rn "localhost:8081" frontend/src/` → sin resultados.
- [x] `git check-ignore -q frontend/node_modules/x && echo OK` → OK.
- [x] `git status --short` → limpio, sin `node_modules` ni `dist` sin trackear.
- [x] `docker compose -f docker-compose.prod.yml config` → el servicio `mysql-db` sin `ports`.
- [x] Arranque con perfil `prod` contra base vacía → no se crea ningún usuario ni producto de ejemplo.
- [x] Usuario B intenta `PUT` sobre un producto de usuario A → 403.


---

## ✅ Resultado de la ejecución (2026-08-25)

Ejecutado en la rama `fase5-auditoria-pre-deploy`, seis commits.

**Línea de base:** 136 tests → **156 tests, 0 failures, 0 errors.**

### Verificaciones corridas

| Verificación | Resultado |
|---|---|
| `rm -rf target/surefire-reports && mvn -B clean test` | 156 tests, 0 failures |
| `npm run build` | PASS |
| `grep -rn "localhost:8081" frontend/src/` | sin resultados |
| `git check-ignore` sobre node_modules, dist, .claude | todos ignorados |
| `git status --short` | limpio |
| `docker compose -f docker-compose.prod.yml config` | `mysql-db` sin `ports` |
| `caddy validate` | Valid configuration |
| `docker compose up -d --build` | los 3 servicios healthy, catálogo y SPA respondiendo |
| Arranque con perfil `prod` contra base vacía | Flyway aplica 7 migraciones; 0 usuarios, 0 productos, 0 categorías |
| Logs en `prod` | 0 líneas de SQL, 0 líneas DEBUG |
| Imágenes Docker | backend uid 1001, nginx uid 101 (maestro incluido) |
| IDOR (usuario B sobre producto de A) | 403 — y verificado que **sin** el fix devolvía 200/204 |

### Desvíos respecto del plan

1. **Task 5 (Lombok).** El plan proponía dejar que el BOM de Spring Boot fijara la
   versión. No funciona: el BOM de 3.2.12 trae 1.18.36, que no entiende JDK 25, y el
   procesador de anotaciones corre dentro del JDK que ejecuta Maven, no en el `release
   17` al que se compila. Por eso el proyecto había terminado en `edge-SNAPSHOT`. Se
   pineó a **1.18.46**, publicada en Maven Central, que cumple el objetivo real de la
   task (build reproducible, sin repositorios externos) y además funciona.

2. **Task 9 (axios).** El plan decía que las 6 vulnerabilidades colgaban todas de axios
   y que al sacarlo el audit quedaría limpio. Al sacarlo aparecieron **3 highs** que
   axios tapaba, en `@remix-run/router` (XSS vía open redirect); se cerraron subiendo
   react-router-dom 6.30.1 → 6.30.6, sin breaking change. Quedan **2 moderates** que
   sólo se cierran migrando a react-router v7. Ninguna es alcanzable en esta app: la de
   `deserializeErrors` necesita SSR y esto es una SPA pura, y la de open redirect por
   backslash necesita un `to` enteramente controlado por el usuario, mientras que acá
   todas las rutas dinámicas interpolan un id en un prefijo fijo. Queda documentado.

3. **Task 11 (imágenes).** Se implementó el endpoint de upload con volumen, no la
   mitigación de restringir a URLs pegadas.

4. **Task 12 (perfil docker).** Crear `application-docker.properties` no alcanzaba: el
   compose de desarrollo tampoco le pasaba las credenciales de Mercado Pago al backend,
   así que seguía reiniciando en loop. Lo destapó levantar el stack de verdad. Se
   agregaron con `:?` para que el error salga al levantar el compose.

5. **Task 13 (JWT).** El access token corto + refresh token con revocación queda como
   deuda evaluada y explícita: es un rediseño de la autenticación que toca `JwtUtil`,
   `AuthController`, `SecurityConfig` y el frontend entero. Anotado en los Próximos
   Pasos del README.

6. **Cambio no previsto: el perfil por defecto.** `application.properties` fijaba
   `spring.profiles.active=prod`, un perfil que exige variables de entorno inexistentes
   en una máquina local — o sea que el default ya estaba roto. Pasa a `dev`; producción
   activa `prod` explícitamente en `docker-compose.prod.yml`.

7. **Cambio no previsto: el README mandaba a un camino inexistente.** El "Método 2"
   indicaba `npm install && npm run start` desde la raíz, y en la raíz no hay
   `package.json`. Se reemplazó por las instrucciones reales.

### Pendiente de verificar fuera de este entorno

- **Task 7 (gate de CI).** El `needs: test` sólo se ejercita en un push real a `main`;
  `build-and-push.yml` no se dispara en ramas. La configuración quedó validada como
  YAML, pero la prueba de que una imagen no se publica con tests rotos sólo puede darse
  en el próximo push a `main`.
- **Task 8 (CSP).** Los headers están puestos y Caddy valida la configuración, pero la
  CSP hay que mirarla en la consola del navegador contra el sitio ya desplegado: las
  imágenes de producto vienen de dominios externos y es ahí donde se ve si algo queda
  bloqueado.
