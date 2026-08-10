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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Tests de Integración - DireccionController")
class DireccionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private String token;
    private String tokenOtroUsuario;

    private static final String DIRECCION_JSON = """
            {"calle":"Av. Corrientes","numero":"1234","ciudad":"CABA","provincia":"Buenos Aires","codigoPostal":"1043","pais":"Argentina","esPredeterminada":true}
            """;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.flush();

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Pérez").username("juan").email("juan@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        token = jwtUtil.generateToken(usuario.getEmail(), usuario.getId());

        Usuario otro = usuarioRepository.save(Usuario.builder()
                .nombre("Ana").apellido("Gómez").username("ana").email("ana@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        tokenOtroUsuario = jwtUtil.generateToken(otro.getEmail(), otro.getId());
    }

    @Test
    @DisplayName("Debería crear y luego listar la dirección del usuario autenticado")
    void testCrearYListarDireccion() throws Exception {
        mockMvc.perform(post("/api/direcciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIRECCION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/direcciones")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].calle").value("Av. Corrientes"));
    }

    @Test
    @DisplayName("Un usuario no debería ver las direcciones de otro usuario")
    void testListarDirecciones_SoloLasPropias() throws Exception {
        mockMvc.perform(post("/api/direcciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIRECCION_JSON))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/direcciones")
                        .header("Authorization", "Bearer " + tokenOtroUsuario))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("Un usuario no debería poder eliminar la dirección de otro usuario")
    void testEliminarDireccionAjena_Forbidden() throws Exception {
        String creada = mockMvc.perform(post("/api/direcciones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(DIRECCION_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = com.jayway.jsonpath.JsonPath.parse(creada).read("$.id", Integer.class).longValue();

        mockMvc.perform(delete("/api/direcciones/" + id)
                        .header("Authorization", "Bearer " + tokenOtroUsuario))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token no se puede acceder a las direcciones")
    void testListarDirecciones_SinToken() throws Exception {
        mockMvc.perform(get("/api/direcciones"))
                .andExpect(status().isForbidden());
    }
}
