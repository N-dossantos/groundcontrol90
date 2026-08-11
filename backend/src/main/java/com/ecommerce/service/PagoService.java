package com.ecommerce.service;

import com.ecommerce.entity.DetallePedido;
import com.ecommerce.entity.EstadoPago;
import com.ecommerce.entity.Pago;
import com.ecommerce.entity.Pedido;
import com.ecommerce.repository.PagoRepository;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.preference.Preference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PagoService {

    @Autowired
    private PreferenceClient preferenceClient;

    @Autowired
    private PagoRepository pagoRepository;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.backend-url}")
    private String backendUrl;

    /**
     * El init point es el dato que necesita el navegador para irse al checkout, pero no
     * es estado del pago que valga la pena persistir: Mercado Pago lo devuelve junto con
     * la preferencia recién creada. Se devuelve al lado del Pago para no tener que
     * volver a pedirle la preferencia a Mercado Pago solo para leer esa URL.
     */
    public record PreferenciaCreada(Pago pago, String initPoint) {}

    public PreferenciaCreada crearPreferencia(Pedido pedido) {
        try {
            List<PreferenceItemRequest> items = pedido.getItems().stream()
                    .map(this::aItemDeMercadoPago)
                    .toList();

            // Los tres caminos van a la misma pantalla: el estado real del pago lo
            // define el webhook, no el redirect (que el comprador puede no completar).
            String urlDeRetorno = frontendUrl + "/checkout/resultado?pedidoId=" + pedido.getId();
            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(urlDeRetorno)
                    .failure(urlDeRetorno)
                    .pending(urlDeRetorno)
                    .build();

            PreferenceRequest request = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .autoReturn("approved")
                    // Con el id del pedido acá, el webhook puede correlacionar el pago
                    // entrante con el pedido sin depender de nuestra tabla de pagos.
                    .externalReference(pedido.getId().toString())
                    .notificationUrl(backendUrl + "/api/pagos/webhook")
                    .build();

            Preference preference = preferenceClient.create(request);

            Pago pago = pagoRepository.save(Pago.builder()
                    .pedido(pedido)
                    .preferenceId(preference.getId())
                    .estado(EstadoPago.PENDING)
                    .monto(pedido.getTotal())
                    .build());

            return new PreferenciaCreada(pago, preference.getInitPoint());
        } catch (MPException | MPApiException e) {
            throw new IllegalStateException("Error al crear la preferencia de pago en Mercado Pago", e);
        }
    }

    private PreferenceItemRequest aItemDeMercadoPago(DetallePedido item) {
        return PreferenceItemRequest.builder()
                .title(item.getProductoNombre() + " (talle " + item.getTalle() + ")")
                .quantity(item.getCantidad())
                .unitPrice(item.getPrecioUnitario())
                .currencyId("ARS")
                .build();
    }
}
