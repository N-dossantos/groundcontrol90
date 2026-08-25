package com.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Tests de Integración - AuthRateLimitFilter")
class AuthRateLimitFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthRateLimitFilter filter;

    private static final String LOGIN_BODY =
            "{\"emailOrUsername\":\"no-existe@test.com\",\"password\":\"lo-que-sea\"}";

    @Test
    @DisplayName("Debería devolver 429 luego de superar el límite de intentos de login")
    void testRateLimit_BloqueaTrasLimiteDeIntentos() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("Los buckets inactivos se liberan en vez de acumularse una entrada por IP")
    void testEviccion_LiberaBucketsInactivos() throws Exception {
        // Antes el mapa crecía una entrada por IP y no liberaba ninguna nunca: un barrido
        // desde muchas IPs lo hacía crecer sin techo hasta agotar el heap.
        int inicial = filter.cantidadDeBuckets();

        for (int i = 0; i < 50; i++) {
            String ip = "198.51.100." + i;  // TEST-NET-2 (RFC 5737), no es una IP real
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> { request.setRemoteAddr(ip); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY));
        }
        assertTrue(filter.cantidadDeBuckets() >= inicial + 50,
                "las 50 IPs deberían haber creado su bucket");

        // Un corte en el pasado no descarta nada: las entradas se acaban de usar. Esto es
        // lo que garantiza que la evicción no le devuelva los intentos a un atacante activo.
        filter.evictarBucketsUsadosAntesDe(System.currentTimeMillis() - 60_000);
        assertTrue(filter.cantidadDeBuckets() >= inicial + 50,
                "un bucket recién usado no debería descartarse");

        // Con el corte adelantado —equivalente a que pase la ventana de inactividad— se
        // liberan todos. El corte se pasa por parámetro para no esperar 10 minutos reales.
        filter.evictarBucketsUsadosAntesDe(System.currentTimeMillis() + 1);
        assertEquals(0, filter.cantidadDeBuckets(),
                "pasada la ventana de inactividad no debería quedar ningún bucket");
    }
}
