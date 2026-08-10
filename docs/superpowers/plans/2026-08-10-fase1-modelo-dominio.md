# Fase 1 — Modelo de dominio de camisetas de fútbol — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Transformar el catálogo genérico actual (`Producto` con un `stock` agregado) en un catálogo real de camisetas/shorts de fútbol con club, liga, temporada, tipo y stock por talle, y propagar ese cambio al carrito, al pedido y al panel de vendedor.

**Architecture:** Se agrega una nueva entidad `ProductoVariante` (talle + stock + SKU) hija de `Producto`, se elimina el campo `stock` agregado de `Producto` (fuente única de verdad = suma de variantes), y `DetallePedido` pasa a referenciar la variante comprada. El filtrado de catálogo se resuelve con Spring Data JPA Specifications para combinar filtros opcionales (club/liga/talle/tipo/precio) sin explotar en decenas de métodos de repositorio. En el frontend, el carrito pasa a identificar sus líneas por variante (producto + talle), no solo por producto.

**Tech Stack:** Mismo stack de la Fase 0 (Spring Boot 3.2/MySQL/Flyway ya introducido, React 18/Vite). Se agrega `spring-boot-starter-data-jpa` Specifications (ya incluido en el starter existente, sin dependencia nueva).

## Global Constraints

- Depende de que la Fase 0 esté aplicada: Flyway ya activo (`spring.flyway.enabled=true`, `ddl-auto=validate` en prod), `@EnableMethodSecurity` habilitado, controllers ya usando `@AuthenticationPrincipal Usuario`.
- Toda migración de esquema nueva es un archivo Flyway `V{n}__descripcion.sql` en `backend/src/main/resources/db/migration/`, nunca un cambio directo a mano en una base existente.
- Sin capas de compatibilidad: `Producto.stock` se elimina, no se deja en paralelo "por las dudas" (`AGENTS.md`).
- Estilo de tests: JUnit 5 + Mockito, `@DisplayName` en español, igual que el resto del proyecto; para lo que toca `@PreAuthorize`/contexto Spring se usa el patrón `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")` introducido en la Fase 0.
- Talle se modela como `String` libre (no enum Java) para no requerir una migración de esquema cada vez que se agregue un talle nuevo (ej. talles de niño); `tipo` (CAMISETA/SHORT) sí es un enum Java porque es un conjunto cerrado, siguiendo el mismo patrón que `Role`/`EstadoPedido` ya existentes en el proyecto.

---

### Task 1: Extender `Producto` con club, liga, temporada y tipo

**Files:**
- Create: `backend/src/main/java/com/ecommerce/entity/TipoProducto.java`
- Modify: `backend/src/main/java/com/ecommerce/entity/Producto.java`
- Modify: `backend/src/main/java/com/ecommerce/dto/ProductoDTO.java`
- Create: `backend/src/main/resources/db/migration/V2__producto_camisetas_futbol.sql`
- Modify: `backend/src/test/java/com/ecommerce/service/ProductoServiceTest.java`

**Interfaces:**
- Produces: `TipoProducto` enum (`CAMISETA`, `SHORT`) — consumido por `Producto`, `ProductoDTO` y el filtro de catálogo del Task 5.
- Produces: campos `Producto.club/liga/temporada/tipo` — consumidos por el frontend (Task 8, Task 9) y el filtro de catálogo (Task 5).

- [x] **Step 1: Escribir el test que falla**

En `backend/src/test/java/com/ecommerce/service/ProductoServiceTest.java`, agregar (siguiendo el patrón `@Mock`/`@InjectMocks` que ya usa la clase):

```java
@Test
@DisplayName("Debería crear un producto con club, liga, temporada y tipo")
void testCrearProducto_ConDatosDeCamiseta() {
    Producto nuevo = Producto.builder()
            .name("Camiseta Titular 2026")
            .club("Boca Juniors")
            .liga("Liga Profesional Argentina")
            .temporada("2026")
            .tipo(TipoProducto.CAMISETA)
            .price(new BigDecimal("45000"))
            .build();

    when(usuarioService.findById(1L)).thenReturn(vendedor);
    when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

    Producto creado = productoService.crearProducto(nuevo, 1L);

    assertEquals("Boca Juniors", creado.getClub());
    assertEquals("Liga Profesional Argentina", creado.getLiga());
    assertEquals("2026", creado.getTemporada());
    assertEquals(TipoProducto.CAMISETA, creado.getTipo());
}
```

(Revisar el `setUp()` existente de la clase para reusar el mock `vendedor`/`usuarioService` ya definido; si el nombre difiere, ajustar la referencia.)

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=ProductoServiceTest`
Expected: FALLA — no compila, `Producto` no tiene `club`/`liga`/`temporada`/`tipo` ni existe `TipoProducto`.

- [x] **Step 3: Crear el enum `TipoProducto`**

```java
package com.ecommerce.entity;

public enum TipoProducto {
    CAMISETA,
    SHORT
}
```

- [x] **Step 4: Extender `Producto`**

En `backend/src/main/java/com/ecommerce/entity/Producto.java`, agregar los campos después de `stock` (línea 31) y quitar el campo `stock` (línea 30-31) — el stock pasa a vivir únicamente en `ProductoVariante` (Task 2):

```java
    @Column(nullable = false)
    private String club;

    @Column(nullable = false)
    private String liga;

    @Column(nullable = false)
    private String temporada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoProducto tipo;
```

Quitar también el constructor auxiliar `Producto(String name, BigDecimal price)` (líneas 54-60) que asignaba `this.stock = 0` — ya no compila sin el campo `stock`, y no tiene otro uso (verificar con `grep -rn "new Producto(" backend/src/main/java` antes de borrarlo; a la fecha de este plan no hay otros usos).

- [x] **Step 5: Actualizar `ProductoDTO`**

En `backend/src/main/java/com/ecommerce/dto/ProductoDTO.java`, quitar el campo `stock` (se resuelve en el Task 2 como suma de variantes) y agregar:

```java
    private String club;
    private String liga;
    private String temporada;
    private com.ecommerce.entity.TipoProducto tipo;
```

y en el constructor de mapeo:

```java
        this.club = producto.getClub();
        this.liga = producto.getLiga();
        this.temporada = producto.getTemporada();
        this.tipo = producto.getTipo();
```

(El campo `stock` del DTO se retoma en el Task 2 como propiedad calculada.)

- [x] **Step 6: Migración Flyway**

Crear `backend/src/main/resources/db/migration/V2__producto_camisetas_futbol.sql`:

```sql
ALTER TABLE productos
    ADD COLUMN club VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN liga VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN temporada VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'CAMISETA';

ALTER TABLE productos ALTER COLUMN club DROP DEFAULT;
ALTER TABLE productos ALTER COLUMN liga DROP DEFAULT;
ALTER TABLE productos ALTER COLUMN temporada DROP DEFAULT;
ALTER TABLE productos ALTER COLUMN tipo DROP DEFAULT;
```

(Los `DEFAULT` transitorios existen solo para no romper filas ya cargadas en una base con datos reales; se quitan en la misma migración porque hacia adelante estos campos son obligatorios y sin default implícito.)

- [x] **Step 7: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=ProductoServiceTest`
Expected: PASS

- [x] **Step 8: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: FALLA en otros puntos que todavía referencian `Producto.getStock()`/`setStock()` (p. ej. `ProductoController`, `PedidoService`, `ProductoRepository`) — es esperado, se resuelve en el Task 2. Anotar la lista de errores de compilación para direccionar el Task 2.

- [x] **Step 9: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/TipoProducto.java \
        backend/src/main/java/com/ecommerce/entity/Producto.java \
        backend/src/main/java/com/ecommerce/dto/ProductoDTO.java \
        backend/src/main/resources/db/migration/V2__producto_camisetas_futbol.sql \
        backend/src/test/java/com/ecommerce/service/ProductoServiceTest.java
