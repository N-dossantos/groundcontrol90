package com.ecommerce.service;

import com.ecommerce.dto.CreatePedidoDTO;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prueba que el @Version de ProductoVariante cumple su propósito: dos checkouts
 * simultáneos por la última unidad no pueden sobrevender.
 *
 * Es un test de integración con DB real (H2) porque el conflicto lo detecta el UPDATE
 * de Hibernate contra la fila versionada — con Mockito, que no ejecuta SQL, no existiría.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tests de Integración - Concurrencia de stock en checkout")
class PedidoConcurrenciaTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository productoVarianteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Long varianteId;
    private Long compradorId;

    @BeforeEach
    void setUp() {
        // El contexto de Spring (y por lo tanto la H2 en memoria) se comparte entre clases
        // de test, así que se limpia respetando el orden de las FK.
        pedidoRepository.deleteAll();
        productoVarianteRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("V").apellido("V").username("vendedor2").email("vendedor2@test.com")
                .password("x").role(Role.USER).build());
        Usuario comprador = usuarioRepository.save(Usuario.builder()
                .nombre("C").apellido("C").username("comprador2").email("comprador2@test.com")
                .password("x").role(Role.USER).build());
        compradorId = comprador.getId();

        Producto producto = productoRepository.save(Producto.builder()
                .name("Última Camiseta").club("Boca Juniors").liga("LPA").temporada("2026")
                .tipo(TipoProducto.CAMISETA).price(new BigDecimal("50000")).ownerUser(vendedor)
                .createdAt(java.time.LocalDateTime.now()).build());
        ProductoVariante variante = productoVarianteRepository.save(ProductoVariante.builder()
                .producto(producto).talle("M").stock(1).sku("ULTIMA-M").build());
        varianteId = variante.getId();
    }

    @Test
    @DisplayName("Dos pedidos simultáneos por la última unidad: solo uno debería tener éxito")
    void testDosCheckoutsConcurrentes_SoloUnoGanaLaUltimaUnidad() throws InterruptedException {
        int intentos = 2;
        ExecutorService executor = Executors.newFixedThreadPool(intentos);
        CountDownLatch listos = new CountDownLatch(intentos);
        CountDownLatch arrancar = new CountDownLatch(1);
        AtomicInteger exitosos = new AtomicInteger(0);
        AtomicInteger fallidos = new AtomicInteger(0);

        for (int i = 0; i < intentos; i++) {
            executor.submit(() -> {
                try {
                    listos.countDown();
                    arrancar.await();
                    CreatePedidoDTO dto = CreatePedidoDTO.builder()
                            .items(List.of(new CreatePedidoDTO.ItemCarritoDTO(varianteId, 1)))
                            .direccionEnvio("Calle Falsa 123").build();
                    pedidoService.crearPedido(compradorId, dto);
                    exitosos.incrementAndGet();
                } catch (Exception e) {
                    // Perder la carrera se manifiesta de dos formas según el timing:
                    // ObjectOptimisticLockingFailureException si ambas transacciones se
                    // solapan, o StockInsuficienteException si la segunda arranca después
                    // de que la primera ya commiteó. Ambas son el resultado correcto.
                    fallidos.incrementAndGet();
                }
            });
        }

        listos.await();
        arrancar.countDown();
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        assertEquals(1, exitosos.get(), "Solo un checkout debería haber conseguido la última unidad");
        assertEquals(1, fallidos.get(), "El otro checkout debería haber fallado, no sobrevender");

        ProductoVariante varianteFinal = productoVarianteRepository.findById(varianteId).orElseThrow();
        assertEquals(0, varianteFinal.getStock(), "El stock nunca puede quedar negativo");
    }
}
