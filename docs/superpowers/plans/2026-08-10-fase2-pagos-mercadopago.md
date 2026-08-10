# Fase 2 — Checkout y pagos reales con Mercado Pago — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cobrar pedidos reales con Mercado Pago Checkout Pro, sin sobrevender stock en checkouts concurrentes y sin confiar en una notificación de pago sin verificar su firma.

**Architecture:** El pedido se crea (y el stock se reserva) igual que hoy, en el momento del `POST /api/pedidos` (ya implementado en la Fase 1). Se agrega un `PagoController` que crea una `Preference` de Mercado Pago para ese pedido y redirige al comprador al Checkout hosteado por Mercado Pago. Un webhook (`POST /api/pagos/webhook`) recibe la confirmación, valida su firma, y actualiza el estado del pedido: pago aprobado → pedido `CONFIRMADO`; pago rechazado/cancelado → pedido `PAGO_RECHAZADO` y el stock reservado se libera. Un job programado libera el stock de pedidos que quedan `PENDIENTE` sin ninguna confirmación de pago durante más de 30 minutos (usuario que abandona el checkout).

**Tech Stack:** Spring Boot 3.2, MP Java SDK (`com.mercadopago:sdk-java`), Spring `@Scheduled` (ya incluido en `spring-boot-starter`), React 18.

## Global Constraints

- Depende de la Fase 0 (secretos por variable de entorno, `@EnableMethodSecurity`, Flyway) y la Fase 1 (`ProductoVariante` con `@Version`, `DetallePedido.variante`) ya aplicadas.
- Ninguna credencial de Mercado Pago (access token, webhook secret) se commitea — mismo patrón de `${VAR}` sin default en prod que ya establece la Fase 0.
- El backend nunca recibe ni procesa número de tarjeta — todo pasa por el Checkout hosteado de Mercado Pago (Checkout Pro).
- **Verificar antes de implementar:** los nombres exactos de clases/métodos del SDK de Mercado Pago (`com.mercadopago:sdk-java`) usados en este plan están escritos según la forma estable conocida del SDK v2 al momento de escribir este plan, pero **hay que confirmarlos contra la documentación oficial** (`https://github.com/mercadopago/sdk-java` y `https://www.mercadopago.com.ar/developers/es/docs`) antes de codear el Task 4/5/6, porque el SDK puede haber cambiado de versión. Si algún nombre difiere, usar el equivalente real del SDK instalado — la estructura de los tests (qué se espera que pase) no cambia.

---

### Task 1: Entidad `Pago`

**Files:**
- Create: `backend/src/main/java/com/ecommerce/entity/Pago.java`
- Create: `backend/src/main/java/com/ecommerce/entity/EstadoPago.java`
- Create: `backend/src/main/java/com/ecommerce/repository/PagoRepository.java`
- Create: `backend/src/main/resources/db/migration/V6__pagos.sql`

**Interfaces:**
- Produces: `Pago{id, pedido, proveedor, preferenceId, paymentIdExterno, estado, monto, moneda, createdAt, updatedAt}`, `EstadoPago{PENDING, APPROVED, REJECTED, IN_PROCESS, REFUNDED}`, `PagoRepository.findTopByPedidoIdOrderByCreatedAtDesc(Long pedidoId)`, `PagoRepository.findByPreferenceId(String preferenceId)` — consumidos por el `PagoController` (Task 4, 5, 8).

- [ ] **Step 1: Crear `EstadoPago`**

```java
package com.ecommerce.entity;

public enum EstadoPago {
    PENDING,
    APPROVED,
    REJECTED,
    IN_PROCESS,
    REFUNDED
}
```

- [ ] **Step 2: Crear `Pago`**

```java
package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @Column(nullable = false)
    @Builder.Default
    private String proveedor = "MERCADOPAGO";

    @Column(name = "preference_id")
    private String preferenceId;

    @Column(name = "payment_id_externo")
    private String paymentIdExterno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoPago estado = EstadoPago.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    @Builder.Default
    private String moneda = "ARS";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Crear `PagoRepository`**

```java
package com.ecommerce.repository;

import com.ecommerce.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {
    Optional<Pago> findTopByPedidoIdOrderByCreatedAtDesc(Long pedidoId);
    Optional<Pago> findByPreferenceId(String preferenceId);
}
```

- [ ] **Step 4: Migración Flyway**

```sql
CREATE TABLE pagos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    proveedor VARCHAR(50) NOT NULL,
    preference_id VARCHAR(255),
    payment_id_externo VARCHAR(255),
    estado VARCHAR(20) NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    CONSTRAINT fk_pago_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id)
);

CREATE INDEX idx_pagos_pedido_id ON pagos(pedido_id);
CREATE INDEX idx_pagos_preference_id ON pagos(preference_id);
```

Agregar también en esta misma migración el nuevo estado de pedido que se usa en el Task 6 (no requiere `ALTER` porque `estado` en `pedidos`/`detalle_pedidos` es `VARCHAR`, no un `ENUM` de MySQL — el nuevo valor `PAGO_RECHAZADO` de `EstadoPedido` no necesita cambio de esquema, solo el cambio en el enum Java del Task 6).

- [ ] **Step 5: Compilar**

Run: `cd backend && mvnd compile`
Expected: compila sin errores (esta tarea no tiene lógica de negocio propia para testear todavía, se ejercita end-to-end en el Task 4).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/Pago.java \
        backend/src/main/java/com/ecommerce/entity/EstadoPago.java \
        backend/src/main/java/com/ecommerce/repository/PagoRepository.java \
        backend/src/main/resources/db/migration/V6__pagos.sql
git commit -m "feat: agregar entidad Pago para trackear transacciones de Mercado Pago"
```

---

### Task 2: Manejo correcto de conflictos de stock concurrentes (optimistic locking)

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/exception/GlobalExceptionHandler.java`
- Create: `backend/src/test/java/com/ecommerce/service/PedidoConcurrenciaTest.java`

**Interfaces:**
- Produces: `GlobalExceptionHandler` traduce `ObjectOptimisticLockingFailureException` (lanzada por Hibernate cuando dos transacciones pisan la misma `ProductoVariante` con `@Version`) a un 409 legible, en vez de un 500 genérico.

Este test es el que prueba de verdad que el `@Version` agregado a `ProductoVariante` en la Fase 1 cumple su propósito: dos pedidos simultáneos por el último talle disponible no pueden sobrevender.

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/service/PedidoConcurrenciaTest.java` como test de integración con DB real (H2), disparando dos transacciones concurrentes de verdad (no se puede simular con Mockito, que no ejecuta SQL):

