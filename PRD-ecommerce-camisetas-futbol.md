# PRD — Ecommerce de Camisetas y Shorts de Fútbol

| | |
|---|---|
| **Versión** | 1.0 |
| **Fecha** | 28 de julio de 2026 |
| **Autor** | Generado con Claude (Anthropic) a partir del brief del equipo de producto |
| **Estado** | Borrador para revisión |

> **Nota sobre la referencia de marca:** Instagram bloquea el acceso automatizado a perfiles públicos (robots.txt), por lo que no fue posible extraer directamente la paleta de colores, tipografías o fotografías del perfil `@groundcontrol.90`. Este documento asume una dirección de diseño típica de marcas de indumentaria de fútbol/streetwear (estética urbana, minimalista, fotografía editorial, tipografía bold) y la marca como **"pendiente de confirmación visual"**. Se recomienda que el equipo adjunte 5-10 capturas de pantalla del feed, o exporte la guía de marca si existe, para que el diseño final sea 1:1 con la referencia.

---

## 1. Resumen ejecutivo

Se desarrollará una tienda online (ecommerce) especializada en la venta de **camisetas y shorts de fútbol**, con un diseño moderno inspirado en la estética de la cuenta de Instagram `@groundcontrol.90`. El sitio se construirá con **Next.js** (frontend + capa de servidor) y **Supabase** (base de datos, autenticación, storage y funciones backend), e integrará una **pasarela de pagos basada en tokenización** (sin almacenar datos de tarjeta en servidores propios). Habrá dos tipos de cuentas: un **administrador único** con acceso total al panel de gestión, y **usuarios compradores** (clientes) con acceso a compra, perfil e historial de pedidos.

---

## 2. Objetivos del producto

| Objetivo | Descripción | Métrica de éxito |
|---|---|---|
| Vender online | Habilitar la venta directa de camisetas y shorts de fútbol | Nº de pedidos/mes |
| Experiencia moderna | Web rápida, mobile-first, alineada a la identidad visual de referencia | Core Web Vitals "Good", NPS |
| Pago seguro | Procesar pagos sin exponer datos sensibles de tarjeta | 0 incidentes de seguridad, tasa de aprobación de pago |
| Gestión simple | Un admin puede operar toda la tienda sin soporte técnico | Tiempo medio para publicar un producto |
| Escalabilidad | Arquitectura que soporte crecimiento de catálogo y tráfico | Tiempo de carga bajo carga, uptime |

**KPIs de negocio sugeridos (a validar con el cliente):**
- Tasa de conversión (visitas → compra)
- Ticket promedio (AOV)
- Tasa de abandono de carrito
- Tiempo de carga (LCP < 2.5s)
- Retención de usuarios registrados

---

## 3. Alcance del proyecto

### 3.1 Dentro del alcance (MVP)
- Catálogo de productos (camisetas y shorts) con variantes (talle, equipo/club, temporada, tipo — local/visitante/alternativa)
- Ficha de producto con múltiples imágenes, descripción, tabla de talles
- Carrito de compras persistente
- Checkout con pago online tokenizado (tarjeta de crédito/débito) + al menos un medio de pago local (ver sección 10)
- Registro/login de usuarios (email + contraseña, y opcionalmente Google OAuth)
- Perfil de usuario: datos personales, direcciones, historial de pedidos
- Panel de administración (rol único "admin"): CRUD de productos, gestión de stock, gestión de pedidos, gestión de cupones/descuentos, vista de ventas
- Notificaciones transaccionales por email (confirmación de pedido, cambio de estado)
- Diseño responsive (mobile, tablet, desktop) con identidad visual moderna
- SEO básico (metadatos, sitemap, Open Graph)

### 3.2 Fuera del alcance (fase 2 o posterior)
- App móvil nativa
- Multi-tienda / marketplace con múltiples vendedores
- Múltiples administradores con roles diferenciados (editor, soporte, etc.)
- Programa de fidelización / puntos
- Chat en vivo / soporte integrado
- Internacionalización multi-idioma y multi-moneda completa
- Reseñas y calificaciones de producto (puede evaluarse como "nice to have" en MVP si el tiempo lo permite)
- Integración con ERP/contabilidad externa