git commit -m "feat: agregar club, liga, temporada y tipo a Producto"
```

(Este commit puede quedar con el build roto a propósito — se cierra en el Task 2, que es indivisible del anterior en términos de compilación. Si el equipo prefiere no commitear estados rotos, fusionar el Task 1 y el Task 2 en un solo commit final.)

---

### Task 2: `ProductoVariante` (talle + stock + SKU) reemplaza el stock agregado

**Files:**
- Create: `backend/src/main/java/com/ecommerce/entity/ProductoVariante.java`
- Create: `backend/src/main/java/com/ecommerce/repository/ProductoVarianteRepository.java`
- Create: `backend/src/main/java/com/ecommerce/dto/ProductoVarianteDTO.java`
- Create: `backend/src/main/resources/db/migration/V3__producto_variantes.sql`
- Modify: `backend/src/main/java/com/ecommerce/entity/Producto.java`
- Modify: `backend/src/main/java/com/ecommerce/dto/ProductoDTO.java`
- Modify: `backend/src/main/java/com/ecommerce/service/ProductoService.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/ProductoController.java`
- Modify: `backend/src/main/java/com/ecommerce/repository/ProductoRepository.java`
- Modify: `backend/src/test/java/com/ecommerce/service/ProductoServiceTest.java`

**Interfaces:**
- Produces: `ProductoVariante{id, producto, talle, stock, sku, version}`, `ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long>` con `findByProductoId(Long productoId)`.
- Produces: `ProductoService.reemplazarVariantes(Long productoId, List<ProductoVarianteDTO> variantes)` — usado por `crearProducto`/`actualizarProducto` y consumido por el frontend (Task 8).
- Produces: `ProductoDTO.variantes: List<ProductoVarianteDTO>` y `ProductoDTO.stockTotal: Integer` (suma de variantes) — consumido por el frontend (Task 8, Task 9).
- El campo `version` (`@Version`) en `ProductoVariante` se agrega en esta tarea aunque recién se use activamente para optimistic locking en la Fase 2 (checkout) — es parte del modelo de datos, no tiene sentido introducirlo después con otra migración.

- [x] **Step 1: Escribir el test que falla**

Agregar a `backend/src/test/java/com/ecommerce/service/ProductoServiceTest.java`:

```java
@Test
@DisplayName("Debería crear un producto con variantes de talle y calcular el stock total")
void testCrearProducto_ConVariantes() {
    Producto nuevo = Producto.builder()
            .name("Camiseta Titular 2026")
            .club("Boca Juniors").liga("Liga Profesional Argentina")
            .temporada("2026").tipo(TipoProducto.CAMISETA)
            .price(new BigDecimal("45000"))
            .build();

    List<ProductoVarianteDTO> variantes = List.of(
            ProductoVarianteDTO.builder().talle("S").stock(5).sku("BOCA-2026-S").build(),
            ProductoVarianteDTO.builder().talle("M").stock(8).sku("BOCA-2026-M").build()
    );

    when(usuarioService.findById(1L)).thenReturn(vendedor);
    when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));
    when(productoVarianteRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

    Producto creado = productoService.crearProducto(nuevo, 1L, variantes);

    assertEquals(2, creado.getVariantes().size());
    assertEquals(13, creado.getVariantes().stream().mapToInt(ProductoVariante::getStock).sum());
}
```

Agregar el mock correspondiente al inicio de la clase (junto a los `@Mock` existentes):

```java
    @Mock
    private ProductoVarianteRepository productoVarianteRepository;
```

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=ProductoServiceTest`
Expected: FALLA — no compila (`ProductoVariante`, `ProductoVarianteDTO`, `productoVarianteRepository`, la sobrecarga de `crearProducto` con variantes no existen todavía).

- [x] **Step 3: Crear la entidad `ProductoVariante`**

```java
package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "producto_variantes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVariante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    @ToString.Exclude
    private Producto producto;

    @Column(nullable = false)
    private String talle;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false, unique = true)
    private String sku;

    @Version
    private Long version;
}
```

- [x] **Step 4: Crear `ProductoVarianteRepository`**

```java
package com.ecommerce.repository;

import com.ecommerce.entity.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {
    List<ProductoVariante> findByProductoId(Long productoId);
}
```

- [x] **Step 5: Crear `ProductoVarianteDTO`**

```java
package com.ecommerce.dto;

import com.ecommerce.entity.ProductoVariante;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVarianteDTO {
    private Long id;
    private String talle;
    private Integer stock;
    private String sku;

    public ProductoVarianteDTO(ProductoVariante variante) {
        this.id = variante.getId();
        this.talle = variante.getTalle();
        this.stock = variante.getStock();
        this.sku = variante.getSku();
    }
}
```

- [x] **Step 6: Agregar la relación en `Producto` y actualizar `ProductoDTO`**

En `Producto.java`, agregar:

```java
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductoVariante> variantes = new ArrayList<>();
```

(agregar `import java.util.ArrayList;` si no está ya importado — sí lo está, se usa en otra colección de la clase original antes de este cambio... si no, agregarlo).

En `ProductoDTO.java`, agregar:

```java
    private List<ProductoVarianteDTO> variantes;
    private Integer stockTotal;
```

y en el constructor de mapeo:

```java
        this.variantes = producto.getVariantes().stream().map(ProductoVarianteDTO::new).collect(java.util.stream.Collectors.toList());
        this.stockTotal = this.variantes.stream().mapToInt(ProductoVarianteDTO::getStock).sum();
```

- [x] **Step 7: Actualizar `ProductoService`**

En `backend/src/main/java/com/ecommerce/service/ProductoService.java`, inyectar `ProductoVarianteRepository` y agregar el método usado por el test, además de ajustar `crearProducto`/`actualizarProducto` para no tocar más el campo `stock` (ya no existe):

```java
    @Autowired
    private ProductoVarianteRepository productoVarianteRepository;

    public Producto crearProducto(Producto producto, Long ownerUserId, List<ProductoVarianteDTO> variantes) {
        Usuario ownerUser = usuarioService.findById(ownerUserId);
        if (ownerUser == null) {
            throw new UsuarioNotFoundException(ownerUserId);
        }

        producto.setOwnerUser(ownerUser);
        producto.setCreatedAt(LocalDateTime.now());
        Producto guardado = productoRepository.save(producto);

        reemplazarVariantes(guardado, variantes);

        return guardado;
    }

    public void reemplazarVariantes(Producto producto, List<ProductoVarianteDTO> variantesDTO) {
        List<ProductoVariante> nuevasVariantes = variantesDTO.stream()
                .map(dto -> ProductoVariante.builder()
                        .producto(producto)
                        .talle(dto.getTalle())
                        .stock(dto.getStock())
                        .sku(dto.getSku())
                        .build())
                .collect(java.util.stream.Collectors.toList());

        productoVarianteRepository.saveAll(nuevasVariantes);
        producto.setVariantes(nuevasVariantes);
    }
```

Quitar el método `crearProducto(Producto, Long)` de dos parámetros (queda reemplazado por la sobrecarga de tres) y actualizar `actualizarProducto` para aceptar también `List<ProductoVarianteDTO>` y llamar a `reemplazarVariantes` (borrando antes las variantes previas del producto vía `productoVarianteRepository.deleteAll(productoVarianteRepository.findByProductoId(id))`, ya que `orphanRemoval = true` en la relación se encarga de la baja cuando se reasigna la lista completa en el mismo `save`).

- [x] **Step 8: Actualizar `ProductoController`**

`crearProducto`/`actualizarProducto` reciben hoy un `Producto` crudo por `@RequestBody`. Se cambia a un DTO de request que incluya las variantes:

```java
    public static class ProductoRequest {
        private Producto producto;
        private java.util.List<ProductoVarianteDTO> variantes;
        // getters/setters
    }
```

y los métodos pasan a:

```java
    @PostMapping
    public ResponseEntity<ProductoDTO> crearProducto(@RequestBody ProductoRequest request,
                                                       @AuthenticationPrincipal Usuario usuario) {
        Producto productoCreado = productoService.crearProducto(request.getProducto(), usuario.getId(), request.getVariantes());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProductoDTO(productoCreado));
    }
```

(Ajustar `actualizarProducto` de forma equivalente.) Quitar del `ProductoRepository` los métodos `findByStockGreaterThan`/`findByStockEquals` (ya no existe la columna `stock` en `productos`) — se reemplazan en el Task 5 por una consulta sobre variantes.

- [x] **Step 9: Migración Flyway**

Crear `backend/src/main/resources/db/migration/V3__producto_variantes.sql`:

```sql
CREATE TABLE producto_variantes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    talle VARCHAR(20) NOT NULL,
    stock INT NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_variante_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

-- Migrar el stock agregado existente a una variante única "UNICO" antes de borrar la columna,
-- para no perder inventario cargado en producción.
INSERT INTO producto_variantes (producto_id, talle, stock, sku, version)
SELECT id, 'UNICO', stock, CONCAT('LEGACY-', id), 0
FROM productos;

ALTER TABLE productos DROP COLUMN stock;
```

- [x] **Step 10: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=ProductoServiceTest`
Expected: PASS

- [x] **Step 11: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: FALLA todavía en `PedidoService`/`PedidoServiceTest` (usan `producto.getStock()`) — se resuelve en el Task 4. Confirmar que los fallos restantes son únicamente en esos archivos.

- [x] **Step 12: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/ProductoVariante.java \
        backend/src/main/java/com/ecommerce/repository/ProductoVarianteRepository.java \
        backend/src/main/java/com/ecommerce/dto/ProductoVarianteDTO.java \
        backend/src/main/resources/db/migration/V3__producto_variantes.sql \
        backend/src/main/java/com/ecommerce/entity/Producto.java \
        backend/src/main/java/com/ecommerce/dto/ProductoDTO.java \
        backend/src/main/java/com/ecommerce/service/ProductoService.java \
        backend/src/main/java/com/ecommerce/controller/ProductoController.java \
        backend/src/main/java/com/ecommerce/repository/ProductoRepository.java \
        backend/src/test/java/com/ecommerce/service/ProductoServiceTest.java
git commit -m "feat: reemplazar stock agregado de Producto por ProductoVariante (talle+stock+sku)"
```

---

### Task 3: `DetallePedido` referencia la variante comprada (talle)

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/entity/DetallePedido.java`
- Modify: `backend/src/main/java/com/ecommerce/dto/CreatePedidoDTO.java`
- Modify: `backend/src/main/java/com/ecommerce/dto/DetallePedidoDTO.java`
- Create: `backend/src/main/resources/db/migration/V4__detalle_pedido_variante.sql`

**Interfaces:**
- Produces: `DetallePedido.variante: ProductoVariante`, `DetallePedido.talle: String` (snapshot) — consumido por `PedidoService` (Task 4) y por el frontend (`DetallePedidoDTO.talle`).
- Produces: `CreatePedidoDTO.ItemCarritoDTO.productoVarianteId` reemplaza a `productoId` — consumido por el frontend (Task 10).

- [x] **Step 1: Actualizar `DetallePedido`**

Agregar campos después de `producto` (mantener `producto` para no perder la referencia al producto padre, útil para agrupar por producto en reportes):

```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_variante_id", nullable = false)
    private ProductoVariante variante;

    @Column(nullable = false)
    private String talle;
```

- [x] **Step 2: Actualizar `CreatePedidoDTO.ItemCarritoDTO`**

```java
    public static class ItemCarritoDTO {
        private Long productoVarianteId;
        private Integer cantidad;
    }
```

- [x] **Step 3: Actualizar `DetallePedidoDTO`**

Agregar el campo `talle` y mapearlo en el constructor:

```java
    private String talle;
```

```java
        this.talle = detalle.getTalle();
```

- [x] **Step 4: Migración Flyway**

```sql
ALTER TABLE detalle_pedidos
    ADD COLUMN producto_variante_id BIGINT NULL,
    ADD COLUMN talle VARCHAR(20) NULL;

-- Backfill: asignar la variante "UNICO" migrada en V3 a los detalles de pedido existentes
UPDATE detalle_pedidos dp
JOIN producto_variantes pv ON pv.producto_id = dp.producto_id AND pv.talle = 'UNICO'
SET dp.producto_variante_id = pv.id, dp.talle = 'UNICO';

ALTER TABLE detalle_pedidos
    MODIFY COLUMN producto_variante_id BIGINT NOT NULL,
    MODIFY COLUMN talle VARCHAR(20) NOT NULL,
    ADD CONSTRAINT fk_detalle_variante FOREIGN KEY (producto_variante_id) REFERENCES producto_variantes(id);
```

- [x] **Step 5: Compilar (sin test dedicado — este cambio de esquema se prueba de punta a punta en el Task 4)**

Run: `cd backend && mvnd compile`
Expected: compila (los usos de `CreatePedidoDTO.ItemCarritoDTO.getProductoId()` en `PedidoService` quedan rotos hasta el Task 4, que es indivisible de este).

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/DetallePedido.java \
        backend/src/main/java/com/ecommerce/dto/CreatePedidoDTO.java \
        backend/src/main/java/com/ecommerce/dto/DetallePedidoDTO.java \
        backend/src/main/resources/db/migration/V4__detalle_pedido_variante.sql
git commit -m "feat: DetallePedido referencia ProductoVariante (talle) en vez de solo Producto"
```

---

### Task 4: `PedidoService` valida y descuenta stock por variante

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/service/PedidoService.java`
- Modify: `backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java`

**Interfaces:**
- Consumes: `ProductoVarianteRepository` (Task 2), `DetallePedido.variante/talle` (Task 3).
- Produces: `PedidoService.crearPedido` valida y descuenta `ProductoVariante.stock` en vez de `Producto.stock`; `cancelarPedido`/`actualizarEstadoItem` devuelven stock a la variante correspondiente.

- [x] **Step 1: Revisar y ajustar `PedidoServiceTest` existente**

Leer `backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java` completo. Reemplazar cada `Producto` de prueba armado con `.stock(n)` por un `Producto` con una `ProductoVariante` asociada (`.stock(n)` pasa a vivir en la variante), y agregar el mock `ProductoVarianteRepository` a la clase (mismo patrón `@Mock` que ya usa para `ProductoRepository`).

Agregar (o adaptar el test de stock insuficiente existente a) este caso:

```java
@Test
@DisplayName("Debería descontar el stock de la variante (talle) correcta, no de otras variantes del mismo producto")
void testCrearPedido_DescuentaSoloLaVarianteComprada() {
    ProductoVariante talleS = ProductoVariante.builder().id(10L).talle("S").stock(5).sku("SKU-S").build();
    ProductoVariante talleM = ProductoVariante.builder().id(11L).talle("M").stock(8).sku("SKU-M").build();
    Producto producto = Producto.builder().id(1L).name("Camiseta").price(new BigDecimal("45000"))
            .ownerUser(vendedor).variantes(List.of(talleS, talleM)).build();

    CreatePedidoDTO dto = CreatePedidoDTO.builder()
            .items(List.of(new CreatePedidoDTO.ItemCarritoDTO(11L, 3)))
            .direccionEnvio("Calle Falsa 123").build();

    when(usuarioRepository.findById(1L)).thenReturn(Optional.of(comprador));
    when(productoVarianteRepository.findById(11L)).thenReturn(Optional.of(talleM));
    when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

    pedidoService.crearPedido(1L, dto);

    assertEquals(5, talleS.getStock()); // sin cambios
    assertEquals(5, talleM.getStock()); // 8 - 3
    verify(productoVarianteRepository).save(talleM);
}
```

(Ajustar nombres de mocks/fixtures `comprador`/`vendedor` al `setUp()` real de la clase.)

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: FALLA en compilación — `PedidoService` todavía usa `ItemCarritoDTO.getProductoId()` y `productoRepository.findById`, no `productoVarianteRepository`.

- [x] **Step 3: Reescribir `PedidoService.crearPedido`**

Inyectar `ProductoVarianteRepository` y reemplazar el bucle de procesamiento de items (líneas 100-140 del archivo original):

```java
    @Autowired
    private ProductoVarianteRepository productoVarianteRepository;
