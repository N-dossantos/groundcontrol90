# Fase 3 — Notificaciones, legal y reportes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Emails transaccionales (cuenta, pedido confirmado, cambio de estado), aceptación obligatoria y auditable de Términos y Condiciones al registrarse, y un panel de reportes de ventas para el admin — todo lo que el PRD pide para el MVP fuera de catálogo/checkout/pagos.

**Architecture:** Un `EmailService` centralizado (Spring Mail vía SMTP, asíncrono para no bloquear el request que lo dispara) se invoca desde los puntos donde ya existe lógica de negocio relevante (`AuthController.register`, `PedidoService.confirmarPago`, `PedidoService.actualizarEstado`) — no se agrega una capa de eventos/colas nueva, es una llamada directa que YAGNI no justifica más infraestructura para 3 tipos de email. Los reportes reutilizan `Pedido`/`DetallePedido` ya existentes con dos queries nuevas (ventas por período, productos más vendidos).

**Tech Stack:** `spring-boot-starter-mail` (SMTP vía Brevo o Resend, a elección del equipo — cualquiera de los dos ofrece un relay SMTP estándar compatible con `JavaMailSender`), Spring `@Async`.

## Global Constraints

- Depende de las Fases 0-2 ya aplicadas (en particular, `PedidoService.confirmarPago`/`cancelarPedidoPorPagoRechazado` de la Fase 2, y el modelo de `ProductoVariante`/`talle` de la Fase 1 para los templates de email).
- Ninguna credencial SMTP se commitea — mismo patrón `${VAR}` de las fases anteriores.
- Emails en texto plano (`SimpleMailMessage`), no HTML — no se agrega un motor de templates (Thymeleaf/Freemarker) para tres emails corporativos simples; si el equipo quiere emails con diseño más adelante, es una mejora de fase futura, no bloquea la venta real.
- Estilo de tests: igual al de las fases anteriores (Mockito puro para lógica; `@SpringBootTest + MockMvc` para lo que necesita el contexto completo).

---

### Task 1: `EmailService` (infraestructura de envío)

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/ecommerce/service/EmailService.java`
- Create: `backend/src/test/java/com/ecommerce/service/EmailServiceTest.java`
- Modify: `backend/src/main/resources/application-dev.properties`
- Modify: `backend/src/main/resources/application-prod.properties`
- Modify: `backend/src/test/resources/application-test.properties`
- Modify: `backend/src/main/java/com/ecommerce/EcommerceBackendApplication.java`

**Interfaces:**
- Produces: `EmailService.enviarConfirmacionCuenta(Usuario)`, `EmailService.enviarConfirmacionPedido(Pedido)`, `EmailService.enviarCambioEstadoPedido(Pedido, EstadoPedido)` — consumidos por `AuthController` (Task 2) y `PedidoService` (Task 3). Los tres son `@Async` — no bloquean al llamador ni hacen fallar la operación de negocio si el envío de mail falla.

- [ ] **Step 1: Agregar la dependencia**

En `backend/pom.xml`:

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-mail</artifactId>
        </dependency>
```

- [ ] **Step 2: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/service/EmailServiceTest.java`:

```java
package com.ecommerce.service;

import com.ecommerce.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - EmailService")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "no-reply@tienda-test.com");
    }

    @Test
    @DisplayName("Debería enviar el email de confirmación de cuenta al email del usuario")
    void testEnviarConfirmacionCuenta() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();

        emailService.enviarConfirmacionCuenta(usuario);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage enviado = captor.getValue();
        assertArrayEquals(new String[]{"juan@test.com"}, enviado.getTo());
        assertTrue(enviado.getText().contains("Juan"));
    }

    @Test
    @DisplayName("Debería enviar el email de confirmación de pedido con el número de pedido")
    void testEnviarConfirmacionPedido() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();
        DetallePedido item = DetallePedido.builder()
                .productoNombre("Camiseta Boca").talle("M").cantidad(1)
                .precioUnitario(new BigDecimal("45000")).build();
        Pedido pedido = Pedido.builder().id(77L).usuario(usuario).total(new BigDecimal("45000"))
                .items(List.of(item)).build();

        emailService.enviarConfirmacionPedido(pedido);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getSubject().contains("77"));
    }

    @Test
    @DisplayName("Debería enviar el email de cambio de estado con el nuevo estado")
    void testEnviarCambioEstadoPedido() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();
        Pedido pedido = Pedido.builder().id(78L).usuario(usuario).total(new BigDecimal("45000"))
                .items(List.of()).build();

        emailService.enviarCambioEstadoPedido(pedido, EstadoPedido.ENVIADO);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getText().contains("ENVIADO"));
    }
}
```

- [ ] **Step 3: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=EmailServiceTest`
Expected: FALLA — `EmailService` no existe.

