# Fase 0 — Hardening de seguridad y base de infra — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cerrar los gaps de seguridad e infraestructura que bloquean manejar dinero real (secretos hardcodeados, autorización ad-hoc, esquema de DB sin control de versiones, sin TLS, sin rate limiting) antes de tocar cualquier feature de negocio.

**Architecture:** Cambios acotados al backend Spring Boot existente (sin nuevas capas): externalizar configuración sensible a variables de entorno con fail-fast, centralizar CORS y autorización en los mecanismos estándar de Spring Security, introducir Flyway para versionar el esquema, y agregar Dockerfiles + un reverse proxy delante del stack Docker Compose ya existente.

**Tech Stack:** Spring Boot 3.2.0 (Java 17), Spring Security, JJWT 0.12.3, MySQL 8 / H2 (test), Flyway, Bucket4j, Docker Compose, Caddy (reverse proxy con TLS automático).

## Global Constraints

- Java 17, Spring Boot 3.2.0 — no se suben versiones salvo que una dependencia nueva lo requiera explícitamente.
- Estilo de tests existente: JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), `@DisplayName` en español, como en `backend/src/test/java/com/ecommerce/**`.
- Lombok ya está en uso (`@Data @NoArgsConstructor @AllArgsConstructor @Builder`) — seguir ese patrón en clases nuevas que lo requieran.
- Sin capas de compatibilidad hacia atrás ni defaults inseguros "por las dudas" (`AGENTS.md`): se elimina configuración obsoleta en vez de mantenerla en paralelo.
- Ningún secreto ni hostname de producción se commitea al repo — todo lo sensible sale por variable de entorno.
- El mecanismo de despliegue sigue siendo Docker Compose (no se migra a un PaaS).

---

### Task 1: JWT signing key desde variable de entorno, con fail-fast

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/util/JwtUtil.java`
- Modify: `backend/src/test/java/com/ecommerce/util/JwtUtilTest.java`
- Modify: `backend/src/main/resources/application-dev.properties`
- Modify: `backend/src/main/resources/application-prod.properties`

**Interfaces:**
- Produces: `JwtUtil.init()` (package-private, `@PostConstruct`) — valida y cachea la `SecretKey`; usado directamente por los tests del mismo paquete.
- Produces: propiedad `jwt.secret` leída por `JwtUtil` vía `@Value("${jwt.secret}")`.

Hoy `JwtUtil.getSigningKey()` (líneas 18-22) devuelve una clave HS512 fija hardcodeada en el código y versionada en git. Pasa a leerse de `jwt.secret`, validarse una sola vez al arrancar, y cachearse.

- [x] **Step 1: Escribir el test que falla — `init()` debe rechazar un secret ausente o débil**

En `backend/src/test/java/com/ecommerce/util/JwtUtilTest.java`, reemplazar el método `setUp()` y agregar los tests nuevos (dejar el resto de los tests existentes tal cual, ya que seguirán pasando):

```java
package com.ecommerce.util;

import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - JwtUtil")
class JwtUtilTest {

    private static final String VALID_SECRET =
            "test-secret-key-para-hs512-con-longitud-suficiente-de-al-menos-64-caracteres-xxxxxxxxxxxx";

    private JwtUtil jwtUtil;

    private String email;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        email = "test@example.com";
        userId = 1L;
        jwtUtil = new JwtUtil();
        setField("secret", VALID_SECRET);
        setField("expiration", 3600000); // 1 hora, para que los tests no dependan del reloj
        jwtUtil.init();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JwtUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(jwtUtil, value);
    }

    @Test
    @DisplayName("init() debería fallar si jwt.secret no está configurado")
    void testInit_ThrowsWhenSecretMissing() throws Exception {
        JwtUtil sinSecret = new JwtUtil();
        setFieldOn(sinSecret, "secret", null);

        assertThrows(IllegalStateException.class, sinSecret::init);
    }

    @Test
    @DisplayName("init() debería fallar si jwt.secret es demasiado corto para HS512")
    void testInit_ThrowsWhenSecretTooShort() throws Exception {
        JwtUtil secretCorto = new JwtUtil();
        setFieldOn(secretCorto, "secret", "muy-corto");

        assertThrows(WeakKeyException.class, secretCorto::init);
    }

    private void setFieldOn(JwtUtil target, String name, Object value) throws Exception {
        Field field = JwtUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Debería generar un token JWT válido")
    void testGenerateToken() {
        String nuevoToken = jwtUtil.generateToken(email, userId);

        assertNotNull(nuevoToken);
        assertFalse(nuevoToken.isEmpty());
        assertEquals(3, nuevoToken.split("\\.").length);
    }

    @Test
    @DisplayName("Debería extraer el email del token")
    void testGetEmailFromToken() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertEquals(email, jwtUtil.getEmailFromToken(tokenFresco));
    }

    @Test
    @DisplayName("Debería extraer el userId del token")
    void testGetUserIdFromToken() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertEquals(userId, jwtUtil.getUserIdFromToken(tokenFresco));
    }

    @Test
    @DisplayName("Debería validar un token válido")
    void testValidateToken_TokenValido() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertTrue(jwtUtil.validateToken(tokenFresco));
    }

    @Test
    @DisplayName("Debería retornar false para un token inválido")
    void testValidateToken_TokenInvalido() {
        assertFalse(jwtUtil.validateToken("token.invalido.malformado"));
    }

    @Test
    @DisplayName("Debería retornar false para un token nulo")
    void testValidateToken_TokenNulo() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("Debería obtener la fecha de expiración del token")
    void testGetExpirationDateFromToken() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        Date fechaExpiracion = jwtUtil.getExpirationDateFromToken(tokenFresco);

        assertNotNull(fechaExpiracion);
        assertTrue(fechaExpiracion.after(new Date()));
    }

    @Test
    @DisplayName("Debería verificar si un token está expirado")
    void testIsTokenExpired_TokenNoExpirado() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertFalse(jwtUtil.isTokenExpired(tokenFresco));
    }

    @Test
    @DisplayName("Debería generar tokens diferentes para diferentes emails")
    void testGenerateToken_DiferentesEmails() {
        String token1 = jwtUtil.generateToken("email1@test.com", 1L);
        String token2 = jwtUtil.generateToken("email2@test.com", 2L);

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Debería generar tokens diferentes para el mismo email pero diferentes usuarios")
    void testGenerateToken_DiferentesUsuarios() {
        String token1 = jwtUtil.generateToken(email, 1L);
        String token2 = jwtUtil.generateToken(email, 2L);

        assertNotEquals(token1, token2);
    }
}
```

- [x] **Step 2: Correr los tests y verificar que fallan**

Run: `cd backend && mvnd test -Dtest=JwtUtilTest`
Expected: FALLA — `JwtUtil` todavía no tiene el campo `secret` ni el método `init()`.

- [x] **Step 3: Implementar `JwtUtil` leyendo el secret de configuración**

Reemplazar el contenido completo de `backend/src/main/java/com/ecommerce/util/JwtUtil.java`:

```java
package com.ecommerce.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration:86400000}") // 24 horas en milisegundos
    private int expiration;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "jwt.secret no está configurado. Definí la variable de entorno JWT_SECRET.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(String email, Long userId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.get("userId", Long.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Date getExpirationDateFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return claims.getExpiration();
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = getExpirationDateFromToken(token);
            return expiration.before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
