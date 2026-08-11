package com.ecommerce.service;

import com.ecommerce.dto.CreatePedidoDTO;
import com.ecommerce.entity.*;
import com.ecommerce.exception.ProductoNotFoundException;
import com.ecommerce.exception.StockInsuficienteException;
import com.ecommerce.exception.UsuarioNotFoundException;
import com.ecommerce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - PedidoService")
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private DetallePedidoRepository detallePedidoRepository;

    @Mock
    private ProductoVarianteRepository productoVarianteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private PedidoService pedidoService;

    private Usuario usuario;
    private Usuario vendedor;
    private Producto producto;
    private ProductoVariante talleS;
    private ProductoVariante talleM;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .email("juan@test.com")
                .username("juan")
                .password("password")
                .role(Role.USER)
                .build();

        vendedor = Usuario.builder()
                .id(2L)
                .nombre("Vendedor")
                .email("vendedor@test.com")
                .build();

        producto = Producto.builder()
                .id(1L)
                .name("Camiseta Titular")
                .price(new BigDecimal("45000.00"))
                .club("Boca Juniors")
                .liga("Liga Profesional Argentina")
                .temporada("2026")
                .tipo(TipoProducto.CAMISETA)
                .ownerUser(vendedor)
                .build();

        talleS = ProductoVariante.builder()
                .id(10L).producto(producto).talle("S").stock(5).sku("BOCA-2026-S").build();
        talleM = ProductoVariante.builder()
                .id(11L).producto(producto).talle("M").stock(8).sku("BOCA-2026-M").build();
        producto.setVariantes(List.of(talleS, talleM));

        pedido = Pedido.builder()
                .id(1L)
                .usuario(usuario)
                .estado(EstadoPedido.PENDIENTE)
                .total(new BigDecimal("1500.00"))
                .createdAt(LocalDateTime.now())
                .items(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("Debería obtener todos los pedidos")
    void testObtenerTodosLosPedidos() {
        // Arrange
        List<Pedido> pedidosEsperados = Arrays.asList(pedido);
        when(pedidoRepository.findAll()).thenReturn(pedidosEsperados);

        // Act
        List<Pedido> resultado = pedidoService.obtenerTodosLosPedidos();

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(pedidoRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Debería obtener un pedido por ID cuando existe")
    void testObtenerPedidoPorId_Existe() {
        // Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));

        // Act
        Optional<Pedido> resultado = pedidoService.obtenerPedidoPorId(1L);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals(EstadoPedido.PENDIENTE, resultado.get().getEstado());
        verify(pedidoRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Debería obtener pedidos por usuario")
    void testObtenerPedidosPorUsuario() {
        // Arrange
        List<Pedido> pedidosEsperados = Arrays.asList(pedido);
        when(pedidoRepository.findByUsuarioIdOrderByCreatedAtDesc(1L)).thenReturn(pedidosEsperados);

        // Act
        List<Pedido> resultado = pedidoService.obtenerPedidosPorUsuario(1L);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(pedidoRepository, times(1)).findByUsuarioIdOrderByCreatedAtDesc(1L);
    }

    @Test
    @DisplayName("Debería crear un pedido correctamente")
    void testCrearPedido() {
        // Arrange
        CreatePedidoDTO.ItemCarritoDTO itemDTO = CreatePedidoDTO.ItemCarritoDTO.builder()
                .productoVarianteId(11L)
                .cantidad(1)
                .build();

        CreatePedidoDTO createPedidoDTO = CreatePedidoDTO.builder()
                .items(Arrays.asList(itemDTO))
                .direccionEnvio("Calle Falsa 123")
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(productoVarianteRepository.findById(11L)).thenReturn(Optional.of(talleM));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(invocation -> {
            Pedido p = invocation.getArgument(0);
            p.setId(1L);
            return p;
        });

        // Act
        Pedido resultado = pedidoService.crearPedido(1L, createPedidoDTO);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.getItems().size());
        assertEquals("M", resultado.getItems().get(0).getTalle());
        verify(usuarioRepository, times(1)).findById(1L);
        verify(productoVarianteRepository, times(1)).findById(11L);
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Debería descontar el stock de la variante (talle) correcta, no de otras variantes del mismo producto")
    void testCrearPedido_DescuentaSoloLaVarianteComprada() {
        // Arrange
        CreatePedidoDTO dto = CreatePedidoDTO.builder()
                .items(List.of(new CreatePedidoDTO.ItemCarritoDTO(11L, 3)))
                .direccionEnvio("Calle Falsa 123")
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(productoVarianteRepository.findById(11L)).thenReturn(Optional.of(talleM));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        pedidoService.crearPedido(1L, dto);

        // Assert
        assertEquals(5, talleS.getStock()); // sin cambios
        assertEquals(5, talleM.getStock()); // 8 - 3
        verify(productoVarianteRepository).save(talleM);
    }

    @Test
    @DisplayName("Debería lanzar excepción cuando el usuario no existe")
    void testCrearPedido_UsuarioNoExiste() {
        // Arrange
        CreatePedidoDTO createPedidoDTO = CreatePedidoDTO.builder()
                .items(new ArrayList<>())
                .build();

        when(usuarioRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(UsuarioNotFoundException.class, 
                () -> pedidoService.crearPedido(999L, createPedidoDTO));
        verify(usuarioRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Debería lanzar excepción cuando la variante no existe")
    void testCrearPedido_VarianteNoExiste() {
        // Arrange
        CreatePedidoDTO.ItemCarritoDTO itemDTO = CreatePedidoDTO.ItemCarritoDTO.builder()
                .productoVarianteId(999L)
                .cantidad(1)
                .build();

        CreatePedidoDTO createPedidoDTO = CreatePedidoDTO.builder()
                .items(Arrays.asList(itemDTO))
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(productoVarianteRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ProductoNotFoundException.class,
                () -> pedidoService.crearPedido(1L, createPedidoDTO));
    }

    @Test
    @DisplayName("Debería lanzar excepción cuando no hay stock suficiente en el talle elegido")
    void testCrearPedido_StockInsuficiente() {
        // Arrange
        talleM.setStock(0); // Sin stock en ese talle

        CreatePedidoDTO.ItemCarritoDTO itemDTO = CreatePedidoDTO.ItemCarritoDTO.builder()
                .productoVarianteId(11L)
                .cantidad(1)
                .build();

        CreatePedidoDTO createPedidoDTO = CreatePedidoDTO.builder()
                .items(Arrays.asList(itemDTO))
                .build();

        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(productoVarianteRepository.findById(11L)).thenReturn(Optional.of(talleM));

        // Act & Assert
        assertThrows(StockInsuficienteException.class,
                () -> pedidoService.crearPedido(1L, createPedidoDTO));
    }

    @Test
    @DisplayName("Debería actualizar el estado de un pedido")
    void testActualizarEstado() {
        // Arrange
        when(pedidoRepository.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidoRepository.save(any(Pedido.class))).thenReturn(pedido);

        // Act
        Pedido resultado = pedidoService.actualizarEstado(1L, EstadoPedido.CANCELADO_COMPRADOR);

        // Assert
        assertNotNull(resultado);
        verify(pedidoRepository, times(1)).findById(1L);
        verify(pedidoRepository, times(1)).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Debería obtener pedidos por estado")
    void testObtenerPedidosPorEstado() {
        // Arrange
        List<Pedido> pedidosEsperados = Arrays.asList(pedido);
        when(pedidoRepository.findByEstado(EstadoPedido.PENDIENTE)).thenReturn(pedidosEsperados);

        // Act
        List<Pedido> resultado = pedidoService.obtenerPedidosPorEstado(EstadoPedido.PENDIENTE);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(pedidoRepository, times(1)).findByEstado(EstadoPedido.PENDIENTE);
    }

    @Test
    @DisplayName("Debería liberar el stock de la variante y marcar el pedido como PAGO_RECHAZADO")
    void testCancelarPedidoPorPagoRechazado_LiberaStock() {
        ProductoVariante variante = ProductoVariante.builder().id(20L).talle("M").stock(2).sku("SKU-M").build();
        DetallePedido item = DetallePedido.builder().variante(variante).cantidad(3)
                .estadoItem(EstadoPedido.PENDIENTE).build();
        Pedido pedidoPendiente = Pedido.builder().id(50L).estado(EstadoPedido.PENDIENTE)
                .items(List.of(item)).build();

        when(pedidoRepository.findById(50L)).thenReturn(Optional.of(pedidoPendiente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido resultado = pedidoService.cancelarPedidoPorPagoRechazado(50L);

        assertEquals(5, variante.getStock()); // 2 + 3 devueltos
        assertEquals(EstadoPedido.PAGO_RECHAZADO, resultado.getEstado());
        assertEquals(EstadoPedido.PAGO_RECHAZADO, item.getEstadoItem());
    }

    @Test
    @DisplayName("No debería devolver el stock dos veces si llega un webhook de rechazo duplicado")
    void testCancelarPedidoPorPagoRechazado_PedidoYaNoPendiente_NoTocaElStock() {
        ProductoVariante variante = ProductoVariante.builder().id(21L).talle("L").stock(2).sku("SKU-L").build();
        DetallePedido item = DetallePedido.builder().variante(variante).cantidad(3)
                .estadoItem(EstadoPedido.PAGO_RECHAZADO).build();
        Pedido yaRechazado = Pedido.builder().id(51L).estado(EstadoPedido.PAGO_RECHAZADO)
                .items(List.of(item)).build();

        when(pedidoRepository.findById(51L)).thenReturn(Optional.of(yaRechazado));

        Pedido resultado = pedidoService.cancelarPedidoPorPagoRechazado(51L);

        assertEquals(2, variante.getStock(), "El stock ya se había devuelto, no se duplica");
        assertEquals(EstadoPedido.PAGO_RECHAZADO, resultado.getEstado());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }

    @Test
    @DisplayName("Debería confirmar el pedido y sus items cuando el pago es aprobado")
    void testConfirmarPago_MueveAConfirmado() {
        DetallePedido item = DetallePedido.builder().cantidad(1).estadoItem(EstadoPedido.PENDIENTE).build();
        Pedido pedidoPendiente = Pedido.builder().id(60L).estado(EstadoPedido.PENDIENTE)
                .items(List.of(item)).build();

        when(pedidoRepository.findById(60L)).thenReturn(Optional.of(pedidoPendiente));
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> inv.getArgument(0));

        Pedido resultado = pedidoService.confirmarPago(60L);

        assertEquals(EstadoPedido.CONFIRMADO, resultado.getEstado());
        assertEquals(EstadoPedido.CONFIRMADO, item.getEstadoItem());
    }

    @Test
    @DisplayName("No debería tocar un pedido que ya salió de PENDIENTE (webhook duplicado)")
    void testConfirmarPago_PedidoYaNoPendiente_NoHaceNada() {
        Pedido yaConfirmado = Pedido.builder().id(61L).estado(EstadoPedido.CONFIRMADO)
                .items(List.of()).build();
        when(pedidoRepository.findById(61L)).thenReturn(Optional.of(yaConfirmado));

        Pedido resultado = pedidoService.confirmarPago(61L);

        assertEquals(EstadoPedido.CONFIRMADO, resultado.getEstado());
        verify(pedidoRepository, never()).save(any(Pedido.class));
    }
}

