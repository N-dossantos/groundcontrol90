# Fase 4 — Deploy y go-live — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar de depender de un build manual y de una cuenta personal de Docker Hub, tener backups reales de la base de datos, y correr un checklist de smoke test completo con Mercado Pago en modo real antes de anunciar el lanzamiento.

**Architecture:** CI en GitHub Actions corre los tests del backend en cada push/PR y, al mergear a `main`, construye y publica las imágenes Docker a GitHub Container Registry (reemplaza la dependencia de la cuenta Docker Hub personal usada hasta ahora). Un script de backup con cron dumpea MySQL y lo sube a almacenamiento S3-compatible fuera del VPS. El go-live es un checklist manual — no hay forma de automatizar "¿el pago real funciona de punta a punta?" sin plata real de por medio.

**Tech Stack:** GitHub Actions, GitHub Container Registry (`ghcr.io`), `mysqldump`, cron, cualquier proveedor S3-compatible (AWS S3, Backblaze B2, DigitalOcean Spaces) para backups offsite.

## Global Constraints

- Depende de las Fases 0-3 aplicadas: Dockerfiles reales (Fase 0 Task 9/10), reverse proxy Caddy con TLS (Fase 0 Task 11), Flyway con migraciones versionadas (Fase 0 Task 7 + las agregadas en Fases 1-3).
- Ningún secreto (credenciales del VPS, tokens de CI, credenciales de backup) se commitea — los de CI van como GitHub Actions Secrets, los del servidor como `.env` fuera de git (mismo patrón que las fases anteriores).
- Esta fase no tiene "tests" en el sentido de JUnit — es infraestructura y checklist operativo. Cada tarea igual tiene un paso de verificación concreto y ejecutable, no una aprobación a ojo.

---

### Task 1: CI — correr los tests del backend en cada push/PR

**Files:**
- Create: `.github/workflows/backend-ci.yml`

- [ ] **Step 1: Crear el workflow**

```yaml
name: Backend CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Run backend test suite
        working-directory: backend
        run: mvn -B test
```

- [ ] **Step 2: Verificar localmente que el comando que va a correr CI funciona**

Run: `cd backend && mvn -B test`
Expected: PASS (mismo comando que ejecuta el workflow; si acá falla, CI también va a fallar).

- [ ] **Step 3: Commit y verificar en GitHub**

```bash
git add .github/workflows/backend-ci.yml
git commit -m "ci: correr la suite de tests del backend en cada push y PR"
git push
```

Abrir la pestaña "Actions" del repositorio en GitHub y confirmar que el workflow corrió y quedó en verde para este commit.

---

### Task 2: CD — build y push de imágenes a GitHub Container Registry

**Files:**
- Create: `.github/workflows/build-and-push.yml`
- Modify: `docker-compose.prod.yml`
- Modify: `.env.example`
- Modify: `README.md`

Hasta ahora `docker-compose.prod.yml` bajaba `bautistabozzer/ecommerce-backend:latest`/`ecommerce-frontend:latest`, imágenes publicadas manualmente por un integrante puntual del equipo desde su cuenta personal de Docker Hub — un punto único de fallo para el deploy. Se reemplaza por `ghcr.io/<org>/<repo>-backend`/`-frontend`, publicadas automáticamente por CI usando el propio `GITHUB_TOKEN` del repositorio (sin credenciales adicionales que gestionar).

- [ ] **Step 1: Crear el workflow de build y push**

```yaml
name: Build and Push Images

on:
  push:
    branches: [main]

env:
  REGISTRY: ghcr.io

jobs:
  build-and-push:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    strategy:
      matrix:
        include:
          - context: backend
            image: ecommerce-backend
          - context: frontend
            image: ecommerce-frontend
    steps:
      - uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push
        uses: docker/build-push-action@v5
        with:
          context: ${{ matrix.context }}
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ github.repository_owner }}/${{ matrix.image }}:latest
            ${{ env.REGISTRY }}/${{ github.repository_owner }}/${{ matrix.image }}:${{ github.sha }}
```

(Este workflow corre después/en paralelo al de `backend-ci.yml`; a propósito no depende de él con `needs:` para no complicar el plan con orquestación entre workflows — si el equipo quiere bloquear el build hasta que los tests pasen, alcanza con `needs: test` una vez que ambos estén en el mismo archivo, mejora incremental fuera de este alcance.)

