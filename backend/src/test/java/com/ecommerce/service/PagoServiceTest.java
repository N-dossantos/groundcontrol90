package com.ecommerce.service;

import com.ecommerce.entity.*;
import com.ecommerce.repository.PagoRepository;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PreferenceClient hace una llamada HTTP real, así que PagoService lo recibe inyectado
 * como bean de Spring (mockeable) en vez de instanciarlo con new dentro del método.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - PagoService")
class PagoServiceTest {

    @Mock
    private PreferenceClient preferenceClient;

    @Mock
    private PagoRepository pagoRepository;

    @InjectMocks
    private PagoService pagoService;

    private Pedido pedidoDePrueba() {
        Usuario comprador = Usuario.builder().id(1L).nombre("Juan").apellido("Pérez")
                .email("juan@test.com").build();
        DetallePedido item = DetallePedido.builder()
                .productoNombre("Camiseta Boca").talle("M").cantidad(2)
                .precioUnitario(new BigDecimal("45000")).build();
        return Pedido.builder().id(99L).usuario(comprador).total(new BigDecimal("90000"))
                .items(List.of(item)).build();
    }

    // Preference solo expone getters (el SDK la construye deserializando la respuesta
    // de Mercado Pago), así que la única forma de armar una de prueba es mockearla.
    // Se arma en una variable aparte y nunca dentro de un when(...) sin cerrar:
    // stubbear un mock adentro de otro stubbing rompe a Mockito.
    private Preference preferenceFalsa(String id, String initPoint) {
        Preference preference = mock(Preference.class);
        lenient().when(preference.getId()).thenReturn(id);
        lenient().when(preference.getInitPoint()).thenReturn(initPoint);
        return preference;
    }

    @Test
    @DisplayName("Debería crear una preferencia de MP y guardar un Pago PENDING con su preferenceId")
    void testCrearPreferencia() throws Exception {
        Pedido pedido = pedidoDePrueba();
        String initPoint = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123";
        Preference preference = preferenceFalsa("pref-123", initPoint);

        when(preferenceClient.create(any(PreferenceRequest.class))).thenReturn(preference);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        PagoService.PreferenciaCreada resultado = pagoService.crearPreferencia(pedido);

        Pago pago = resultado.pago();
        assertEquals("pref-123", pago.getPreferenceId());
        assertEquals(EstadoPago.PENDING, pago.getEstado());
        assertEquals(new BigDecimal("90000"), pago.getMonto());
        assertEquals(pedido, pago.getPedido());
        assertEquals(initPoint, resultado.initPoint(),
                "El init point sale de la respuesta del create, sin una segunda llamada a MP");
    }

    @Test
    @DisplayName("Debería mandar el id del pedido como external_reference para poder correlacionar el webhook")
    void testCrearPreferencia_MandaExternalReferenceYItems() throws Exception {
        Pedido pedido = pedidoDePrueba();
        Preference preference = preferenceFalsa("pref-123", "https://mp.test/checkout");

        when(preferenceClient.create(any(PreferenceRequest.class))).thenReturn(preference);
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> inv.getArgument(0));

        pagoService.crearPreferencia(pedido);

        ArgumentCaptor<PreferenceRequest> captor = ArgumentCaptor.forClass(PreferenceRequest.class);
        org.mockito.Mockito.verify(preferenceClient).create(captor.capture());
        PreferenceRequest enviado = captor.getValue();

        assertEquals("99", enviado.getExternalReference());
        assertEquals(1, enviado.getItems().size());
        assertEquals("Camiseta Boca (talle M)", enviado.getItems().get(0).getTitle());
        assertEquals(2, enviado.getItems().get(0).getQuantity());
        assertEquals(new BigDecimal("45000"), enviado.getItems().get(0).getUnitPrice());
        assertEquals("ARS", enviado.getItems().get(0).getCurrencyId());
        assertTrue(enviado.getNotificationUrl().endsWith("/api/pagos/webhook"));
    }

    @Test
    @DisplayName("Debería envolver un fallo de la API de Mercado Pago en vez de propagar la excepción del SDK")
    void testCrearPreferencia_ErrorDeMercadoPago() throws Exception {
        Pedido pedido = pedidoDePrueba();

        when(preferenceClient.create(any(PreferenceRequest.class)))
                .thenThrow(new com.mercadopago.exceptions.MPException("Mercado Pago caído"));

        assertThrows(IllegalStateException.class, () -> pagoService.crearPreferencia(pedido));
    }
}
