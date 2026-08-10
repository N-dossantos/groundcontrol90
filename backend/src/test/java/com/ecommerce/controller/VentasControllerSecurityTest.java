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
@DisplayName("Tests de Integración - Seguridad de VentasController")
class VentasControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private String tokenVendedor;

    @BeforeEach
    void setUp() {
        usuarioRepository.deleteAll();
        usuarioRepository.flush();

        Usuario vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("Vendedor").apellido("Uno").username("vendedor").email("vendedor@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        tokenVendedor = jwtUtil.generateToken(vendedor.getEmail(), vendedor.getId());
    }

    @Test
    @DisplayName("Sin token no se pueden consultar las ventas propias")
    void testMisVentas_SinToken_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/ventas/mis-ventas"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un usuario logueado puede consultar sus propias ventas")
    void testMisVentas_Autenticado_Ok() throws Exception {
        mockMvc.perform(get("/api/ventas/mis-ventas")
                        .header("Authorization", "Bearer " + tokenVendedor))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un usuario logueado puede filtrar sus ventas por estado")
    void testMisVentasPorEstado_Autenticado_Ok() throws Exception {
        mockMvc.perform(get("/api/ventas/mis-ventas/estado/PENDIENTE")
                        .header("Authorization", "Bearer " + tokenVendedor))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un usuario logueado puede consultar sus estadísticas de ventas")
    void testEstadisticasVentas_Autenticado_Ok() throws Exception {
        mockMvc.perform(get("/api/ventas/estadisticas")
                        .header("Authorization", "Bearer " + tokenVendedor))
                .andExpect(status().isOk());
    }
}