```java
package com.ecommerce.service;

import com.ecommerce.dto.CreatePedidoDTO;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Concurrencia de stock en checkout")
class PedidoConcurrenciaTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository productoVarianteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long varianteId;
    private Long compradorId;

    @BeforeEach
    void setUp() {
        productoVarianteRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("V").apellido("V").username("vendedor2").email("vendedor2@test.com")
                .password("x").role(Role.USER).build());
        Usuario comprador = usuarioRepository.save(Usuario.builder()
                .nombre("C").apellido("C").username("comprador2").email("comprador2@test.com")
                .password("x").role(Role.USER).build());
        compradorId = comprador.getId();

        Producto producto = productoRepository.save(Producto.builder()
                .name("Última Camiseta").club("Boca Juniors").liga("LPA").temporada("2026")
                .tipo(TipoProducto.CAMISETA).price(new BigDecimal("50000")).ownerUser(vendedor)
                .createdAt(java.time.LocalDateTime.now()).build());
        ProductoVariante variante = productoVarianteRepository.save(ProductoVariante.builder()
                .producto(producto).talle("M").stock(1).sku("ULTIMA-M").build());
        varianteId = variante.getId();
    }

    @Test
    @DisplayName("Dos pedidos simultáneos por la última unidad: solo uno debería tener éxito")
    void testDosCheckoutsConcurrentes_SoloUnoGanaLaUltimaUnidad() throws InterruptedException {
        int intentos = 2;
        ExecutorService executor = Executors.newFixedThreadPool(intentos);
        CountDownLatch listos = new CountDownLatch(intentos);
        CountDownLatch arrancar = new CountDownLatch(1);
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);

        for (int i = 0; i < intentos; i++) {
            executor.submit(() -> {
                try {
                    listos.countDown();
                    arrancar.await();
                    CreatePedidoDTO dto = CreatePedidoDTO.builder()
                            .items(List.of(new CreatePedidoDTO.ItemCarritoDTO(varianteId, 1)))
                            .direccionEnvio("Calle Falsa 123").build();
                    pedidoService.crearPedido(compradorId, dto);
                    exitosos.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException | com.ecommerce.exception.StockInsuficienteException e) {
                    fallidos.incrementAndGet();
                } catch (Exception e) {
                    fallidos.incrementAndGet();
                } finally {
                    // no-op
                }
            });
        }

        listos.await();
        arrancar.countDown();
        executor.shutdown();
        executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertEquals(1, exitosos.get(), "Solo un checkout debería haber conseguido la última unidad");
        assertEquals(1, fallidos.get());

        ProductoVariante varianteFinal = productoVarianteRepository.findById(varianteId).orElseThrow();
        assertEquals(0, varianteFinal.getStock());
    }
}
```

- [ ] **Step 2: Correr el test**

Run: `cd backend && mvnd test -Dtest=PedidoConcurrenciaTest`
Expected: dependiendo del timing, puede pasar directamente (H2 + Hibernate ya aplican el `@Version` sin código adicional) o fallar de forma intermitente si `crearPedido` no está bien delimitado transaccionalmente. Si falla, es la señal real de que falta algo en el manejo transaccional de `PedidoService.crearPedido` (a la fecha de este plan, la clase ya es `@Transactional` a nivel de clase, así que debería pasar tal cual). Correrlo 3 veces seguidas para descartar flakiness:

Run: `cd backend && for i in 1 2 3; do mvnd test -Dtest=PedidoConcurrenciaTest; done`
Expected: PASS las 3 veces.

- [ ] **Step 3: Traducir la excepción a una respuesta HTTP clara**

En `backend/src/main/java/com/ecommerce/exception/GlobalExceptionHandler.java`, agregar (junto a los demás `@ExceptionHandler`):

```java
    @ExceptionHandler(org.springframework.orm.ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLocking(
            org.springframework.orm.ObjectOptimisticLockingFailureException ex) {

        Map<String, Object> error = new HashMap<>();
        error.put("timestamp", LocalDateTime.now());
        error.put("status", HttpStatus.CONFLICT.value());
        error.put("error", "Conflicto de stock");
        error.put("message", "Alguien se adelantó a comprar este talle. Volvé a intentar.");

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }
```

- [ ] **Step 4: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ecommerce/exception/GlobalExceptionHandler.java \
        backend/src/test/java/com/ecommerce/service/PedidoConcurrenciaTest.java
git commit -m "test: verificar que el optimistic locking evita sobreventa en checkouts concurrentes"
```

---

### Task 3: Configuración del SDK de Mercado Pago

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/ecommerce/config/MercadoPagoConfig.java`
- Modify: `backend/src/main/resources/application-dev.properties`
- Modify: `backend/src/main/resources/application-prod.properties`
- Modify: `backend/src/test/resources/application-test.properties`
- Create: `backend/src/main/java/com/ecommerce/controller/PagoConfigController.java`

**Interfaces:**
- Produces: SDK de Mercado Pago inicializado con el access token al arrancar (`@PostConstruct`, mismo patrón fail-fast que `JwtUtil` de la Fase 0).
- Produces: `GET /api/pagos/config` → `{ "publicKey": "..." }` (la única credencial de Mercado Pago que puede llegar al frontend).

- [ ] **Step 1: Agregar la dependencia**

En `backend/pom.xml`:

```xml
        <dependency>
            <groupId>com.mercadopago</groupId>
            <artifactId>sdk-java</artifactId>
            <version>2.1.29</version>
        </dependency>
```

(Verificar la última versión estable en Maven Central antes de fijarla: `https://mvnrepository.com/artifact/com.mercadopago/sdk-java`.)

- [ ] **Step 2: Crear la configuración**

```java
package com.ecommerce.config;

import com.mercadopago.MercadoPagoConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MercadoPagoConfiguration {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @PostConstruct
    void init() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "mercadopago.access-token no está configurado. Definí MERCADOPAGO_ACCESS_TOKEN.");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
    }
}
```