- [ ] **Step 2: Actualizar `docker-compose.prod.yml` para usar las nuevas imágenes**

Reemplazar `image: bautistabozzer/ecommerce-backend:latest` por `image: ghcr.io/${GITHUB_REPOSITORY_OWNER}/ecommerce-backend:latest` — como Docker Compose no expande `${GITHUB_REPOSITORY_OWNER}` automáticamente (esa variable solo existe en el contexto de GitHub Actions), se define explícita en `.env`:

```yaml
  backend:
    image: ${BACKEND_IMAGE:?Falta BACKEND_IMAGE en el .env, ej: ghcr.io/tu-org/ecommerce-backend:latest}
```

```yaml
  frontend:
    image: ${FRONTEND_IMAGE:?Falta FRONTEND_IMAGE en el .env, ej: ghcr.io/tu-org/ecommerce-frontend:latest}
```

- [ ] **Step 3: Documentar las variables nuevas**

En `.env.example`, agregar:

```bash
BACKEND_IMAGE=ghcr.io/tu-org/ecommerce-backend:latest
FRONTEND_IMAGE=ghcr.io/tu-org/ecommerce-frontend:latest
```

- [ ] **Step 4: Actualizar el `README.md`**

Reemplazar la sección "Opción B: Producción (usar imágenes de Docker Hub)" y las referencias a `bautistabozzer/ecommerce-*` por la nueva ubicación en GitHub Container Registry, y la explicación de que las imágenes se publican automáticamente por CI al mergear a `main`.

- [ ] **Step 5: Verificar el paquete publicado en GitHub**

Run: `git push` (con el workflow ya en `main`).
Expected: en la pestaña "Actions" el job termina en verde, y en la pestaña "Packages" del repositorio (o de la organización) aparecen `ecommerce-backend` y `ecommerce-frontend` con el tag `latest`.

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/build-and-push.yml docker-compose.prod.yml .env.example README.md
git commit -m "ci: publicar imágenes Docker a GitHub Container Registry en cada push a main"
```

---

### Task 3: Backups automáticos de MySQL con copia fuera del servidor

**Files:**
- Create: `scripts/backup-mysql.sh`
- Modify: `.env.example`
- Modify: `README.md`

**Interfaces:**
- Produces: `scripts/backup-mysql.sh` — dump comprimido de la base, rotación local de los últimos 7 días, y subida opcional a un bucket S3-compatible si `BACKUP_S3_BUCKET` está configurado.

- [ ] **Step 1: Crear el script**

```bash
#!/usr/bin/env bash
set -euo pipefail

# Requiere correr en el mismo host que docker-compose.prod.yml, con las mismas
# variables de entorno cargadas (MYSQL_DATABASE, MYSQL_ROOT_PASSWORD) y, opcionalmente,
# BACKUP_S3_BUCKET / BACKUP_S3_ENDPOINT para subir la copia fuera del servidor.

BACKUP_DIR="${BACKUP_DIR:-/var/backups/ecommerce-mysql}"
RETENCION_DIAS="${BACKUP_RETENCION_DIAS:-7}"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
ARCHIVO="${BACKUP_DIR}/ecommerce_db-${TIMESTAMP}.sql.gz"

mkdir -p "${BACKUP_DIR}"

docker exec ecommerce-mysql \
  mysqldump -u root -p"${MYSQL_ROOT_PASSWORD}" "${MYSQL_DATABASE}" \
  | gzip > "${ARCHIVO}"

echo "Backup creado: ${ARCHIVO}"

# Rotación: borrar backups locales más viejos que RETENCION_DIAS
find "${BACKUP_DIR}" -name "ecommerce_db-*.sql.gz" -mtime "+${RETENCION_DIAS}" -delete

# Copia fuera del servidor (opcional pero fuertemente recomendado — un backup que
# vive solo en el mismo disco que la base no protege contra la pérdida del servidor)
if [[ -n "${BACKUP_S3_BUCKET:-}" ]]; then
  aws s3 cp "${ARCHIVO}" "s3://${BACKUP_S3_BUCKET}/$(basename "${ARCHIVO}")" \
    ${BACKUP_S3_ENDPOINT:+--endpoint-url "${BACKUP_S3_ENDPOINT}"}
  echo "Backup subido a s3://${BACKUP_S3_BUCKET}/$(basename "${ARCHIVO}")"
else
  echo "AVISO: BACKUP_S3_BUCKET no está configurado — el backup solo existe en este servidor."
