package com.ecommerce.service;

import com.ecommerce.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - EmailService")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "from", "no-reply@tienda-test.com");
    }

    @Test
    @DisplayName("Debería enviar el email de confirmación de cuenta al email del usuario")
    void testEnviarConfirmacionCuenta() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();

        emailService.enviarConfirmacionCuenta(usuario);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage enviado = captor.getValue();
        assertArrayEquals(new String[]{"juan@test.com"}, enviado.getTo());
        assertTrue(enviado.getText().contains("Juan"));
    }

    @Test
    @DisplayName("Debería enviar el email de confirmación de pedido con el número de pedido")
    void testEnviarConfirmacionPedido() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();
        DetallePedido item = DetallePedido.builder()
                .productoNombre("Camiseta Boca").talle("M").cantidad(1)
                .precioUnitario(new BigDecimal("45000")).build();
        Pedido pedido = Pedido.builder().id(77L).usuario(usuario).total(new BigDecimal("45000"))
                .items(List.of(item)).build();

        emailService.enviarConfirmacionPedido(pedido);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getSubject().contains("77"));
    }

    @Test
    @DisplayName("Debería enviar el email de cambio de estado con el nuevo estado")
    void testEnviarCambioEstadoPedido() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();
        Pedido pedido = Pedido.builder().id(78L).usuario(usuario).total(new BigDecimal("45000"))
                .items(List.of()).build();

        emailService.enviarCambioEstadoPedido(pedido, EstadoPedido.ENVIADO);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertTrue(captor.getValue().getText().contains("ENVIADO"));
    }

    @Test
    @DisplayName("No debería propagar la excepción si el envío del email falla")
    void testNoPropagaExcepcionSiFallaElEnvio() {
        Usuario usuario = Usuario.builder().nombre("Juan").email("juan@test.com").build();
        doThrow(new MailSendException("smtp caído")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.enviarConfirmacionCuenta(usuario));
    }
}