---

## 4. Usuarios y roles

| Rol | Cantidad | Permisos |
|---|---|---|
| **Admin** | 1 (único, hardcodeado/gestionado manualmente) | Acceso total al panel `/admin`: productos, stock, pedidos, cupones, usuarios (solo lectura), reportes de ventas |
| **Usuario comprador** | Ilimitados (self-signup) | Navegar catálogo, comprar, gestionar su propio perfil, ver su propio historial de pedidos |
| **Visitante (guest)** | N/A | Navegar catálogo, agregar al carrito; puede requerir crear cuenta o comprar como invitado (a definir, ver sección 12) |

> **Importante:** al ser un único administrador, no se requiere un sistema de roles jerárquico complejo. Se recomienda igualmente modelar la tabla `profiles` con un campo `role` (`admin` / `customer`) para dejar la puerta abierta a futuros roles sin rediseñar el esquema.

---

## 5. Historias de usuario principales

**Como visitante/comprador:**
- Quiero ver el catálogo de camisetas y shorts filtrado por club, liga, talle y precio, para encontrar rápido lo que busco.
- Quiero ver fotos grandes y una guía de talles en la ficha de producto, para comprar con confianza.
- Quiero agregar productos al carrito y modificar cantidades/talles antes de pagar.
- Quiero pagar con tarjeta de forma segura sin salir del flujo de checkout (o con redirección a la pasarela, según se defina).
- Quiero recibir un email de confirmación con el detalle de mi compra.
- Quiero ver el estado de mis pedidos anteriores desde mi cuenta.
- Quiero guardar mis direcciones de envío para no volver a cargarlas.

**Como administrador:**
- Quiero cargar un producto nuevo (nombre, club, categoría, imágenes, talles, precio, stock) en pocos minutos.
- Quiero ver y actualizar el estado de un pedido (pendiente, pagado, en preparación, enviado, entregado, cancelado).
- Quiero ver el stock disponible por talle y recibir alerta cuando esté bajo.
- Quiero crear cupones de descuento para campañas puntuales.
- Quiero ver un resumen de ventas (por día/semana/mes, por producto más vendido).

---

## 6. Requisitos funcionales por módulo

### 6.1 Catálogo y navegación
- Home con productos destacados, novedades y banners promocionales
- Listado de productos con filtros: club/equipo, liga, tipo (camiseta/short), talle, precio, temporada
- Buscador de productos (por nombre de club o texto libre)
- Ficha de producto: galería de imágenes (zoom), selector de talle, selector de cantidad, disponibilidad de stock en tiempo real, precio, descripción, tabla de talles, botón "Agregar al carrito"
- Productos relacionados/sugeridos en la ficha

### 6.2 Carrito de compras
- Carrito persistente (asociado a sesión de usuario logueado; opcionalmente también a `localStorage`/cookie para invitados)
- Editar cantidad, talle o eliminar ítems
- Cálculo automático de subtotal, envío (si aplica) y total
- Aplicación de cupones de descuento

### 6.3 Checkout y pagos
- Checkout en pasos claros: (1) datos de envío, (2) método de pago, (3) confirmación
- Soporte para comprar como invitado o logueado (definir con el cliente)
- Integración con pasarela de pago vía **tokenización** (ver sección 10)
- Validación de stock al confirmar el pago (evitar sobreventa)
- Página de confirmación de pedido con número de orden
- Manejo de pagos fallidos/rechazados con mensaje claro y reintento

### 6.4 Cuenta de usuario
- Registro / login (email + contraseña; opcional OAuth con Google)
- Recuperación de contraseña
- Edición de datos personales y direcciones
- Historial de pedidos con estado y detalle
- Cierre de sesión