- [ ] **Step 4: Implementar `EmailService`**

```java
package com.ecommerce.service;

import com.ecommerce.entity.DetallePedido;
import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Async
    public void enviarConfirmacionCuenta(Usuario usuario) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(usuario.getEmail());
        mensaje.setSubject("¡Bienvenido a la tienda!");
        mensaje.setText("Hola " + usuario.getNombre() + ",\n\nTu cuenta fue creada exitosamente.");
        enviarSeguro(mensaje);
    }

    @Async
    public void enviarConfirmacionPedido(Pedido pedido) {
        String detalle = pedido.getItems().stream()
                .map(item -> "- " + item.getProductoNombre() + " (talle " + item.getTalle() + ") x" + item.getCantidad())
                .collect(Collectors.joining("\n"));

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(pedido.getUsuario().getEmail());
        mensaje.setSubject("Pedido #" + pedido.getId() + " confirmado");
        mensaje.setText("Hola " + pedido.getUsuario().getNombre() + ",\n\n"
                + "Tu pedido #" + pedido.getId() + " fue confirmado:\n\n" + detalle
                + "\n\nTotal: $" + pedido.getTotal());
        enviarSeguro(mensaje);
    }

    @Async
    public void enviarCambioEstadoPedido(Pedido pedido, EstadoPedido nuevoEstado) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(pedido.getUsuario().getEmail());
        mensaje.setSubject("Pedido #" + pedido.getId() + " actualizado");
        mensaje.setText("Hola " + pedido.getUsuario().getNombre() + ",\n\n"
                + "Tu pedido #" + pedido.getId() + " ahora está en estado: " + nuevoEstado);
        enviarSeguro(mensaje);
    }

    private void enviarSeguro(SimpleMailMessage mensaje) {
        try {
            mailSender.send(mensaje);
        } catch (Exception e) {
            // Un email que no sale no debe tumbar el pedido/pago que ya se confirmó en la DB.
            log.error("Error enviando email a {}", (Object[]) mensaje.getTo(), e);
        }
    }
}
```

- [ ] **Step 5: Habilitar `@Async` y configurar SMTP por entorno**

En `EcommerceBackendApplication.java`, agregar `@EnableAsync` (junto al `@EnableScheduling` de la Fase 2):

```java
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class EcommerceBackendApplication {
```

En `application-prod.properties`:

```properties
# Email (SMTP)
spring.mail.host=${SMTP_HOST}
spring.mail.port=${SMTP_PORT}
spring.mail.username=${SMTP_USERNAME}
spring.mail.password=${SMTP_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
app.mail.from=${MAIL_FROM}
```

En `application-dev.properties` (con defaults vacíos — a diferencia del JWT secret, levantar el backend sin SMTP configurado no debe romper el arranque, solo falla si de verdad se intenta mandar un mail; `enviarSeguro` ya atrapa ese error):

```properties
# Email (SMTP) — completar con credenciales de test de Brevo/Resend para probar el envío real
spring.mail.host=${SMTP_HOST:localhost}
spring.mail.port=${SMTP_PORT:1025}
spring.mail.username=${SMTP_USERNAME:}
spring.mail.password=${SMTP_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
app.mail.from=${MAIL_FROM:no-reply@localhost}
```

En `backend/src/test/resources/application-test.properties`, agregar:

```properties
spring.mail.host=localhost
spring.mail.port=1025
app.mail.from=no-reply@test.com
```

- [ ] **Step 6: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=EmailServiceTest`
Expected: PASS

- [ ] **Step 7: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/java/com/ecommerce/service/EmailService.java \
        backend/src/test/java/com/ecommerce/service/EmailServiceTest.java \
        backend/src/main/resources/application-dev.properties \
        backend/src/main/resources/application-prod.properties \
        backend/src/test/resources/application-test.properties \
        backend/src/main/java/com/ecommerce/EcommerceBackendApplication.java
git commit -m "feat: agregar EmailService asíncrono vía SMTP para notificaciones transaccionales"
```