```

```java
        for (CreatePedidoDTO.ItemCarritoDTO itemDTO : createPedidoDTO.getItems()) {
            ProductoVariante variante = productoVarianteRepository.findById(itemDTO.getProductoVarianteId())
                    .orElseThrow(() -> new ProductoNotFoundException(itemDTO.getProductoVarianteId()));
            Producto producto = variante.getProducto();

            if (variante.getStock() < itemDTO.getCantidad()) {
                throw new StockInsuficienteException(
                        producto.getName() + " (talle " + variante.getTalle() + ")",
                        variante.getStock(),
                        itemDTO.getCantidad()
                );
            }

            DetallePedido detalle = DetallePedido.builder()
                    .pedido(pedido)
                    .producto(producto)
                    .variante(variante)
                    .talle(variante.getTalle())
                    .vendedor(producto.getOwnerUser())
                    .cantidad(itemDTO.getCantidad())
                    .precioUnitario(producto.getPrice())
                    .productoNombre(producto.getName())
                    .productoImagen(producto.getImages() != null && !producto.getImages().isEmpty()
                            ? producto.getImages().get(0)
                            : null)
                    .estadoItem(EstadoPedido.PENDIENTE)
                    .build();

            pedido.getItems().add(detalle);

            variante.setStock(variante.getStock() - itemDTO.getCantidad());
            productoVarianteRepository.save(variante);

            BigDecimal subtotal = producto.getPrice().multiply(BigDecimal.valueOf(itemDTO.getCantidad()));
            totalPedido = totalPedido.add(subtotal);
        }
```

- [x] **Step 4: Actualizar `cancelarPedido` y `actualizarEstadoItem` para devolver stock a la variante**

En `cancelarPedido`, reemplazar el bloque que devuelve stock (líneas 182-191 del archivo original):

```java
        for (DetallePedido detalle : pedido.getItems()) {
            if (detalle.getEstadoItem() == EstadoPedido.PENDIENTE) {
                ProductoVariante variante = detalle.getVariante();
                if (variante != null) {
                    variante.setStock(variante.getStock() + detalle.getCantidad());
                    productoVarianteRepository.save(variante);
                }
                detalle.setEstadoItem(EstadoPedido.CANCELADO_COMPRADOR);
            }
        }
```

En `actualizarEstadoItem`, reemplazar el bloque equivalente (líneas 256-263 del archivo original) por el mismo patrón, usando `detalle.getVariante()` en vez de `detalle.getProducto()`.

- [x] **Step 5: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PedidoServiceTest`
Expected: PASS

- [x] **Step 6: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [x] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ecommerce/service/PedidoService.java \
        backend/src/test/java/com/ecommerce/service/PedidoServiceTest.java
git commit -m "feat: PedidoService valida y descuenta stock por variante (talle), no por producto"
```

---

### Task 5: Filtro de catálogo por club, liga, talle, tipo y precio

**Files:**
- Create: `backend/src/main/java/com/ecommerce/repository/spec/ProductoSpecifications.java`
- Modify: `backend/src/main/java/com/ecommerce/repository/ProductoRepository.java`
- Modify: `backend/src/main/java/com/ecommerce/service/ProductoService.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/ProductoController.java`
- Create: `backend/src/test/java/com/ecommerce/service/ProductoFiltroTest.java`

**Interfaces:**
- Produces: `ProductoService.filtrarProductos(ProductoFiltroDTO filtro)` → `List<Producto>`, endpoint `GET /api/productos/filtrar?club=&liga=&tipo=&talle=&precioMin=&precioMax=`.

- [x] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/service/ProductoFiltroTest.java` como test de integración (necesita join real con `producto_variantes`, no es mockeable con Mockito puro):

```java
package com.ecommerce.service;

import com.ecommerce.entity.*;
import com.ecommerce.repository.CategoriaRepository;
import com.ecommerce.repository.ProductoRepository;
import com.ecommerce.repository.ProductoVarianteRepository;
import com.ecommerce.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Filtro de catálogo")
class ProductoFiltroTest {

    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository productoVarianteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario vendedor;

    @BeforeEach
    void setUp() {
        productoVarianteRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("Vendedor").apellido("Test").username("vendedor").email("vendedor@test.com")
                .password("x").role(Role.USER).build());

        crearProducto("Camiseta Boca", "Boca Juniors", "Liga Profesional Argentina", TipoProducto.CAMISETA, "M");
        crearProducto("Short River", "River Plate", "Liga Profesional Argentina", TipoProducto.SHORT, "L");
    }

    private void crearProducto(String nombre, String club, String liga, TipoProducto tipo, String talle) {
        Producto producto = productoRepository.save(Producto.builder()
                .name(nombre).club(club).liga(liga).temporada("2026").tipo(tipo)
                .price(new BigDecimal("30000")).ownerUser(vendedor).createdAt(java.time.LocalDateTime.now())
                .build());
        productoVarianteRepository.save(ProductoVariante.builder()
                .producto(producto).talle(talle).stock(10).sku(nombre + "-" + talle).build());
    }

    @Test
    @DisplayName("Debería filtrar productos por club")
    void testFiltrarPorClub() {
        List<Producto> resultado = productoService.filtrarProductos(
                ProductoFiltroDTO.builder().club("Boca Juniors").build());

        assertEquals(1, resultado.size());
        assertEquals("Camiseta Boca", resultado.get(0).getName());
    }

    @Test
    @DisplayName("Debería filtrar productos por tipo y talle combinados")
    void testFiltrarPorTipoYTalle() {
        List<Producto> resultado = productoService.filtrarProductos(
                ProductoFiltroDTO.builder().tipo(TipoProducto.SHORT).talle("L").build());

        assertEquals(1, resultado.size());
        assertEquals("Short River", resultado.get(0).getName());
    }
}
```

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=ProductoFiltroTest`
Expected: FALLA — no compila, `ProductoFiltroDTO` y `ProductoService.filtrarProductos` no existen.

- [x] **Step 3: Crear `ProductoFiltroDTO`**

```java
package com.ecommerce.dto;

import com.ecommerce.entity.TipoProducto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoFiltroDTO {
    private String club;
    private String liga;
    private TipoProducto tipo;
    private String talle;
    private BigDecimal precioMin;
    private BigDecimal precioMax;
}
```

- [x] **Step 4: Crear `ProductoSpecifications`**

```java
package com.ecommerce.repository.spec;

import com.ecommerce.dto.ProductoFiltroDTO;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.ProductoVariante;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

public class ProductoSpecifications {

    private ProductoSpecifications() {}

