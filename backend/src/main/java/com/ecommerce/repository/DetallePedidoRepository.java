package com.ecommerce.repository;

import com.ecommerce.entity.DetallePedido;
import com.ecommerce.entity.EstadoPedido;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DetallePedidoRepository extends JpaRepository<DetallePedido, Long> {
    
    // Buscar detalles por pedido
    List<DetallePedido> findByPedidoId(Long pedidoId);
    
    // Buscar detalles por producto
    List<DetallePedido> findByProductoId(Long productoId);

    // ¿Esta variante (talle) ya se vendió alguna vez?
    boolean existsByVarianteId(Long varianteId);
    
    // ========== MÉTODOS PARA VENDEDORES ==========
    
    // Buscar todas las ventas de un vendedor (ordenadas por fecha de pedido descendente)
    List<DetallePedido> findByVendedorIdOrderByPedidoCreatedAtDesc(Long vendedorId);
    
    // Buscar ventas de un vendedor por estado del item
    List<DetallePedido> findByVendedorIdAndEstadoItemOrderByPedidoCreatedAtDesc(Long vendedorId, EstadoPedido estadoItem);
    
    // Contar ventas totales de un vendedor
    Long countByVendedorId(Long vendedorId);
    
    // Contar ventas de un vendedor por estado
    Long countByVendedorIdAndEstadoItem(Long vendedorId, EstadoPedido estadoItem);

    // ========== MÉTODOS PARA REPORTES (ADMIN) ==========

    interface ProductoMasVendidoProjection {
        String getNombre();
        Long getCantidadVendida();
    }

    // Ranking de productos por cantidad vendida en un período, excluyendo pedidos que no llegaron a pagarse
    @Query("SELECT d.productoNombre AS nombre, SUM(d.cantidad) AS cantidadVendida " +
           "FROM DetallePedido d " +
           "WHERE d.pedido.createdAt BETWEEN :desde AND :hasta " +
           "AND d.pedido.estado NOT IN :estadosExcluidos " +
           "GROUP BY d.productoNombre " +
           "ORDER BY SUM(d.cantidad) DESC")
    List<ProductoMasVendidoProjection> productosMasVendidos(
            @Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta,
            @Param("estadosExcluidos") List<EstadoPedido> estadosExcluidos);
}

