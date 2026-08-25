package com.ecommerce.controller;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.Usuario;
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
 * Regresión del IDOR de productos: antes de la Fase 5, PUT y DELETE de /api/productos/**
 * sólo exigían .authenticated(), así que en un marketplace multi-vendedor —donde todos
 * los vendedores tienen Role.USER— cualquier cuenta registrada podía reescribir el precio
 * o borrar el producto de otro.
 *
 * Sin @Transactional a propósito, igual que ProductoControllerIntegrationTest: cada
 * request tiene que commitear para que el chequeo de propiedad se ejercite contra la
 * entidad realmente persistida y no contra una que quedó en el contexto de persistencia.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Propiedad de productos y categorías")
class ProductoOwnershipSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private Usuario vendedorA;
    private Usuario vendedorB;
    private Usuario admin;
    private String tokenA;
    private String tokenB;
    private String tokenAdmin;

    @BeforeEach
    void setUp() {
        vendedorA = crearUsuario("vendedor-a", "vendedor-a@test.com", Role.USER);
        vendedorB = crearUsuario("vendedor-b", "vendedor-b@test.com", Role.USER);
        admin = crearUsuario("admin-ownership", "admin-ownership@test.com", Role.ADMIN);

        tokenA = jwtUtil.generateToken(vendedorA.getEmail(), vendedorA.getId());
        tokenB = jwtUtil.generateToken(vendedorB.getEmail(), vendedorB.getId());
        tokenAdmin = jwtUtil.generateToken(admin.getEmail(), admin.getId());
    }

    @AfterEach
    void tearDown() {
        // Como no hay rollback, se limpia a mano lo creado por el test
        for (Usuario u : java.util.List.of(vendedorA, vendedorB, admin)) {
            productoRepository.deleteAll(productoRepository.findByOwnerUserId(u.getId()));
        }
        usuarioRepository.deleteAll(java.util.List.of(vendedorA, vendedorB, admin));
    }

    private Usuario crearUsuario(String username, String email, Role role) {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Test").apellido("Ownership").username(username).email(email)
                .password(passwordEncoder.encode("password123")).role(role).build());
    }

    /** Crea un producto con el token dado y devuelve su id. */
    private Long crearProducto(String token) throws Exception {
        String creado = mockMvc.perform(post("/api/productos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"producto":{"name":"Camiseta","description":"d","price":45000,
                                 "club":"Boca Juniors","liga":"LPA","temporada":"2026",
                                 "tipo":"CAMISETA","images":[]},
                                 "variantes":[{"talle":"M","stock":5,"sku":"OWN-M"}]}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(creado).read("$.id", Integer.class).longValue();
    }

    private static final String BODY_EDICION = """
            {"producto":{"name":"Precio reescrito","description":"d","price":1,
             "club":"Boca Juniors","liga":"LPA","temporada":"2026","tipo":"CAMISETA","images":[]},
             "variantes":[{"talle":"M","stock":5,"sku":"OWN-M"}]}
            """;

    @Test
    @DisplayName("Un vendedor no puede editar el producto de otro vendedor")
    void testEditarProductoAjeno_Forbidden() throws Exception {
        Long id = crearProducto(tokenA);

        mockMvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_EDICION))
                .andExpect(status().isForbidden());

        // El precio original sobrevivió al intento
        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(45000));
    }

    @Test
    @DisplayName("Un vendedor no puede borrar el producto de otro vendedor")
    void testBorrarProductoAjeno_Forbidden() throws Exception {
        Long id = crearProducto(tokenA);

        mockMvc.perform(delete("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("El vendedor propietario sí puede editar su propio producto")
    void testEditarProductoPropio_Ok() throws Exception {
        Long id = crearProducto(tokenA);

        mockMvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Precio reescrito"));
    }

    @Test
    @DisplayName("El vendedor propietario sí puede borrar su propio producto")
    void testBorrarProductoPropio_NoContent() throws Exception {
        Long id = crearProducto(tokenA);

        mockMvc.perform(delete("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/productos/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un admin puede editar el producto de cualquier vendedor (moderación)")
    void testAdminEditaProductoAjeno_Ok() throws Exception {
        Long id = crearProducto(tokenA);

        mockMvc.perform(put("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Precio reescrito"));
    }

    @Test
    @DisplayName("Un admin puede borrar el producto de cualquier vendedor (moderación)")
    void testAdminBorraProductoAjeno_NoContent() throws Exception {
        Long id = crearProducto(tokenA);

        mockMvc.perform(delete("/api/productos/" + id)
                        .header("Authorization", "Bearer " + tokenAdmin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Un usuario sin rol admin no puede crear categorías")
    void testCrearCategoria_UsuarioComun_Forbidden() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Categoría pirata","descripcion":"no debería crearse"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario sin rol admin no puede borrar categorías")
    void testBorrarCategoria_UsuarioComun_Forbidden() throws Exception {
        mockMvc.perform(delete("/api/categorias/1")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Las categorías siguen siendo de lectura pública")
    void testListarCategorias_SinToken_Ok() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk());
    }
}
