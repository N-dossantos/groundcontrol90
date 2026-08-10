package com.ecommerce.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests Unitarios - CorsConfig")
class CorsConfigTest {

    @Test
    @DisplayName("Debería exponer únicamente los orígenes configurados, sin espacios")
    void testCorsConfigurationSource_ParseaOrigenes() throws Exception {
        CorsConfig corsConfig = new CorsConfig();
        setAllowedOrigins(corsConfig, "https://tienda.com, https://admin.tienda.com");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/productos");
        CorsConfiguration resolved = source.getCorsConfiguration(request);

        assertNotNull(resolved);
        assertEquals(2, resolved.getAllowedOrigins().size());
        assertTrue(resolved.getAllowedOrigins().contains("https://tienda.com"));
        assertTrue(resolved.getAllowedOrigins().contains("https://admin.tienda.com"));
        assertTrue(resolved.getAllowCredentials());
    }

    @Test
    @DisplayName("No debería aplicar CORS a rutas fuera de /api/**")
    void testCorsConfigurationSource_NoAplicaFueraDeApi() throws Exception {
        CorsConfig corsConfig = new CorsConfig();
        setAllowedOrigins(corsConfig, "https://tienda.com, https://admin.tienda.com");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        CorsConfiguration resolved = source.getCorsConfiguration(request);

        assertNull(resolved);
    }

    private void setAllowedOrigins(CorsConfig target, String value) throws Exception {
        Field field = CorsConfig.class.getDeclaredField("allowedOriginsRaw");
        field.setAccessible(true);
        field.set(target, value);
    }
}