    public static Specification<Producto> conFiltro(ProductoFiltroDTO filtro) {
        return (root, query, cb) -> {
            var predicates = cb.conjunction();

            if (filtro.getClub() != null && !filtro.getClub().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("club"), filtro.getClub()));
            }
            if (filtro.getLiga() != null && !filtro.getLiga().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("liga"), filtro.getLiga()));
            }
            if (filtro.getTipo() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("tipo"), filtro.getTipo()));
            }
            if (filtro.getPrecioMin() != null) {
                predicates = cb.and(predicates, cb.ge(root.get("price"), filtro.getPrecioMin()));
            }
            if (filtro.getPrecioMax() != null) {
                predicates = cb.and(predicates, cb.le(root.get("price"), filtro.getPrecioMax()));
            }
            if (filtro.getTalle() != null && !filtro.getTalle().isBlank()) {
                query.distinct(true);
                Join<Producto, ProductoVariante> variantes = root.join("variantes");
                predicates = cb.and(predicates, cb.equal(variantes.get("talle"), filtro.getTalle()));
            }

            return predicates;
        };
    }
}
```

- [x] **Step 5: Extender `ProductoRepository` con `JpaSpecificationExecutor`**

```java
public interface ProductoRepository extends JpaRepository<Producto, Long>, JpaSpecificationExecutor<Producto> {
```

(agregar `import org.springframework.data.jpa.repository.JpaSpecificationExecutor;`)

- [x] **Step 6: Agregar `ProductoService.filtrarProductos`**

```java
    @Transactional(readOnly = true)
    public List<Producto> filtrarProductos(ProductoFiltroDTO filtro) {
        return productoRepository.findAll(ProductoSpecifications.conFiltro(filtro));
    }
```

- [x] **Step 7: Agregar el endpoint en `ProductoController`**

```java
    @GetMapping("/filtrar")
    public ResponseEntity<List<ProductoDTO>> filtrarProductos(
            @RequestParam(required = false) String club,
            @RequestParam(required = false) String liga,
            @RequestParam(required = false) TipoProducto tipo,
            @RequestParam(required = false) String talle,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax) {
        ProductoFiltroDTO filtro = ProductoFiltroDTO.builder()
                .club(club).liga(liga).tipo(tipo).talle(talle)
                .precioMin(precioMin).precioMax(precioMax).build();

        List<ProductoDTO> productos = productoService.filtrarProductos(filtro)
                .stream().map(ProductoDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }
```

- [x] **Step 8: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=ProductoFiltroTest`
Expected: PASS

- [x] **Step 9: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [x] **Step 10: Commit**

```bash
git add backend/src/main/java/com/ecommerce/repository/spec/ProductoSpecifications.java \
        backend/src/main/java/com/ecommerce/dto/ProductoFiltroDTO.java \
        backend/src/main/java/com/ecommerce/repository/ProductoRepository.java \
        backend/src/main/java/com/ecommerce/service/ProductoService.java \
        backend/src/main/java/com/ecommerce/controller/ProductoController.java \
        backend/src/test/java/com/ecommerce/service/ProductoFiltroTest.java
git commit -m "feat: filtro de catálogo por club, liga, tipo, talle y precio"
```

---

### Task 6: Address book (`Direccion`)

**Files:**
- Create: `backend/src/main/java/com/ecommerce/entity/Direccion.java`
- Create: `backend/src/main/java/com/ecommerce/repository/DireccionRepository.java`
- Create: `backend/src/main/java/com/ecommerce/dto/DireccionDTO.java`
- Create: `backend/src/main/java/com/ecommerce/controller/DireccionController.java`
- Create: `backend/src/main/resources/db/migration/V5__direcciones.sql`
- Create: `backend/src/test/java/com/ecommerce/controller/DireccionControllerTest.java`

**Interfaces:**
- Produces: `GET/POST/PUT/DELETE /api/direcciones` (todas requieren usuario autenticado, cada usuario solo ve/edita las suyas) — consumido por el checkout de la Fase 2 y el frontend (Task 7).

- [x] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/controller/DireccionControllerTest.java` como integración (mismo patrón `@SpringBootTest + @AutoConfigureMockMvc + @ActiveProfiles("test")` de la Fase 0):

```java
package com.ecommerce.controller;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.Usuario;
import com.ecommerce.repository.UsuarioRepository;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - DireccionController")
class DireccionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private String token;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Pérez").username("juan").email("juan@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        token = jwtUtil.generateToken(usuario.getEmail(), usuario.getId());
    }

    @Test
    @DisplayName("Debería crear y luego listar la dirección del usuario autenticado")
    void testCrearYListarDireccion() throws Exception {
        String body = """
                {"calle":"Av. Corrientes","numero":"1234","ciudad":"CABA","provincia":"Buenos Aires","codigoPostal":"1043","pais":"Argentina","esPredeterminada":true}
                """;

        mockMvc.perform(post("/api/direcciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/direcciones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calle").value("Av. Corrientes"));
    }
}
```

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=DireccionControllerTest`
Expected: FALLA — no compila, ninguna de las clases existe.

- [x] **Step 3: Crear la entidad `Direccion`**

```java
package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "direcciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private String calle;

    @Column(nullable = false)
    private String numero;

    @Column(nullable = false)
    private String ciudad;

    @Column(nullable = false)
    private String provincia;

    @Column(name = "codigo_postal", nullable = false)
    private String codigoPostal;

    @Column(nullable = false)
    private String pais;

    @Column(name = "es_predeterminada", nullable = false)
    @Builder.Default
    private boolean esPredeterminada = false;
}
```

- [x] **Step 4: Crear `DireccionRepository`**

```java
package com.ecommerce.repository;

import com.ecommerce.entity.Direccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DireccionRepository extends JpaRepository<Direccion, Long> {
    List<Direccion> findByUsuarioId(Long usuarioId);
}
```

- [x] **Step 5: Crear `DireccionDTO`**

```java
package com.ecommerce.dto;

import com.ecommerce.entity.Direccion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DireccionDTO {
    private Long id;
    private String calle;
    private String numero;
    private String ciudad;
    private String provincia;
    private String codigoPostal;
    private String pais;
    private boolean esPredeterminada;

    public DireccionDTO(Direccion direccion) {
        this.id = direccion.getId();
        this.calle = direccion.getCalle();
        this.numero = direccion.getNumero();
        this.ciudad = direccion.getCiudad();
        this.provincia = direccion.getProvincia();
        this.codigoPostal = direccion.getCodigoPostal();
        this.pais = direccion.getPais();
        this.esPredeterminada = direccion.isEsPredeterminada();
    }
}
```

- [x] **Step 6: Crear `DireccionController`**

```java
package com.ecommerce.controller;

import com.ecommerce.dto.DireccionDTO;
import com.ecommerce.entity.Direccion;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.repository.DireccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/direcciones")
public class DireccionController {

    @Autowired
    private DireccionRepository direccionRepository;

    @GetMapping
    public ResponseEntity<List<DireccionDTO>> listar(@AuthenticationPrincipal Usuario usuario) {
        List<DireccionDTO> direcciones = direccionRepository.findByUsuarioId(usuario.getId())
                .stream().map(DireccionDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(direcciones);
    }

    @PostMapping
    public ResponseEntity<DireccionDTO> crear(@RequestBody Direccion direccion, @AuthenticationPrincipal Usuario usuario) {
        direccion.setUsuario(usuario);
        Direccion guardada = direccionRepository.save(direccion);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DireccionDTO(guardada));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DireccionDTO> actualizar(@PathVariable Long id, @RequestBody Direccion direccion,
                                                     @AuthenticationPrincipal Usuario usuario) {
        Direccion existente = direccionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dirección no encontrada"));
        if (!existente.getUsuario().getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso para editar esta dirección");
        }
        direccion.setId(id);
        direccion.setUsuario(usuario);
        return ResponseEntity.ok(new DireccionDTO(direccionRepository.save(direccion)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Direccion existente = direccionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dirección no encontrada"));
        if (!existente.getUsuario().getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso para eliminar esta dirección");
        }
        direccionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [x] **Step 7: Migración Flyway**

```sql
CREATE TABLE direcciones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    calle VARCHAR(255) NOT NULL,
    numero VARCHAR(50) NOT NULL,
    ciudad VARCHAR(255) NOT NULL,
    provincia VARCHAR(255) NOT NULL,
    codigo_postal VARCHAR(20) NOT NULL,
    pais VARCHAR(100) NOT NULL,
    es_predeterminada BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_direccion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);
```

- [x] **Step 8: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=DireccionControllerTest`
Expected: PASS

- [x] **Step 9: Commit**

```bash
git add backend/src/main/java/com/ecommerce/entity/Direccion.java \
        backend/src/main/java/com/ecommerce/repository/DireccionRepository.java \
        backend/src/main/java/com/ecommerce/dto/DireccionDTO.java \
        backend/src/main/java/com/ecommerce/controller/DireccionController.java \
        backend/src/main/resources/db/migration/V5__direcciones.sql \
        backend/src/test/java/com/ecommerce/controller/DireccionControllerTest.java
git commit -m "feat: agregar address book de usuario (Direccion)"
```