---

### Task 2: Email de confirmación de cuenta al registrarse

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/controller/AuthController.java`
- Modify: `backend/src/test/java/com/ecommerce/controller/AuthControllerTest.java`

- [ ] **Step 1: Escribir el test que falla**

En `AuthControllerTest.java`, agregar el mock `@Mock private EmailService emailService;` (junto a los `@Mock` existentes) y extender `testRegister`:

```java
    verify(emailService, times(1)).enviarConfirmacionCuenta(nuevoUsuario);
```

(agregar esa línea al final del test `testRegister` ya existente, después de las verificaciones que ya tiene).

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=AuthControllerTest`
Expected: FALLA — `AuthController` no depende de `EmailService` todavía.

- [ ] **Step 3: Inyectar y llamar `EmailService`**

En `AuthController.java`, agregar `@Autowired private EmailService emailService;` y, en `register`, justo después de `Usuario usuarioGuardado = usuarioService.save(nuevoUsuario);`:

```java
        emailService.enviarConfirmacionCuenta(usuarioGuardado);
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=AuthControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ecommerce/controller/AuthController.java \
        backend/src/test/java/com/ecommerce/controller/AuthControllerTest.java
git commit -m "feat: enviar email de confirmación de cuenta al registrarse"
```

---

### Task 3: Email de pedido confirmado y de cambio de estado

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/service/PedidoService.java`
- Modify: `backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java`

**Interfaces:**
- Consumes: `EmailService` (Task 1).

- [ ] **Step 1: Escribir los tests que fallan**

Agregar el mock `@Mock private EmailService emailService;` a `PedidoServiceTest` y extender los tests ya escritos en la Fase 2:

```java
// Extender testConfirmarPago_MueveAConfirmado (Fase 2, Task 6) agregando al final:
verify(emailService, times(1)).enviarConfirmacionPedido(pedido);
```

Agregar además un test nuevo para el cambio de estado manual (admin):

```java
@Test
@DisplayName("Debería enviar email cuando el admin marca un pedido como ENVIADO")
void testActualizarEstado_Enviado_MandaEmail() {
    Usuario comprador = Usuario.builder().id(5L).nombre("Juan").email("juan@test.com").build();
    Pedido pedido = Pedido.builder().id(80L).usuario(comprador).estado(EstadoPedido.CONFIRMADO).items(List.of()).build();

    when(pedidoRepository.findById(80L)).thenReturn(Optional.of(pedido));
    when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

    pedidoService.actualizarEstado(80L, EstadoPedido.ENVIADO);

    verify(emailService, times(1)).enviarCambioEstadoPedido(pedido, EstadoPedido.ENVIADO);
}
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: FALLA — `PedidoService` no depende de `EmailService` todavía.

- [ ] **Step 3: Inyectar `EmailService` y llamarlo**

En `PedidoService.java`, agregar `@Autowired private EmailService emailService;`.

En `confirmarPago`, justo antes del `return pedidoRepository.save(pedido);`:

```java
        Pedido guardado = pedidoRepository.save(pedido);
        emailService.enviarConfirmacionPedido(guardado);
        return guardado;
```

(reemplaza el `return pedidoRepository.save(pedido);` final por estas tres líneas).

En `actualizarEstado`, después de `pedido.setUpdatedAt(LocalDateTime.now());`:

```java
        Pedido guardado = pedidoRepository.save(pedido);
        emailService.enviarCambioEstadoPedido(guardado, nuevoEstado);
        return guardado;
```

(reemplaza el `return pedidoRepository.save(pedido);` final de ese método).

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: PASS

- [ ] **Step 5: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/service/PedidoService.java \
        backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java
git commit -m "feat: enviar email al confirmar el pago y al cambiar el estado de un pedido"
```

---

### Task 4: Aceptación obligatoria y auditable de Términos y Condiciones

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/entity/Usuario.java`
- Modify: `backend/src/main/java/com/ecommerce/dto/RegisterRequestDTO.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/AuthController.java`
- Create: `backend/src/main/resources/db/migration/V7__terminos_aceptados.sql`
- Create: `backend/src/test/java/com/ecommerce/controller/AuthControllerValidationTest.java`