(Nombre de clase `MercadoPagoConfiguration` para no chocar con `com.mercadopago.MercadoPagoConfig`, importada en el mismo archivo.)

- [ ] **Step 3: Exponer la public key al frontend**

```java
package com.ecommerce.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoConfigController {

    @Value("${mercadopago.public-key}")
    private String publicKey;

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }
}
```

- [ ] **Step 4: Configuración por entorno**

En `backend/src/main/resources/application-prod.properties`, agregar (sin default, obligatorio):

```properties
# Mercado Pago
mercadopago.access-token=${MERCADOPAGO_ACCESS_TOKEN}
mercadopago.public-key=${MERCADOPAGO_PUBLIC_KEY}
mercadopago.webhook-secret=${MERCADOPAGO_WEBHOOK_SECRET}
app.frontend-url=${FRONTEND_URL}
app.backend-url=${BACKEND_URL}
```

En `backend/src/main/resources/application-dev.properties`, agregar (también por variable de entorno — Mercado Pago requiere credenciales de prueba reales, no hay un valor fijo válido para desarrollo; generarlas gratis en `https://www.mercadopago.com.ar/developers/panel/app`, cuenta de test):

```properties
# Mercado Pago (credenciales de TEST, generar en el panel de desarrolladores)
mercadopago.access-token=${MERCADOPAGO_ACCESS_TOKEN:}
mercadopago.public-key=${MERCADOPAGO_PUBLIC_KEY:}
mercadopago.webhook-secret=${MERCADOPAGO_WEBHOOK_SECRET:}
app.frontend-url=${FRONTEND_URL:http://localhost:5173}
app.backend-url=${BACKEND_URL:http://localhost:8081}
```

(En dev, si no se define `MERCADOPAGO_ACCESS_TOKEN`, `MercadoPagoConfiguration.init()` va a fallar el arranque — esto es intencional: nadie debería levantar el backend completo sin credenciales de test si va a tocar el flujo de pagos. Para trabajar en otras partes del backend sin Mercado Pago configurado, se documenta en el `README` correr con `mvnd spring-boot:run -Dspring-boot.run.profiles=dev` solo después de exportar esas tres variables con las credenciales de test.)

En `backend/src/test/resources/application-test.properties`, agregar credenciales dummy (el `MercadoPagoConfiguration` igual exige que no estén en blanco, pero los tests de este plan no llegan a pegarle a la API real de Mercado Pago):

```properties
mercadopago.access-token=TEST-dummy-access-token-para-tests
mercadopago.public-key=TEST-dummy-public-key-para-tests
mercadopago.webhook-secret=dummy-webhook-secret-para-tests
app.frontend-url=http://localhost:5173
app.backend-url=http://localhost:8081
```

- [ ] **Step 5: Verificar que el contexto arranca**

Run: `cd backend && mvnd test -Dtest=AdminControllerSecurityTest`
Expected: PASS (este test ya levanta el contexto completo de Spring vía `@SpringBootTest`; si `MercadoPagoConfiguration` estuviera mal, fallaría acá).

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/java/com/ecommerce/config/MercadoPagoConfiguration.java \
        backend/src/main/java/com/ecommerce/controller/PagoConfigController.java \
        backend/src/main/resources/application-dev.properties \
        backend/src/main/resources/application-prod.properties \
        backend/src/test/resources/application-test.properties
git commit -m "infra: configurar SDK de Mercado Pago con access token por variable de entorno"
```

---

### Task 4: Crear la preferencia de pago (`POST /api/pagos/pedidos/{pedidoId}/preferencia`)

**Files:**
- Create: `backend/src/main/java/com/ecommerce/service/PagoService.java`
- Create: `backend/src/main/java/com/ecommerce/controller/PagoController.java`
- Create: `backend/src/test/java/com/ecommerce/service/PagoServiceTest.java`
- Modify: `backend/src/main/java/com/ecommerce/config/MercadoPagoConfiguration.java` (agrega los beans `PreferenceClient`/`PaymentClient`)

**Interfaces:**
- Produces: `PagoService.crearPreferencia(Pedido pedido)` → `Pago` (con `preferenceId` seteado); `POST /api/pagos/pedidos/{pedidoId}/preferencia` → `{ "preferenceId": "...", "initPoint": "..." }`.
- Consumes: `Pago` (Task 1), `Pedido`/`DetallePedido` (Fase 1).

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/service/PagoServiceTest.java`. El `PreferenceClient` del SDK hace una llamada HTTP real — para no depender de la red en el test unitario, `PagoService` recibe el `PreferenceClient` inyectado como bean de Spring (mockeable), no instanciado con `new` dentro del método:

```java
package com.ecommerce.service;

import com.ecommerce.entity.*;
import com.ecommerce.repository.PagoRepository;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.resources.preference.Preference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - PagoService")
class PagoServiceTest {

    @Mock
    private PreferenceClient preferenceClient;

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private PagoService pagoService;

    @Test
    @DisplayName("Debería crear una preferencia de MP y guardar un Pago PENDING con su preferenceId")
    void testCrearPreferencia() throws Exception {
        Usuario comprador = Usuario.builder().id(1L).nombre("Juan").apellido("Pérez").email("juan@test.com").build();
        DetallePedido item = DetallePedido.builder()
                .productoNombre("Camiseta Boca").talle("M").cantidad(2)
                .precioUnitario(new BigDecimal("45000")).build();
        Pedido pedido = Pedido.builder().id(99L).usuario(comprador).total(new BigDecimal("90000"))
                .items(List.of(item)).build();

        Preference preferenceFake = new Preference();
        preferenceFake.setId("pref-123");
        preferenceFake.setInitPoint("https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123");

        when(preferenceClient.create(any())).thenReturn(preferenceFake);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        Pago pago = pagoService.crearPreferencia(pedido);

        assertEquals("pref-123", pago.getPreferenceId());
        assertEquals(EstadoPago.PENDING, pago.getEstado());
        assertEquals(new BigDecimal("90000"), pago.getMonto());
        assertEquals(pedido, pago.getPedido());
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PagoServiceTest`
Expected: FALLA — `PagoService` no existe.

- [ ] **Step 3: Implementar `PagoService`**

