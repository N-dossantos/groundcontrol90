package com.ecommerce.repository;

import com.ecommerce.entity.Pago;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PagoRepository extends JpaRepository<Pago, Long> {

    // El pago vigente de un pedido: si el comprador reintentó, es el último creado
    Optional<Pago> findTopByPedidoIdOrderByCreatedAtDesc(Long pedidoId);

    Optional<Pago> findByPreferenceId(String preferenceId);
}
