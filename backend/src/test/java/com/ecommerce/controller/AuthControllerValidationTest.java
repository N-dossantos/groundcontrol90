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

        mockMvc.perform(post("/api/auth/register")
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

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }
}
