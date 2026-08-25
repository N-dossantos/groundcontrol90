package com.ecommerce.controller;

import com.ecommerce.entity.Role;
import com.ecommerce.entity.Usuario;
import com.ecommerce.repository.UsuarioRepository;
import com.ecommerce.service.AlmacenamientoService;
import com.ecommerce.util.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Subida de imágenes de producto. Reemplaza al URL.createObjectURL del frontend, que
 * generaba URLs blob: válidas sólo en la pestaña que las creó.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Subida de imágenes")
class ImagenControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private AlmacenamientoService almacenamientoService;

    private Usuario vendedor;
    private String token;

    /** PNG mínimo válido: los 8 bytes de firma alcanzan para la detección por contenido. */
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13
    };

    @BeforeEach
    void setUp() {
        vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("Vendedor").apellido("Imagenes").username("vendedor-imagenes")
                .email("vendedor-imagenes@test.com")
                .password(passwordEncoder.encode("password123")).role(Role.USER).build());
        token = jwtUtil.generateToken(vendedor.getEmail(), vendedor.getId());
    }

    @AfterEach
    void tearDown() {
        usuarioRepository.delete(vendedor);
    }

    @Test
    @DisplayName("Un vendedor logueado sube un PNG y recibe una URL bajo /uploads/")
    void testSubirPng_Ok() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "camiseta.png", "image/png", PNG);

        String body = mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/uploads/")))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.endsWith(".png")))
                .andReturn().getResponse().getContentAsString();

        // El archivo quedó realmente escrito, no sólo devuelto en el JSON
        String url = com.jayway.jsonpath.JsonPath.parse(body).read("$.url");
        Path enDisco = almacenamientoService.getDirectorio().resolve(url.substring("/uploads/".length()));
        assertTrue(Files.exists(enDisco), "el archivo debería existir en " + enDisco);
        Files.deleteIfExists(enDisco);
    }

    @Test
    @DisplayName("La imagen subida se sirve públicamente, sin token")
    void testImagenSubidaEsPublica() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "camiseta.png", "image/png", PNG);

        String body = mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String url = com.jayway.jsonpath.JsonPath.parse(body).read("$.url");

        // Sin Authorization: el catálogo lo ve cualquier visitante
        mockMvc.perform(get(url)).andExpect(status().isOk());

        Files.deleteIfExists(almacenamientoService.getDirectorio()
                .resolve(url.substring("/uploads/".length())));
    }

    @Test
    @DisplayName("Sin token no se puede subir nada")
    void testSubirSinToken_Unauthorized() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "camiseta.png", "image/png", PNG);

        int status = mockMvc.perform(multipart("/api/imagenes").file(archivo))
                .andReturn().getResponse().getStatus();

        assertTrue(status == 401 || status == 403,
                "debería rechazar el anónimo, devolvió " + status);
    }

    @Test
    @DisplayName("Un archivo que no es imagen se rechaza aunque diga image/png")
    void testContentTypeMentido_BadRequest() throws Exception {
        // El Content-Type y el nombre los controla el cliente: lo que decide es el contenido
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile archivo = new MockMultipartFile("file", "inocente.png", "image/png", html);

        mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un SVG se rechaza: es XML y puede llevar script adentro")
    void testSvg_BadRequest() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        MockMultipartFile archivo = new MockMultipartFile("file", "logo.svg", "image/svg+xml", svg);

        mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Un archivo vacío se rechaza")
    void testArchivoVacio_BadRequest() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "vacio.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("El nombre que manda el cliente no se usa: no hay path traversal")
    void testNombreConPathTraversal_NoEscapaDelDirectorio() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile(
                "file", "../../../../tmp/evil.png", "image/png", PNG);

        String body = mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String url = com.jayway.jsonpath.JsonPath.parse(body).read("$.url");
        assertFalse(url.contains(".."), "la URL no debería arrastrar el nombre del cliente: " + url);
        assertFalse(url.contains("evil"), "el nombre del cliente no debería usarse: " + url);

        Path enDisco = almacenamientoService.getDirectorio().resolve(url.substring("/uploads/".length()));
        assertTrue(enDisco.startsWith(almacenamientoService.getDirectorio()),
                "el archivo tiene que quedar dentro del directorio de uploads");
        Files.deleteIfExists(enDisco);
    }

    @Test
    @DisplayName("Dos subidas del mismo archivo no se pisan entre sí")
    void testNombresUnicos() throws Exception {
        MockMultipartFile archivo = new MockMultipartFile("file", "misma.png", "image/png", PNG);

        String url1 = subir(archivo);
        String url2 = subir(archivo);

        assertNotEquals(url1, url2, "cada subida debería tener su propio nombre");

        for (String u : new String[]{url1, url2}) {
            Files.deleteIfExists(almacenamientoService.getDirectorio()
                    .resolve(u.substring("/uploads/".length())));
        }
    }

    private String subir(MockMultipartFile archivo) throws Exception {
        String body = mockMvc.perform(multipart("/api/imagenes").file(archivo)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.parse(body).read("$.url");
    }
}
