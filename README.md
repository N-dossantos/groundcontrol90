# 🛒 E-commerce Full Stack

Aplicación completa de e-commerce con **React + Vite** (Frontend) y **Spring Boot + MySQL** (Backend).

## 🚀 Inicio Rápido

### 🐳 Método 1: Docker (Recomendado)

#### Opción A: Desarrollo Local (construir desde código)
```powershell
# Construir e iniciar desde código local
docker-compose up -d --build

# ¡Listo! Los servicios están corriendo
```

#### Opción B: Producción (usar imágenes publicadas por CI)
```bash
# Requiere un .env completo: ver "Deploy a producción" más abajo
docker compose -f docker-compose.prod.yml up -d
```

Las imágenes las publica automáticamente GitHub Actions en **GitHub Container Registry**
(`ghcr.io`) en cada merge a `main` — no hay que construirlas ni subirlas a mano, ni
depender de la cuenta personal de nadie.

### 💻 Método 2: Desarrollo Local (Manual)

```powershell
# 1. MySQL con Docker
docker run --name mysql-ecommerce -e MYSQL_ROOT_PASSWORD=password -e MYSQL_DATABASE=ecommerce_db -p 3308:3306 -d mysql:8.0

# 2. Instalar dependencias (solo primera vez)
cd TPO-Ecommerce
npm install

# 3. Iniciar aplicación completa (Backend + Frontend)
npm run start
```

### URLs de Acceso:
- **Frontend**: http://localhost:5173
- **Backend API**: http://localhost:8081/api

### Credenciales de Prueba:
- **Admin**: `admin@test.com` / `admin123`
- **Usuario**: `user1@test.com` / `user123`
- **Usuario**: `test@test.com` / `test123`

---

## 🎯 Características

### ✅ Implementadas:
- **Backend**: Spring Boot + MySQL + JWT + Spring Security
- **Frontend**: React + TailwindCSS + Context API
- **Autenticación**: Login/Register con JWT
- **Productos**: CRUD completo con imágenes
- **Categorías**: Gestión completa
- **Carrito**: Sistema de compras funcional
- **UI/UX**: Diseño moderno y responsivo + Dark mode

---

## 🔧 Tecnologías

### Backend:
- **Java 24** + **Spring Boot 3.2.0**
- **MySQL 8.0** (Docker)
- **Spring Security** + **JWT**
- **Spring Data JPA** + **Hibernate**

### Frontend:
- **React 18** + **Vite**
- **TailwindCSS** + **Lucide Icons**
- **React Router** + **Context API**

---

## 📁 Estructura del Proyecto

```
TPO-Ecommerce/
├── backend/                 # Spring Boot + MySQL
│   ├── src/main/java/      # Código Java
│   ├── src/main/resources/ # Configuración
│   └── postman-collection-complete.json
├── frontend/               # React Frontend
│   ├── src/
│   │   ├── pages/         # Páginas principales
│   │   ├── components/    # Componentes reutilizables
│   │   ├── context/        # Estado global
│   │   └── services/api.js # API integrada con backend
│   └── package.json
├── docs/                   # Documentación completa
│   ├── DOCUMENTACION-COMPLETA.md  # Guía completa de desarrollo
│   └── DOCKER.md          # Guía específica de Docker
├── docker-compose.yml      # Orquestación Docker
└── package.json           # Scripts de desarrollo
```

---

## 📚 Documentación

- **[Documentación Completa](./docs/DOCUMENTACION-COMPLETA.md)** - Guía completa de desarrollo, arquitectura, backend, frontend, testing
- **[Guía de Docker](./docs/DOCKER.md)** - Configuración, comandos y troubleshooting de Docker
- **Postman Collection**: `backend/postman-collection-complete.json` - Colección completa para testing de la API

---

## 🛠️ Comandos Útiles

### 🐳 Docker Compose (Recomendado)

#### Desarrollo Local (construir desde código)
```powershell
# Construir e iniciar desde código local
docker-compose up -d --build        # Construir e iniciar todo
docker-compose logs -f             # Ver logs en tiempo real
docker-compose ps                   # Ver estado de servicios
docker-compose restart              # Reiniciar servicios
docker-compose down                 # Detener servicios
docker-compose down -v              # Detener y eliminar volúmenes
```