---

### Task 7: `api.js` — mapear club/liga/temporada/tipo/variantes y direcciones

**Files:**
- Modify: `frontend/src/services/api.js`

**Interfaces:**
- Produces: `api.getProducts/getProduct/createProduct/updateProduct` mapean `club/liga/temporada/tipo/variantes/stockTotal`.
- Produces: `api.getDirecciones()`, `api.createDireccion(data)`, `api.updateDireccion(id, data)`, `api.deleteDireccion(id)`.
- Produces: `api.getProductsFiltered(filtro)` → `GET /api/productos/filtrar`.

No hay test automatizado de frontend en este proyecto (no hay `vitest`/`jest` configurado en `package.json`); la verificación de este task es manual, vía Task 8/9/10 usando la app corriendo.

- [x] **Step 1: Actualizar el mapeo de productos**

En `frontend/src/services/api.js`, en `getProducts`, `getProduct`, `createProduct` y `updateProduct`, agregar a cada objeto mapeado:

```js
      club: product.club,
      liga: product.liga,
      temporada: product.temporada,
      tipo: product.tipo,
      variantes: (product.variantes || []).map(v => ({ id: v.id, talle: v.talle, stock: v.stock, sku: v.sku })),
      stockTotal: product.stockTotal,
```

y quitar la línea `stock: product.stock` (ya no existe en el backend — ver Fase 1 Task 2), reemplazándola donde se usaba por `stockTotal`.

En `createProduct`/`updateProduct`, el body enviado pasa de `{ name, description, price, stock, images, categoriaId, ownerUserId }` a:

```js
  async createProduct(productData) {
    const request = {
      producto: {
        name: productData.name,
        description: productData.description,
        price: Number(productData.price),
        club: productData.club,
        liga: productData.liga,
        temporada: productData.temporada,
        tipo: productData.tipo,
        images: productData.images || [],
        categoriaId: productData.categoryId ? Number(productData.categoryId) : null,
      },
      variantes: (productData.variantes || []).map(v => ({
        talle: v.talle,
        stock: Number(v.stock),
        sku: v.sku,
      })),
    }

    const created = await request('/productos', {
      method: 'POST',
      body: JSON.stringify(request),
    })
    // ... mismo mapeo de respuesta que getProduct
  },
```

(Nota: el nombre de variable local `request` colisiona con la función `request(...)` del módulo — renombrarla a `payload` en la implementación real.) Aplicar el mismo cambio de forma equivalente en `updateProduct`.

Quitar `updateProductStock` (ya no tiene sentido: el stock ahora es por variante y se gestiona a través de `updateProduct` con su lista de `variantes`, no hay más un único stock a pisar).

- [x] **Step 2: Agregar `getProductsFiltered`**

```js
  async getProductsFiltered(filtro = {}) {
    const params = new URLSearchParams()
    if (filtro.club) params.set('club', filtro.club)
    if (filtro.liga) params.set('liga', filtro.liga)
    if (filtro.tipo) params.set('tipo', filtro.tipo)
    if (filtro.talle) params.set('talle', filtro.talle)
    if (filtro.precioMin) params.set('precioMin', filtro.precioMin)
    if (filtro.precioMax) params.set('precioMax', filtro.precioMax)

    const productos = await request(`/productos/filtrar?${params.toString()}`)
    return productos.map(product => ({
      id: product.id,
      name: product.name,
      price: product.price,
      images: product.images || [],
      club: product.club,
      liga: product.liga,
      temporada: product.temporada,
      tipo: product.tipo,
      variantes: (product.variantes || []).map(v => ({ id: v.id, talle: v.talle, stock: v.stock, sku: v.sku })),
      stockTotal: product.stockTotal,
    }))
  },
```

- [x] **Step 3: Agregar endpoints de direcciones**

```js
  // ===== DIRECCIONES =====

  async getDirecciones() {
    return request('/direcciones')
  },

  async createDireccion(data) {
    return request('/direcciones', { method: 'POST', body: JSON.stringify(data) })
  },

  async updateDireccion(id, data) {
    return request(`/direcciones/${id}`, { method: 'PUT', body: JSON.stringify(data) })
  },

  async deleteDireccion(id) {
    await request(`/direcciones/${id}`, { method: 'DELETE' })
    return true
  },
```

- [x] **Step 4: Commit**

```bash
git add frontend/src/services/api.js
git commit -m "feat: mapear club/liga/temporada/tipo/variantes y agregar endpoints de direcciones en api.js"
```

---

### Task 8: `ProductForm.jsx` — club, liga, temporada, tipo y editor de variantes (talle+stock)

**Files:**
- Modify: `frontend/src/pages/ProductForm.jsx`

- [x] **Step 1: Agregar los campos nuevos al estado del formulario**

En `formData`, agregar `club`, `liga`, `temporada`, `tipo` (default `"CAMISETA"`) y `variantes` (default `[{ talle: "", stock: "", sku: "" }]`) en vez de `stock`.

- [x] **Step 2: Agregar los inputs de club/liga/temporada/tipo**

Junto a los campos existentes de nombre/descripción, agregar (mismo patrón de `input`/`errors` que ya usa el resto del formulario):

```jsx
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 mb-6">
            <div>
              <label htmlFor="club" className="block text-sm font-medium text-gray-700 mb-2">
                Club <span className="text-red-500">*</span>
              </label>
              <input type="text" id="club" name="club" value={formData.club} onChange={handleChange}
                     className={`input ${errors.club ? "border-red-500 focus:ring-red-500" : ""}`}
                     placeholder="Ej: Boca Juniors" />
              {errors.club && <p className="mt-1 text-sm text-red-600">{errors.club}</p>}
            </div>
            <div>
              <label htmlFor="liga" className="block text-sm font-medium text-gray-700 mb-2">
                Liga <span className="text-red-500">*</span>
              </label>
              <input type="text" id="liga" name="liga" value={formData.liga} onChange={handleChange}
                     className={`input ${errors.liga ? "border-red-500 focus:ring-red-500" : ""}`}
                     placeholder="Ej: Liga Profesional Argentina" />
              {errors.liga && <p className="mt-1 text-sm text-red-600">{errors.liga}</p>}
            </div>
            <div>
              <label htmlFor="temporada" className="block text-sm font-medium text-gray-700 mb-2">
                Temporada <span className="text-red-500">*</span>
              </label>
              <input type="text" id="temporada" name="temporada" value={formData.temporada} onChange={handleChange}
                     className={`input ${errors.temporada ? "border-red-500 focus:ring-red-500" : ""}`}
                     placeholder="Ej: 2026" />
              {errors.temporada && <p className="mt-1 text-sm text-red-600">{errors.temporada}</p>}
            </div>
            <div>
              <label htmlFor="tipo" className="block text-sm font-medium text-gray-700 mb-2">
                Tipo <span className="text-red-500">*</span>
              </label>
              <select id="tipo" name="tipo" value={formData.tipo} onChange={handleChange} className="input">
                <option value="CAMISETA">Camiseta</option>
                <option value="SHORT">Short</option>
              </select>
            </div>
          </div>
```

- [x] **Step 3: Agregar el editor de variantes, reemplazando el input único de "Stock"**

Quitar el bloque de input `stock` (líneas 227-242 del archivo original) y agregar:

```jsx
          <div className="mb-6">
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Talles y stock <span className="text-red-500">*</span>
            </label>
            {formData.variantes.map((variante, index) => (
              <div key={index} className="flex gap-3 mb-2">
                <select
                  value={variante.talle}
                  onChange={(e) => handleVarianteChange(index, "talle", e.target.value)}
                  className="input"
                >
                  <option value="">Talle</option>
                  {["XS", "S", "M", "L", "XL", "XXL"].map(t => <option key={t} value={t}>{t}</option>)}
                </select>
                <input
                  type="number" min="0" placeholder="Stock" value={variante.stock}
                  onChange={(e) => handleVarianteChange(index, "stock", e.target.value)}
                  className="input"
                />
                <input
                  type="text" placeholder="SKU" value={variante.sku}
                  onChange={(e) => handleVarianteChange(index, "sku", e.target.value)}
                  className="input"
                />
                {formData.variantes.length > 1 && (
                  <button type="button" onClick={() => removeVariante(index)} className="btn btn-secondary">
                    Quitar
                  </button>
                )}
              </div>
            ))}
            <button type="button" onClick={addVariante} className="btn btn-secondary mt-2">
              Agregar talle
            </button>
            {errors.variantes && <p className="mt-1 text-sm text-red-600">{errors.variantes}</p>}
          </div>
```

Agregar las funciones auxiliares junto a `handleChange`:

```jsx
  const handleVarianteChange = (index, field, value) => {
    setFormData((prev) => {
      const variantes = [...prev.variantes]
      variantes[index] = { ...variantes[index], [field]: value }
      return { ...prev, variantes }
    })
  }
  const addVariante = () => {
    setFormData((prev) => ({ ...prev, variantes: [...prev.variantes, { talle: "", stock: "", sku: "" }] }))
  }
  const removeVariante = (index) => {
    setFormData((prev) => ({ ...prev, variantes: prev.variantes.filter((_, i) => i !== index) }))
  }
```

- [x] **Step 4: Actualizar validación y submit**

En `validateForm`, reemplazar la validación de `stock` por:

```js
    if (formData.variantes.some(v => !v.talle || v.stock === "" || !v.sku)) {
      newErrors.variantes = "Completá talle, stock y SKU para cada variante"
    }
```

y agregar validaciones equivalentes para `club`/`liga`/`temporada` (`validateRequired`, mismo patrón que `name`).

En `handleSubmit`, `productData` pasa a incluir `club, liga, temporada, tipo, variantes` en vez de `stock`.

- [x] **Step 5: Verificación manual**

Run: `npm run dev` (en `frontend/`) y `mvnd spring-boot:run` (en `backend/`, perfil dev). En el navegador: `/dashboard/products/new`, completar el formulario con al menos dos talles, guardar, y confirmar en la ficha del producto (`/product/{id}`) que ambos talles aparecen con su stock.

- [x] **Step 6: Commit**

```bash
git add frontend/src/pages/ProductForm.jsx
git commit -m "feat: ProductForm agrega club/liga/temporada/tipo y editor de variantes por talle"
```

---

### Task 9: `ProductDetail.jsx` y `ProductCard.jsx` — selector de talle

**Files:**
- Modify: `frontend/src/pages/ProductDetail.jsx`
- Modify: `frontend/src/components/ProductCard.jsx`

Un producto con variantes no se puede agregar al carrito sin elegir talle. Se resuelve así: `ProductCard` dentro del listado ya no agrega directo al carrito — lleva a la ficha del producto, donde sí está el selector. Esto evita construir un selector de talle flotante sobre cada card del catálogo (alcance innecesario para el MVP).

- [x] **Step 1: `ProductCard.jsx` — quitar el agregado directo, dejar solo navegación a la ficha**

Quitar el botón "Agregar al Carrito" (líneas 55-69 del archivo original) y su `handleAddToCart`; el único punto de interacción de la card pasa a ser el `<Link to={/product/${product.id}}>` que ya envuelve la imagen y el título. Agregar debajo del precio/stock un texto simple:

```jsx
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
            {product.stockTotal > 0 ? `${product.variantes?.length || 0} talles disponibles` : "Sin stock"}
          </p>
```

y reemplazar `isOutOfStock = product.stock === 0` por `isOutOfStock = (product.stockTotal ?? 0) === 0`.

- [x] **Step 2: `ProductDetail.jsx` — selector de talle**

Agregar estado `const [selectedTalle, setSelectedTalle] = useState(null)`. Reemplazar el bloque de precio/stock (líneas 118-138 del archivo original) agregando el selector antes del botón de agregar al carrito:

```jsx
          <div className="border-t border-b border-gray-200 dark:border-gray-600 py-6">
            <div className="flex items-center justify-between mb-4">
              <span className="text-3xl font-bold text-blue-600 dark:text-blue-400">{formatPrice(product.price)}</span>
            </div>
            <div>
              <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">Talle</p>
              <div className="flex flex-wrap gap-2">
                {product.variantes?.map((variante) => (
                  <button
                    key={variante.id}
                    type="button"
                    disabled={variante.stock === 0}
                    onClick={() => setSelectedTalle(variante)}
                    className={`px-4 py-2 rounded-lg border text-sm font-medium ${
                      variante.stock === 0
                        ? "border-gray-200 text-gray-300 cursor-not-allowed dark:border-gray-700 dark:text-gray-600"
                        : selectedTalle?.id === variante.id
                          ? "border-blue-600 bg-blue-50 text-blue-700 dark:bg-blue-900 dark:text-blue-200"
                          : "border-gray-300 text-gray-700 hover:border-blue-400 dark:border-gray-600 dark:text-gray-300"
                    }`}
                  >
                    {variante.talle}
                  </button>
                ))}
              </div>
              {selectedTalle && (
                <p className="text-sm text-gray-600 dark:text-gray-400 mt-2">
                  {selectedTalle.stock} disponibles en talle {selectedTalle.talle}
                </p>
              )}
            </div>
          </div>
```

- [x] **Step 3: Actualizar `handleAddToCart` para requerir talle seleccionado**

```jsx
  const handleAddToCart = () => {
    if (!product) return
    if (!selectedTalle) {
      error("Elegí un talle antes de agregar al carrito")
      return
    }
    addToCart(product, selectedTalle)
    success(`${product.name} (talle ${selectedTalle.talle}) agregado al carrito`)
  }
```

y deshabilitar el botón mientras no haya talle elegido: `disabled={!selectedTalle}` en vez de `disabled={isOutOfStock}` (la variable `isOutOfStock` se recalcula como `(product.stockTotal ?? 0) === 0` y se usa para el badge de la imagen, no para el botón).

- [x] **Step 4: Verificación manual**

Run: con backend y frontend corriendo, entrar a la ficha de un producto cargado en el Task 8, confirmar que el botón "Agregar al carrito" está deshabilitado hasta elegir un talle, y que los talles sin stock aparecen deshabilitados.

- [x] **Step 5: Commit**

```bash
git add frontend/src/pages/ProductDetail.jsx frontend/src/components/ProductCard.jsx
git commit -m "feat: selector de talle obligatorio antes de agregar al carrito"
```

---

### Task 10: Carrito por variante (talle), no por producto

**Files:**
- Modify: `frontend/src/reducers/cartReducer.js`
- Modify: `frontend/src/context/CartContext.jsx`
- Modify: `frontend/src/pages/Cart.jsx`

Hoy el carrito identifica cada línea únicamente por `product.id` (`cartReducer.js:8`), así que agregar dos talles distintos de la misma camiseta pisaría la misma línea. Cada línea de carrito pasa a identificarse por `cartItemId = "${productId}:${varianteId}"`.

- [x] **Step 1: Actualizar `cartReducer.js`**

