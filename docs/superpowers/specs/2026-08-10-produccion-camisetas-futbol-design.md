# Diseño — Llevar TPO-Ecommerce a producción real (venta de camisetas de fútbol)

| | |
|---|---|
| **Fecha** | 2026-08-10 |
| **Basado en** | `PRD-ecommerce-camisetas-futbol.md` |
| **Estado** | Aprobado por el usuario en brainstorming, pendiente de plan de implementación |

## 1. Contexto y decisión clave sobre el PRD

El PRD original (`PRD-ecommerce-camisetas-futbol.md`) fue escrito asumiendo un stack Next.js + Supabase y un modelo de negocio de admin único. El proyecto real **TPO-Ecommerce** es distinto en ambos aspectos:

- **Stack real**: Spring Boot 3.2 (Java 17) + MySQL + Spring Security/JWT (backend), React 18 + Vite + TailwindCSS + Context API (frontend), Docker Compose con imágenes publicadas en Docker Hub.
- **Modelo real**: marketplace multi-vendedor ya funcional — cualquier usuario con rol `USER` puede cargar productos y vender; `DetallePedido` trackea vendedor y estado por ítem.

**Decisión**: el PRD se reinterpreta sobre el stack y modelo de negocio existentes. No hay migración a Next.js/Supabase ni reducción a admin único. Este documento reemplaza las secciones 8–13 del PRD para este proyecto puntual; el resto del PRD (objetivos, historias de usuario, requisitos funcionales de catálogo/carrito/checkout) sigue vigente como intención de producto.

Este desarrollo es para **producción real**: va a procesar pagos y dinero reales, no un sandbox académico. Eso eleva el nivel de exigencia en seguridad, manejo de secretos y aspectos legales/de negocio.

## 2. Qué se mantiene sin cambios

- Backend Spring Boot + MySQL + JPA/Hibernate, capas Controller → Service → Repository.
- Frontend React + Vite + Tailwind + Context API.
- Autenticación JWT propia + Spring Security + BCrypt (sin proveedores externos tipo Auth0/Supabase Auth).
- Modelo de marketplace multi-vendedor (`Usuario.role` USER/ADMIN, gestión de roles vía `AdminController` ya existente — no se restringe a un admin único hardcodeado).
- Despliegue vía Docker Compose, corriendo en un VPS/cloud genérico (no se migra a un PaaS).
- Checkout requiere cuenta de usuario (no se implementa compra como invitado).

## 3. Hardening de seguridad (bloqueante antes de manejar dinero real)

Hallazgos concretos en el código actual que deben corregirse:

