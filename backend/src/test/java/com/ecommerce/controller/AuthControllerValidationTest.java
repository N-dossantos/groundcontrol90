package com.ecommerce.controller;

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
@DisplayName("Tests de Integración - Validación de registro")
class AuthControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Debería rechazar el registro si no se aceptan los Términos y Condiciones")
    void testRegister_SinAceptarTerminos_Rechazado() throws Exception {
        String body = """
                {"username":"nuevo","email":"nuevo@test.com","password":"password123",
                 "nombre":"Nuevo","apellido":"Usuario","aceptaTerminos":false}
                """;

        // IP fija y distinta por test (TEST-NET-3, RFC 5737, reservada para documentación,
        // no corresponde a ningún host real): AuthRateLimitFilter bucketiza por IP y comparte
        // el bucket entre /login y /register, así que si dos tests (o AuthRateLimitFilterTest)
        // comparten la IP del cliente de MockMvc, uno puede agotarle el cupo al otro según el
        // orden de ejecución. Con una IP propia, este test estrena su propio bucket siempre.
        mockMvc.perform(post("/api/auth/register")
                        .with(request -> { request.setRemoteAddr("203.0.113.10"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debería aceptar el registro si se aceptan los Términos y Condiciones")
    void testRegister_AceptandoTerminos_Ok() throws Exception {
        String body = """
                {"username":"nuevo2","email":"nuevo2@test.com","password":"password123",
                 "nombre":"Nuevo","apellido":"Usuario","aceptaTerminos":true}
                """;

        // IP fija y distinta de la del test anterior (ver comentario arriba) para que este
        // test tampoco dependa del orden de ejecución ni del cupo consumido por otro test.
        mockMvc.perform(post("/api/auth/register")
                        .with(request -> { request.setRemoteAddr("203.0.113.11"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Debería rechazar una contraseña de menos de 8 caracteres")
    void testRegister_PasswordCorta_Rechazado() throws Exception {
        String body = """
                {"username":"corto","email":"corto@test.com","password":"abc1234",
                 "nombre":"Nuevo","apellido":"Usuario","aceptaTerminos":true}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(request -> { request.setRemoteAddr("203.0.113.12"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Debería rechazar una contraseña de la lista de las más comunes")
    void testRegister_PasswordComun_Rechazado() throws Exception {
        String body = """
                {"username":"comun","email":"comun@test.com","password":"12345678",
                 "nombre":"Nuevo","apellido":"Usuario","aceptaTerminos":true}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(request -> { request.setRemoteAddr("203.0.113.13"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