**Interfaces:**
- Produces: `Usuario.terminosAceptadosAt: LocalDateTime` — registro auditable de cuándo cada usuario aceptó los términos (requisito de Ley 25.326 y de la cuenta real de Mercado Pago). `RegisterRequestDTO.aceptaTerminos: boolean` — campo obligatorio (`@AssertTrue`) validado por Spring en el registro.

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/controller/AuthControllerValidationTest.java` (necesita el contexto completo de Spring para que `@Valid` se aplique — llamar al método del controller directo, como hace `AuthControllerTest`, no ejercita bean validation):

```java
package com.ecommerce.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Validación de registro")
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Debería rechazar el registro si no se aceptan los Términos y Condiciones")
    void testRegister_SinAceptarTerminos_Rechazado() throws Exception {
        String body = """
                {"username":"nuevo","email":"nuevo@test.com","password":"password123",
                 "nombre":"Nuevo","apellido":"Usuario","aceptaTerminos":false}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debería aceptar el registro si se aceptan los Términos y Condiciones")
    void testRegister_AceptandoTerminos_Ok() throws Exception {
        String body = """
                {"username":"nuevo2","email":"nuevo2@test.com","password":"password123",
                 "nombre":"Nuevo","apellido":"Usuario","aceptaTerminos":true}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=AuthControllerValidationTest`
Expected: FALLA — `aceptaTerminos` no existe en `RegisterRequestDTO`, así que ambos requests hoy devuelven 200 sin validar nada.

- [ ] **Step 3: Agregar el campo a `Usuario` y `RegisterRequestDTO`**

En `Usuario.java`, agregar:

```java
    @Column(name = "terminos_aceptados_at")
    private LocalDateTime terminosAceptadosAt;
```

En `RegisterRequestDTO.java`, agregar el import `jakarta.validation.constraints.AssertTrue` y el campo:

```java
    @AssertTrue(message = "Debés aceptar los Términos y Condiciones y la Política de Privacidad")
    private boolean aceptaTerminos;
```

- [ ] **Step 4: Setear el timestamp al registrar**

En `AuthController.register`, en la construcción de `nuevoUsuario`, agregar:

```java
                .terminosAceptadosAt(LocalDateTime.now())
```

(agregar el import `java.time.LocalDateTime` si no está ya presente en el archivo — sí lo está, se usa indirectamente vía Lombok en otras partes del proyecto; si el compilador se queja, agregarlo explícitamente).

- [ ] **Step 5: Migración Flyway**

```sql
ALTER TABLE usuarios ADD COLUMN terminos_aceptados_at DATETIME NULL;
```

(Nullable porque los usuarios ya existentes en una base real no tienen este dato retroactivo — queda como una limitación conocida y documentada, no se puede inventar un timestamp de aceptación que nunca ocurrió.)

- [ ] **Step 6: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=AuthControllerValidationTest`
Expected: PASS

- [ ] **Step 7: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS (revisar que `AuthControllerTest`, que arma sus `RegisterRequestDTO` a mano, siga compilando — el nuevo campo es un `boolean` primitivo con default `false`, así que los tests existentes que no lo setean van a fallar la validación solo si pasan por MockMvc; `AuthControllerTest` llama al controller directo sin `@Valid`, así que no se ve afectado).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/Usuario.java \
        backend/src/main/java/com/ecommerce/dto/RegisterRequestDTO.java \
        backend/src/main/java/com/ecommerce/controller/AuthController.java \
        backend/src/main/resources/db/migration/V7__terminos_aceptados.sql \
        backend/src/test/java/com/ecommerce/controller/AuthControllerValidationTest.java
git commit -m "feat: exigir y auditar la aceptación de Términos y Condiciones al registrarse"
```

---

### Task 5: Checkbox de Términos y Condiciones en `Register.jsx`

**Files:**
- Modify: `frontend/src/pages/Register.jsx`
- Modify: `frontend/src/services/api.js`

No se agrega el mismo checkbox en el checkout: como la Fase 1 ya definió que comprar requiere cuenta (no hay compra como invitado), la aceptación ya quedó registrada en el registro — pedirla de nuevo en cada compra sería fricción redundante.

- [ ] **Step 1: Enviar `aceptaTerminos` en `api.register`**

En `frontend/src/services/api.js`, en `register`, agregar al body enviado:

```js
          aceptaTerminos: userData.aceptaTerminos === true,
```

- [ ] **Step 2: Agregar el checkbox al formulario**

En `frontend/src/pages/Register.jsx`, importar `TermsModal` y `PrivacyModal` (`../components/modals/TermsModal`, `../components/modals/PrivacyModal`), agregar al estado:

```jsx
  const [aceptaTerminos, setAceptaTerminos] = useState(false)
  const [showTerms, setShowTerms] = useState(false)
  const [showPrivacy, setShowPrivacy] = useState(false)
```

Agregar el checkbox antes del botón de submit (después del campo "Apellido", línea 206-207 del archivo original):

```jsx
            <div className="flex items-start gap-2">
              <input
                type="checkbox"
                id="aceptaTerminos"
                checked={aceptaTerminos}
                onChange={(e) => setAceptaTerminos(e.target.checked)}
                className="mt-1"
              />
              <label htmlFor="aceptaTerminos" className="text-sm text-gray-600 dark:text-gray-300">
                Acepto los{" "}
                <button type="button" onClick={() => setShowTerms(true)} className="text-blue-600 dark:text-blue-400 underline">
                  Términos y Condiciones
                </button>{" "}
                y la{" "}
                <button type="button" onClick={() => setShowPrivacy(true)} className="text-blue-600 dark:text-blue-400 underline">
                  Política de Privacidad
                </button>
              </label>
            </div>
            {errors.aceptaTerminos && <p className="text-sm text-red-600 dark:text-red-400">{errors.aceptaTerminos}</p>}
```

y, al final del componente, antes del cierre del `<div>` raíz:

```jsx
        <TermsModal isOpen={showTerms} onClose={() => setShowTerms(false)} />
        <PrivacyModal isOpen={showPrivacy} onClose={() => setShowPrivacy(false)} />
```

- [ ] **Step 3: Validar y enviar**

En `validateForm`, agregar:

```js
    if (!aceptaTerminos) {
      newErrors.aceptaTerminos = "Debés aceptar los Términos y Condiciones para crear una cuenta"
    }
```

En `handleSubmit`, el objeto pasado a `register(...)` pasa a incluir `aceptaTerminos` (`register({ ...formData, aceptaTerminos })`), y deshabilitar el botón de submit mientras `!aceptaTerminos` además de `loading`.

- [ ] **Step 4: Verificación manual**

Run: `npm run dev` en `frontend/`, ir a `/register`, confirmar que el botón de crear cuenta está deshabilitado (o falla la validación) sin tildar el checkbox, y que tildándolo y completando el resto del formulario la cuenta se crea correctamente.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/Register.jsx frontend/src/services/api.js
git commit -m "feat: exigir aceptación de Términos y Condiciones en el registro (frontend)"
```

---

### Task 6: Reporte de ventas — backend (por período y productos más vendidos)

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/repository/PedidoRepository.java`
- Modify: `backend/src/main/java/com/ecommerce/repository/DetallePedidoRepository.java`
- Create: `backend/src/main/java/com/ecommerce/dto/ReporteVentasDTO.java`
- Create: `backend/src/main/java/com/ecommerce/dto/ProductoMasVendidoDTO.java`
- Modify: `backend/src/main/java/com/ecommerce/service/PedidoService.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/PedidoController.java`
- Create: `backend/src/test/java/com/ecommerce/service/ReporteVentasTest.java`

**Interfaces:**
- Produces: `PedidoService.generarReporteVentas(LocalDateTime desde, LocalDateTime hasta)` → `ReporteVentasDTO{ventasTotales, ticketPromedio, cantidadPedidos, productosMasVendidos}`; `GET /api/pedidos/admin/reportes?desde=&hasta=` (`@PreAuthorize("hasRole('ADMIN')")`).
- Solo cuentan como "venta" los pedidos que llegaron a pagarse (`estado` distinto de `PENDIENTE` y `PAGO_RECHAZADO`) — un carrito abandonado no es una venta.

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/service/ReporteVentasTest.java` como test de integración (necesita agregaciones SQL reales sobre `detalle_pedidos`, no mockeable):

```java
package com.ecommerce.service;

import com.ecommerce.dto.ReporteVentasDTO;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Reporte de ventas")
// Nota: las comparaciones de BigDecimal en este test usan compareTo (vía assertBigDecimalEquals),
// no assertEquals directo — BigDecimal.equals() es sensible a la escala (155000 != 155000.00
// aunque representen el mismo valor), así que assertEquals produciría falsos negativos.
class ReporteVentasTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario comprador = usuarioRepository.save(Usuario.builder()
                .nombre("C").apellido("C").username("comprador3").email("comprador3@test.com")
                .password("x").role(Role.USER).build());

        crearPedidoConfirmado(comprador, "Camiseta Boca", 2, new BigDecimal("45000"));
        crearPedidoConfirmado(comprador, "Camiseta Boca", 1, new BigDecimal("45000"));
        crearPedidoConfirmado(comprador, "Short River", 1, new BigDecimal("20000"));
        crearPedidoPendiente(comprador, "Camiseta River", 5, new BigDecimal("45000")); // no debe contar
    }

    private void crearPedidoConfirmado(Usuario comprador, String producto, int cantidad, BigDecimal precioUnitario) {
        guardarPedido(comprador, producto, cantidad, precioUnitario, EstadoPedido.CONFIRMADO);
    }

    private void crearPedidoPendiente(Usuario comprador, String producto, int cantidad, BigDecimal precioUnitario) {
        guardarPedido(comprador, producto, cantidad, precioUnitario, EstadoPedido.PENDIENTE);
    }

    private void guardarPedido(Usuario comprador, String producto, int cantidad, BigDecimal precioUnitario, EstadoPedido estado) {
        Pedido pedido = pedidoRepository.save(Pedido.builder()
                .usuario(comprador).estado(estado)
                .total(precioUnitario.multiply(BigDecimal.valueOf(cantidad)))
                .createdAt(LocalDateTime.now()).items(new java.util.ArrayList<>()).build());
        DetallePedido item = DetallePedido.builder()
                .pedido(pedido).productoNombre(producto).talle("M").cantidad(cantidad)
                .precioUnitario(precioUnitario).estadoItem(EstadoPedido.PENDIENTE).build();
        detallePedidoRepository.save(item);
    }

    @Test
    @DisplayName("Debería sumar solo pedidos pagados y rankear productos por cantidad vendida")
    void testGenerarReporteVentas() {
        ReporteVentasDTO reporte = pedidoService.generarReporteVentas(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertBigDecimalEquals("155000", reporte.getVentasTotales()); // (2+1)*45000 + 1*20000
        assertEquals(3, reporte.getCantidadPedidos());
        assertEquals("Camiseta Boca", reporte.getProductosMasVendidos().get(0).getNombre());
        assertEquals(3L, reporte.getProductosMasVendidos().get(0).getCantidadVendida());
    }

    private void assertBigDecimalEquals(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "Esperado " + esperado + " pero fue " + real);
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=ReporteVentasTest`
Expected: FALLA — nada de esto existe todavía.

- [ ] **Step 3: Agregar las queries a los repositorios**

En `PedidoRepository.java`:

```java
    List<Pedido> findByCreatedAtBetweenAndEstadoNotIn(LocalDateTime desde, LocalDateTime hasta, List<EstadoPedido> estadosExcluidos);
```

En `DetallePedidoRepository.java`, agregar la interfaz de proyección y la query:

```java
    interface ProductoMasVendidoProjection {
        String getNombre();
        Long getCantidadVendida();
    }

    @Query("SELECT d.productoNombre AS nombre, SUM(d.cantidad) AS cantidadVendida " +
           "FROM DetallePedido d " +
           "WHERE d.pedido.createdAt BETWEEN :desde AND :hasta " +
           "AND d.pedido.estado NOT IN :estadosExcluidos " +
           "GROUP BY d.productoNombre " +
           "ORDER BY SUM(d.cantidad) DESC")
    List<ProductoMasVendidoProjection> productosMasVendidos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estadosExcluidos") List<EstadoPedido> estadosExcluidos);
```

- [ ] **Step 4: Crear los DTOs**

```java
// ProductoMasVendidoDTO.java
package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoMasVendidoDTO {
    private String nombre;
    private Long cantidadVendida;
}
```

```java
// ReporteVentasDTO.java
package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteVentasDTO {
    private BigDecimal ventasTotales;
    private BigDecimal ticketPromedio;
    private Integer cantidadPedidos;
    private List<ProductoMasVendidoDTO> productosMasVendidos;
}
```

- [ ] **Step 5: Implementar `PedidoService.generarReporteVentas`**

```java
    private static final List<EstadoPedido> ESTADOS_NO_VENTA = List.of(EstadoPedido.PENDIENTE, EstadoPedido.PAGO_RECHAZADO);

    @Transactional(readOnly = true)
    public ReporteVentasDTO generarReporteVentas(LocalDateTime desde, LocalDateTime hasta) {
        List<Pedido> pedidosPagados = pedidoRepository.findByCreatedAtBetweenAndEstadoNotIn(desde, hasta, ESTADOS_NO_VENTA);

        BigDecimal ventasTotales = pedidosPagados.stream()
                .map(Pedido::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal ticketPromedio = pedidosPagados.isEmpty()
                ? BigDecimal.ZERO
                : ventasTotales.divide(BigDecimal.valueOf(pedidosPagados.size()), 2, java.math.RoundingMode.HALF_UP);

        List<ProductoMasVendidoDTO> productosMasVendidos = detallePedidoRepository
                .productosMasVendidos(desde, hasta, ESTADOS_NO_VENTA).stream()
                .map(p -> ProductoMasVendidoDTO.builder().nombre(p.getNombre()).cantidadVendida(p.getCantidadVendida()).build())
                .collect(java.util.stream.Collectors.toList());

        return ReporteVentasDTO.builder()
                .ventasTotales(ventasTotales)
                .ticketPromedio(ticketPromedio)
                .cantidadPedidos(pedidosPagados.size())
                .productosMasVendidos(productosMasVendidos)
                .build();
    }
```

- [ ] **Step 6: Agregar el endpoint**

En `PedidoController.java`:

```java
    @GetMapping("/admin/reportes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReporteVentasDTO> obtenerReporteVentas(
            @RequestParam(required = false) java.time.LocalDateTime desde,
            @RequestParam(required = false) java.time.LocalDateTime hasta) {
        java.time.LocalDateTime desdeEfectivo = desde != null ? desde : java.time.LocalDateTime.now().minusDays(30);
        java.time.LocalDateTime hastaEfectivo = hasta != null ? hasta : java.time.LocalDateTime.now();
        return ResponseEntity.ok(pedidoService.generarReporteVentas(desdeEfectivo, hastaEfectivo));
    }
```

- [ ] **Step 7: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=ReporteVentasTest`
Expected: PASS

- [ ] **Step 8: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/ecommerce/repository/PedidoRepository.java \
        backend/src/main/java/com/ecommerce/repository/DetallePedidoRepository.java \
        backend/src/main/java/com/ecommerce/dto/ReporteVentasDTO.java \
        backend/src/main/java/com/ecommerce/dto/ProductoMasVendidoDTO.java \
        backend/src/main/java/com/ecommerce/service/PedidoService.java \
        backend/src/main/java/com/ecommerce/controller/PedidoController.java \
        backend/src/test/java/com/ecommerce/service/ReporteVentasTest.java
git commit -m "feat: reporte de ventas por período y productos más vendidos (admin)"
```

---

### Task 7: Panel de reportes — frontend

**Files:**
- Modify: `frontend/src/services/api.js`
- Create: `frontend/src/pages/AdminReportes.jsx`
- Modify: `frontend/src/router.jsx`
- Modify: `frontend/src/pages/AdminPanel.jsx`

**Interfaces:**
- Produces: ruta `/admin/reportes`, enlazada desde `AdminPanel.jsx`.

- [ ] **Step 1: Agregar el cliente en `api.js`**

```js
  async getReporteVentas({ desde, hasta } = {}) {
    const params = new URLSearchParams()
    if (desde) params.set('desde', desde)
    if (hasta) params.set('hasta', hasta)
    return request(`/pedidos/admin/reportes?${params.toString()}`)
  },
```

- [ ] **Step 2: Crear `AdminReportes.jsx`**

```jsx
import { useState } from "react"
import { Link } from "react-router-dom"
import { useFetch } from "../hooks/useFetch"
import { api } from "../services/api"
import { formatPrice } from "../utils/formatters"
import LoadingSpinner from "../components/LoadingSpinner"
import { ArrowLeft, TrendingUp, ShoppingBag, DollarSign } from "lucide-react"

const AdminReportes = () => {
  const [desde, setDesde] = useState("")
  const [hasta, setHasta] = useState("")

  const { data: reporte, loading, error, refetch } = useFetch(
    () => api.getReporteVentas({ desde: desde || undefined, hasta: hasta || undefined }),
    [desde, hasta]
  )

  return (
    <div className="max-w-5xl mx-auto">
      <Link to="/admin" className="inline-flex items-center text-blue-600 dark:text-blue-400 mb-6">
        <ArrowLeft size={16} className="mr-1" /> Volver al panel
      </Link>
      <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-6">Reportes de Ventas</h1>

      <div className="flex gap-4 mb-6">
        <input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} className="input" />
        <input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} className="input" />
      </div>

      {loading && <LoadingSpinner size="lg" />}
      {error && <p className="text-red-600">Error al cargar el reporte: {error}</p>}

      {reporte && (
        <>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6 mb-8">
            <div className="card p-6 flex items-center gap-4">
              <DollarSign className="text-green-600" size={32} />
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Ventas totales</p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatPrice(reporte.ventasTotales)}</p>
              </div>
            </div>
            <div className="card p-6 flex items-center gap-4">
              <TrendingUp className="text-blue-600" size={32} />
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Ticket promedio</p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{formatPrice(reporte.ticketPromedio)}</p>
              </div>
            </div>
            <div className="card p-6 flex items-center gap-4">
              <ShoppingBag className="text-purple-600" size={32} />
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">Pedidos pagados</p>
                <p className="text-2xl font-bold text-gray-900 dark:text-white">{reporte.cantidadPedidos}</p>
              </div>
            </div>
          </div>

          <div className="card p-6">
            <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Productos más vendidos</h2>
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 dark:text-gray-400 border-b dark:border-gray-700">
                  <th className="py-2">Producto</th>
                  <th className="py-2 text-right">Unidades vendidas</th>
                </tr>
              </thead>
              <tbody>
                {reporte.productosMasVendidos.map((p) => (
                  <tr key={p.nombre} className="border-b dark:border-gray-700">
                    <td className="py-2 text-gray-900 dark:text-white">{p.nombre}</td>
                    <td className="py-2 text-right text-gray-900 dark:text-white">{p.cantidadVendida}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  )
}

export default AdminReportes
```

- [ ] **Step 3: Agregar la ruta**

En `router.jsx`, importar `AdminReportes` y agregar (junto a `/admin`):

```jsx
      <Route
        path="/admin/reportes"
        element={
          <ProtectedRoute>
            <Layout>
              <AdminReportes />
            </Layout>
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 4: Enlazar desde `AdminPanel.jsx`**

En `AdminPanel.jsx`, junto al ícono `BarChart3` ya importado (línea 8 del archivo original — hoy no se usa para nada visible, es un buen indicio de que esta sección se planeó y no se terminó de conectar), agregar un botón/link cerca del encabezado del panel:

```jsx
        <Link to="/admin/reportes" className="btn btn-secondary inline-flex items-center gap-2">
          <BarChart3 size={18} />
          Ver reportes de ventas
        </Link>
```

(agregar el import `import { Link } from "react-router-dom"` si `AdminPanel.jsx` no lo tiene ya — revisar el archivo real antes de aplicar).

- [ ] **Step 5: Verificación manual**

Run: con backend+frontend corriendo y sesión de admin, ir a `/admin`, click en "Ver reportes de ventas", confirmar que se muestran los tres KPIs y la tabla de productos más vendidos, y que cambiar las fechas dispara un nuevo fetch.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/services/api.js frontend/src/pages/AdminReportes.jsx frontend/src/router.jsx frontend/src/pages/AdminPanel.jsx
git commit -m "feat: panel de reportes de ventas para el admin"
```

---

## Resumen de verificación de la Fase 3

```bash
cd backend && mvnd test
```
Expected: todos los tests en verde.

Luego, manualmente: registrar un usuario nuevo y confirmar que llega el email de bienvenida (con SMTP de test configurado, ej. Mailtrap o Brevo sandbox), completar un pedido pagado y confirmar el email de confirmación, y revisar `/admin/reportes` con datos reales cargados.
