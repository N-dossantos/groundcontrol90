package com.ecommerce.service;

import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Libera el stock reservado por checkouts que nunca se pagaron. Un pedido queda
 * PENDIENTE (con su stock ya descontado) desde que se crea hasta que el webhook
 * de Mercado Pago confirma o rechaza el pago; si el comprador abandona el
 * checkout, ese webhook no llega nunca y el stock quedaría retenido para siempre.
 */
@Component
public class PedidoExpiracionJob {

    private static final Logger log = LoggerFactory.getLogger(PedidoExpiracionJob.class);

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoService pedidoService;

    @Value("${pedidos.expiracion-minutos:30}")
    private int expiracionMinutos;

    @Scheduled(fixedDelay = 5 * 60 * 1000)
    public void expirarPedidosVencidos() {
        LocalDateTime limite = LocalDateTime.now().minusMinutes(expiracionMinutos);
        pedidoRepository.findByEstadoAndCreatedAtBefore(EstadoPedido.PENDIENTE, limite)
                .forEach(pedido -> {
                    try {
                        pedidoService.cancelarPedidoPorPagoRechazado(pedido.getId());
                        log.info("Pedido {} expirado por falta de pago, stock liberado", pedido.getId());
                    } catch (Exception e) {
                        // Un pedido que falla no debe frenar la expiración de los demás.
                        log.error("Error al expirar el pedido {}", pedido.getId(), e);
                    }
                });
    }
}
