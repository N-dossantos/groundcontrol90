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
