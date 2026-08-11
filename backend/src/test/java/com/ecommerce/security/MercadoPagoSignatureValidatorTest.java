package com.ecommerce.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Tests Unitarios - MercadoPagoSignatureValidator")
class MercadoPagoSignatureValidatorTest {

    private final MercadoPagoSignatureValidator validator = new MercadoPagoSignatureValidator();
    private static final String SECRET = "un-secreto-de-prueba";

    private String firmar(String dataId, String requestId, String ts) throws Exception {
        String manifest = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(), "HmacSHA256"));
        return bytesToHex(mac.doFinal(manifest.getBytes()));
    }

    @Test
    @DisplayName("Debería validar una firma calculada correctamente")
    void testFirmaValida() throws Exception {
        String dataId = "123456";
        String requestId = "req-abc";
        String ts = "1700000000";

        String xSignature = "ts=" + ts + ",v1=" + firmar(dataId, requestId, ts);

        assertTrue(validator.esValida(xSignature, requestId, dataId, SECRET));
    }

    @Test
    @DisplayName("Debería rechazar una firma alterada")
    void testFirmaInvalida() {
        String xSignature = "ts=1700000000,v1=firma-totalmente-inventada";
        assertFalse(validator.esValida(xSignature, "req-abc", "123456", SECRET));
    }

    @Test
    @DisplayName("Debería rechazar un header x-signature ausente")
    void testFirmaNula() {
        assertFalse(validator.esValida(null, "req-abc", "123456", SECRET));
    }

    @Test
    @DisplayName("Debería rechazar una firma válida para otro data.id (replay sobre otro pago)")
    void testFirmaDeOtroPago() throws Exception {
        String ts = "1700000000";
        String xSignature = "ts=" + ts + ",v1=" + firmar("111", "req-abc", ts);

        assertFalse(validator.esValida(xSignature, "req-abc", "222", SECRET),
                "La firma del pago 111 no puede autorizar una notificación del pago 222");
    }

    @Test
    @DisplayName("Debería rechazar un x-signature sin el segmento v1")
    void testSinSegmentoV1() {
        assertFalse(validator.esValida("ts=1700000000", "req-abc", "123456", SECRET));
    }

    @Test
    @DisplayName("Debería normalizar a minúsculas el data.id alfanumérico, como especifica Mercado Pago")
    void testDataIdAlfanumericoEnMayusculas() throws Exception {
        String ts = "1700000000";
        // Mercado Pago arma el manifest con el id en minúsculas cuando es alfanumérico
        String xSignature = "ts=" + ts + ",v1=" + firmar("abc-def", "req-abc", ts);

        assertTrue(validator.esValida(xSignature, "req-abc", "ABC-DEF", SECRET));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
