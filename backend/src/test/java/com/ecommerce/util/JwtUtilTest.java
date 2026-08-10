package com.ecommerce.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - JwtUtil")
class JwtUtilTest {

    private static final String VALID_SECRET =
            "test-secret-key-para-hs512-con-longitud-suficiente-de-al-menos-64-caracteres-xxxxxxxxxxxx";

    private JwtUtil jwtUtil;

    private String email;
    private Long userId;

    @BeforeEach
    void setUp() throws Exception {
        email = "test@example.com";
        userId = 1L;
        jwtUtil = new JwtUtil();
        setField("secret", VALID_SECRET);
        setField("expiration", 3600000); // 1 hora, para que los tests no dependan del reloj
        jwtUtil.init();
    }

    private void setField(String name, Object value) throws Exception {
        Field field = JwtUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(jwtUtil, value);
    }

    @Test
    @DisplayName("init() debería fallar si jwt.secret no está configurado")
    void testInit_ThrowsWhenSecretMissing() throws Exception {
        JwtUtil sinSecret = new JwtUtil();
        setFieldOn(sinSecret, "secret", null);

        assertThrows(IllegalStateException.class, sinSecret::init);
    }

    @Test
    @DisplayName("init() debería fallar si jwt.secret es demasiado corto para HS512")
    void testInit_ThrowsWhenSecretTooShort() throws Exception {
        JwtUtil secretCorto = new JwtUtil();
        setFieldOn(secretCorto, "secret", "1234567890123456789012345678901234567890"); // 40 bytes (320 bits)

        assertThrows(IllegalStateException.class, secretCorto::init);
    }

    private void setFieldOn(JwtUtil target, String name, Object value) throws Exception {
        Field field = JwtUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    @DisplayName("Debería generar un token JWT válido")
    void testGenerateToken() {
        String nuevoToken = jwtUtil.generateToken(email, userId);

        assertNotNull(nuevoToken);
        assertFalse(nuevoToken.isEmpty());
        assertEquals(3, nuevoToken.split("\\.").length);
    }

    @Test
    @DisplayName("Debería extraer el email del token")
    void testGetEmailFromToken() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertEquals(email, jwtUtil.getEmailFromToken(tokenFresco));
    }

    @Test
    @DisplayName("Debería extraer el userId del token")
    void testGetUserIdFromToken() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertEquals(userId, jwtUtil.getUserIdFromToken(tokenFresco));
    }

    @Test
    @DisplayName("Debería validar un token válido")
    void testValidateToken_TokenValido() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertTrue(jwtUtil.validateToken(tokenFresco));
    }

    @Test
    @DisplayName("Debería retornar false para un token inválido")
    void testValidateToken_TokenInvalido() {
        assertFalse(jwtUtil.validateToken("token.invalido.malformado"));
    }

    @Test
    @DisplayName("Debería retornar false para un token nulo")
    void testValidateToken_TokenNulo() {
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    @DisplayName("Debería obtener la fecha de expiración del token")
    void testGetExpirationDateFromToken() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        Date fechaExpiracion = jwtUtil.getExpirationDateFromToken(tokenFresco);

        assertNotNull(fechaExpiracion);
        assertTrue(fechaExpiracion.after(new Date()));
    }

    @Test
    @DisplayName("Debería verificar si un token está expirado")
    void testIsTokenExpired_TokenNoExpirado() {
        String tokenFresco = jwtUtil.generateToken(email, userId);
        assertFalse(jwtUtil.isTokenExpired(tokenFresco));
    }

    @Test
    @DisplayName("Debería generar tokens diferentes para diferentes emails")
    void testGenerateToken_DiferentesEmails() {
        String token1 = jwtUtil.generateToken("email1@test.com", 1L);
        String token2 = jwtUtil.generateToken("email2@test.com", 2L);

        assertNotEquals(token1, token2);
    }

    @Test
    @DisplayName("Debería generar tokens diferentes para el mismo email pero diferentes usuarios")
    void testGenerateToken_DiferentesUsuarios() {
        String token1 = jwtUtil.generateToken(email, 1L);
        String token2 = jwtUtil.generateToken(email, 2L);

        assertNotEquals(token1, token2);
    }
}

