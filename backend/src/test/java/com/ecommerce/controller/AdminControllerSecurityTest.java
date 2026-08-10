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
        // flush() fuerza el DELETE físico ya (en vez de dejarlo pendiente en la
        // sesión de Hibernate): Usuario usa GenerationType.IDENTITY, que fuerza un
        // INSERT inmediato en cada save() de más abajo. Sin este flush, ese INSERT
        // puede ejecutarse antes de que el DELETE en cola llegue a la base y
        // choca contra filas ya existentes (p.ej. las que siembra DataInitializer
        // al arrancar el contexto).
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

    @Test
    @DisplayName("Sin token no se puede listar usuarios vía /api/admin/usuarios")
    void testListarUsuarios_SinToken_Forbidden() throws Exception {
        // Se espera 403 y no 401: SecurityConfig no configura .httpBasic()/.formLogin()/
        // .exceptionHandling() con un AuthenticationEntryPoint propio, así que Spring usa
        // Http403ForbiddenEntryPoint por defecto, y AnonymousAuthenticationFilter deja pasar
        // la request sin token como "anónima" hasta el mismo AccessDeniedHandler que deniega
        // por rol. En esta app, 401 es inalcanzable para este endpoint.
        mockMvc.perform(get("/api/admin/usuarios"))
                .andExpect(status().isForbidden());
    }
}