1. **JWT signing key hardcodeada** en `backend/src/main/java/com/ecommerce/util/JwtUtil.java` (string fijo en el código, versionado en git). → Pasa a leerse de una variable de entorno obligatoria (`JWT_SECRET`); el arranque debe fallar si no está presente en el perfil `prod`.
2. **Credenciales de MySQL en texto plano** en `backend/src/main/resources/application-prod.properties` (`root`/`password` commiteados). → Se externalizan a variables de entorno sin defaults inseguros en el perfil prod.
3. **CORS duplicado y hardcodeado** a `localhost:3000`/`localhost:5173` en cada `@CrossOrigin` de `AdminController`, `PedidoController`, etc., además de la config en `application.properties`. → Se centraliza en un único `CorsConfigurationSource` leído de una variable de entorno (`CORS_ALLOWED_ORIGINS`) con el dominio real de producción; se eliminan los `@CrossOrigin` por controller.
4. **Autorización ad-hoc** en `PedidoController`: parsea `Authorization` a mano y llama a un método `isAdmin(authHeader)` propio, en vez de apoyarse en `SecurityContext`/`@PreAuthorize` como hace `AdminController`. → Se unifica al patrón estándar de Spring Security en todos los controllers.
5. **Sin HTTPS**: no hay terminación TLS en el stack actual. → Se agrega un reverse proxy (Nginx o Caddy) delante de Docker Compose con certificado (Let's Encrypt).
6. **Sin rate limiting**: `/api/auth/login` y `/api/auth/register` no tienen límite de intentos. → Rate limiting básico (a nivel de proxy o con bucket4j en el backend).
7. **Webhook de pagos sin validar firma** (a introducir en la Fase de pagos): el endpoint que reciba notificaciones de Mercado Pago debe validar la firma (`x-signature`) antes de confiar en el contenido — si no, cualquiera podría simular un pago aprobado.
8. **Esquema de base de datos sin control de versiones**: `spring.jpa.hibernate.ddl-auto=update` en prod deja que Hibernate modifique el esquema automáticamente al arrancar. → Se introduce Flyway, se genera una migración baseline del esquema actual, y `ddl-auto` pasa a `validate`.

## 4. Modelo de datos — cambios

### 4.1 `Producto` (modificación)
Se agregan columnas: `club` (String), `liga` (String), `temporada` (String), `tipo` (enum `CAMISETA`/`SHORT`).
Se **elimina** el campo `stock` agregado — la fuente única de verdad pasa a ser la suma de `producto_variantes` (sin capas de compatibilidad, según las convenciones del proyecto en `AGENTS.md`).

### 4.2 `ProductoVariante` (nueva entidad — tabla `producto_variantes`)
`id`, `producto_id` (FK), `talle` (String/enum), `stock` (Integer), `sku` (String, unique), `@Version` (columna de optimistic locking para evitar sobreventa en checkouts concurrentes).

### 4.3 `DetallePedido` (modificación)
Se agrega relación a `ProductoVariante` (o al menos `producto_variante_id` + snapshot `talle`), siguiendo el mismo patrón de snapshot que ya usan `productoNombre`/`productoImagen` (para no perder el dato si la variante cambia o se elimina después).

### 4.4 `Direccion` (nueva entidad — tabla `direcciones`)
`id`, `usuario_id` (FK), `calle`, `numero`, `ciudad`, `provincia`, `codigo_postal`, `pais`, `es_predeterminada`. El campo `direccionEnvio` de `Pedido` se sigue usando como snapshot de texto libre al momento de la compra.

### 4.5 `Pago` (nueva entidad — tabla `pagos`)
`id`, `pedido_id` (FK), `proveedor` (`MERCADOPAGO`), `preference_id`, `payment_id_externo`, `estado` (`PENDING`/`APPROVED`/`REJECTED`/`IN_PROCESS`/`REFUNDED`), `monto`, `moneda`, `created_at`, `updated_at`. Nunca se guarda número de tarjeta, CVV ni vencimiento — solo referencias que devuelve Mercado Pago.

## 5. Checkout y pagos — Mercado Pago

- **Checkout Pro** (redirect a checkout hosteado por Mercado Pago) en vez de Checkout API embebido: mismo nivel de tokenización/cumplimiento PCI, bastante menos superficie de implementación. Se puede evaluar Checkout API (bricks embebidos) como mejora futura.
- **Reserva de stock atómica al crear el pedido** (no al confirmarse el pago), usando optimistic locking (`@Version`) en `producto_variantes` dentro de la misma transacción que crea el `Pedido`, para evitar sobreventa entre checkouts concurrentes.
- Si el pago falla, se rechaza o expira, se libera el stock automáticamente — se extiende la lógica que ya existe en `PedidoService.cancelarPedido` (que ya "devuelve el stock a los productos"), agregando un job programado que cancela pedidos `PENDIENTE` sin pago confirmado tras una ventana de tiempo (ej. 30 minutos).
- Nuevo `PagoController`:
  - `POST /api/pagos/preferencia`: crea la preferencia de pago en Mercado Pago para un pedido del usuario autenticado, en estado `PENDIENTE`.
  - `POST /api/pagos/webhook`: recibe notificaciones de Mercado Pago, valida la firma, consulta el pago vía la API de Mercado Pago con el access token del backend, actualiza `Pago` y el estado del `Pedido`.
  - `GET /api/pagos/estado/{pedidoId}`: consulta de estado para que el frontend haga polling/mostrar confirmación tras el redirect.
- Las credenciales de Mercado Pago (access token / secret) se guardan como variables de entorno del backend; la public key es la única expuesta al frontend.

## 6. Envíos, facturación, notificaciones, legal (Fase posterior)

- **Envío**: costo fijo o configurable manualmente por el admin. No se integra un transportista en este alcance.
- **Facturación**: no se emite factura electrónica AFIP en este alcance — se envía un comprobante no fiscal por email al comprador. Queda anotado como limitación de negocio real: sin integración AFIP no pueden facturar formalmente ventas ante el fisco argentino; se evalúa como fase futura.
- **Emails transaccionales**: proveedor SMTP/API tipo Brevo o Resend (planes gratuitos alcanzan el volumen inicial) para: confirmación de cuenta, confirmación de pedido, cambio de estado de pedido.
- **Cupones de descuento**: quedan fuera de este alcance (no bloquean la venta real); se implementan en una fase posterior si el negocio lo requiere.
- **Legal**: se formaliza el `TermsModal.jsx` ya existente como página de Términos y Condiciones, se agrega Política de Privacidad, y checkbox de aceptación obligatorio en registro/checkout. Necesario tanto por la Ley de Protección de Datos Personales (25.326) como para habilitar la cuenta real de Mercado Pago como vendedor.

## 7. Fuera del alcance técnico (a resolver como negocio)

- Cuenta de Mercado Pago habilitada como vendedor real (CBU/CVU, datos fiscales/CUIT).
- Contratación de dominio propio y VPS de producción.

## 8. Roadmap por fases

| Fase | Contenido | Bloquea a |
|---|---|---|
| **Fase 0 — Hardening de seguridad y base de infra** | Secretos a variables de entorno, CORS centralizado, autorización unificada (`@PreAuthorize`), Flyway + migración baseline, reverse proxy con TLS, rate limiting en auth | Todo lo demás — es prerrequisito para manejar dinero real |
| **Fase 1 — Modelo de dominio camisetas** | `Producto` (club/liga/temporada/tipo), `ProductoVariante` (talle+stock+SKU), migración de `DetallePedido` a variante, `Direccion`, filtros de catálogo, ficha de producto con selector de talle | Fase 2 (el checkout necesita variantes y direcciones) |
| **Fase 2 — Checkout y pagos reales (Mercado Pago)** | Cuenta MP real, `PagoController`, entidad `Pago`, reserva atómica de stock, webhook con validación de firma, job de expiración de pedidos no pagados, flujo de checkout en frontend (envío → pago → confirmación) | Go-live |
| **Fase 3 — Notificaciones, legal, cupones (si aplica), reportes** | Emails transaccionales, páginas legales + checkbox de aceptación, cupones (si se confirma que se necesitan), ajuste de `VentasController`/reportes al nuevo modelo | — |
| **Fase 4 — Deploy y go-live** | Dominio + VPS productivo, pipeline de build/push de imágenes, backups automáticos de MySQL, checklist de smoke test end-to-end con Mercado Pago en modo real | — |

## 9. Riesgos

| Riesgo | Mitigación |
|---|---|
| Sobreventa de stock en checkouts concurrentes | Optimistic locking (`@Version`) en `producto_variantes`, reserva atómica al crear el pedido |
| Webhook de pago falsificado | Validación obligatoria de firma antes de procesar cualquier notificación |
| Secretos filtrados vía git history (ya commiteados) | Rotar JWT secret y credenciales de MySQL al migrar a variables de entorno — el valor viejo commiteado queda inválido |
| Lanzar sin cuenta real de Mercado Pago habilitada | Es un bloqueante de negocio explícito en la Fase 2, no técnico — debe resolverse antes del go-live |
| Facturación no fiscal (sin AFIP) | Aceptado como limitación conocida para este alcance; documentado para revisión futura |