### 6.5 Panel de administración (`/admin`)
- Login diferenciado para el admin (mismo sistema de auth de Supabase, validado por rol)
- **Productos:** alta, baja, edición, carga de imágenes (Supabase Storage), gestión de variantes (talle/stock por talle), activar/desactivar publicación
- **Pedidos:** listado con filtros por estado, detalle de pedido, cambio de estado, datos de envío y contacto del comprador
- **Cupones/descuentos:** crear cupones por porcentaje o monto fijo, con fecha de vigencia y usos máximos
- **Reportes:** ventas por período, productos más vendidos, ticket promedio
- **Usuarios:** listado de clientes registrados (solo lectura en MVP)

### 6.6 Notificaciones
- Email de confirmación de cuenta
- Email de confirmación de pedido
- Email de cambio de estado de pedido (enviado, entregado, cancelado)
- (Opcional fase 2) Notificación al admin por cada nueva venta

---

## 7. Requisitos no funcionales

| Categoría | Requisito |
|---|---|
| **Performance** | LCP < 2.5s en 4G, imágenes optimizadas (Next.js Image, formatos WebP/AVIF) |
| **Seguridad** | HTTPS obligatorio, Row Level Security (RLS) en Supabase, sanitización de inputs, protección CSRF/XSS, nunca almacenar datos de tarjeta en la app |
| **Disponibilidad** | Objetivo 99.5% uptime (dependiente del hosting elegido, ej. Vercel + Supabase Cloud) |
| **Escalabilidad** | Arquitectura serverless (Next.js + Supabase) que escale automáticamente ante picos de tráfico |
| **Accesibilidad** | Cumplir criterios básicos de WCAG 2.1 AA (contraste, navegación por teclado, alt en imágenes) |
| **SEO** | Server-side rendering / static generation para páginas de producto y categoría, metadatos dinámicos, sitemap.xml |
| **Compatibilidad** | Últimas 2 versiones de Chrome, Safari, Firefox, Edge; iOS y Android recientes |
| **Mantenibilidad** | Código tipado (TypeScript), estructura modular, componentes reutilizables |

---

## 8. Arquitectura técnica

### 8.1 Stack propuesto
- **Frontend/SSR:** Next.js (App Router), TypeScript, Tailwind CSS
- **Backend/DB/Auth/Storage:** Supabase (PostgreSQL + Auth + Storage + Edge Functions)
- **Pagos:** Pasarela con tokenización (ver sección 10)
- **Hosting frontend:** Vercel (recomendado por integración nativa con Next.js)
- **Hosting backend:** Supabase Cloud
- **Email transaccional:** Resend / SendGrid (a definir), integrado vía Supabase Edge Functions o API routes de Next.js
- **Gestión de imágenes:** Supabase Storage con CDN

### 8.2 Diagrama de flujo (alto nivel)

```
[Usuario] → [Next.js Frontend (Vercel)]
                 │
                 ├─ Lecturas de catálogo → [Supabase DB (PostgreSQL)]
                 ├─ Auth (login/registro) → [Supabase Auth]
                 ├─ Imágenes → [Supabase Storage]
                 ├─ Checkout → [API Route Next.js] → [Pasarela de pago (tokenización)]
                 │                                         │
                 │                              ← Webhook confirmación pago
                 └─ Panel admin (rol=admin) → [Supabase DB con RLS]
```

### 8.3 Autenticación y control de acceso
- Supabase Auth para registro/login (email+password, y opcionalmente OAuth)
- Tabla `profiles` vinculada a `auth.users`, con campo `role` (`admin` | `customer`)
- El único usuario admin se crea manualmente (seed) o mediante invitación directa; **no habrá flujo de auto-registro como admin**
- **Row Level Security (RLS)** en todas las tablas sensibles:
  - Un `customer` solo puede leer/editar sus propios registros (`orders`, `addresses`, `profile`)
  - Solo `role = 'admin'` puede escribir en `products`, `coupons`, y leer/editar todos los `orders`