#### Producción (usar imágenes publicadas por CI)
```bash
docker compose -f docker-compose.prod.yml up -d      # Descargar y levantar las imágenes de ghcr.io
docker compose -f docker-compose.prod.yml logs -f    # Ver logs
docker compose -f docker-compose.prod.yml ps          # Ver estado
docker compose -f docker-compose.prod.yml down        # Detener servicios
```

**Diferencia**:
- `docker-compose.yml` → Construye las imágenes desde tu código local
- `docker-compose.prod.yml` → Descarga las imágenes que publicó CI en GitHub Container
  Registry, según `BACKEND_IMAGE`/`FRONTEND_IMAGE` del `.env`
  (ej. `ghcr.io/tu-org/ecommerce-backend:latest`)

### 💻 Desarrollo Local
```powershell
# Desarrollo
npm run dev              # Solo frontend
npm run backend          # Solo backend
npm run start            # Backend + Frontend

# Docker MySQL
docker start mysql-ecommerce
docker restart mysql-ecommerce
docker logs mysql-ecommerce

# Backend (manual)
cd backend
mvnd spring-boot:run

# Verificar estado
docker ps                                           # MySQL corriendo
netstat -an | Select-String ":8081"                # Backend corriendo
netstat -an | Select-String ":5173"                # Frontend corriendo
```

---

## 🚢 Deploy a Producción

Procedimiento completo para levantar el stack en un servidor real desde cero. Se corre
una sola vez (y sirve de guía si hay que migrar de servidor).

### 1. Provisionar el VPS
Cualquier proveedor sirve (DigitalOcean, Hetzner, AWS EC2). Mínimo **2GB de RAM** —
conviven MySQL, el backend Java, el frontend y Caddy — con **Ubuntu 22.04 LTS** o superior.

### 2. Instalar Docker
```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
# cerrar sesión y volver a entrar para que el grupo docker tome efecto
docker compose version
```

### 3. Apuntar el dominio
Crear un registro `A` en el proveedor de DNS apuntando a la IP pública del VPS.
Verificar la propagación con `dig tu-dominio.com` antes de seguir: Caddy no puede
emitir el certificado TLS si el dominio todavía no resuelve al servidor.

### 4. Clonar el repo y configurar el `.env`
```bash
git clone <url-del-repo> ecommerce
cd ecommerce
cp .env.example .env
nano .env
```

Completar **todas** las variables del `.env` (el compose falla al arrancar, con el
nombre de la variable que falta, si queda alguna vacía):
- Credenciales de MySQL
- `JWT_SECRET` — generar con `openssl rand -base64 64`
- `CORS_ALLOWED_ORIGINS`, `DOMAIN`, `FRONTEND_URL`, `BACKEND_URL` con el dominio real
- `MERCADOPAGO_ACCESS_TOKEN`/`PUBLIC_KEY`/`WEBHOOK_SECRET` **de producción** (no las de test)
- `SMTP_HOST`/`PORT`/`USERNAME`/`PASSWORD` y `MAIL_FROM` — sin esto el backend no arranca
- `BACKEND_IMAGE`/`FRONTEND_IMAGE` con el owner real de GitHub

### 5. Levantar el stack
```bash
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
```
Los cuatro servicios (`mysql-db`, `backend`, `frontend`, `proxy`) deben quedar en
`running`/`healthy`.

### 6. Verificar HTTPS
```bash
curl -I https://tu-dominio.com
```
Debe devolver `HTTP/2 200` con certificado válido. Caddy lo emite automáticamente
contra Let's Encrypt la primera vez que recibe tráfico en el dominio configurado;
puede tardar unos segundos.

### 7. Antes de anunciar el lanzamiento
Correr completo el [checklist de go-live](./docs/superpowers/plans/checklist-go-live.md),
que valida el flujo de pago real de punta a punta.

---

## 💾 Backups

`scripts/backup-mysql.sh` genera un dump comprimido de la base, rota los backups
locales más viejos que `BACKUP_RETENCION_DIAS` (7 por defecto) y, si `BACKUP_S3_BUCKET`
está configurado, sube la copia a un bucket S3-compatible.

