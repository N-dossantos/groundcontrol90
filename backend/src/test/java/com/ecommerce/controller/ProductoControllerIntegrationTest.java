package com.ecommerce.controller;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.Usuario;
import com.ecommerce.repository.PedidoRepository;
import com.ecommerce.repository.ProductoRepository;
import com.ecommerce.repository.UsuarioRepository;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
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

/**
 * Ejercita el alta y edición de productos de punta a punta (HTTP -> service -> base).
 * ProductoControllerTest mockea el service, así que no cubre el camino real.
 *
 * Deliberadamente SIN @Transactional: cada request tiene que commitear. Los errores
 * de mapeo de colecciones de Hibernate (p. ej. sustituir una colección con
 * orphanRemoval en vez de vaciarla) recién aparecen al hacer flush en el commit,
 * y un test que hace rollback no los ve nunca.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Alta y edición de productos con variantes")
class ProductoControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private Usuario vendedor;
    private String token;

    @BeforeEach
    void setUp() {
        vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("Vendedor").apellido("Integración").username("vendedor-integracion")
                .email("vendedor-integracion@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        token = jwtUtil.generateToken(vendedor.getEmail(), vendedor.getId());
    }

    @AfterEach
    void tearDown() {
        // Como no hay rollback, se limpia a mano lo creado por el test
        pedidoRepository.deleteAll(pedidoRepository.findByUsuarioIdOrderByCreatedAtDesc(vendedor.getId()));
        productoRepository.deleteAll(productoRepository.findByOwnerUserId(vendedor.getId()));
        usuarioRepository.delete(vendedor);
    }

    /** Crea un producto y devuelve su id. */
    private Long crearProducto(String variantesJson) throws Exception {
        String creado = mockMvc.perform(post("/api/productos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"producto":{"name":"Camiseta","description":"d","price":45000,
                                 "club":"Boca Juniors","liga":"LPA","temporada":"2026","tipo":"CAMISETA","images":[]},
                                 "variantes":%s}
                                """.formatted(variantesJson)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(creado).read("$.id", Integer.class).longValue();
    }

    @Test
    @DisplayName("POST /api/productos crea el producto con sus variantes y devuelve el stock total")
    void testCrearProductoConVariantes() throws Exception {
        String body = """
                {"producto":{"name":"Camiseta Titular 2026","description":"Camiseta oficial","price":45000,
                 "club":"Boca Juniors","liga":"Liga Profesional Argentina","temporada":"2026",
                 "tipo":"CAMISETA","images":["http://ejemplo/camiseta.png"]},
                 "variantes":[{"talle":"S","stock":5,"sku":"INT-S"},{"talle":"M","stock":8,"sku":"INT-M"}]}
                """;

        String creado = mockMvc.perform(post("/api/productos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.club").value("Boca Juniors"))
                .andExpect(jsonPath("$.tipo").value("CAMISETA"))
                .andExpect(jsonPath("$.stockTotal").value(13))
                .andExpect(jsonPath("$.variantes.length()").value(2))
                .andExpect(jsonPath("$.ownerUserId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // Releer desde la base: confirma que lo escrito sobrevivió al commit
        Long id = com.jayway.jsonpath.JsonPath.parse(creado).read("$.id", Integer.class).longValue();
        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockTotal").value(13))
                .andExpect(jsonPath("$.variantes.length()").value(2));
    }

    @Test
    @DisplayName("PUT /api/productos/{id} reemplaza las variantes y conserva el vendedor propietario")
    void testActualizarProductoReemplazandoVariantes() throws Exception {
        Long id = crearProducto("""
                [{"talle":"S","stock":5,"sku":"UPD-S"},{"talle":"M","stock":8,"sku":"UPD-M"}]""");

        // El talle S desaparece, aparece L, y el SKU UPD-M se reutiliza en la variante que queda
        mockMvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"producto":{"name":"Camiseta editada","description":"d","price":52000,
                                 "club":"Boca Juniors","liga":"LPA","temporada":"2027","tipo":"CAMISETA","images":[]},
                                 "variantes":[{"talle":"M","stock":4,"sku":"UPD-M"},{"talle":"L","stock":6,"sku":"UPD-L"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Camiseta editada"))
                .andExpect(jsonPath("$.temporada").value("2027"))
                .andExpect(jsonPath("$.stockTotal").value(10))
                .andExpect(jsonPath("$.variantes.length()").value(2))
                // El vendedor no viaja en el body del PUT: tiene que preservarse
                .andExpect(jsonPath("$.ownerUserId").value(vendedor.getId().intValue()));
    }

    @Test
    @DisplayName("Editar un producto ya vendido conserva el id de la variante vendida")
    void testActualizarProductoYaVendido_ConservaLaVariante() throws Exception {
        Long id = crearProducto("""
                [{"talle":"S","stock":5,"sku":"SOLD-S"},{"talle":"M","stock":8,"sku":"SOLD-M"}]""");
        Integer varianteM = leerVariante(id, "M", "id");

        comprar(varianteM, 2);

        // Editar el producto manteniendo el talle vendido: el historial de pedidos y los
        // carritos referencian el id de la variante, así que no puede cambiar
        mockMvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"producto":{"name":"Camiseta editada","description":"d","price":52000,
                                 "club":"Boca Juniors","liga":"LPA","temporada":"2026","tipo":"CAMISETA","images":[]},
                                 "variantes":[{"talle":"S","stock":3,"sku":"SOLD-S"},{"talle":"M","stock":9,"sku":"SOLD-M"}]}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockTotal").value(12));
        org.junit.jupiter.api.Assertions.assertEquals(varianteM, leerVariante(id, "M", "id"),
                "la variante vendida tiene que conservar su id");
    }

    @Test
    @DisplayName("No se puede eliminar un talle que ya tiene ventas")
    void testEliminarTalleVendido_Rechazado() throws Exception {
        Long id = crearProducto("""
                [{"talle":"S","stock":5,"sku":"DEL-S"},{"talle":"M","stock":8,"sku":"DEL-M"}]""");
        comprar(leerVariante(id, "M", "id"), 1);

        // Se intenta guardar el producto sin el talle M, que ya se vendió
        mockMvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"producto":{"name":"Camiseta","description":"d","price":45000,
                                 "club":"Boca Juniors","liga":"LPA","temporada":"2026","tipo":"CAMISETA","images":[]},
                                 "variantes":[{"talle":"S","stock":5,"sku":"DEL-S"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("talle M")));

        // El producto quedó intacto
        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(jsonPath("$.variantes.length()").value(2));
    }

    private Integer leerVariante(Long productoId, String talle, String campo) throws Exception {
        String json = mockMvc.perform(get("/api/productos/" + productoId))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(json)
                .read("$.variantes[?(@.talle=='" + talle + "')]." + campo, java.util.List.class)
                .get(0) instanceof Integer valor ? valor : null;
    }

    private void comprar(Integer varianteId, int cantidad) throws Exception {
        mockMvc.perform(post("/api/pedidos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productoVarianteId":%d,"cantidad":%d}],"direccionEnvio":"Calle Falsa 123"}
                                """.formatted(varianteId, cantidad)))
                .andExpect(status().isCreated());
    }
}
