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
      Pago necesita poder pegarle desde afuera). Probar con:

      curl -i -X POST https://tu-dominio.com/api/pagos/webhook \
        -H "Content-Type: application/json" -d '{}'

      Debe responder **401** por firma inválida — que es exactamente lo que se espera:
      significa que la request llegó al backend y la validación HMAC la rechazó. Un
      timeout o un 502 significan que el proxy o el backend no están bien. (El header
      `Content-Type: application/json` no es opcional: sin él la respuesta es 415 y no
      prueba nada sobre la firma.)
- [ ] El backup de MySQL (`scripts/backup-mysql.sh`) corrió al menos una vez
      exitosamente en el servidor y el archivo resultante no está vacío.
- [ ] El cron de backup diario quedó instalado (`crontab -l` lo muestra).

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