```java
package com.ecommerce.service;

import com.ecommerce.entity.DetallePedido;
import com.ecommerce.entity.EstadoPago;
import com.ecommerce.entity.Pago;
import com.ecommerce.entity.Pedido;
import com.ecommerce.repository.PagoRepository;
import com.mercadopago.client.preference.*;
import com.mercadopago.resources.preference.Preference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PagoService {

    @Autowired
    private PreferenceClient preferenceClient;

    @Autowired
    private PagoRepository pagoRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    public Pago crearPreferencia(Pedido pedido) {
        try {
            List<PreferenceItemRequest> items = pedido.getItems().stream()
                    .map(this::aItemDeMercadoPago)
                    .collect(Collectors.toList());

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(frontendUrl + "/checkout/resultado?pedidoId=" + pedido.getId())
                    .failure(frontendUrl + "/checkout/resultado?pedidoId=" + pedido.getId())
                    .pending(frontendUrl + "/checkout/resultado?pedidoId=" + pedido.getId())
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .autoReturn("approved")
                    .externalReference(pedido.getId().toString())
                    .notificationUrl(backendUrl + "/api/pagos/webhook")
                    .build();

            Preference preference = preferenceClient.create(request);

            Pago pago = Pago.builder()
                    .pedido(pedido)
                    .preferenceId(preference.getId())
                    .estado(EstadoPago.PENDING)
                    .monto(pedido.getTotal())
                    .build();

            return pagoRepository.save(pago);
        } catch (com.mercadopago.exceptions.MPException | com.mercadopago.exceptions.MPApiException e) {
            throw new IllegalStateException("Error al crear la preferencia de pago en Mercado Pago", e);
        }
    }

    public String obtenerInitPoint(String preferenceId) {
        try {
            Preference preference = preferenceClient.get(preferenceId);
            return preference.getInitPoint();
        } catch (com.mercadopago.exceptions.MPException | com.mercadopago.exceptions.MPApiException e) {
            throw new IllegalStateException("Error al obtener la preferencia de pago", e);
        }
    }

    private PreferenceItemRequest aItemDeMercadoPago(DetallePedido item) {
        return PreferenceItemRequest.builder()
                .title(item.getProductoNombre() + " (talle " + item.getTalle() + ")")
                .quantity(item.getCantidad())
                .unitPrice(BigDecimal.valueOf(item.getPrecioUnitario().doubleValue()))
                .currencyId("ARS")
                .build();
    }
}
```

Registrar el bean del cliente de Mercado Pago (no requiere `new` con argumentos, el SDK toma la config estática del Task 3):

```java
    // En MercadoPagoConfiguration.java, agregar:
    @org.springframework.context.annotation.Bean
    public com.mercadopago.client.preference.PreferenceClient preferenceClient() {
        return new com.mercadopago.client.preference.PreferenceClient();
    }

    @org.springframework.context.annotation.Bean
    public com.mercadopago.client.payment.PaymentClient paymentClient() {
        return new com.mercadopago.client.payment.PaymentClient();
    }
```

(El `paymentClient()` se usa recién en el Task 5, se declara acá para tener los dos beans juntos en la misma clase de configuración.)

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PagoServiceTest`
Expected: PASS

- [ ] **Step 5: Crear el endpoint en `PagoController`**

```java
package com.ecommerce.controller;

import com.ecommerce.entity.Pago;
import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.exception.PedidoNotFoundException;
import com.ecommerce.service.PagoService;
import com.ecommerce.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PedidoService pedidoService;

    @PostMapping("/pedidos/{pedidoId}/preferencia")
    public ResponseEntity<Map<String, String>> crearPreferencia(@PathVariable Long pedidoId,
                                                                  @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.obtenerPedidoPorId(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        if (!pedido.getUsuario().getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso sobre este pedido");
        }

        Pago pago = pagoService.crearPreferencia(pedido);
        String initPoint = pagoService.obtenerInitPoint(pago.getPreferenceId());

        return ResponseEntity.ok(Map.of("preferenceId", pago.getPreferenceId(), "initPoint", initPoint));
    }
}
```

- [ ] **Step 6: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ecommerce/service/PagoService.java \
        backend/src/main/java/com/ecommerce/controller/PagoController.java \
        backend/src/main/java/com/ecommerce/config/MercadoPagoConfiguration.java \
        backend/src/test/java/com/ecommerce/service/PagoServiceTest.java
git commit -m "feat: crear preferencia de pago de Mercado Pago para un pedido"
```

---

### Task 5: `EstadoPedido.PAGO_RECHAZADO` y liberación de stock al fallar el pago

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/entity/EstadoPedido.java`
- Modify: `backend/src/main/java/com/ecommerce/service/PedidoService.java`
- Modify: `backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java`

**Interfaces:**
- Produces: `EstadoPedido.PAGO_RECHAZADO` — nuevo valor de enum.
- Produces: `PedidoService.cancelarPedidoPorPagoRechazado(Long pedidoId)` — cancela el pedido y devuelve el stock a las variantes, sin requerir que el llamador sea el dueño del pedido (lo dispara el webhook, no el usuario).

- [ ] **Step 1: Agregar el valor al enum**

En `backend/src/main/java/com/ecommerce/entity/EstadoPedido.java`, agregar antes del cierre:

```java
    PAGO_RECHAZADO       // El pago fue rechazado o cancelado por la pasarela
