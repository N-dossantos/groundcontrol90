package com.ecommerce.service;

import com.ecommerce.dto.ReporteVentasDTO;
import com.ecommerce.entity.*;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional // hace rollback al terminar cada test: no deja filas de producto/variante/pedido
                // para las clases de test que corren después en el mismo contexto compartido
                // (ver ProductoFiltroTest, que limpia producto_variantes en su propio @BeforeEach)
@DisplayName("Tests de Integración - Reporte de ventas")
// Nota: las comparaciones de BigDecimal en este test usan compareTo (vía assertBigDecimalEquals),
// no assertEquals directo — BigDecimal.equals() es sensible a la escala (155000 != 155000.00
// aunque representen el mismo valor), así que assertEquals produciría falsos negativos.
class ReporteVentasTest {

    @Autowired private PedidoService pedidoService;
    @Autowired private PedidoRepository pedidoRepository;
    @Autowired private DetallePedidoRepository detallePedidoRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository productoVarianteRepository;

    private Usuario vendedor;
    private int contadorSku = 0;

    @BeforeEach
    void setUp() {
        // El contexto de Spring (y por lo tanto la H2 en memoria) se comparte entre clases
        // de test, así que se limpia respetando el orden de las FK (igual que en
        // PedidoConcurrenciaTest, que comparte este mismo contexto).
        detallePedidoRepository.deleteAll();
        pedidoRepository.deleteAll();
        productoVarianteRepository.deleteAll();
        productoRepository.deleteAll();
        usuarioRepository.deleteAll();

        Usuario comprador = usuarioRepository.save(Usuario.builder()
                .nombre("C").apellido("C").username("comprador3").email("comprador3@test.com")
                .password("x").role(Role.USER).build());
        vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("V").apellido("V").username("vendedor3").email("vendedor3@test.com")
                .password("x").role(Role.USER).build());

        crearPedidoConfirmado(comprador, "Camiseta Boca", 2, new BigDecimal("45000"));
        crearPedidoConfirmado(comprador, "Camiseta Boca", 1, new BigDecimal("45000"));
        crearPedidoConfirmado(comprador, "Short River", 1, new BigDecimal("20000"));
        crearPedidoPendiente(comprador, "Camiseta River", 5, new BigDecimal("45000")); // no debe contar
    }

    private void crearPedidoConfirmado(Usuario comprador, String producto, int cantidad, BigDecimal precioUnitario) {
        guardarPedido(comprador, producto, cantidad, precioUnitario, EstadoPedido.CONFIRMADO);
    }

    private void crearPedidoPendiente(Usuario comprador, String producto, int cantidad, BigDecimal precioUnitario) {
        guardarPedido(comprador, producto, cantidad, precioUnitario, EstadoPedido.PENDIENTE);
    }

    private void guardarPedido(Usuario comprador, String producto, int cantidad, BigDecimal precioUnitario, EstadoPedido estado) {
        // DetallePedido.producto/variante/vendedor son NOT NULL a nivel de columna, así que
        // hace falta una fila mínima de Producto + ProductoVariante para poder persistir el
        // detalle (el brief original no las seteaba). El reporte agrupa por el snapshot
        // productoNombre, no por estas entidades, así que esto no afecta lo que se testea.
        Producto productoEntity = productoRepository.save(Producto.builder()
                .name(producto).club("Club").liga("Liga").temporada("2026")
                .tipo(TipoProducto.CAMISETA).price(precioUnitario).ownerUser(vendedor)
                .createdAt(LocalDateTime.now()).build());
        ProductoVariante variante = productoVarianteRepository.save(ProductoVariante.builder()
                .producto(productoEntity).talle("M").stock(100).sku("SKU-" + (++contadorSku)).build());

        Pedido pedido = pedidoRepository.save(Pedido.builder()
                .usuario(comprador).estado(estado)
                .total(precioUnitario.multiply(BigDecimal.valueOf(cantidad)))
                .createdAt(LocalDateTime.now()).items(new java.util.ArrayList<>()).build());
        DetallePedido item = DetallePedido.builder()
                .pedido(pedido).producto(productoEntity).variante(variante).vendedor(vendedor)
                .productoNombre(producto).talle("M").cantidad(cantidad)
                .precioUnitario(precioUnitario).estadoItem(EstadoPedido.PENDIENTE).build();
        detallePedidoRepository.save(item);
    }

    @Test
    @DisplayName("Debería sumar solo pedidos pagados y rankear productos por cantidad vendida")
    void testGenerarReporteVentas() {
        ReporteVentasDTO reporte = pedidoService.generarReporteVentas(
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));

        assertBigDecimalEquals("155000", reporte.getVentasTotales()); // (2+1)*45000 + 1*20000
        assertEquals(3, reporte.getCantidadPedidos());
        assertEquals("Camiseta Boca", reporte.getProductosMasVendidos().get(0).getNombre());
        assertEquals(3L, reporte.getProductosMasVendidos().get(0).getCantidadVendida());
    }

    private void assertBigDecimalEquals(String esperado, BigDecimal real) {
        assertEquals(0, new BigDecimal(esperado).compareTo(real),
                () -> "Esperado " + esperado + " pero fue " + real);
    }
}