```

- [x] **Step 4: Agregar `jwt.secret` a la configuración de dev y prod**

En `backend/src/main/resources/application-dev.properties`, agregar al final:

```properties
# JWT (clave fija de desarrollo, no sensible, solo para entorno local)
jwt.secret=dev-only-secret-key-no-usar-en-produccion-necesita-64-caracteres-minimo-xxxxxxxx
jwt.expiration=86400000
```

En `backend/src/main/resources/application-prod.properties`, agregar al final (sin default — si `JWT_SECRET` no está seteada, el arranque falla al resolver el placeholder):

```properties
# JWT (obligatorio por variable de entorno, sin default)
jwt.secret=${JWT_SECRET}
jwt.expiration=${JWT_EXPIRATION:86400000}
```

- [x] **Step 5: Correr los tests y verificar que pasan**

Run: `cd backend && mvnd test -Dtest=JwtUtilTest`
Expected: PASS — los 13 tests (11 existentes + 2 nuevos) en verde.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/util/JwtUtil.java \
        backend/src/test/java/com/ecommerce/util/JwtUtilTest.java \
        backend/src/main/resources/application-dev.properties \
        backend/src/main/resources/application-prod.properties
git commit -m "security: leer JWT signing key de variable de entorno con fail-fast"
```

---

### Task 2: Credenciales de MySQL solo por variable de entorno en prod

**Files:**
- Modify: `backend/src/main/resources/application-prod.properties`
- Modify: `docker-compose.prod.yml`
- Create: `.env.example`

**Interfaces:**
- Consumes: ninguna (cambio de configuración pura).
- Produces: contrato de variables de entorno requeridas en prod (`MYSQL_ROOT_PASSWORD`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`) — usado por las tareas siguientes de este plan.

Este cambio es de configuración, no de lógica de negocio — no hay test unitario razonable; la verificación es arrancar el proceso con y sin las variables seteadas.

- [x] **Step 1: Quitar usuario/password hardcodeados de `application-prod.properties`**

En `backend/src/main/resources/application-prod.properties`, reemplazar las líneas 13-16:

```properties
spring.datasource.url=jdbc:mysql://localhost:3308/ecommerce_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
spring.datasource.username=root
spring.datasource.password=password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

por:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
```

y reemplazar el bloque de CORS (líneas 35-39, que ya no se usa — ver Task 3) por nada; se elimina en el Task 3.

- [x] **Step 2: Forzar que Docker Compose falle si faltan las variables sensibles en prod**

En `docker-compose.prod.yml`, reemplazar los defaults inseguros por la sintaxis `${VAR:?mensaje}` de Compose (falla el `up` si la variable no está seteada), tanto en `mysql-db` como en `backend`:

```yaml
  mysql-db:
    image: mysql:8.0
    container_name: ecommerce-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?Falta MYSQL_ROOT_PASSWORD en el .env}
      MYSQL_DATABASE: ${MYSQL_DATABASE:?Falta MYSQL_DATABASE en el .env}
      MYSQL_USER: ${MYSQL_USER:?Falta MYSQL_USER en el .env}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?Falta MYSQL_PASSWORD en el .env}
```

```yaml
  backend:
    image: bautistabozzer/ecommerce-backend:latest
    container_name: ecommerce-backend
    restart: unless-stopped
    ports:
      - "${BACKEND_PORT:-8081}:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql-db:3306/${MYSQL_DATABASE:?Falta MYSQL_DATABASE en el .env}?useSSL=true&requireSSL=true&verifyServerCertificate=false&serverTimezone=UTC&allowPublicKeyRetrieval=true
      - SPRING_DATASOURCE_USERNAME=${SPRING_DATASOURCE_USERNAME:?Falta SPRING_DATASOURCE_USERNAME en el .env}
      - SPRING_DATASOURCE_PASSWORD=${SPRING_DATASOURCE_PASSWORD:?Falta SPRING_DATASOURCE_PASSWORD en el .env}
      - JWT_SECRET=${JWT_SECRET:?Falta JWT_SECRET en el .env}
      - CORS_ALLOWED_ORIGINS=${CORS_ALLOWED_ORIGINS:?Falta CORS_ALLOWED_ORIGINS en el .env}
      - SERVER_PORT=8081
```

(Nota: `SPRING_PROFILES_ACTIVE` pasa de `docker` a `prod` — el perfil `docker` no existe como archivo `application-docker.properties`, es un perfil fantasma que hoy cae en los defaults de `application.properties`; se corrige para que use el perfil `prod` real.)

- [x] **Step 3: Documentar las variables requeridas**

Crear `.env.example` en la raíz del repo:

```bash
# Copiar a .env y completar con valores reales antes de levantar docker-compose.prod.yml
# Nunca commitear el .env real.

MYSQL_ROOT_PASSWORD=
MYSQL_DATABASE=ecommerce_db
MYSQL_USER=user_app
MYSQL_PASSWORD=