fi
```

- [ ] **Step 2: Hacerlo ejecutable y probarlo localmente**

Run:
```bash
chmod +x scripts/backup-mysql.sh
MYSQL_DATABASE=ecommerce_db MYSQL_ROOT_PASSWORD=password BACKUP_DIR=/tmp/backup-test ./scripts/backup-mysql.sh
ls -la /tmp/backup-test
```
Expected: aparece un archivo `ecommerce_db-<timestamp>.sql.gz` no vacío (correr esto contra un `docker-compose up -d` local con datos de prueba cargados).

- [ ] **Step 3: Documentar el cron en el servidor de producción**

En `README.md`, agregar una sección "Backups" con la instrucción de instalar el cron en el VPS (se ejecuta manualmente en el servidor, no vía CI — no es algo que se automatice desde GitHub Actions sin darle a un runner acceso de escritura a producción):

```bash
# Backup diario a las 3 AM, con las variables de entorno del .env de producción cargadas
crontab -e
# agregar:
0 3 * * * cd /ruta/al/repo && set -a && source .env && set +a && ./scripts/backup-mysql.sh >> /var/log/ecommerce-backup.log 2>&1
```

- [ ] **Step 4: Documentar las variables opcionales**

En `.env.example`, agregar:

```bash
# Backups (opcional pero recomendado en producción real)
BACKUP_DIR=/var/backups/ecommerce-mysql
BACKUP_RETENCION_DIAS=7
BACKUP_S3_BUCKET=
BACKUP_S3_ENDPOINT=
```

- [ ] **Step 5: Commit**

```bash
git add scripts/backup-mysql.sh .env.example README.md
git commit -m "ops: agregar script de backup de MySQL con rotación y copia offsite opcional"
```

---

### Task 4: Provisioning del VPS de producción y primer deploy

**Files:**
- Modify: `README.md`

Esta tarea es una checklist de comandos a correr una vez, a mano, en el servidor real — no hay código de aplicación que escribir. Se documenta acá para que quede como procedimiento repetible (ej. si hay que migrar de servidor).

- [ ] **Step 1: Provisionar el VPS**

Elegir un proveedor (DigitalOcean, Hetzner, AWS EC2, etc.), crear una instancia con al menos 2GB de RAM (MySQL + backend Java + frontend + Caddy corriendo juntos), Ubuntu 22.04 LTS o superior.

- [ ] **Step 2: Instalar Docker y Docker Compose**

Run (en el servidor, por SSH):
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# cerrar sesión y volver a entrar para que el grupo docker tome efecto
docker compose version
```
Expected: imprime la versión de Docker Compose sin error.

- [ ] **Step 3: Apuntar el dominio**

En el proveedor de DNS del dominio, crear un registro `A` apuntando a la IP pública del VPS. Esperar la propagación (`dig tu-dominio.com` debería devolver la IP del servidor).

- [ ] **Step 4: Clonar el repo y configurar el `.env`**

```bash
git clone <url-del-repo> ecommerce
cd ecommerce
cp .env.example .env
# Completar .env con: credenciales de MySQL, JWT_SECRET (openssl rand -base64 64),
# CORS_ALLOWED_ORIGINS=https://tu-dominio.com, DOMAIN=tu-dominio.com,
# MERCADOPAGO_ACCESS_TOKEN/PUBLIC_KEY/WEBHOOK_SECRET reales (no de test),
# SMTP_*, BACKEND_IMAGE, FRONTEND_IMAGE
nano .env
```

- [ ] **Step 5: Levantar el stack**

Run:
```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```
Expected: los cuatro servicios (`mysql-db`, `backend`, `frontend`, `proxy`) en estado `running`/`healthy`.

- [ ] **Step 6: Verificar HTTPS**

