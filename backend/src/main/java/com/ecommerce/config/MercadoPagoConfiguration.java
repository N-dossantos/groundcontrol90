package com.ecommerce.config;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inicializa el SDK de Mercado Pago con el access token al arrancar.
 * Se llama MercadoPagoConfiguration (y no MercadoPagoConfig) para no chocar con la
 * clase estática del SDK que importa este mismo archivo.
 *
 * Falla el arranque si falta el token, mismo criterio fail-fast que JwtUtil: es
 * preferible no levantar a levantar sin poder cobrar.
 */
@Configuration
public class MercadoPagoConfiguration {

    @Value("${mercadopago.access-token}")
    private String accessToken;

    @PostConstruct
    void init() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "mercadopago.access-token no está configurado. Definí MERCADOPAGO_ACCESS_TOKEN.");
        }
        MercadoPagoConfig.setAccessToken(accessToken);
    }

    // Los clientes del SDK toman la configuración estática de arriba, no reciben
    // argumentos. Se declaran como beans para poder mockearlos en los tests.
    @Bean
    public PreferenceClient preferenceClient() {
        return new PreferenceClient();
    }

    @Bean
    public PaymentClient paymentClient() {
        return new PaymentClient();
    }
}
