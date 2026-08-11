package com.ecommerce.controller;

import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.exception.PedidoNotFoundException;
import com.ecommerce.service.PagoService;
import com.ecommerce.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    @Autowired
    private PagoService pagoService;

    @Autowired
    private PedidoService pedidoService;

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
}
