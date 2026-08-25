# 🛒 E-commerce de Camisetas de Fútbol

Marketplace multi-vendedor con **React + Vite** (frontend) y **Spring Boot + MySQL**
(backend), con pagos por Mercado Pago y deploy dockerizado detrás de Caddy con TLS
automático.

## 🚀 Inicio Rápido

### 🐳 Método 1: Docker (recomendado)

#### Opción A: desarrollo local (construir desde el código)
```bash
docker compose up -d --build
```
Levanta MySQL, el backend y el frontend. El backend corre con el perfil `docker`
(`application-docker.properties`): MySQL con Flyway, igual que producción, pero con
secretos de desarrollo y datos de ejemplo.

- Frontend: http://localhost
- Backend API: http://localhost:8081/api

> **Requiere credenciales de test de Mercado Pago.** Se generan gratis con una cuenta de
> prueba en el [panel de desarrolladores](https://www.mercadopago.com.ar/developers/panel/app).
> Ponelas en un `.env` en la raíz o exportalas antes de levantar:
>
> ```bash
> MERCADOPAGO_ACCESS_TOKEN=TEST-...
> MERCADOPAGO_PUBLIC_KEY=TEST-...
> MERCADOPAGO_WEBHOOK_SECRET=...
> ```
>
> Sin ellas el compose corta con el nombre de la variable que falta. Es deliberado: el
> backend no levanta sin poder cobrar, así nadie prueba el stack completo creyendo que
> el flujo de pago funciona.

#### Opción B: producción (imágenes publicadas por CI)
```bash
# Requiere un .env completo: ver "Deploy a producción" más abajo
docker compose -f docker-compose.prod.yml up -d
```

Las imágenes las publica GitHub Actions en **GitHub Container Registry** (`ghcr.io`) en
cada push a `main`, y sólo si la suite de tests pasa.

### 💻 Método 2: desarrollo local sin Docker

Dos terminales. El backend corre con el perfil `dev` (H2 en memoria, sin MySQL):

```bash
# Terminal 1 — backend en http://localhost:8081
cd backend
mvn spring-boot:run

# Terminal 2 — frontend en http://localhost:5173
cd frontend
npm install     # sólo la primera vez
npm run dev
```

El frontend le pega a la API por la ruta relativa `/api`; en desarrollo el proxy de
Vite (`vite.config.js`) la redirige al backend, y en producción lo hace Caddy. Por eso
no hay ninguna URL absoluta con `localhost` en el código del frontend: en una build de
producción apuntaría a la máquina del visitante.

### Credenciales de desarrollo (perfiles `dev` y `docker` únicamente):

> ⚠️ Estas cuentas las crea `DataInitializer`, que está anotado con
> `@Profile({"dev","docker"})` y **sólo corre en desarrollo**. En producción la base
> arranca vacía y el primer admin se crea a mano (ver [Primer admin](#9-primer-admin)).
> Nunca uses estas contraseñas en un servidor real.

- **Admin**: `admin@test.com` / `admin123`
- **Usuario**: `user1@test.com` / `user123`
- **Usuario**: `test@test.com` / `test123`

---

## 🎯 Características

- **Autenticación** con JWT (HS512) y rate limiting en `/api/auth/*`
- **Marketplace multi-vendedor**: cada usuario publica y gestiona sus propios productos
- **Catálogo** de camisetas y shorts con variantes de talle y stock por talle
- **Carrito y pedidos** con reserva de stock y expiración automática de pendientes
- **Pagos** con Mercado Pago (Checkout Pro + webhook con firma HMAC verificada)
- **Subida de imágenes** de producto, guardadas en el servidor
- **Panel de administración**: usuarios, roles y estadísticas de ventas
- **Notificaciones** por email transaccional
- **Deploy** con Docker Compose, Caddy con TLS automático y backups de MySQL

---

## 🔧 Tecnologías

### Backend
- **Java 17** + **Spring Boot 3.2.12**
- **MySQL 8.0** con **Flyway** para versionar el esquema
- **Spring Security** + **JWT** (jjwt)
- **Spring Data JPA** + **Hibernate**
- **Bucket4j** para el rate limiting

### Frontend
- **React 18** + **Vite**
- **TailwindCSS** + **Lucide Icons**
- **React Router** + **Context API**
- `fetch` nativo — sin cliente HTTP externo

---

## 📁 Estructura del Proyecto

```
TPO-Ecommerce/
├── backend/                      # Spring Boot + MySQL
│   ├── src/main/java/            # Código Java
│   ├── src/main/resources/
│   │   ├── application.properties        # Config base (perfil dev por defecto)
│   │   ├── application-dev.properties    # H2 en memoria
│   │   ├── application-docker.properties # MySQL del compose de desarrollo
│   │   ├── application-prod.properties   # Todo por variable de entorno
│   │   └── db/migration/                 # Migraciones de Flyway
│   ├── src/test/java/            # 156 tests
│   ├── Dockerfile
│   └── postman-collection-complete.json
├── frontend/                     # React + Vite
│   ├── src/
│   │   ├── pages/                # Páginas principales
│   │   ├── components/           # Componentes reutilizables
│   │   ├── context/              # Estado global
│   │   └── services/api.js       # Cliente de la API
│   ├── nginx.conf                # Sirve la SPA en el contenedor
│   └── Dockerfile
├── scripts/
│   └── backup-mysql.sh           # Dump comprimido + rotación + copia offsite
├── docs/
│   ├── DOCUMENTACION-COMPLETA.md
│   └── superpowers/plans/        # Planes de las fases y checklist de go-live
├── .github/workflows/            # CI: tests y publicación de imágenes
├── Caddyfile                     # Reverse proxy + TLS + headers de seguridad
├── docker-compose.yml            # Stack de desarrollo (build local)
├── docker-compose.prod.yml       # Stack de producción (imágenes de ghcr.io)
└── .env.example                  # Plantilla de variables de producción
```

---

## 📚 Documentación

- **[Documentación completa](./docs/DOCUMENTACION-COMPLETA.md)** — arquitectura, backend, frontend, testing
- **[Checklist de go-live](./docs/superpowers/plans/checklist-go-live.md)** — validación del flujo de pago real antes de lanzar
- **Postman**: `backend/postman-collection-complete.json`

---

## 🛠️ Comandos Útiles

### Docker Compose

```bash
# Desarrollo (construye desde el código local)
docker compose up -d --build
docker compose logs -f
docker compose ps
docker compose down
docker compose down -v            # también borra los volúmenes (¡y los datos!)

# Producción (usa las imágenes que publicó CI)
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml logs -f
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml down
```

**Diferencia**:
- `docker-compose.yml` → construye las imágenes desde el código local
- `docker-compose.prod.yml` → descarga las que publicó CI en GitHub Container Registry,
  según `BACKEND_IMAGE`/`FRONTEND_IMAGE` del `.env`

### Backend

```bash
cd backend
mvn spring-boot:run          # Perfil dev (H2), en http://localhost:8081
mvn -B test                  # Suite completa

# Ojo: borrar los reportes viejos antes de leer resultados, o un build que ni
# siquiera llegó a correr puede parecer exitoso
rm -rf target/surefire-reports && mvn -B test
```

### Frontend

```bash
cd frontend
npm run dev                  # http://localhost:5173, con proxy a /api
npm run build
npm audit --omit=dev
```

---

## 🚢 Deploy a Producción

Procedimiento completo para levantar el stack en un servidor real desde cero. Se corre
una sola vez, y sirve de guía si hay que migrar de servidor.

### 1. Provisionar el VPS

Ubuntu 22.04 o 24.04 LTS, **mínimo 2 GB de RAM** — conviven MySQL, la JVM del backend,
nginx y Caddy. Con 1 GB el backend muere por OOM durante el arranque de Hibernate.
Cualquier proveedor sirve (DigitalOcean, Hetzner, AWS EC2).

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

> ⚠️ Antes de cerrar la sesión actual, abrí **otra terminal** y confirmá que entrás como
> `deploy`. Con la sesión vieja abierta, un error se arregla; si la cerraste, perdiste el
> acceso al servidor.

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

> **ufw no filtra los puertos que publica Docker.** Docker escribe sus propias reglas en
> la cadena `DOCKER-USER` y en la tabla `nat`, que se evalúan antes que las de ufw, así
> que un `ufw status` mostrando un puerto cerrado da una falsa sensación de seguridad.
> La protección real es no publicar el puerto: por eso `mysql-db` no tiene `ports:` en
> `docker-compose.prod.yml`.

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

Caddy no puede emitir el certificado si el dominio todavía no resuelve, y Let's Encrypt
aplica rate limits: cinco fallos consecutivos bloquean el dominio por una hora.

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

El compose falla al arrancar, nombrando la variable que falta, si queda alguna vacía.

### 7. Levantar el stack

```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps        # los 4 en running/healthy
docker compose -f docker-compose.prod.yml logs -f backend
```

Si el backend reinicia en loop, casi siempre es una variable faltante o un `JWT_SECRET`
de menos de 64 bytes — el log lo dice explícitamente.

### 8. Verificar

```bash
curl -I https://tu-dominio.com                      # HTTP/2 200 con certificado válido

curl -i -X POST https://tu-dominio.com/api/pagos/webhook \
  -H "Content-Type: application/json" -d '{}'       # 401 = llegó al backend y la firma se rechazó
```

Un timeout o un 502 en el webhook significan que el proxy o el backend no están bien. El
`Content-Type` no es opcional: sin él la respuesta es 415 y no prueba nada sobre la firma.

### 9. Primer admin

La base de producción arranca **vacía**: `DataInitializer` sólo corre en los perfiles de
desarrollo. Registrarse por la UI con el email real y promover esa cuenta, una sola vez:

```bash
docker exec -it ecommerce-mysql mysql -u root -p ecommerce_db \
  -e "UPDATE usuarios SET role='ADMIN' WHERE email='tu-email-real@dominio.com';"
```

### 10. Webhook en el panel de Mercado Pago

Configurar la notification URL como `https://tu-dominio.com/api/pagos/webhook` y copiar
el secret que genera el panel a `MERCADOPAGO_WEBHOOK_SECRET` en el `.env`. Reiniciar el
backend después de cambiarlo.

### 11. Backups

```bash
set -a && source .env && set +a
./scripts/backup-mysql.sh        # probar a mano antes de automatizar

crontab -e
# 0 3 * * * cd /home/deploy/ecommerce && set -a && source .env && set +a && ./scripts/backup-mysql.sh >> /var/log/ecommerce-backup.log 2>&1
```

Configurar `BACKUP_S3_BUCKET`: un backup que vive en el mismo disco que la base no
protege contra perder el servidor.

### 12. Actualizaciones posteriores

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

### 13. Go-live

Correr completo el [checklist de go-live](./docs/superpowers/plans/checklist-go-live.md),
que valida el flujo de pago real de punta a punta.

### Acceso administrativo a MySQL

El puerto de MySQL **no está publicado**, a propósito. Para conectar un cliente de SQL,
túnel SSH en vez de republicarlo:

```bash
ssh -L 3307:localhost:3306 deploy@tu-dominio.com
# o directamente dentro del contenedor:
docker exec -it ecommerce-mysql mysql -u root -p
```

---

## 💾 Backups

`scripts/backup-mysql.sh` genera un dump comprimido de la base, rota los backups locales
más viejos que `BACKUP_RETENCION_DIAS` (7 por defecto) y, si `BACKUP_S3_BUCKET` está
configurado, sube la copia a un bucket S3-compatible.

```bash
set -a && source .env && set +a
./scripts/backup-mysql.sh
```

Restaurar un backup:
```bash
gunzip -c ecommerce_db-20260811-030000.sql.gz | docker exec -i ecommerce-mysql mysql -u root -p ecommerce_db
```

> Las **imágenes de producto** viven en el volumen `uploads_data`, no en la base: el dump
> de MySQL no las incluye. Para respaldarlas:
> ```bash
> docker run --rm -v tpo-ecommerce_uploads_data:/data -v "$PWD":/backup alpine \
>   tar czf /backup/uploads-$(date +%Y%m%d).tar.gz -C /data .
> ```

---

## ⚙️ CI/CD

| Workflow | Cuándo corre | Qué hace |
|---|---|---|
| `.github/workflows/backend-ci.yml` | PR a `main`, y como gate de `build-and-push` | Corre la suite del backend (`mvn -B test`) |
| `.github/workflows/build-and-push.yml` | push a `main` | Corre los tests y, **sólo si pasan**, construye y publica las imágenes en `ghcr.io` con los tags `latest` y el SHA del commit |

El `needs: test` es lo que impide que un commit con tests rotos publique `:latest` y que
el `docker compose pull` del servidor se lo baje.

Las imágenes se publican con el `GITHUB_TOKEN` del propio repositorio: no hay
credenciales de registry que gestionar.

---

## 📊 Datos Iniciales

Sólo en los perfiles `dev` y `docker`. En producción la base arranca vacía.

- **3 usuarios** (1 admin, 2 usuarios normales)
- **4 categorías**: Clubes Argentinos, Clubes Europeos, Selecciones, Retro
- **6 productos** con variantes de talle y stock

---

## 🧪 Testing

```bash
cd backend
rm -rf target/surefire-reports && mvn -B test    # 156 tests
```

Cubre servicios, controllers, seguridad (propiedad de productos, roles, rate limiting,
firma del webhook), subida de imágenes, concurrencia de stock y expiración de pedidos.

### Endpoints principales

```http
# Autenticación
POST /api/auth/login
POST /api/auth/register

# Productos
GET    /api/productos
GET    /api/productos/{id}
GET    /api/productos/filtrar?club=&liga=&tipo=&talle=&precioMin=&precioMax=
POST   /api/productos          # Requiere JWT
PUT    /api/productos/{id}     # Requiere JWT — sólo el vendedor propietario o un admin
DELETE /api/productos/{id}     # Requiere JWT — sólo el vendedor propietario o un admin

# Imágenes
POST /api/imagenes             # Requiere JWT — multipart, devuelve la URL
GET  /uploads/{archivo}        # Público

# Categorías
GET    /api/categorias         # Público
POST   /api/categorias         # Sólo ADMIN
```

---

## 🆘 Solución de Problemas

### El backend no arranca

Casi siempre es una variable de entorno faltante. El log lo dice explícitamente:
- `JWT_SECRET` de menos de 64 bytes → `JwtUtil` falla al arranque, a propósito
- Credenciales de Mercado Pago vacías → `MercadoPagoConfiguration` corta el arranque
- En `prod`, cualquier variable del `.env` sin completar

### MySQL no conecta

```bash
docker compose ps                      # ¿está healthy?
docker compose logs mysql-db
```

Verificar que `SPRING_DATASOURCE_USERNAME`/`PASSWORD` coincidan con `MYSQL_USER`/
`MYSQL_PASSWORD`: es el error más común del `.env`.

### Puerto 8081 ocupado

```bash
lsof -ti:8081 | xargs kill        # macOS / Linux
```

### Empezar de cero en desarrollo

```bash
docker compose down -v && docker compose up -d --build
```

---

## 👨‍💻 Desarrollo

### Estructura del código
- **Backend**: arquitectura en capas (Controller → Service → Repository)
- **Frontend**: componentes funcionales con Hooks
- **Estado**: Context API para autenticación y carrito
- **Estilos**: TailwindCSS

### Flujo de autenticación
1. Login en el frontend → request al backend
2. El backend valida credenciales y genera el JWT (HS512)
3. El frontend guarda el token y lo manda en cada request
4. `JwtAuthenticationFilter` lo valida y puebla el `SecurityContext`

### Perfiles de Spring

| Perfil | Base | Cuándo |
|---|---|---|
| `dev` | H2 en memoria | `mvn spring-boot:run` (por defecto) |
| `docker` | MySQL del compose | `docker compose up` |
| `prod` | MySQL, todo por variable de entorno | `docker-compose.prod.yml` |
| `test` | H2 en memoria | Suite de tests |

`dev` y `docker` siembran datos de ejemplo; `prod` nunca.

---

## 📈 Próximos Pasos

- [x] Sistema de órdenes/pedidos
- [x] Panel de administración
- [x] Gestión de usuarios
- [x] Sistema de marketplace multi-vendedor
- [x] Pasarela de pagos (Mercado Pago)
- [x] Deploy a producción
- [ ] Sistema de reviews y ratings
- [ ] Access token corto + refresh token con revocación

---

## 📄 Licencia

Este proyecto es de código abierto y está disponible bajo la licencia MIT.

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor, abrí un issue primero para discutir
cambios mayores.

---

**¿Necesitás ayuda?** Consultá la [documentación completa](./docs/DOCUMENTACION-COMPLETA.md).