SPRING_DATASOURCE_USERNAME=user_app
SPRING_DATASOURCE_PASSWORD=

# Generar con: openssl rand -base64 64
JWT_SECRET=

# Dominio real de producción, separado por comas si hay más de uno
CORS_ALLOWED_ORIGINS=https://tu-dominio.com

BACKEND_PORT=8081
FRONTEND_PORT=80
```

- [x] **Step 4: Verificar el fail-fast manualmente**

Run: `docker compose -f docker-compose.prod.yml config`
Expected: error indicando qué variable falta (probar sin `.env` presente). Luego, con un `.env` completo basado en `.env.example`:

Run: `docker compose -f docker-compose.prod.yml config`
Expected: el YAML resuelto se imprime sin errores.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/resources/application-prod.properties docker-compose.prod.yml .env.example
git commit -m "security: eliminar credenciales de MySQL hardcodeadas, requerir variables de entorno en prod"
```

---

### Task 3: CORS centralizado en un único bean

**Files:**
- Create: `backend/src/main/java/com/ecommerce/config/CorsConfig.java`
- Create: `backend/src/test/java/com/ecommerce/config/CorsConfigTest.java`
- Modify: `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/AdminController.java` (quitar línea 23)
- Modify: `backend/src/main/java/com/ecommerce/controller/AuthController.java` (quitar línea 22)
- Modify: `backend/src/main/java/com/ecommerce/controller/CategoriaController.java` (quitar línea 18)
- Modify: `backend/src/main/java/com/ecommerce/controller/PedidoController.java` (quitar línea 26)
- Modify: `backend/src/main/java/com/ecommerce/controller/ProductoController.java` (quitar línea 20)
- Modify: `backend/src/main/java/com/ecommerce/controller/VentasController.java` (quitar línea 24)
- Modify: `backend/src/main/resources/application-dev.properties`
- Modify: `backend/src/main/resources/application-prod.properties`

**Interfaces:**
- Produces: `CorsConfig.corsConfigurationSource()` — bean `CorsConfigurationSource` consumido por `SecurityConfig.securityFilterChain`.
- Consumes: propiedad `cors.allowed-origins` (string separado por comas).

Hoy cada controller repite `@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})` (6 ocurrencias) y las propiedades `spring.web.cors.*` en `application-dev.properties`/`application-prod.properties` no las lee ningún código (Spring Boot no las bindea automáticamente para MVC) — son configuración muerta. Se reemplaza todo por un único `CorsConfigurationSource` leído de `cors.allowed-origins` y enchufado en `SecurityConfig`.

- [x] **Step 1: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/config/CorsConfigTest.java`:

```java
package com.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios - CorsConfig")
class CorsConfigTest {

    @Test
    @DisplayName("Debería exponer únicamente los orígenes configurados, sin espacios")
    void testCorsConfigurationSource_ParseaOrigenes() throws Exception {
        CorsConfig corsConfig = new CorsConfig();
        setAllowedOrigins(corsConfig, "https://tienda.com, https://admin.tienda.com");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/productos");
        CorsConfiguration resolved = source.getCorsConfiguration(request);

        assertNotNull(resolved);
        assertEquals(2, resolved.getAllowedOrigins().size());
        assertTrue(resolved.getAllowedOrigins().contains("https://tienda.com"));
        assertTrue(resolved.getAllowedOrigins().contains("https://admin.tienda.com"));
        assertTrue(resolved.getAllowCredentials());
    }

    private void setAllowedOrigins(CorsConfig target, String value) throws Exception {
        Field field = CorsConfig.class.getDeclaredField("allowedOriginsRaw");
        field.setAccessible(true);
        field.set(target, value);
    }
}
```

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=CorsConfigTest`
Expected: FALLA — `CorsConfig` no existe todavía.

- [x] **Step 3: Implementar `CorsConfig`**

Crear `backend/src/main/java/com/ecommerce/config/CorsConfig.java`:

```java
package com.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

- [x] **Step 4: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=CorsConfigTest`
Expected: PASS

- [x] **Step 5: Enchufar el bean en `SecurityConfig` y quitar `@CrossOrigin` de los 6 controllers**

En `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`, agregar el import `org.springframework.web.cors.CorsConfigurationSource`, inyectar el bean y llamar a `.cors(...)` antes de `.csrf(...)` dentro de `securityFilterChain`:

```java
    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                // ... resto sin cambios
```

En cada uno de estos 6 archivos, borrar la línea `@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})` y el import `org.springframework.web.bind.annotation.CrossOrigin` si quedó sin usar (los controllers importan con `import org.springframework.web.bind.annotation.*;`, así que no hace falta tocar imports):
- `AdminController.java:23`
- `AuthController.java:22`
- `CategoriaController.java:18`
- `PedidoController.java:26`
- `ProductoController.java:20`
- `VentasController.java:24`

- [x] **Step 6: Reemplazar las propiedades CORS muertas por `cors.allowed-origins`**

En `backend/src/main/resources/application-dev.properties`, reemplazar el bloque (líneas 24-28):

```properties
# Configuración de CORS
spring.web.cors.allowed-origins=http://localhost:3000,http://localhost:5173
spring.web.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS
spring.web.cors.allowed-headers=*
spring.web.cors.allow-credentials=true
```

por:

```properties
# CORS
cors.allowed-origins=http://localhost:3000,http://localhost:5173
```

En `backend/src/main/resources/application-prod.properties`, reemplazar el bloque equivalente (líneas 35-39, ya vaciado en el Task 2) por:

```properties
# CORS (obligatorio por variable de entorno, sin default)
cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
```

- [x] **Step 7: Correr toda la suite y verificar que compila y pasa**

Run: `cd backend && mvnd test`
Expected: PASS (todos los tests existentes siguen en verde, más el nuevo `CorsConfigTest`).

- [x] **Step 8: Commit**

