package com.ecommerce.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Valida la firma de los webhooks de Mercado Pago.
 *
 * El webhook es un endpoint público (Mercado Pago no manda un JWT nuestro), así que
 * esta firma es lo único que separa una notificación legítima de cualquiera que
 * conozca la URL y quiera marcar pedidos como pagados.
 *
 * Esquema documentado por Mercado Pago: el header x-signature trae "ts=...,v1=...",
 * donde v1 es el HMAC-SHA256 del manifest "id:{dataId};request-id:{requestId};ts:{ts};"
 * firmado con el secreto del webhook.
 */
@Component
public class MercadoPagoSignatureValidator {

    public boolean esValida(String xSignature, String xRequestId, String dataId, String secret) {
        if (xSignature == null || xRequestId == null || dataId == null || secret == null) {
            return false;
        }

        Map<String, String> partes = parsear(xSignature);
        String ts = partes.get("ts");
        String v1Recibido = partes.get("v1");
        if (ts == null || v1Recibido == null) {
            return false;
        }

        // Mercado Pago arma el manifest con el data.id en minúsculas cuando es alfanumérico
        String manifest = "id:" + dataId.toLowerCase(Locale.ROOT)
                + ";request-id:" + xRequestId + ";ts:" + ts + ";";

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
            String v1Calculado = bytesToHex(hash);

            // Comparación en tiempo constante: un equals normal filtra, por el tiempo
            // que tarda en cortar, cuántos caracteres del prefijo acertó el atacante.
            return MessageDigest.isEqual(
                    v1Calculado.getBytes(StandardCharsets.UTF_8),
                    v1Recibido.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> parsear(String xSignature) {
        Map<String, String> partes = new HashMap<>();
        for (String segmento : xSignature.split(",")) {
            String[] kv = segmento.split("=", 2);
            if (kv.length == 2) {
                partes.put(kv[0].trim(), kv[1].trim());
            }
        }
        return partes;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