Run: `curl -I https://tu-dominio.com`
Expected: `HTTP/2 200`, y el certificado es válido (Caddy lo emite automáticamente contra Let's Encrypt la primera vez que recibe tráfico en el dominio configurado — puede tardar unos segundos la primera vez).

- [ ] **Step 7: Documentar el procedimiento en `README.md`**

Agregar una sección "Deploy a producción" con los pasos 1-6 resumidos, para que no dependa de la memoria de quien lo hizo la primera vez.

- [ ] **Step 8: Commit**

```bash
git add README.md
git commit -m "docs: documentar el procedimiento de deploy a producción"
```

---

### Task 5: Checklist de go-live con Mercado Pago en modo real

**Files:**
- Create: `docs/superpowers/plans/checklist-go-live.md`

No hay forma de convertir "¿el pago real funciona?" en un test automatizado sin mover dinero real — este es el único punto del roadmap completo que es intencionalmente manual. Se documenta como checklist para que se corra una vez, completo, antes de anunciar el lanzamiento, y quede registro de que se corrió.

- [ ] **Step 1: Crear el checklist**

```markdown
# Checklist de Go-Live

Correr esta lista completa, en orden, contra el entorno de producción real (no contra
localhost ni contra credenciales de test de Mercado Pago), antes de anunciar el lanzamiento.

## Cuenta y credenciales
- [ ] La cuenta de Mercado Pago está habilitada como vendedor real (CBU/CVU y datos
      fiscales cargados en el panel de Mercado Pago).
- [ ] `MERCADOPAGO_ACCESS_TOKEN`/`MERCADOPAGO_PUBLIC_KEY`/`MERCADOPAGO_WEBHOOK_SECRET`
      en el `.env` de producción son las credenciales de **producción**, no las de test
      (las credenciales de test empiezan con `TEST-`, las reales no).
- [ ] `JWT_SECRET`, credenciales de MySQL y `SMTP_*` fueron generadas específicamente
      para este servidor (no reutilizadas de ningún entorno de desarrollo/test).

## Infraestructura
- [ ] `https://tu-dominio.com` responde 200 con certificado TLS válido.
- [ ] `https://tu-dominio.com/api/pagos/webhook` es alcanzable públicamente (Mercado
      Pago necesita poder pegarle desde afuera — probar con
      `curl -X POST https://tu-dominio.com/api/pagos/webhook -d '{}'`, debe responder
      401 por firma inválida, no timeout ni 502).
- [ ] El backup de MySQL (Task 3) corrió al menos una vez exitosamente y el archivo
      resultante no está vacío.

## Flujo de compra end-to-end (con dinero real, monto bajo)
- [ ] Registrar una cuenta nueva de prueba → llega el email de bienvenida.
- [ ] Cargar un producto de bajo valor desde el panel de admin/vendedor con al menos
      dos talles con stock.
- [ ] Comprar ese producto con una tarjeta real propia, monto bajo → redirige a
      Mercado Pago, el pago se aprueba, vuelve a `/checkout/resultado` con estado
      "¡Pago aprobado!".
- [ ] Llega el email de confirmación de pedido.
- [ ] En `/orders/{id}`, el pedido figura `CONFIRMADO`.
- [ ] El stock del talle comprado bajó en 1 unidad (`GET /api/productos/{id}`).
- [ ] Desde el panel de vendedor (`/sales`), marcar el item como `ENVIADO` → llega el
      email de cambio de estado al comprador.
- [ ] `/admin/reportes` refleja esa venta en "Ventas totales" y "Productos más vendidos".
- [ ] Solicitar el reembolso de esa compra de prueba desde el panel de Mercado Pago
      (para no dejar un cargo real sin motivo).

## Legal
- [ ] El checkbox de Términos y Condiciones es obligatorio para registrarse (probar
      intentando registrarse sin tildarlo).
- [ ] Los textos de `TermsModal`/`PrivacyModal` reflejan el negocio real (razón social,
      email de contacto, dirección) y no quedaron los placeholders genéricos.

## Post-lanzamiento
- [ ] Alguien del equipo tiene acceso SSH al servidor y a las credenciales de Mercado
      Pago/SMTP documentado en un gestor de contraseñas compartido (no en la cabeza de
      una sola persona — mitigación del riesgo de "admin único" que señala el PRD).
```

- [ ] **Step 2: Commit**

```bash
git add docs/superpowers/plans/checklist-go-live.md
git commit -m "docs: agregar checklist de go-live para validar el flujo de pago real antes del lanzamiento"
```

---

## Resumen de verificación de la Fase 4

- CI en verde en GitHub Actions para el último commit de `main` (Task 1).
- Imágenes publicadas en GitHub Container Registry (Task 2).
- Un backup de prueba generado y, si corresponde, subido a almacenamiento offsite (Task 3).
- Stack de producción corriendo en el VPS real, HTTPS válido (Task 4).
- Checklist de go-live (Task 5) completo y sin ítems sin marcar antes de anunciar el lanzamiento públicamente.