---

## 9. Modelo de datos (propuesta inicial)

| Tabla | Campos clave |
|---|---|
| `profiles` | id (FK auth.users), nombre, apellido, teléfono, role (admin/customer), created_at |
| `addresses` | id, user_id, calle, número, ciudad, provincia, código postal, país, es_predeterminada |
| `products` | id, nombre, club, liga, tipo (camiseta/short), temporada, descripción, precio, imágenes[], activo, created_at |
| `product_variants` | id, product_id, talle, stock, sku |
| `orders` | id, user_id (nullable si invitado), estado, subtotal, envío, descuento, total, dirección_envío, created_at |
| `order_items` | id, order_id, product_variant_id, cantidad, precio_unitario |
| `payments` | id, order_id, proveedor, payment_token_ref, estado, monto, moneda, created_at |
| `coupons` | id, código, tipo (porcentaje/monto), valor, fecha_inicio, fecha_fin, usos_máximos, usos_actuales, activo |
| `categories` (opcional) | id, nombre, slug |

> **Nota:** la tabla `payments` guarda únicamente **referencias/IDs de transacción** devueltos por la pasarela (nunca número de tarjeta, CVV ni fecha de vencimiento).

---

## 10. Pagos: manejo de tokens y credenciales

Este es un punto crítico de seguridad y cumplimiento normativo (PCI DSS). Principios de diseño:

1. **Nunca se almacenan datos de tarjeta en los servidores propios (Next.js/Supabase).** La captura de los datos de tarjeta se realiza mediante el SDK/formulario embebido de la pasarela de pago (client-side), que devuelve un **token** de un solo uso o de referencia.
2. El backend (API route de Next.js o Edge Function de Supabase) solo recibe y reenvía ese **token** a la pasarela para crear el cobro — nunca ve el número de tarjeta completo.
3. Las **credenciales/API keys** de la pasarela (secret key) se guardan como **variables de entorno del lado servidor** (Vercel/Supabase secrets), nunca expuestas al cliente. Solo la **public key** se usa en el frontend.
4. Confirmación del pago mediante **webhook** de la pasarela hacia un endpoint seguro de la app (validando firma/secreto del webhook), que actualiza el estado del pedido en la base de datos.
5. Se recomienda registrar logs de auditoría de cada transacción (sin datos sensibles) para trazabilidad.

**Opciones de pasarela a evaluar (a confirmar con el cliente):**
- **Mercado Pago** (Checkout Pro o Checkout API con tokenización) — recomendado si el mercado principal es Argentina/Latinoamérica, por ser el método de pago más adoptado localmente y soportar tarjetas, cuotas y medios de pago en efectivo (Rapipago/Pago Fácil).
- **Stripe** — recomendado si se planea vender también a mercados internacionales, con excelente documentación y checkout tokenizado (Stripe.js/Elements).
- Es posible integrar ambas en el roadmap (Mercado Pago para mercado local, Stripe como fase 2 para internacional).

> **Pregunta abierta para el cliente:** ¿Cuál es el mercado principal (solo Argentina, o también otros países)? Esto define la pasarela principal y la moneda base (ARS, USD, etc.).

---

## 11. Diseño UX/UI

### 11.1 Dirección visual (a validar contra la referencia real de Instagram)
- Estética **streetwear / cultura futbolera moderna**: fotografía de producto editorial, tipografía bold/condensada para títulos, paleta con base neutra (negro/blanco/gris) y un color de acento fuerte
- Diseño **mobile-first**, grillas limpias, mucho espacio en blanco, animaciones sutiles en hover/scroll
- Sistema de diseño consistente: tokens de color, tipografía y espaciado reutilizables en todo el sitio (ver skill de frontend-design del equipo de desarrollo)

