package com.ecommerce.controller;

import com.ecommerce.entity.EstadoPago;
import com.ecommerce.entity.Pago;
import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.exception.PedidoNotFoundException;
import com.ecommerce.repository.PagoRepository;
import com.ecommerce.security.MercadoPagoSignatureValidator;
import com.ecommerce.service.PagoService;
import com.ecommerce.service.PedidoService;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.resources.payment.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private static final Logger log = LoggerFactory.getLogger(PagoController.class);

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private MercadoPagoSignatureValidator signatureValidator;

    @Autowired
    private PaymentClient paymentClient;

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    /**
     * Crea la preferencia de Mercado Pago del pedido y devuelve la URL del checkout
     * hosteado a la que el frontend redirige al comprador.
     */
    @PostMapping("/pedidos/{pedidoId}/preferencia")
    public ResponseEntity<Map<String, String>> crearPreferencia(@PathVariable Long pedidoId,
                                                                @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.obtenerPedidoPorId(pedidoId)
                .orElseThrow(() -> new PedidoNotFoundException(pedidoId));

        if (!pedido.getUsuario().getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso sobre este pedido");
        }

        PagoService.PreferenciaCreada preferencia = pagoService.crearPreferencia(pedido);

        return ResponseEntity.ok(Map.of(
                "preferenceId", preferencia.pago().getPreferenceId(),
                "initPoint", preferencia.initPoint()
        ));
    }

    /**
     * Notificación de Mercado Pago sobre el estado de un pago.
     *
     * Es la única fuente de verdad del cobro: el redirect del comprador no sirve como
     * confirmación porque puede no completarse (cierra la pestaña) o falsearse.
     *
     * Ruta pública porque Mercado Pago no tiene ningún JWT nuestro. Toda su seguridad
     * es la firma HMAC: sin firma válida se corta acá y no se procesa nada.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataIdQueryParam,
            @RequestParam(value = "type", required = false) String tipoQueryParam,
            @RequestBody(required = false) Map<String, Object> body) {

        // Mercado Pago manda data.id como query param de la notification URL y arma el
        // manifest de la firma con ese valor; el body es el fallback para el mismo dato.
        String dataId = dataIdQueryParam != null ? dataIdQueryParam : dataIdDelBody(body);

        if (!signatureValidator.esValida(xSignature, xRequestId, dataId, webhookSecret)) {
            log.warn("Webhook de Mercado Pago con firma inválida rechazado (dataId={})", dataId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String tipo = tipoQueryParam != null ? tipoQueryParam : tipoDelBody(body);
        if (tipo != null && !tipo.equals("payment")) {
            // Mercado Pago también notifica merchant_order y otros topics que no cambian
            // el estado del pedido. Se aceptan sin procesar para no ensuciar los logs.
            return ResponseEntity.ok().build();
        }

        try {
            Payment payment = paymentClient.get(Long.parseLong(dataId));
            Long pedidoId = Long.valueOf(payment.getExternalReference());

            Pago pago = pagoRepository.findTopByPedidoIdOrderByCreatedAtDesc(pedidoId)
                    .orElseThrow(() -> new IllegalStateException("No hay Pago registrado para el pedido " + pedidoId));

            pago.setPaymentIdExterno(String.valueOf(payment.getId()));

            String status = payment.getStatus(); // approved | rejected | cancelled | pending | in_process
            switch (status) {
                case "approved" -> {
                    pago.setEstado(EstadoPago.APPROVED);
                    pedidoService.confirmarPago(pedidoId);
                }
                case "rejected", "cancelled" -> {
                    pago.setEstado(EstadoPago.REJECTED);
                    pedidoService.cancelarPedidoPorPagoRechazado(pedidoId);
                }
                case "refunded", "charged_back" -> pago.setEstado(EstadoPago.REFUNDED);
                default -> pago.setEstado(EstadoPago.IN_PROCESS);
            }
            pagoRepository.save(pago);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Devolver 200 igual evita que Mercado Pago reintente indefinidamente un webhook
            // que nunca va a poder procesarse (ej. pedido borrado); loggear para revisión manual.
            log.error("Error procesando webhook de Mercado Pago para dataId={}", dataId, e);
            return ResponseEntity.ok().build();
        }
    }

    private String tipoDelBody(Map<String, Object> body) {
        return body != null && body.get("type") instanceof String tipo ? tipo : null;
    }

    @SuppressWarnings("unchecked")
    private String dataIdDelBody(Map<String, Object> body) {
        if (body == null) {
            return null;
        }
        Object data = body.get("data");
        if (!(data instanceof Map)) {
            return null;
        }
        Object id = ((Map<String, Object>) data).get("id");
        return id != null ? String.valueOf(id) : null;
    }
}