```js
export const cartInitialState = {
  items: [],
  total: 0,
}
export const cartReducer = (state, action) => {
  switch (action.type) {
    case "ADD_TO_CART": {
      const { product, variante } = action.payload
      const cartItemId = `${product.id}:${variante.id}`
      const existingItem = state.items.find((item) => item.cartItemId === cartItemId)

      if (existingItem) {
        const updatedItems = state.items.map((item) =>
          item.cartItemId === cartItemId ? { ...item, quantity: item.quantity + 1 } : item,
        )
        return { ...state, items: updatedItems, total: calculateTotal(updatedItems) }
      }

      const newItem = {
        cartItemId,
        id: product.id,
        varianteId: variante.id,
        talle: variante.talle,
        name: product.name,
        price: product.price,
        images: product.images,
        quantity: 1,
      }
      const newItems = [...state.items, newItem]
      return { ...state, items: newItems, total: calculateTotal(newItems) }
    }
    case "REMOVE_FROM_CART": {
      const updatedItems = state.items.filter((item) => item.cartItemId !== action.payload)
      return { ...state, items: updatedItems, total: calculateTotal(updatedItems) }
    }
    case "UPDATE_QUANTITY": {
      const updatedItems = state.items
        .map((item) => (item.cartItemId === action.payload.cartItemId ? { ...item, quantity: action.payload.quantity } : item))
        .filter((item) => item.quantity > 0)
      return { ...state, items: updatedItems, total: calculateTotal(updatedItems) }
    }
    case "CLEAR_CART":
      return cartInitialState
    case "RESTORE_CART":
      return { items: action.payload, total: calculateTotal(action.payload) }
    default:
      return state
  }
}
const calculateTotal = (items) => {
  return items.reduce((total, item) => total + item.price * item.quantity, 0)
}
```

- [x] **Step 2: Actualizar `CartContext.jsx`**

```jsx
  const addToCart = (product, variante) => {
    dispatch({ type: "ADD_TO_CART", payload: { product, variante } })
  }
  const removeFromCart = (cartItemId) => {
    dispatch({ type: "REMOVE_FROM_CART", payload: cartItemId })
  }
  const updateQuantity = (cartItemId, quantity) => {
    dispatch({ type: "UPDATE_QUANTITY", payload: { cartItemId, quantity } })
  }
```

y en `checkout`, `orderData.items` pasa a:

```js
        items: state.items.map(item => ({
          productoVarianteId: item.varianteId,
          cantidad: item.quantity
        })),
```

En `api.js` (`createOrder`), actualizar el mapeo de `newOrder.items` para usar `productoVarianteId: item.productoVarianteId` en vez de `productoId: item.productId || item.id`.

- [x] **Step 3: Actualizar `Cart.jsx`**

En `frontend/src/pages/Cart.jsx`:

1. `handleQuantityChange`/`handleRemoveItem` (líneas 21-31) reciben hoy `productId`; renombrar el parámetro a `cartItemId` y pasarlo tal cual a `removeFromCart`/`updateQuantity` (ya no hace falta ningún otro cambio en el cuerpo de esas dos funciones).
2. `items.map((item) => (<CartItem key={item.id} item={item} ...>` (línea 114): cambiar `key={item.id}` por `key={item.cartItemId}`.
3. En el resumen del pedido, `items.map((item) => (<div key={item.id} ...>` (línea 124): cambiar `key={item.id}` por `key={item.cartItemId}`, y agregar el talle al texto de la línea 126 (`{item.name} × {item.quantity}` → `` {item.name} (talle {item.talle}) × {item.quantity} ``).
4. En el componente `CartItem` (líneas 240-298): agregar `<p>` con el talle debajo del nombre (línea 255-256, mismo estilo que la línea de descripción que ya existe en 257), y cambiar las tres llamadas que hoy usan `item.id` como identificador de operación — `onQuantityChange(item.id, item.quantity - 1)` (línea 264), `onQuantityChange(item.id, item.quantity + 1)` (línea 272), `onRemove(item.id, item.name)` (línea 281) — por `item.cartItemId` en el primer argumento de cada una. La navegación a la ficha de producto (línea 254, `Link to={/product/${item.id}}`) **no cambia**, sigue usando `item.id` porque ahí sí se refiere al producto, no a la línea de carrito.

- [x] **Step 4: Verificación manual**

Run: con la app corriendo, agregar la misma camiseta en dos talles distintos desde la ficha de producto y confirmar en `/cart` que aparecen como dos líneas separadas, cada una con su talle y cantidad editable de forma independiente. Completar un checkout de prueba y confirmar en `/orders` que el pedido creado muestra el talle correcto por ítem.

- [x] **Step 5: Commit**

```bash
git add frontend/src/reducers/cartReducer.js frontend/src/context/CartContext.jsx frontend/src/pages/Cart.jsx frontend/src/services/api.js
git commit -m "feat: carrito identifica líneas por variante (producto+talle), no solo por producto"
```

---

## Resumen de verificación de la Fase 1

```bash
cd backend && mvnd test
```
Expected: todos los tests en verde.

Luego, manualmente: levantar backend + frontend, cargar un producto con 2+ talles desde `/dashboard/products/new`, agregarlo al carrito en dos talles distintos, y completar un pedido de prueba end-to-end confirmando que el stock por talle se descuenta correctamente (`GET /api/productos/{id}` antes y después del pedido).

---

## Notas de ejecución (Fase 1 aplicada)

Ejecutado sobre la rama `fase-1-modelo-dominio`. Desvíos respecto del plan escrito,
todos por cosas que aparecieron al implementarlo:

- **Los Tasks 1 a 4 fueron un solo commit.** El plan preveía dejar el build roto entre
  el Task 1 y el Task 2; al sacar `Producto.stock` también queda roto `PedidoService`,
  que recién se arregla en el Task 4. Con lo cual el primer punto donde el proyecto
  vuelve a compilar es el final del Task 4.
- **`DataInitializer` se reescribió** (no figuraba en el plan): sembraba productos
  genéricos con `.stock(n)` y no compilaba. Ahora siembra camisetas y shorts con sus
  variantes de talle, y categorías del rubro.
- **`ProductoService.actualizarProducto` pasa a modificar la entidad persistida** en vez
  de guardar la que llega en el body: el body no trae `ownerUser` ni `createdAt`, así que
  cada PUT dejaba el producto sin vendedor.
- **`reemplazarVariantes` aparea las variantes por talle y las actualiza en su lugar**,
  en vez de borrarlas y recrearlas como decía el plan. Borrarlas rompe la FK de
  `detalle_pedidos` en cuanto el talle ya se vendió, y además cambia los ids de variante,
  que son lo que referencian el historial de pedidos y los carritos. Eliminar un talle con
  ventas devuelve 400 pidiendo ponerle stock 0.
- **`productos.tipo` es `ENUM('CAMISETA','SHORT')`, no `VARCHAR(20)`**: Hibernate materializa
  un `@Enumerated(EnumType.STRING)` como ENUM nativo en MySQL y el `ddl-auto=validate` de
  producción rechazaba el VARCHAR.
- **Se eliminaron `GET /api/productos/stock` y `buscarProductosPorStock`** (el plan solo
  mencionaba borrar los métodos de repositorio): dependían de la columna borrada, no tenían
  consumidores en el frontend y el filtro del Task 5 los reemplaza.
- **Se migraron 4 vistas de frontend que el plan no listaba** (`ProductListItem`,
  `ProductCarousel`, `DashboardProducts`, `Home`): leían el `stock` plano que el backend ya
  no manda. `ProductListItem` además agregaba al carrito sin talle, igual que `ProductCard`.
- **`VentaDTO` expone el talle**: sin eso el vendedor no sabe qué talle despachar.
- **Tests agregados fuera del plan**: `ProductoControllerIntegrationTest`, deliberadamente sin
  `@Transactional`. Los errores de mapeo de colecciones de Hibernate recién aparecen al
  commitear, así que un test que hace rollback no los ve. Fue el que destapó los dos bugs de
  `reemplazarVariantes`.

Verificación ejecutada: 106 tests de backend en verde; frontend compila y lintea sin errores;
flujo completo probado contra el backend corriendo (alta de producto con 2 talles, filtros,
pedido que descuenta solo el talle comprado, stock insuficiente por talle, cancelación que
repone, address book y edición de producto); y migraciones aplicadas por Flyway contra un
MySQL 8 real, con `ddl-auto=validate` pasando y el backfill de datos previos verificado.

Pendiente: la verificación visual en el navegador de los Tasks 8, 9 y 10.
