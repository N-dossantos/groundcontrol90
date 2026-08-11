package com.ecommerce.service;

import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.repository.PedidoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - PedidoExpiracionJob")
class PedidoExpiracionJobTest {

    @Mock private PedidoRepository pedidoRepository;
    @Mock private PedidoService pedidoService;

    @InjectMocks
    private PedidoExpiracionJob job;

    @Test
    @DisplayName("Debería cancelar por pago rechazado cada pedido PENDIENTE vencido")
    void testExpirarPedidosVencidos_CancelaLosVencidos() {
        Pedido vencido1 = Pedido.builder().id(1L).estado(EstadoPedido.PENDIENTE)
                .createdAt(LocalDateTime.now().minusMinutes(45)).build();
        Pedido vencido2 = Pedido.builder().id(2L).estado(EstadoPedido.PENDIENTE)
                .createdAt(LocalDateTime.now().minusMinutes(31)).build();

        when(pedidoRepository.findByEstadoAndCreatedAtBefore(eq(EstadoPedido.PENDIENTE), any()))
                .thenReturn(List.of(vencido1, vencido2));

        job.expirarPedidosVencidos();

        verify(pedidoService).cancelarPedidoPorPagoRechazado(1L);
        verify(pedidoService).cancelarPedidoPorPagoRechazado(2L);
    }
}
