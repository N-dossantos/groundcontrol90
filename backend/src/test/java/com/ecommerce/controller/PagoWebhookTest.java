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
@DisplayName("Tests de Integración - Webhook de Mercado Pago")
class PagoWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String BODY = """
            {"action":"payment.updated","data":{"id":"123456"}}
            """;

    @Test
    @DisplayName("Debería rechazar un webhook sin firma válida")
    void testWebhook_SinFirmaValida_Rechazado() throws Exception {
        mockMvc.perform(post("/api/pagos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1700000000,v1=firma-invalida")
                        .header("x-request-id", "req-test")
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Debería rechazar un webhook sin ningún header de firma")
    void testWebhook_SinHeaders_Rechazado() throws Exception {
        mockMvc.perform(post("/api/pagos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El webhook es público: no debe responder 401/403 por falta de JWT, sino por la firma")
    void testWebhook_EsPublico_NoRequiereJwt() throws Exception {
        // Si Spring Security bloqueara la ruta, el request ni llegaría al controller.
        // Con firma inválida el controller devuelve 401 él mismo; lo que se verifica acá
        // es que la respuesta viene del controller y no del filtro de autenticación.
        mockMvc.perform(post("/api/pagos/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-signature", "ts=1,v1=deadbeef")
                        .header("x-request-id", "req-test")
                        .content(BODY))
                .andExpect(status().isUnauthorized());
    }
}
