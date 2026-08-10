package com.ecommerce.controller;

import com.ecommerce.dto.VentaDTO;
import com.ecommerce.entity.DetallePedido;
import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Usuario;
import com.ecommerce.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controlador para gestionar las ventas desde la perspectiva del vendedor
 * Los usuarios pueden ver y gestionar los productos que han vendido
 */
@RestController
@RequestMapping("/api/ventas")
public class VentasController {
    
    @Autowired
    private PedidoService pedidoService;

    /**
     * GET /api/ventas/mis-ventas
     * Obtiene todas las ventas del usuario autenticado (como vendedor)
     */
    @GetMapping("/mis-ventas")
    public ResponseEntity<?> obtenerMisVentas(@AuthenticationPrincipal Usuario vendedor) {
        List<VentaDTO> ventas = pedidoService.obtenerVentasPorVendedor(vendedor.getId())
                .stream()
                .map(VentaDTO::new)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ventas);
    }
    
    /**
     * GET /api/ventas/mis-ventas/estado/{estado}
     * Obtiene las ventas del vendedor filtradas por estado
     */
    @GetMapping("/mis-ventas/estado/{estado}")
    public ResponseEntity<?> obtenerMisVentasPorEstado(
            @PathVariable String estado,
            @AuthenticationPrincipal Usuario vendedor) {
        EstadoPedido estadoPedido;
        try {
            estadoPedido = EstadoPedido.valueOf(estado.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido: " + estado);
        }

        List<VentaDTO> ventas = pedidoService.obtenerVentasPorVendedorYEstado(vendedor.getId(), estadoPedido)
                .stream()
                .map(VentaDTO::new)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ventas);
    }
    
    /**
     * GET /api/ventas/{detalleId}
     * Obtiene el detalle de una venta específica
     */
    @GetMapping("/{detalleId}")
    public ResponseEntity<?> obtenerVentaPorId(
            @PathVariable Long detalleId,
            @AuthenticationPrincipal Usuario vendedor) {
        DetallePedido detalle = pedidoService.obtenerVentaPorId(detalleId, vendedor.getId());
        return ResponseEntity.ok(new VentaDTO(detalle));
    }
    
    /**
     * PUT /api/ventas/{detalleId}/estado
     * Actualiza el estado de una venta (item)
     * Solo el vendedor puede actualizar el estado de sus propios items
     */
    @PutMapping("/{detalleId}/estado")
    public ResponseEntity<?> actualizarEstadoVenta(
            @PathVariable Long detalleId,
            @RequestParam String estado,
            @AuthenticationPrincipal Usuario vendedor) {
        EstadoPedido nuevoEstado;
        try {
            nuevoEstado = EstadoPedido.valueOf(estado.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Estado inválido: " + estado);
        }

        DetallePedido detalle = pedidoService.actualizarEstadoItem(detalleId, vendedor.getId(), nuevoEstado);
        return ResponseEntity.ok(new VentaDTO(detalle));
    }
    
    /**
     * GET /api/ventas/estadisticas
     * Obtiene estadísticas de ventas del vendedor
     */
    @GetMapping("/estadisticas")
    public ResponseEntity<?> obtenerEstadisticasVentas(@AuthenticationPrincipal Usuario vendedor) {
        List<DetallePedido> todasLasVentas = pedidoService.obtenerVentasPorVendedor(vendedor.getId());
        
        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("totalVentas", todasLasVentas.size());
        estadisticas.put("ventasPendientes", 
                todasLasVentas.stream()
                        .filter(v -> v.getEstadoItem() == EstadoPedido.PENDIENTE)
                        .count());
        estadisticas.put("ventasConfirmadas", 
                todasLasVentas.stream()
                        .filter(v -> v.getEstadoItem() == EstadoPedido.CONFIRMADO)
                        .count());
        estadisticas.put("ventasEnviadas", 
                todasLasVentas.stream()
                        .filter(v -> v.getEstadoItem() == EstadoPedido.ENVIADO || 
                                   v.getEstadoItem() == EstadoPedido.EN_TRANSITO)
                        .count());
        estadisticas.put("ventasEntregadas", 
                todasLasVentas.stream()
                        .filter(v -> v.getEstadoItem() == EstadoPedido.ENTREGADO)
                        .count());
        estadisticas.put("ventasCanceladas", 
                todasLasVentas.stream()
                        .filter(v -> v.getEstadoItem() == EstadoPedido.CANCELADO ||
                                   v.getEstadoItem() == EstadoPedido.CANCELADO_COMPRADOR || 
                                   v.getEstadoItem() == EstadoPedido.CANCELADO_VENDEDOR)
                        .count());
        
        return ResponseEntity.ok(estadisticas);
    }

}