```

- [ ] **Step 2: Escribir el test que falla**

Agregar a `backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java`:

```java
@Test
@DisplayName("Debería liberar el stock de la variante y marcar el pedido como PAGO_RECHAZADO")
void testCancelarPedidoPorPagoRechazado_LiberaStock() {
    ProductoVariante variante = ProductoVariante.builder().id(20L).talle("M").stock(2).sku("SKU-M").build();
    DetallePedido item = DetallePedido.builder().variante(variante).cantidad(3)
            .estadoItem(EstadoPedido.PENDIENTE).build();
    Pedido pedido = Pedido.builder().id(50L).estado(EstadoPedido.PENDIENTE).items(List.of(item)).build();

    when(pedidoRepository.findById(50L)).thenReturn(Optional.of(pedido));
    when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

    Pedido resultado = pedidoService.cancelarPedidoPorPagoRechazado(50L);

    assertEquals(5, variante.getStock()); // 2 + 3 devueltos
    assertEquals(EstadoPedido.PAGO_RECHAZADO, resultado.getEstado());
    assertEquals(EstadoPedido.PAGO_RECHAZADO, item.getEstadoItem());
}
```

- [ ] **Step 3: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: FALLA — `cancelarPedidoPorPagoRechazado` no existe.

- [ ] **Step 4: Implementar el método**

En `PedidoService.java`:

```java
    public Pedido cancelarPedidoPorPagoRechazado(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            // El pedido ya salió de PENDIENTE por otra vía (cancelado por el usuario,
            // o un webhook duplicado) — no se vuelve a tocar el stock.
            return pedido;
        }

        for (DetallePedido detalle : pedido.getItems()) {
            if (detalle.getEstadoItem() == EstadoPedido.PENDIENTE) {
                ProductoVariante variante = detalle.getVariante();
                if (variante != null) {
                    variante.setStock(variante.getStock() + detalle.getCantidad());
                    productoVarianteRepository.save(variante);
                }
                detalle.setEstadoItem(EstadoPedido.PAGO_RECHAZADO);
            }
        }

        pedido.setEstado(EstadoPedido.PAGO_RECHAZADO);
        pedido.setUpdatedAt(LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }
```

- [ ] **Step 5: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/EstadoPedido.java \
        backend/src/main/java/com/ecommerce/service/PedidoService.java \
        backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java
git commit -m "feat: liberar stock automáticamente cuando Mercado Pago rechaza un pago"
```

---

### Task 6: Confirmar pago aprobado — `PedidoService.confirmarPago`

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/service/PedidoService.java`
- Modify: `backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java`

**Interfaces:**
- Produces: `PedidoService.confirmarPago(Long pedidoId)` — mueve el pedido y sus items `PENDIENTE` a `CONFIRMADO` (reutiliza la transición ya válida en `validarTransicionEstado`).

- [ ] **Step 1: Escribir el test que falla**

```java
@Test
@DisplayName("Debería confirmar el pedido y sus items cuando el pago es aprobado")
void testConfirmarPago_MueveAConfirmado() {
    DetallePedido item = DetallePedido.builder().cantidad(1).estadoItem(EstadoPedido.PENDIENTE).build();
    Pedido pedido = Pedido.builder().id(60L).estado(EstadoPedido.PENDIENTE).items(List.of(item)).build();

    when(pedidoRepository.findById(60L)).thenReturn(Optional.of(pedido));
    when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

    Pedido resultado = pedidoService.confirmarPago(60L);

    assertEquals(EstadoPedido.CONFIRMADO, resultado.getEstado());
    assertEquals(EstadoPedido.CONFIRMADO, item.getEstadoItem());
}