Correrlo a mano:
```bash
set -a && source .env && set +a
./scripts/backup-mysql.sh
```

Instalar el cron en el servidor (se hace en el VPS, no desde CI: darle a un runner de
GitHub acceso de escritura a producción es peor que el problema que resuelve):
```bash
crontab -e
# agregar — backup diario a las 3 AM con el .env de producción cargado:
0 3 * * * cd /ruta/al/repo && set -a && source .env && set +a && ./scripts/backup-mysql.sh >> /var/log/ecommerce-backup.log 2>&1
```

Un backup que vive solo en el mismo disco que la base no protege contra perder el
servidor: configurar `BACKUP_S3_BUCKET` (y `BACKUP_S3_ENDPOINT` si el proveedor no es
AWS) para tener la copia afuera.

Restaurar un backup:
```bash
gunzip -c ecommerce_db-20260811-030000.sql.gz | docker exec -i ecommerce-mysql mysql -u root -p ecommerce_db
```

---

## ⚙️ CI/CD

Dos workflows de GitHub Actions:

| Workflow | Cuándo corre | Qué hace |
|---|---|---|
| `.github/workflows/backend-ci.yml` | push y PR a `main` | Corre la suite de tests del backend (`mvn -B test`) |
| `.github/workflows/build-and-push.yml` | push a `main` | Construye las imágenes de backend y frontend y las publica en `ghcr.io` con los tags `latest` y el SHA del commit |

Las imágenes se publican con el `GITHUB_TOKEN` del propio repositorio: no hay
credenciales de registry que gestionar. Para desplegar la última versión en el
servidor alcanza con:
```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

---

## 📊 Datos Iniciales

Al iniciar el backend por primera vez, se cargan automáticamente:
- ✅ **3 usuarios** (1 admin, 2 usuarios normales)
- ✅ **5 categorías** (Electrónicos, Ropa, Hogar, Deportes, Libros)
- ✅ **7 productos** con imágenes y descripciones completas

---

## 🧪 Testing

### Postman
Importar la colección: `backend/postman-collection-complete.json`

### Endpoints Principales
```http
# Autenticación
POST http://localhost:8081/api/auth/login
POST http://localhost:8081/api/auth/register

# Productos
GET  http://localhost:8081/api/productos
GET  http://localhost:8081/api/productos/{id}
POST http://localhost:8081/api/productos          # Requiere JWT

# Categorías
GET  http://localhost:8081/api/categorias
```

---

## 🆘 Solución de Problemas

### Error: MySQL no conecta
```powershell
# Verificar que MySQL esté corriendo
docker ps
docker start mysql-ecommerce
```

### Error: Puerto 8081 ocupado
```powershell
# Detener proceso Java
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
```

### Error: "Credenciales inválidas"
```powershell
# Recrear base de datos
docker exec mysql-ecommerce mysql -u root -ppassword -e "DROP DATABASE IF EXISTS ecommerce_db; CREATE DATABASE ecommerce_db;"
npm run start
```

---

## 👨‍💻 Desarrollo

### Estructura del Código
- **Backend**: Arquitectura en capas (Controller → Service → Repository)
- **Frontend**: Componentes funcionales con Hooks
- **Estado**: Context API para autenticación y carrito
- **Estilos**: TailwindCSS con sistema de diseño consistente

### Flujo de Autenticación
1. Login en frontend → Request a backend
2. Backend valida credenciales
3. Backend genera JWT token
4. Frontend almacena token
5. Token se incluye automáticamente en requests

---

## 📈 Próximos Pasos

- [x] Sistema de órdenes/pedidos ✅
- [x] Panel de administración ✅
- [x] Gestión de usuarios ✅
- [x] Sistema de marketplace multi-vendedor ✅
- [ ] Sistema de reviews y ratings
- [ ] Pasarela de pagos
- [ ] Deploy a producción

---

## 📄 Licencia

Este proyecto es de código abierto y está disponible bajo la licencia MIT.

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Por favor, abre un issue primero para discutir cambios mayores.

---

**¿Necesitas ayuda?** Consulta la [documentación completa](./docs/DOCUMENTACION-COMPLETA.md) o la [guía de Docker](./docs/DOCKER.md).
