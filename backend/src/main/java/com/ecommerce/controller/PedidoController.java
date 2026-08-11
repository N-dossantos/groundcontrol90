package com.ecommerce.controller;

import com.ecommerce.dto.CreatePedidoDTO;
import com.ecommerce.dto.PedidoDTO;
import com.ecommerce.dto.ReporteVentasDTO;
import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.PedidoNotFoundException;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.service.PedidoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    
    @Autowired
    private PedidoService pedidoService;

    /**
     * GET /api/pedidos
     * Obtiene todos los pedidos (solo ADMIN)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerTodosLosPedidos() {
        List<PedidoDTO> pedidos = pedidoService.obtenerTodosLosPedidos()
                .stream()
                .map(PedidoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    /**
     * GET /api/pedidos/mis-pedidos
     * Obtiene los pedidos del usuario autenticado (historial)
     */
    @GetMapping("/mis-pedidos")
    public ResponseEntity<?> obtenerMisPedidos(@AuthenticationPrincipal Usuario usuario) {
        List<PedidoDTO> pedidos = pedidoService.obtenerPedidosPorUsuario(usuario.getId())
                .stream()
                .map(PedidoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    /**
     * GET /api/pedidos/{id}
     * Obtiene un pedido por ID
     * Solo el dueño del pedido o admin pueden verlo
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPedidoPorId(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.obtenerPedidoPorId(id)
                .orElseThrow(() -> new PedidoNotFoundException(id));

        boolean esDueño = pedido.getUsuario().getId().equals(usuario.getId());
        boolean esAdmin = usuario.getRole() == com.ecommerce.entity.Role.ADMIN;
        if (!esDueño && !esAdmin) {
            throw new ForbiddenException("No tienes permiso para ver este pedido");
        }

        return ResponseEntity.ok(new PedidoDTO(pedido));
    }

    /**
     * POST /api/pedidos
     * Crea un nuevo pedido desde el carrito
     */
    @PostMapping
    public ResponseEntity<?> crearPedido(@RequestBody CreatePedidoDTO createPedidoDTO,
                                          @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.crearPedido(usuario.getId(), createPedidoDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(new PedidoDTO(pedido));
    }

    /**
     * PUT /api/pedidos/{id}/estado
     * Actualiza el estado de un pedido
     * Solo ADMIN puede cambiar estados
     */
    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> actualizarEstado(@PathVariable Long id, @RequestParam EstadoPedido estado) {
        Pedido pedido = pedidoService.actualizarEstado(id, estado);
        return ResponseEntity.ok(new PedidoDTO(pedido));
    }

    /**
     * PUT /api/pedidos/{id}/cancelar
     * Cancela un pedido (solo si está PENDIENTE)
     * Devuelve el stock a los productos
     */
    @PutMapping("/{id}/cancelar")
    public ResponseEntity<?> cancelarPedido(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        Pedido pedido = pedidoService.cancelarPedido(id, usuario.getId());
        return ResponseEntity.ok(new PedidoDTO(pedido));
    }

    /**
     * GET /api/pedidos/estado/{estado}
     * Obtiene pedidos por estado (ADMIN)
     */
    @GetMapping("/estado/{estado}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerPedidosPorEstado(@PathVariable EstadoPedido estado) {
        List<PedidoDTO> pedidos = pedidoService.obtenerPedidosPorEstado(estado)
                .stream()
                .map(PedidoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(pedidos);
    }

    /**
     * GET /api/pedidos/admin/ventas-totales
     * Obtiene todas las ventas (items vendidos) de todos los vendedores (ADMIN)
     */
    @GetMapping("/admin/ventas-totales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerTodasLasVentas() {
        // Obtener todos los pedidos y extraer todos los items
        List<Map<String, Object>> todasLasVentas = pedidoService.obtenerTodosLosPedidos()
                .stream()
                .flatMap(pedido -> pedido.getItems().stream().map(item -> {
                    Map<String, Object> venta = new HashMap<>();
                    venta.put("detalleId", item.getId());
                    venta.put("pedidoId", pedido.getId());
                    venta.put("productoNombre", item.getProductoNombre());
                    venta.put("cantidad", item.getCantidad());
                    venta.put("subtotal", item.getSubtotal());
                    venta.put("estadoItem", item.getEstadoItem());
                    venta.put("vendedorId", item.getVendedor() != null ? item.getVendedor().getId() : null);
                    venta.put("vendedorNombre", item.getVendedor() != null ? 
                            item.getVendedor().getNombre() + " " + item.getVendedor().getApellido() : null);
                    venta.put("compradorNombre", pedido.getUsuario() != null ? 
                            pedido.getUsuario().getNombre() + " " + pedido.getUsuario().getApellido() : null);
                    venta.put("fechaPedido", pedido.getCreatedAt());
                    return venta;
                }))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(todasLasVentas);
    }
    
    /**
     * GET /api/pedidos/admin/estadisticas-generales
     * Obtiene estadísticas generales del marketplace (ADMIN)
     */
    @GetMapping("/admin/estadisticas-generales")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> obtenerEstadisticasGenerales() {
        List<Pedido> todosPedidos = pedidoService.obtenerTodosLosPedidos();

        Map<String, Object> estadisticas = new HashMap<>();
        estadisticas.put("totalPedidos", todosPedidos.size());
        estadisticas.put("totalItems", todosPedidos.stream()
                .mapToLong(p -> p.getItems().size())
                .sum());
        estadisticas.put("itemsPendientes", todosPedidos.stream()
                .flatMap(p -> p.getItems().stream())
                .filter(i -> i.getEstadoItem() == EstadoPedido.PENDIENTE)
                .count());
        estadisticas.put("itemsEntregados", todosPedidos.stream()
                .flatMap(p -> p.getItems().stream())
                .filter(i -> i.getEstadoItem() == EstadoPedido.ENTREGADO)
                .count());
        
        return ResponseEntity.ok(estadisticas);
    }

    /**
     * GET /api/pedidos/admin/reportes?desde=&hasta=
     * Reporte de ventas de un período: total facturado, ticket promedio, cantidad de
     * pedidos y ranking de productos más vendidos (ADMIN).
     * `desde`/`hasta` son opcionales y se reciben en formato ISO-8601 con hora
     * (ej. 2026-08-01T00:00:00). Si no vienen, se usa el default: últimos 30 días.
     */
    @GetMapping("/admin/reportes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ReporteVentasDTO> obtenerReporteVentas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime hasta) {
        LocalDateTime desdeEfectivo = desde != null ? desde : LocalDateTime.now().minusDays(30);
        LocalDateTime hastaEfectivo = hasta != null ? hasta : LocalDateTime.now();
        return ResponseEntity.ok(pedidoService.generarReporteVentas(desdeEfectivo, hastaEfectivo));
    }

}

