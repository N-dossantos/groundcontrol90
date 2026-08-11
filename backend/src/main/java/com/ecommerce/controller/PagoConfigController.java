package com.ecommerce.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * La public key es la única credencial de Mercado Pago que puede llegar al navegador.
 * El access token y el webhook secret nunca salen del backend.
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoConfigController {

    @Value("${mercadopago.public-key}")
    private String publicKey;

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }
}
