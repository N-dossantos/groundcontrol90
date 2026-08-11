package com.ecommerce.service;

import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Los mails de pedido salen en un hilo aparte (@Async), así que cuando se ejecutan la
 * sesión de Hibernate del request que los disparó puede estar cerrada. Si el envío
 * necesita leer relaciones LAZY (pedido.usuario / pedido.items) en ese momento, revienta
 * con LazyInitializationException dentro del hilo async: el mail nunca sale y el flujo
 * que lo pidió ni se entera (queda solo un ERROR en el log).
 *
 * Estos tests fijan la garantía: los datos del mail se resuelven mientras la sesión está
 * abierta, y el envío en sí no toca la entidad.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Emails de pedido sin sesión de Hibernate abierta")
class EmailServiceSinSesionTest {

    @Autowired private EmailService emailService;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository productoVarianteRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private TransactionTemplate txTemplate;

    @MockBean private JavaMailSender mailSender;

    private Long pedidoId;

    @BeforeEach
    void setUp() {
        // El contexto de Spring (y por lo tanto la H2 en memoria) se comparte entre clases
        // de test, así que se limpia respetando el orden de las FK.
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        productoVarianteRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("V").apellido("V").username("vendedor4").email("vendedor4@test.com")
                .password("x").role(Role.USER).build());
        Usuario comprador = usuarioRepository.save(Usuario.builder()
                .nombre("Juan").apellido("Perez").username("comprador4").email("comprador4@test.com")
                .password("x").role(Role.USER).build());
        Producto producto = productoRepository.save(Producto.builder()
                .name("Camiseta Boca").club("Boca").liga("Liga").temporada("2026")
                .tipo(TipoProducto.CAMISETA).price(new BigDecimal("45000")).ownerUser(vendedor)
                .createdAt(LocalDateTime.now()).build());
        ProductoVariante variante = productoVarianteRepository.save(ProductoVariante.builder()
                .producto(producto).talle("M").stock(10).sku("SKU-SINSESION").build());

        Pedido pedido = pedidoRepository.save(Pedido.builder()
                .usuario(comprador).estado(EstadoPedido.CONFIRMADO)
                .total(new BigDecimal("45000")).createdAt(LocalDateTime.now())
                .items(new ArrayList<>()).build());
        detallePedidoRepository.save(DetallePedido.builder()
                .pedido(pedido).producto(producto).variante(variante).vendedor(vendedor)
                .productoNombre("Camiseta Boca").talle("M").cantidad(1)
                .precioUnitario(new BigDecimal("45000")).estadoItem(EstadoPedido.CONFIRMADO).build());
        pedidoId = pedido.getId();
    }

    /**
     * Arma el resumen dentro de la transacción y la cierra: replica lo que hace
     * PedidoService (resolver con la sesión viva) y deja al envío sin sesión, como el
     * hilo async que llega tarde.
     */
    private EmailService.ResumenPedido resumenConSesionYaCerrada() {
        return txTemplate.execute(status ->
                EmailService.ResumenPedido.de(pedidoRepository.findById(pedidoId).orElseThrow()));
    }

    @Test
    @DisplayName("Debería mandar la confirmación de pedido aunque la sesión ya esté cerrada")
    void testConfirmacionPedidoSinSesion() {
        EmailService.ResumenPedido pedido = resumenConSesionYaCerrada();

        emailService.enviarConfirmacionPedido(pedido);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(3000)).send(captor.capture());
        assertArrayEquals(new String[]{"comprador4@test.com"}, captor.getValue().getTo());
        assertTrue(captor.getValue().getText().contains("Camiseta Boca"),
                "El mail debería listar los items del pedido");
    }

    @Test
    @DisplayName("Debería mandar el cambio de estado aunque la sesión ya esté cerrada")
    void testCambioEstadoSinSesion() {
        EmailService.ResumenPedido pedido = resumenConSesionYaCerrada();

        emailService.enviarCambioEstadoPedido(pedido, EstadoPedido.ENVIADO);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(3000)).send(captor.capture());
        assertArrayEquals(new String[]{"comprador4@test.com"}, captor.getValue().getTo());
        assertTrue(captor.getValue().getText().contains("ENVIADO"));
    }
}