### 11.2 Páginas principales
1. Home
2. Listado de categoría/catálogo (con filtros)
3. Ficha de producto
4. Carrito
5. Checkout (datos de envío → pago → confirmación)
6. Login / Registro / Recuperar contraseña
7. Mi cuenta (perfil, direcciones, pedidos)
8. Panel admin (dashboard, productos, pedidos, cupones, reportes)

### 11.3 Entregables de diseño esperados
- Wireframes de las páginas principales
- Guía de estilo (colores, tipografía, componentes) — idealmente calcada de la identidad visual real de `@groundcontrol.90` una vez que el cliente comparta capturas o brand kit

---

## 12. Preguntas abiertas / decisiones pendientes con el cliente

Estas definiciones impactan directamente el desarrollo y deben confirmarse antes o durante el sprint 0:

1. **Identidad visual real:** ¿pueden compartir capturas del feed de Instagram, logo en alta resolución y/o manual de marca?
2. **Pasarela de pago:** ¿Mercado Pago, Stripe, u otra? ¿Se requiere pago en cuotas?
3. **Mercado objetivo:** ¿solo Argentina o también otros países? Define moneda e idioma.
4. **Compra como invitado:** ¿se permite comprar sin registrarse, o es obligatorio crear cuenta?
5. **Envíos:** ¿integración con Correo Argentino / Andreani / OCA, cálculo automático de costo por código postal, o costo fijo/manual por el admin?
6. **Stock:** ¿el admin carga el stock manualmente, o hay una integración con un sistema externo (Excel, ERP)?
7. **Facturación:** ¿se requiere emisión de factura electrónica (AFIP)? Esto puede requerir integración adicional.
8. **Volumen esperado de catálogo** (cantidad de productos) y **de tráfico** estimado, para dimensionar la infraestructura.

---

## 13. Roadmap propuesto (fases)

| Fase | Contenido | Duración estimada* |
|---|---|---|
| **Fase 0 — Descubrimiento** | Definir identidad visual real, confirmar pasarela de pago, wireframes y diseño UI | 1-2 semanas |
| **Fase 1 — MVP** | Catálogo, carrito, checkout con pago tokenizado, cuentas de usuario, panel admin básico (productos y pedidos) | 4-6 semanas |
| **Fase 2 — Mejora** | Cupones, reportes de ventas, notificaciones por email, optimización SEO/performance | 2-3 semanas |
| **Fase 3 — Escalado** | Reseñas de producto, integración de envíos automatizada, facturación electrónica, multi-idioma/moneda | A definir |

*Estimaciones referenciales; deben ajustarse según el equipo de desarrollo asignado.

---

## 14. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| No tener acceso confirmado a la identidad visual real de la marca | Retrabajo de diseño | Solicitar brand kit/capturas antes de iniciar diseño final |
| Manejo inseguro de datos de pago | Legal/reputacional alto | Usar siempre tokenización provista por la pasarela; nunca tocar datos crudos de tarjeta |
| Sobreventa de stock en alta demanda | Mala experiencia de cliente | Validar stock en el momento del pago (transacción atómica en DB) |
| Un solo administrador (punto único de fallo operativo) | Operación bloqueada si el admin no está disponible | Documentar accesos y credenciales de forma segura (ej. gestor de contraseñas compartido) para continuidad |

---

## 15. Glosario

- **RLS (Row Level Security):** mecanismo de PostgreSQL/Supabase para restringir el acceso a filas de una tabla según el usuario autenticado.
- **Tokenización:** proceso por el cual los datos sensibles de una tarjeta se reemplazan por un identificador (token) que no tiene valor fuera del sistema de la pasarela de pago.
- **PCI DSS:** estándar de seguridad de la industria de tarjetas de pago, que exige no almacenar datos sensibles de tarjeta fuera de proveedores certificados.
- **SSR/SSG:** Server-Side Rendering / Static Site Generation, técnicas de renderizado de Next.js.

---

*Fin del documento. Este PRD es un punto de partida: se recomienda una sesión de revisión con el cliente para cerrar las preguntas abiertas de la sección 12 antes de comenzar el desarrollo.*