@Test
@DisplayName("No debería tocar un pedido que ya salió de PENDIENTE (webhook duplicado)")
void testConfirmarPago_PedidoYaNoPendiente_NoHaceNada() {
    Pedido pedido = Pedido.builder().id(61L).estado(EstadoPedido.CONFIRMADO).items(List.of()).build();
    when(pedidoRepository.findById(61L)).thenReturn(Optional.of(pedido));

    Pedido resultado = pedidoService.confirmarPago(61L);

    assertEquals(EstadoPedido.CONFIRMADO, resultado.getEstado());
    verify(pedidoRepository, never()).save(any(Pedido.class));
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: FALLA — `confirmarPago` no existe.

- [ ] **Step 3: Implementar**

```java
    public Pedido confirmarPago(Long pedidoId) {
        Pedido pedido = pedidoRepository.findById(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            return pedido;
        }

        for (DetallePedido detalle : pedido.getItems()) {
            if (detalle.getEstadoItem() == EstadoPedido.PENDIENTE) {
                detalle.setEstadoItem(EstadoPedido.CONFIRMADO);
            }
        }

        pedido.setEstado(EstadoPedido.CONFIRMADO);
        pedido.setUpdatedAt(LocalDateTime.now());
        return pedidoRepository.save(pedido);
    }
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/ecommerce/service/PedidoService.java \
        backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java
git commit -m "feat: confirmar pedido y sus items cuando Mercado Pago aprueba el pago"
```

---

### Task 7: Webhook de Mercado Pago con validación de firma

**Files:**
- Create: `backend/src/main/java/com/ecommerce/security/MercadoPagoSignatureValidator.java`
- Create: `backend/src/test/java/com/ecommerce/security/MercadoPagoSignatureValidatorTest.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/PagoController.java`
- Modify: `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`
- Create: `backend/src/test/java/com/ecommerce/controller/PagoWebhookTest.java`

**Interfaces:**
- Produces: `MercadoPagoSignatureValidator.esValida(String xSignature, String xRequestId, String dataId, String secret)` — implementa el esquema de firma documentado por Mercado Pago (manifest `id:{dataId};request-id:{xRequestId};ts:{ts};` firmado con HMAC-SHA256, comparado contra el segmento `v1` del header `x-signature`).
- Produces: `POST /api/pagos/webhook` público (sin JWT — Mercado Pago no manda un Bearer token, autentica por firma) pero **no** por eso desprotegido: sin firma válida, 401 inmediato, no se procesa nada.

- [ ] **Step 1: Escribir el test que falla — validador de firma**

Crear `backend/src/test/java/com/ecommerce/security/MercadoPagoSignatureValidatorTest.java`:

```java
package com.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests Unitarios - MercadoPagoSignatureValidator")
class MercadoPagoSignatureValidatorTest {

    private final MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator();
    private static final String SECRET = "un-secreto-de-prueba";

    @Test
    @DisplayName("Debería validar una firma calculada correctamente")
    void testFirmaValida() throws Exception {
        String dataId = "123456";
        String requestId = "req-abc";
        String ts = "1700000000";
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256"));
        byte[] hash = mac.doFinal(manifest.getBytes());
        String v1 = bytesToHex(hash);

        String xSignature = "ts=" + ts + ",v1=" + v1;

        assertTrue(validator.esValida(xSignature, requestId, dataId, SECRET));
    }

    @Test
    @DisplayName("Debería rechazar una firma alterada")
    void testFirmaInvalida() {
        String xSignature = "ts=1700000000,v1=firma-totalmente-inventada";
        assertFalse(validator.esValida(xSignature, "req-abc", "123456", SECRET));
    }

    @Test
    @DisplayName("Debería rechazar un header x-signature ausente")
    void testFirmaNula() {
        assertFalse(validator.esValida(null, "req-abc", "123456", SECRET));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=MercadoPagoSignatureValidatorTest`
Expected: FALLA — la clase no existe.

- [ ] **Step 3: Implementar el validador**

```java
package com.ecommerce.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class MercadoPagoSignatureValidator {

    public boolean esValida(String xSignature, String xRequestId, String dataId, String secret) {
        if (xSignature == null || xRequestId == null || dataId == null) {
            return false;
        }

        Map<String, String> partes = parsear(xSignature);
        String ts = partes.get("ts");
        String v1Recibido = partes.get("v1");
        if (ts == null || v1Recibido == null) {
            return false;
        }

        String manifest = "id:" + dataId + ";request-id:" + xRequestId + ";ts:" + ts + ";";

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String v1Calculado = bytesToHex(hash);
            return java.security.MessageDigest.isEqual(
                    v1Calculado.getBytes(StandardCharsets.UTF_8),
                    v1Recibido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> parsear(String xSignature) {
        Map<String, String> partes = new HashMap<>();
        for (String segmento : xSignature.split(",")) {
            String[] kv = segmento.split("=", 2);
            if (kv.length == 2) {
                partes.put(kv[0].trim(), kv[1].trim());
            }
        }
        return partes;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=MercadoPagoSignatureValidatorTest`
Expected: PASS

- [ ] **Step 5: Escribir el test de integración del endpoint webhook (que falla)**

Crear `backend/src/test/java/com/ecommerce/controller/PagoWebhookTest.java`:

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
@DisplayName("Tests de Integración - Webhook de Mercado Pago")
class PagoWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Debería rechazar un webhook sin firma válida")
    void testWebhook_SinFirmaValida_Rechazado() throws Exception {
        String body = """
                {"action":"payment.updated","data":{"id":"123456"}}
                """;

        mockMvc.perform(post("/api/pagos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1700000000,v1=firma-invalida")
                        .header("x-request-id", "req-test")
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 6: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PagoWebhookTest`
Expected: FALLA — el endpoint `/api/pagos/webhook` no existe todavía (404, no 401).

- [ ] **Step 7: Implementar el endpoint en `PagoController`**

```java
    @Autowired
    private MercadoPagoSignatureValidator signatureValidator;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private com.mercadopago.client.payment.PaymentClient paymentClient;

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> data = (Map<String, Object>) body.get("data");
        String dataId = data != null ? String.valueOf(data.get("id")) : null;

        if (!signatureValidator.esValida(xSignature, xRequestId, dataId, webhookSecret)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }

        try {
            com.mercadopago.resources.payment.Payment payment = paymentClient.get(Long.parseLong(dataId));
            Long pedidoId = Long.valueOf(payment.getExternalReference());

            Pago pago = pagoRepository.findTopByPedidoIdOrderByCreatedAtDesc(pedidoId)
                    .orElseThrow(() -> new IllegalStateException("No hay Pago registrado para el pedido " + pedidoId));

            pago.setPaymentIdExterno(String.valueOf(payment.getId()));

            String status = payment.getStatus(); // "approved" | "rejected" | "pending" | "in_process" | "cancelled"
            switch (status) {
                case "approved" -> {
                    pago.setEstado(com.ecommerce.entity.EstadoPago.APPROVED);
                    pedidoService.confirmarPago(pedidoId);
                }
                case "rejected", "cancelled" -> {
                    pago.setEstado(com.ecommerce.entity.EstadoPago.REJECTED);
                    pedidoService.cancelarPedidoPorPagoRechazado(pedidoId);
                }
                default -> pago.setEstado(com.ecommerce.entity.EstadoPago.IN_PROCESS);
            }
            pagoRepository.save(pago);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Devolver 200 igual evita que Mercado Pago reintente indefinidamente un webhook
            // que nunca va a poder procesarse (ej. pedido borrado); loggear para revisión manual.
            org.slf4j.LoggerFactory.getLogger(PagoController.class)
                    .error("Error procesando webhook de Mercado Pago para dataId={}", dataId, e);
            return ResponseEntity.ok().build();
        }
    }
```

- [ ] **Step 8: Permitir la ruta pública en `SecurityConfig`**

En `SecurityConfig.securityFilterChain`, agregar antes del catch-all:

```java
                .requestMatchers("/api/pagos/webhook", "/api/pagos/config").permitAll()
```

(El webhook no lleva JWT — Mercado Pago no tiene el token de un usuario — así que su seguridad depende exclusivamente de la validación de firma del Step 7, no de Spring Security.)

- [ ] **Step 9: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PagoWebhookTest`
Expected: PASS

- [ ] **Step 10: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/ecommerce/security/MercadoPagoSignatureValidator.java \
        backend/src/test/java/com/ecommerce/security/MercadoPagoSignatureValidatorTest.java \
        backend/src/main/java/com/ecommerce/controller/PagoController.java \
        backend/src/main/java/com/ecommerce/config/SecurityConfig.java \
        backend/src/test/java/com/ecommerce/controller/PagoWebhookTest.java
git commit -m "feat: webhook de Mercado Pago con validación de firma HMAC obligatoria"
```

---

### Task 8: Endpoint de estado de pago para el frontend

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/controller/PagoController.java`

**Interfaces:**
- Produces: `GET /api/pagos/pedidos/{pedidoId}/estado` → `{ "estadoPago": "PENDING|APPROVED|REJECTED|IN_PROCESS", "estadoPedido": "PENDIENTE|CONFIRMADO|PAGO_RECHAZADO" }` — usado por el frontend (Task 11) para hacer polling después del redirect de vuelta desde Mercado Pago.

- [ ] **Step 1: Agregar el endpoint**

```java
    @GetMapping("/pedidos/{pedidoId}/estado")
    public ResponseEntity<Map<String, String>> obtenerEstado(@PathVariable Long pedidoId,
                                                               @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.obtenerPedidoPorId(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        if (!pedido.getUsuario().getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso sobre este pedido");
        }

        Pago pago = pagoRepository.findTopByPedidoIdOrderByCreatedAtDesc(pedidoId)
                .orElseThrow(() -> new IllegalStateException("No hay pago registrado para este pedido"));

        return ResponseEntity.ok(Map.of(
                "estadoPago", pago.getEstado().name(),
                "estadoPedido", pedido.getEstado().name()
        ));
    }
```

- [ ] **Step 2: Test manual (no amerita un test dedicado — es una lectura simple ya cubierta por los tests de `PagoService`/`PedidoService`)**

Run: `cd backend && mvnd test`
Expected: PASS (sin regresiones)

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/ecommerce/controller/PagoController.java
git commit -m "feat: endpoint de consulta de estado de pago para polling desde el frontend"
```

---

### Task 9: Job programado — expirar pedidos `PENDIENTE` sin pago tras 30 minutos

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/EcommerceBackendApplication.java`
- Create: `backend/src/main/java/com/ecommerce/service/PedidoExpiracionJob.java`
- Create: `backend/src/test/java/com/ecommerce/service/PedidoExpiracionJobTest.java`
- Modify: `backend/src/main/resources/application-prod.properties`
- Modify: `backend/src/main/resources/application-dev.properties`

**Interfaces:**
- Produces: `PedidoExpiracionJob.expirarPedidosVencidos()` — corre cada 5 minutos, cancela (libera stock de) todo `Pedido` en `PENDIENTE` creado hace más de `pedidos.expiracion-minutos` minutos.

- [ ] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/service/PedidoExpiracionJobTest.java`:

```java
package com.ecommerce.service;

import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.repository.PedidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - PedidoExpiracionJob")
class PedidoExpiracionJobTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoService pedidoService;

    @InjectMocks
    private PedidoExpiracionJob job;

    @Test
    @DisplayName("Debería cancelar por pago rechazado cada pedido PENDIENTE vencido")
    void testExpirarPedidosVencidos_CancelaLosVencidos() {
        Pedido vencido1 = Pedido.builder().id(1L).estado(EstadoPedido.PENDIENTE)
                .createdAt(LocalDateTime.now().minusMinutes(45)).build();
        Pedido vencido2 = Pedido.builder().id(2L).estado(EstadoPedido.PENDIENTE)
                .createdAt(LocalDateTime.now().minusMinutes(31)).build();

        when(pedidoRepository.findByEstadoAndCreatedAtBefore(eq(EstadoPedido.PENDIENTE), any()))
                .thenReturn(List.of(vencido1, vencido2));

        job.expirarPedidosVencidos();

        verify(pedidoService).cancelarPedidoPorPagoRechazado(1L);
        verify(pedidoService).cancelarPedidoPorPagoRechazado(2L);
    }
}
```

(Agregar el import `static org.mockito.ArgumentMatchers.any;`.)

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PedidoExpiracionJobTest`
Expected: FALLA — `PedidoExpiracionJob` no existe, y `PedidoRepository.findByEstadoAndCreatedAtBefore` tampoco.

- [ ] **Step 3: Agregar el método al repositorio**

En `backend/src/main/java/com/ecommerce/repository/PedidoRepository.java`, agregar:

```java
    List<Pedido> findByEstadoAndCreatedAtBefore(EstadoPedido estado, LocalDateTime limite);
```

- [ ] **Step 4: Implementar el job**

```java
package com.ecommerce.service;

import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class PedidoExpiracionJob {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoService pedidoService;

    @Value("${pedidos.expiracion-minutos:30}")
    private int expiracionMinutos;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void expirarPedidosVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(expiracionMinutos);
        pedidoRepository.findByEstadoAndCreatedAtBefore(EstadoPedido.PENDIENTE, limite)
                .forEach(pedido -> pedidoService.cancelarPedidoPorPagoRechazado(pedido.getId()));
    }
}
```

- [ ] **Step 5: Habilitar `@Scheduled` en la aplicación**

En `backend/src/main/java/com/ecommerce/EcommerceBackendApplication.java`, agregar `@EnableScheduling`:

```java
@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class EcommerceBackendApplication {
```

- [ ] **Step 6: Configurar el valor por entorno**

En `application-prod.properties` y `application-dev.properties`, agregar:

```properties
pedidos.expiracion-minutos=30
```

- [ ] **Step 7: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PedidoExpiracionJobTest`
Expected: PASS

- [ ] **Step 8: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/ecommerce/EcommerceBackendApplication.java \
        backend/src/main/java/com/ecommerce/service/PedidoExpiracionJob.java \
        backend/src/test/java/com/ecommerce/service/PedidoExpiracionJobTest.java \
        backend/src/main/java/com/ecommerce/repository/PedidoRepository.java \
        backend/src/main/resources/application-prod.properties \
        backend/src/main/resources/application-dev.properties
git commit -m "feat: job programado libera stock de pedidos PENDIENTE sin pago tras 30 minutos"
```

---

### Task 10: `api.js` — integrar el flujo de pago

**Files:**
- Modify: `frontend/src/services/api.js`

- [ ] **Step 1: Agregar los métodos de pago**

```js
  // ===== PAGOS (MERCADO PAGO) =====

  async crearPreferenciaPago(pedidoId) {
    return request(`/pagos/pedidos/${pedidoId}/preferencia`, { method: 'POST' })
  },

  async getEstadoPago(pedidoId) {
    return request(`/pagos/pedidos/${pedidoId}/estado`)
  },
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/services/api.js
git commit -m "feat: agregar cliente de pagos (crear preferencia, consultar estado) en api.js"
```

---

### Task 11: `Cart.jsx` redirige a Mercado Pago; nueva página `CheckoutResultado.jsx`

**Files:**
- Modify: `frontend/src/pages/Cart.jsx`
- Create: `frontend/src/pages/CheckoutResultado.jsx`
- Modify: `frontend/src/router.jsx`
- Modify: `frontend/src/pages/OrderDetail.jsx`

**Interfaces:**
- Produces: ruta `/checkout/resultado?pedidoId=` — destino de los tres `back_urls` (success/failure/pending) configurados en `PagoService.crearPreferencia` (Task 4).

- [ ] **Step 1: Actualizar `handleConfirmCheckout` en `Cart.jsx`**

Reemplazar el cuerpo de `handleConfirmCheckout` (líneas 47-67 del archivo original):

```jsx
  const handleConfirmCheckout = async () => {
    setIsCheckingOut(true)
    try {
      const order = await checkout({
        address: shippingAddress,
        notes: notes
      })

      const { initPoint } = await api.crearPreferenciaPago(order.id)
      window.location.href = initPoint
    } catch (err) {
      error(err.message || "Error al procesar la compra")
      setIsCheckingOut(false)
    }
  }
```

(No hay `finally` con `setIsCheckingOut(false)` en el camino feliz porque la página navega afuera del dominio hacia Mercado Pago — no hay vuelta a este componente montado.)

- [ ] **Step 2: Crear `CheckoutResultado.jsx`**

```jsx
import { useEffect, useState } from "react"
import { useSearchParams, Link, useNavigate } from "react-router-dom"
import { api } from "../services/api"
import LoadingSpinner from "../components/LoadingSpinner"
import { CheckCircle, XCircle, Clock } from "lucide-react"

const POLL_INTERVAL_MS = 3000
const POLL_MAX_INTENTOS = 20

const CheckoutResultado = () => {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const pedidoId = searchParams.get("pedidoId")
  const [estado, setEstado] = useState(null)
  const [intentos, setIntentos] = useState(0)

  useEffect(() => {
    if (!pedidoId) return
    let cancelado = false

    const consultar = async () => {
      try {
        const resultado = await api.getEstadoPago(pedidoId)
        if (cancelado) return
        setEstado(resultado)

        if (resultado.estadoPago === "PENDING" || resultado.estadoPago === "IN_PROCESS") {
          setIntentos((prev) => prev + 1)
        }
      } catch {
        if (!cancelado) setEstado({ estadoPago: "ERROR", estadoPedido: "ERROR" })
      }
    }

    consultar()
    const interval = setInterval(() => {
      if (intentos >= POLL_MAX_INTENTOS) {
        clearInterval(interval)
        return
      }
      consultar()
    }, POLL_INTERVAL_MS)

    return () => {
      cancelado = true
      clearInterval(interval)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pedidoId, intentos])

  if (!pedidoId) {
    return <p className="text-center py-12">Falta el número de pedido.</p>
  }

  if (!estado || estado.estadoPago === "PENDING" || estado.estadoPago === "IN_PROCESS") {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <LoadingSpinner size="lg" />
        <p className="mt-4 text-gray-600 dark:text-gray-400">Confirmando tu pago con Mercado Pago...</p>
      </div>
    )
  }

  if (estado.estadoPago === "APPROVED") {
    return (
      <div className="max-w-md mx-auto text-center py-16">
        <CheckCircle className="mx-auto h-16 w-16 text-green-600 mb-4" />
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">¡Pago aprobado!</h1>
        <p className="text-gray-600 dark:text-gray-400 mb-6">Tu pedido #{pedidoId} fue confirmado.</p>
        <button onClick={() => navigate(`/orders/${pedidoId}`)} className="btn btn-primary">
          Ver mi pedido
        </button>
      </div>
    )
  }

  return (
    <div className="max-w-md mx-auto text-center py-16">
      <XCircle className="mx-auto h-16 w-16 text-red-600 mb-4" />
      <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">El pago no se completó</h1>
      <p className="text-gray-600 dark:text-gray-400 mb-6">
        Tu pedido #{pedidoId} fue cancelado y el stock quedó liberado. Podés volver a intentarlo.
      </p>
      <Link to="/" className="btn btn-primary">Volver al catálogo</Link>
    </div>
  )
}

export default CheckoutResultado
```

- [ ] **Step 3: Agregar la ruta**

En `frontend/src/router.jsx`, agregar el import y la ruta (protegida, junto a `/cart`):

```jsx
import CheckoutResultado from "./pages/CheckoutResultado"
```

```jsx
      <Route
        path="/checkout/resultado"
        element={
          <ProtectedRoute>
            <Layout>
              <CheckoutResultado />
            </Layout>
          </ProtectedRoute>
        }
      />
```

- [ ] **Step 4: Agregar el nuevo estado a `OrderDetail.jsx`**

En `getStatusConfig` (líneas 27-86 del archivo original), agregar la entrada para el nuevo estado:

```js
      PAGO_RECHAZADO: {
        color: "bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400",
        icon: XCircle,
        text: "Pago rechazado",
        description: "El pago fue rechazado y el stock fue liberado"
      },
```

y en el bloque "Cancel Order Button" (línea 265 del archivo original, `{order.estado === "PENDIENTE" && (`), no cambia — un pedido en `PAGO_RECHAZADO` ya no admite cancelación manual porque ya está en un estado terminal.

- [ ] **Step 5: Verificación manual con credenciales de test de Mercado Pago**

Con `MERCADOPAGO_ACCESS_TOKEN`/`MERCADOPAGO_PUBLIC_KEY` de una cuenta de test (panel de desarrolladores de Mercado Pago) configuradas, y backend + frontend corriendo:
1. Agregar un producto al carrito, ir a "Finalizar Compra", confirmar — debería redirigir a `mercadopago.com.ar/checkout/...`.
2. Pagar con una tarjeta de test de aprobación (Mercado Pago provee números de tarjeta de prueba en su documentación de sandbox) — debería volver a `/checkout/resultado` y mostrar "¡Pago aprobado!" en unos segundos.
3. Repetir con una tarjeta de test de rechazo — debería mostrar "El pago no se completó" y el stock del talle comprado debería volver a su valor original (`GET /api/productos/{id}` antes/después).

Para que el webhook llegue durante el desarrollo local (Mercado Pago necesita una URL pública, no `localhost`), exponer el backend con una herramienta de túnel (ej. `ngrok http 8081`) y setear `BACKEND_URL` a esa URL pública antes de crear la preferencia.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Cart.jsx frontend/src/pages/CheckoutResultado.jsx frontend/src/router.jsx frontend/src/pages/OrderDetail.jsx
git commit -m "feat: redirigir a Mercado Pago Checkout Pro y mostrar el resultado del pago"
```

---

## Resumen de verificación de la Fase 2

```bash
cd backend && mvnd test
```
Expected: todos los tests en verde.

Luego, el checklist manual completo del Task 11 Step 5 contra credenciales de test reales de Mercado Pago — es la única forma de confirmar que la integración end-to-end (preferencia → checkout → webhook → confirmación) funciona, porque el SDK de Mercado Pago no se puede simular de forma confiable en tests unitarios.
