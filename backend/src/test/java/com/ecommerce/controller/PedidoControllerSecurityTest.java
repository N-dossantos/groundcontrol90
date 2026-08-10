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
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
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
        usuarioRepository.flush();

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