```bash
git add backend/src/main/java/com/ecommerce/config/CorsConfig.java \
        backend/src/test/java/com/ecommerce/config/CorsConfigTest.java \
        backend/src/main/java/com/ecommerce/config/SecurityConfig.java \
        backend/src/main/java/com/ecommerce/controller/AdminController.java \
        backend/src/main/java/com/ecommerce/controller/AuthController.java \
        backend/src/main/java/com/ecommerce/controller/CategoriaController.java \
        backend/src/main/java/com/ecommerce/controller/PedidoController.java \
        backend/src/main/java/com/ecommerce/controller/ProductoController.java \
        backend/src/main/java/com/ecommerce/controller/VentasController.java \
        backend/src/main/resources/application-dev.properties \
        backend/src/main/resources/application-prod.properties
git commit -m "security: centralizar configuración CORS en un único bean por variable de entorno"
```

---

### Task 4: Habilitar method security y agregar infraestructura de tests de integración

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`
- Create: `backend/src/test/resources/application-test.properties`
- Create: `backend/src/test/java/com/ecommerce/controller/AdminControllerSecurityTest.java`

**Interfaces:**
- Produces: perfil Spring `test` (H2 en memoria) disponible para todos los tests `@SpringBootTest` de las tareas siguientes.

Hallazgo: en toda la base de código no existe ningún `@EnableMethodSecurity` (se verificó con grep). Esto significa que el `@PreAuthorize("hasRole('ADMIN')")` ya presente en `AdminController` **no se está aplicando** — hoy `/api/admin/**` solo queda protegido porque `SecurityConfig` también lo lista como `.hasRole("ADMIN")` a nivel de URL. Si mañana alguien protege un endpoint nuevo solo con `@PreAuthorize`, quedaría completamente abierto. Se habilita method security y se prueba con un test de integración real (MockMvc + contexto Spring), algo que hoy no existe en el proyecto — los tests actuales son unitarios puros con Mockito y nunca levantan el contexto de Spring.

- [x] **Step 1: Crear el perfil de test**

Crear `backend/src/test/resources/application-test.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=create-drop
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect

jwt.secret=test-secret-key-para-hs512-con-longitud-suficiente-de-al-menos-64-caracteres-xxxxxxxxxxxx
jwt.expiration=3600000

cors.allowed-origins=http://localhost:5173
```

- [x] **Step 2: Escribir el test de integración que falla**

Crear `backend/src/test/java/com/ecommerce/controller/AdminControllerSecurityTest.java`:

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Seguridad de AdminController")
class AdminControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String tokenUsuarioComun;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Pérez").username("juan").email("juan@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        tokenUsuarioComun = jwtUtil.generateToken(usuario.getEmail(), usuario.getId());

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin").apellido("Root").username("admin").email("admin@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.ADMIN).build());
        tokenAdmin = jwtUtil.generateToken(admin.getEmail(), admin.getId());
    }

    @Test
    @DisplayName("Un usuario común no puede listar usuarios vía /api/admin/usuarios")
    void testListarUsuarios_UsuarioComun_Forbidden() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenUsuarioComun))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un admin sí puede listar usuarios vía /api/admin/usuarios")
    void testListarUsuarios_Admin_Ok() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }
}
```

- [x] **Step 3: Correr el test — debería pasar igual (protección vía URL matcher), confirmando el punto de partida**

Run: `cd backend && mvnd test -Dtest=AdminControllerSecurityTest`
Expected: PASS — porque `SecurityConfig` ya protege `/api/admin/**` a nivel de URL, independientemente de si `@PreAuthorize` funciona o no. Este test sirve como red de seguridad para el resto del plan, no prueba todavía que method security esté activo.

- [x] **Step 4: Habilitar method security**

En `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`, agregar el import `org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity` y la anotación en la clase:

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
```

- [x] **Step 5: Correr el test de nuevo y verificar que sigue en verde**

Run: `cd backend && mvnd test -Dtest=AdminControllerSecurityTest`
Expected: PASS (ahora la protección de `/api/admin/**` está doblemente garantizada: por URL matcher y por `@PreAuthorize`).

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/config/SecurityConfig.java \
        backend/src/test/resources/application-test.properties \
        backend/src/test/java/com/ecommerce/controller/AdminControllerSecurityTest.java
git commit -m "security: habilitar @EnableMethodSecurity y agregar primer test de integración"
```

---

### Task 5: Refactor de `PedidoController` a `@PreAuthorize` + `@AuthenticationPrincipal`

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/controller/PedidoController.java`
- Create: `backend/src/test/java/com/ecommerce/controller/PedidoControllerSecurityTest.java`

**Interfaces:**
- Consumes: `Usuario` como principal de Spring Security (ya lo provee `JwtAuthenticationFilter` vía `UserDetailsService`, `Usuario implements UserDetails`).
- Produces: mismos endpoints y contratos JSON de `PedidoDTO` que hoy — solo cambia el mecanismo de autorización interno.

Hoy `PedidoController` parsea el header `Authorization` a mano en cada método (`getUserIdFromAuth`, `isAdmin`, líneas 246-270) en vez de delegar en Spring Security. Como ninguna de sus rutas (`/api/pedidos/**`) está listada explícitamente en `SecurityConfig` más allá del catch-all `.anyRequest().authenticated()`, la única barrera real para los endpoints de admin (`GET /api/pedidos`, `PUT /api/pedidos/{id}/estado`, `GET /api/pedidos/estado/{estado}`, `GET /api/pedidos/admin/**`) es ese método `isAdmin()` casero — sin defensa en profundidad. Se reemplaza por `@PreAuthorize`, que ahora sí es efectivo gracias al Task 4.

- [x] **Step 1: Escribir el test de integración que falla**

Crear `backend/src/test/java/com/ecommerce/controller/PedidoControllerSecurityTest.java`:

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Seguridad de PedidoController")
class PedidoControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String tokenUsuarioComun;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Pérez").username("juan").email("juan@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        tokenUsuarioComun = jwtUtil.generateToken(usuario.getEmail(), usuario.getId());

        Usuario admin = usuarioRepository.save(Usuario.builder()
                .nombre("Admin").apellido("Root").username("admin").email("admin@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.ADMIN).build());
        tokenAdmin = jwtUtil.generateToken(admin.getEmail(), admin.getId());
    }

    @Test
    @DisplayName("Un usuario común no puede listar todos los pedidos")
    void testObtenerTodosLosPedidos_UsuarioComun_Forbidden() throws Exception {
        mockMvc.perform(get("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenUsuarioComun))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un admin sí puede listar todos los pedidos")
    void testObtenerTodosLosPedidos_Admin_Ok() throws Exception {
        mockMvc.perform(get("/api/pedidos")
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un usuario logueado puede ver su propio historial de pedidos")
    void testMisPedidos_UsuarioComun_Ok() throws Exception {
        mockMvc.perform(get("/api/pedidos/mis-pedidos")
                        .header("Authorization", "Bearer " + tokenUsuarioComun))
                .andExpect(status().isOk());
    }
}
```

- [x] **Step 2: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=PedidoControllerSecurityTest`
Expected: FALLA en `testObtenerTodosLosPedidos_UsuarioComun_Forbidden` — hoy `isAdmin()` sí lo bloquea correctamente (devuelve 403 vía `ForbiddenException`), así que en realidad este caso puede pasar; lo que confirma la falla es que **no hay ninguna garantía a nivel de framework** — para dejarlo en rojo de verdad antes de refactorizar, comentar temporalmente el cuerpo de `isAdmin()` en `PedidoController` para que devuelva siempre `true` y confirmar que el test de "usuario común forbidden" falla. Revertir ese comentario antes de seguir al Step 3.

- [x] **Step 3: Refactorizar `PedidoController`**

En `backend/src/main/java/com/ecommerce/controller/PedidoController.java`:

1. Agregar imports: `org.springframework.security.access.prepost.PreAuthorize` y `org.springframework.security.core.annotation.AuthenticationPrincipal` y `com.ecommerce.entity.Usuario`.
2. Quitar el `@Autowired private JwtUtil jwtUtil;` (línea 35-36) y los métodos auxiliares `getUserIdFromAuth` e `isAdmin` (líneas 241-270).
3. Reemplazar cada firma de método:

```java
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerTodosLosPedidos() {
        List<PedidoDTO> pedidos = pedidoService.obtenerTodosLosPedidos()
                .stream()
                .map(PedidoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/mis-pedidos")
    public ResponseEntity<?> obtenerMisPedidos(@AuthenticationPrincipal Usuario usuario) {
        List<PedidoDTO> pedidos = pedidoService.obtenerPedidosPorUsuario(usuario.getId())
                .stream()
                .map(PedidoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPedidoPorId(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.obtenerPedidoPorId(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));

        boolean esDueño = pedido.getUsuario().getId().equals(usuario.getId());
        boolean esAdmin = usuario.getRole() == com.ecommerce.entity.Role.ADMIN;
        if (!esDueño && !esAdmin) {
            throw new ForbiddenException("No tienes permiso para ver este pedido");
        }

        return ResponseEntity.ok(new PedidoDTO(pedido));
    }

    @PostMapping
    public ResponseEntity<?> crearPedido(@RequestBody CreatePedidoDTO createPedidoDTO,
                                          @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.crearPedido(usuario.getId(), createPedidoDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PedidoDTO(pedido));
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> actualizarEstado(@PathVariable Long id, @RequestParam EstadoPedido estado) {
        Pedido pedido = pedidoService.actualizarEstado(id, estado);
        return ResponseEntity.ok(new PedidoDTO(pedido));
    }

    @PutMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarPedido(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.cancelarPedido(id, usuario.getId());
        return ResponseEntity.ok(new PedidoDTO(pedido));
    }

    @GetMapping("/estado/{estado}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerPedidosPorEstado(@PathVariable EstadoPedido estado) {
        List<PedidoDTO> pedidos = pedidoService.obtenerPedidosPorEstado(estado)
                .stream()
                .map(PedidoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    @GetMapping("/admin/ventas-totales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerTodasLasVentas() {
        List<Map<String, Object>> todasLasVentas = pedidoService.obtenerTodosLosPedidos()
                .stream()
                .flatMap(pedido -> pedido.getItems().stream().map(item -> {
                    Map<String, Object> venta = new HashMap<>();
                    venta.put("detalleId", item.getId());
                    venta.put("pedidoId", pedido.getId());
                    venta.put("productoNombre", item.getProductoNombre());
                    venta.put("cantidad", item.getCantidad());
                    venta.put("subtotal", item.getSubtotal());
                    venta.put("estadoItem", item.getEstadoItem());
                    venta.put("vendedorId", item.getVendedor() != null ? item.getVendedor().getId() : null);
                    venta.put("vendedorNombre", item.getVendedor() != null ?
                            item.getVendedor().getNombre() + " " + item.getVendedor().getApellido() : null);
                    venta.put("compradorNombre", pedido.getUsuario() != null ?
                            pedido.getUsuario().getNombre() + " " + pedido.getUsuario().getApellido() : null);
                    venta.put("fechaPedido", pedido.getCreatedAt());
                    return venta;
                }))
                .collect(Collectors.toList());

        return ResponseEntity.ok(todasLasVentas);
    }

    @GetMapping("/admin/estadisticas-generales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerEstadisticasGenerales() {
        List<Pedido> todosPedidos = pedidoService.obtenerTodosLosPedidos();

        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("totalPedidos", todosPedidos.size());
        estadisticas.put("totalItems", todosPedidos.stream()
                .mapToLong(p -> p.getItems().size())
                .sum());
        estadisticas.put("itemsPendientes", todosPedidos.stream()
                .flatMap(p -> p.getItems().stream())
                .filter(i -> i.getEstadoItem() == EstadoPedido.PENDIENTE)
                .count());
        estadisticas.put("itemsEntregados", todosPedidos.stream()
                .flatMap(p -> p.getItems().stream())
                .filter(i -> i.getEstadoItem() == EstadoPedido.ENTREGADO)
                .count());

        return ResponseEntity.ok(estadisticas);
    }
```

El resto de la clase (imports de entidades/DTOs/excepciones, campos `pedidoService`/`usuarioService`) queda igual salvo lo indicado.

- [x] **Step 4: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=PedidoControllerSecurityTest`
Expected: PASS

- [x] **Step 5: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS — incluyendo los tests unitarios preexistentes que llamaban a los métodos del controller directamente (revisar `backend/src/test/java/com/ecommerce/controller/` si alguno instanciaba `PedidoController` a mano con los parámetros viejos; a la fecha de este plan no existe `PedidoControllerTest.java` en el repo, solo `PedidoServiceTest.java`, que no se ve afectado).

- [x] **Step 6: Commit**

```bash
git add backend/src/main/java/com/ecommerce/controller/PedidoController.java \
        backend/src/test/java/com/ecommerce/controller/PedidoControllerSecurityTest.java
git commit -m "security: refactorizar PedidoController a @PreAuthorize y @AuthenticationPrincipal"
```

---

### Task 6: Refactor de `ProductoController` y `VentasController` a `@AuthenticationPrincipal`

**Files:**
- Modify: `backend/src/main/java/com/ecommerce/controller/ProductoController.java`
- Modify: `backend/src/main/java/com/ecommerce/controller/VentasController.java`
- Modify: `backend/src/test/java/com/ecommerce/controller/ProductoControllerTest.java`

**Interfaces:**
- Consumes: `Usuario` como principal (igual que Task 5).
- Produces: mismos contratos JSON — solo cambia el mecanismo interno de obtención del usuario autenticado.

Mismo patrón repetido de parseo manual de `Authorization` en `ProductoController` (líneas 150-161) y `VentasController` (líneas al final del archivo). `SecurityConfig` ya exige `.authenticated()` a nivel de URL para `POST/PUT/DELETE /api/productos/**`, así que acá el cambio es de limpieza/consistencia (elimina lógica de parseo de JWT duplicada en 3 controllers), no cierra un agujero de autorización como en el Task 5 — pero reduce superficie de bugs futuros.

- [x] **Step 1: Revisar el test unitario existente que se ve afectado**

Leer `backend/src/test/java/com/ecommerce/controller/ProductoControllerTest.java` para confirmar cómo invoca hoy `crearProducto` (probablemente pasando un `authHeader` de String armado a mano). Ajustarlo para que en cambio pase un `Usuario` mockeado como segundo argumento, seguiendo el mismo patrón `@Mock`/`@InjectMocks` que ya usa el resto de la clase.

- [x] **Step 2: Correr el test existente y verificar que falla (por firma incompatible tras el Step 3)**

Run: `cd backend && mvnd test -Dtest=ProductoControllerTest`
Expected: en este punto todavía compila con la firma vieja — este step es informativo, el rojo real llega recién al cambiar la firma del controller en el Step 3, momento en el que el módulo no compila hasta ajustar el test.

- [x] **Step 3: Refactorizar `ProductoController.crearProducto`**

En `backend/src/main/java/com/ecommerce/controller/ProductoController.java`, agregar imports `org.springframework.security.core.annotation.AuthenticationPrincipal` y `com.ecommerce.entity.Usuario`; quitar el campo `jwtUtil` (líneas 26-27) y el método `getUserIdFromAuth` (líneas 149-161); reemplazar:

```java
    @PostMapping
    public ResponseEntity<ProductoDTO> crearProducto(@RequestBody Producto producto,
                                                       @AuthenticationPrincipal Usuario usuario) {
        Producto productoCreado = productoService.crearProducto(producto, usuario.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProductoDTO(productoCreado));
    }
```

- [x] **Step 4: Refactorizar `VentasController`**

En `backend/src/main/java/com/ecommerce/controller/VentasController.java`, mismo patrón: quitar `jwtUtil` y `getUserIdFromAuth`, agregar `@AuthenticationPrincipal Usuario usuario` a los cuatro métodos (`obtenerMisVentas`, `obtenerMisVentasPorEstado`, `obtenerVentaPorId`, `actualizarEstadoVenta`, `obtenerEstadisticasVentas`) y usar `usuario.getId()` en vez de `vendedorId` obtenido del header.

- [x] **Step 5: Ajustar `ProductoControllerTest.java`**

Actualizar la construcción del `Usuario` mock/real que se le pasa a `crearProducto` en el test, reemplazando el mecanismo de header por el objeto `Usuario` directamente (mismo patrón `Usuario.builder()...build()` que ya usan `AuthControllerTest`/`AdminControllerSecurityTest`).

- [x] **Step 6: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [x] **Step 7: Commit**

```bash
git add backend/src/main/java/com/ecommerce/controller/ProductoController.java \
        backend/src/main/java/com/ecommerce/controller/VentasController.java \
        backend/src/test/java/com/ecommerce/controller/ProductoControllerTest.java
git commit -m "refactor: unificar obtención del usuario autenticado vía @AuthenticationPrincipal"
```

---

### Task 7: Flyway con migración baseline, `ddl-auto=validate` en prod

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/resources/db/migration/V1__baseline_schema.sql`
- Modify: `backend/src/main/resources/application-prod.properties`

**Interfaces:**
- Produces: convención `backend/src/main/resources/db/migration/V{n}__descripcion.sql` para toda migración futura (usada por la Fase 1 de este roadmap).

**Importante — verificación previa obligatoria:** el DDL de este baseline se reconstruyó leyendo las entidades JPA actuales (`Usuario`, `Categoria`, `Producto`, `Pedido`, `DetallePedido`). Antes de aplicar esta migración contra cualquier base con datos reales, generá el DDL real de la base corriendo y comparalo:

```bash
mysqldump --no-data --skip-comments -u root -p ecommerce_db > /tmp/schema-real.sql
diff /tmp/schema-real.sql backend/src/main/resources/db/migration/V1__baseline_schema.sql
```

Ajustá el `V1__baseline_schema.sql` si hay diferencias antes de continuar.

- [x] **Step 1: Agregar la dependencia de Flyway**

En `backend/pom.xml`, agregar dentro de `<dependencies>` (junto a `mysql-connector-j`):

```xml
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-mysql</artifactId>
        </dependency>
```

(Spring Boot 3.2 gestiona la versión de Flyway vía el BOM de `spring-boot-starter-parent`, no hace falta fijar `<version>`.)

- [x] **Step 2: Crear la migración baseline**

Crear `backend/src/main/resources/db/migration/V1__baseline_schema.sql`:

```sql
CREATE TABLE usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    apellido VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL UNIQUE,
    descripcion TEXT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE productos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    category_id BIGINT,
    owner_user_id BIGINT,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_productos_categoria FOREIGN KEY (category_id) REFERENCES categorias(id),
    CONSTRAINT fk_productos_owner FOREIGN KEY (owner_user_id) REFERENCES usuarios(id)
);

CREATE TABLE producto_imagenes (
    producto_id BIGINT NOT NULL,
    imagen_url VARCHAR(255),
    CONSTRAINT fk_producto_imagenes_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

CREATE TABLE pedidos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    estado VARCHAR(50) NOT NULL,
    direccion_envio TEXT,
    notas VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    CONSTRAINT fk_pedidos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE detalle_pedidos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    vendedor_id BIGINT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    producto_nombre VARCHAR(255) NOT NULL,
    producto_imagen VARCHAR(255),
    estado_item VARCHAR(50) NOT NULL,
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT fk_detalle_vendedor FOREIGN KEY (vendedor_id) REFERENCES usuarios(id)
);
```

- [x] **Step 3: Pasar `ddl-auto` a `validate` en prod y dejar Flyway a cargo del esquema**

En `backend/src/main/resources/application-prod.properties`, reemplazar la línea `spring.jpa.hibernate.ddl-auto=update` por:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
```

`application-dev.properties` y `application-test.properties` quedan sin cambios (siguen con H2 + `create-drop`, no necesitan Flyway para desarrollo local rápido).

- [x] **Step 4: Verificar contra una MySQL limpia**

Run:
```bash
docker run --rm -d --name mysql-flyway-check -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=ecommerce_db -p 3309:3306 mysql:8.0
sleep 15
cd backend && mvnd spring-boot:run -Dspring-boot.run.profiles=prod \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:mysql://localhost:3309/ecommerce_db?useSSL=false&allowPublicKeyRetrieval=true --spring.datasource.username=root --spring.datasource.password=test --jwt.secret=verificacion-local-de-flyway-necesita-64-caracteres-minimo-xxxxxxx --cors.allowed-origins=http://localhost:5173"
```
Expected: el log muestra `Flyway ... Successfully applied 1 migration` y el contexto de Spring arranca sin el error `Schema-validation: missing table` ni `wrong column type`. Parar con Ctrl+C y `docker stop mysql-flyway-check`.

- [x] **Step 5: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/resources/db/migration/V1__baseline_schema.sql \
        backend/src/main/resources/application-prod.properties
git commit -m "infra: introducir Flyway con migración baseline, ddl-auto=validate en prod"
```

---

### Task 8: Rate limiting en `/api/auth/login` y `/api/auth/register`

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/ecommerce/security/AuthRateLimitFilter.java`
- Create: `backend/src/test/java/com/ecommerce/security/AuthRateLimitFilterTest.java`
- Modify: `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`

**Interfaces:**
- Produces: filtro `AuthRateLimitFilter` registrado antes de `JwtAuthenticationFilter` en la cadena de seguridad.

- [x] **Step 1: Agregar la dependencia de Bucket4j**

En `backend/pom.xml`:

```xml
        <dependency>
            <groupId>com.bucket4j</groupId>
            <artifactId>bucket4j_jdk17-core</artifactId>
            <version>8.10.1</version>
        </dependency>
```

- [x] **Step 2: Escribir el test que falla**

Crear `backend/src/test/java/com/ecommerce/security/AuthRateLimitFilterTest.java`:

```java
package com.ecommerce.security;

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
@DisplayName("Tests de Integración - AuthRateLimitFilter")
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String LOGIN_BODY =
            "{\"emailOrUsername\":\"no-existe@test.com\",\"password\":\"lo-que-sea\"}";

    @Test
    @DisplayName("Debería devolver 429 luego de superar el límite de intentos de login")
    void testRateLimit_BloqueaTrasLimiteDeIntentos() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());
    }
}
```

- [x] **Step 3: Correr el test y verificar que falla**

Run: `cd backend && mvnd test -Dtest=AuthRateLimitFilterTest`
Expected: FALLA — el sexto intento hoy devuelve 401 (credenciales inválidas), no 429.

- [x] **Step 4: Implementar el filtro**

Crear `backend/src/main/java/com/ecommerce/security/AuthRateLimitFilter.java`:

```java
package com.ecommerce.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> RUTAS_LIMITADAS = Set.of("/api/auth/login", "/api/auth/register");

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (RUTAS_LIMITADAS.contains(request.getRequestURI())) {
            Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), key -> nuevoBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Demasiados intentos, esperá unos minutos\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Bucket nuevoBucket() {
        Bandwidth limite = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limite).build();
    }
}
```

- [x] **Step 5: Registrar el filtro en `SecurityConfig`**

En `backend/src/main/java/com/ecommerce/config/SecurityConfig.java`, inyectar `AuthRateLimitFilter` y agregarlo a la cadena antes del filtro JWT:

```java
    @Autowired
    private AuthRateLimitFilter authRateLimitFilter;
```

y en `securityFilterChain`:

```java
            .addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
```

- [x] **Step 6: Correr el test y verificar que pasa**

Run: `cd backend && mvnd test -Dtest=AuthRateLimitFilterTest`
Expected: PASS

- [x] **Step 7: Correr toda la suite de backend**

Run: `cd backend && mvnd test`
Expected: PASS

- [x] **Step 8: Commit**

```bash
git add backend/pom.xml \
        backend/src/main/java/com/ecommerce/security/AuthRateLimitFilter.java \
        backend/src/test/java/com/ecommerce/security/AuthRateLimitFilterTest.java \
        backend/src/main/java/com/ecommerce/config/SecurityConfig.java
git commit -m "security: agregar rate limiting a /api/auth/login y /api/auth/register"
```

---

### Task 9: Dockerfile del backend

**Files:**
- Create: `backend/Dockerfile`
- Create: `backend/.dockerignore`

**Interfaces:**
- Produces: imagen Docker que expone el puerto `8081`, consumida por `docker-compose.yml` (`build: context: ./backend`).

Hoy `docker-compose.yml` referencia `./backend/Dockerfile`, pero ese archivo no existe en el repo — solo funciona `docker-compose.prod.yml`, que baja una imagen ya publicada manualmente en Docker Hub por un integrante del equipo. Se crea el Dockerfile real para que el build local (`docker-compose up --build`) funcione y para dejar de depender de una cuenta personal de Docker Hub en la Fase 4.

- [x] **Step 1: Crear `backend/.dockerignore`**

```
target/
.mvn/
*.md
```

- [x] **Step 2: Crear `backend/Dockerfile` (multi-stage: build con Maven, runtime con JRE)**

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
```

(Se usa la imagen oficial `maven:3.9-eclipse-temurin-17` para el stage de build en vez de instalar Maven a mano — el `pom.xml` ya define el repositorio extra de Lombok, así que `dependency:go-offline` lo resuelve sin configuración adicional.)

- [x] **Step 3: Build local y smoke test**

Run:
```bash
cd backend && docker build -t ecommerce-backend-local .
docker run --rm -e SPRING_PROFILES_ACTIVE=dev -p 8081:8081 ecommerce-backend-local
```
Expected: el log muestra `Started EcommerceBackendApplication` y `curl http://localhost:8081/api/categorias` (en otra terminal) devuelve `[]` o la lista de categorías, con status 200.

- [x] **Step 4: Commit**

```bash
git add backend/Dockerfile backend/.dockerignore
git commit -m "infra: agregar Dockerfile multi-stage del backend"
```

---

### Task 10: Dockerfile del frontend + Nginx

**Files:**
- Create: `frontend/Dockerfile`
- Create: `frontend/.dockerignore`
- Create: `frontend/nginx.conf`

**Interfaces:**
- Produces: imagen Docker que sirve el build de Vite en el puerto `80` y responde `200` en `/health` (contrato ya asumido por el healthcheck de `docker-compose.yml`).

Mismo gap que el backend: `docker-compose.yml` referencia `./frontend/Dockerfile`, que no existe. El healthcheck ya definido en `docker-compose.yml` (`curl -f http://localhost/health`) da la pista de que se espera un endpoint `/health` servido por Nginx.

- [x] **Step 1: Crear `frontend/.dockerignore`**

```
node_modules/
dist/
*.md
```

- [x] **Step 2: Crear `frontend/nginx.conf`**

```nginx
server {
    listen 80;
    server_name _;
    root /usr/share/nginx/html;
    index index.html;

    location /health {
        access_log off;
        return 200 "ok\n";
        add_header Content-Type text/plain;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

- [x] **Step 3: Crear `frontend/Dockerfile` (multi-stage: build con Node, runtime con Nginx)**

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

- [x] **Step 4: Build local y smoke test**

Run:
```bash
cd frontend && docker build -t ecommerce-frontend-local .
docker run --rm -p 8080:80 ecommerce-frontend-local
```
Expected: `curl http://localhost:8080/health` devuelve `ok` con status 200, y `curl http://localhost:8080/` devuelve el HTML de la SPA.

- [x] **Step 5: Commit**

```bash
git add frontend/Dockerfile frontend/.dockerignore frontend/nginx.conf
git commit -m "infra: agregar Dockerfile multi-stage del frontend con Nginx"
```

---

### Task 11: Reverse proxy con TLS automático delante del stack de producción

**Files:**
- Create: `Caddyfile`
- Modify: `docker-compose.prod.yml`
- Modify: `.env.example`

**Interfaces:**
- Consumes: `Dockerfile` de backend y frontend (Task 9, Task 10).
- Produces: stack de producción accesible por HTTPS en el dominio configurado, sin exponer los puertos 8081/80 directamente a internet.

Se elige Caddy sobre Nginx+Certbot porque renueva certificados de Let's Encrypt automáticamente sin cronjobs ni configuración manual — menos piezas para que este equipo mantenga.

- [x] **Step 1: Crear `Caddyfile`**

```
{$DOMAIN} {
    handle /api/* {
        reverse_proxy backend:8081
    }

    handle {
        reverse_proxy frontend:80
    }
}
```

- [x] **Step 2: Agregar el servicio `proxy` a `docker-compose.prod.yml` y dejar de publicar puertos directos**

Agregar el servicio (y quitar los `ports:` de `backend` y `frontend`, que pasan a comunicarse solo dentro de la red interna `ecommerce-network`):

```yaml
  proxy:
    image: caddy:2-alpine
    container_name: ecommerce-proxy
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    environment:
      - DOMAIN=${DOMAIN:?Falta DOMAIN en el .env}
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - backend
      - frontend
    networks:
      - ecommerce-network
```

Agregar el volumen nuevo al bloque `volumes:` del archivo:

```yaml
volumes:
  mysql_data:
    driver: local
  caddy_data:
    driver: local
  caddy_config:
    driver: local
```

Quitar el bloque `ports:` de los servicios `backend` y `frontend` en `docker-compose.prod.yml` (siguen accesibles entre contenedores por nombre de servicio dentro de `ecommerce-network`, ya no expuestos al host).

- [x] **Step 3: Documentar la variable `DOMAIN`**

En `.env.example`, agregar:

```bash
# Dominio real apuntado por DNS a este servidor (A record)
DOMAIN=tu-dominio.com
```

- [x] **Step 4: Verificar la configuración (sin poder emitir un certificado real desde este entorno de desarrollo)**

Run: `docker compose -f docker-compose.prod.yml config`
Expected: el YAML resuelve sin errores, con `proxy` presente y sin `ports:` en `backend`/`frontend`.

Nota: la emisión real del certificado Let's Encrypt requiere un dominio con DNS apuntando al servidor de producción y los puertos 80/443 abiertos — no se puede verificar end-to-end desde esta sesión. Queda como paso de checklist en la Fase 4 (deploy y go-live).

- [x] **Step 5: Commit**

```bash
git add Caddyfile docker-compose.prod.yml .env.example
git commit -m "infra: agregar reverse proxy Caddy con TLS automático delante del stack de prod"
```

---

## Resumen de verificación de la Fase 0

Al terminar las 11 tareas, correr una vez más de punta a punta:

```bash
cd backend && mvnd test
```

Expected: todos los tests en verde (unitarios preexistentes + los de integración nuevos de este plan).
