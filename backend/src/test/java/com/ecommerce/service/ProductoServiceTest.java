package com.ecommerce.service;

import com.ecommerce.dto.ProductoVarianteDTO;
import com.ecommerce.entity.Categoria;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.ProductoVariante;
import com.ecommerce.entity.TipoProducto;
import com.ecommerce.entity.Usuario;
import com.ecommerce.repository.ProductoRepository;
import com.ecommerce.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests Unitarios - ProductoService")
class ProductoServiceTest {

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private UsuarioService usuarioService;

    @InjectMocks
    private ProductoService productoService;

    private Producto producto;
    private Categoria categoria;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        categoria = Categoria.builder()
                .id(1L)
                .nombre("Camisetas")
                .build();

        usuario = Usuario.builder()
                .id(1L)
                .nombre("Juan")
                .apellido("Pérez")
                .email("juan@test.com")
                .username("juan")
                .password("password")
                .build();

        producto = Producto.builder()
                .id(1L)
                .name("Camiseta Titular")
                .description("Camiseta titular temporada 2026")
                .price(new BigDecimal("45000.00"))
                .club("Boca Juniors")
                .liga("Liga Profesional Argentina")
                .temporada("2026")
                .tipo(TipoProducto.CAMISETA)
                .categoria(categoria)
                .ownerUser(usuario)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Debería obtener todos los productos")
    void testObtenerTodosLosProductos() {
        // Arrange
        List<Producto> productosEsperados = Arrays.asList(producto);
        when(productoRepository.findAll()).thenReturn(productosEsperados);

        // Act
        List<Producto> resultado = productoService.obtenerTodosLosProductos();

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("Camiseta Titular", resultado.get(0).getName());
        verify(productoRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Debería obtener un producto por ID cuando existe")
    void testObtenerProductoPorId_Existe() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));

        // Act
        Optional<Producto> resultado = productoService.obtenerProductoPorId(1L);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals("Camiseta Titular", resultado.get().getName());
        verify(productoRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("Debería retornar Optional vacío cuando el producto no existe")
    void testObtenerProductoPorId_NoExiste() {
        // Arrange
        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Producto> resultado = productoService.obtenerProductoPorId(999L);

        // Assert
        assertFalse(resultado.isPresent());
        verify(productoRepository, times(1)).findById(999L);
    }

    @Test
    @DisplayName("Debería crear un nuevo producto correctamente")
    void testCrearProducto() {
        // Arrange
        Long ownerUserId = 1L;
        Producto nuevoProducto = Producto.builder()
                .name("Short Titular")
                .price(new BigDecimal("22000.00"))
                .club("River Plate")
                .liga("Liga Profesional Argentina")
                .temporada("2026")
                .tipo(TipoProducto.SHORT)
                .build();

        when(usuarioService.findById(ownerUserId)).thenReturn(usuario);
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto p = invocation.getArgument(0);
            p.setId(2L);
            p.setCreatedAt(LocalDateTime.now());
            return p;
        });

        // Act
        Producto resultado = productoService.crearProducto(nuevoProducto, ownerUserId, List.of());

        // Assert
        assertNotNull(resultado);
        assertNotNull(resultado.getId());
        assertNotNull(resultado.getCreatedAt());
        assertNotNull(resultado.getOwnerUser());
        assertEquals(ownerUserId, resultado.getOwnerUser().getId());
        verify(usuarioService, times(1)).findById(ownerUserId);
        verify(productoRepository, times(1)).save(any(Producto.class));
    }

    @Test
    @DisplayName("Debería crear un producto con club, liga, temporada y tipo")
    void testCrearProducto_ConDatosDeCamiseta() {
        // Arrange
        Producto nuevo = Producto.builder()
                .name("Camiseta Titular 2026")
                .club("Boca Juniors")
                .liga("Liga Profesional Argentina")
                .temporada("2026")
                .tipo(TipoProducto.CAMISETA)
                .price(new BigDecimal("45000"))
                .build();

        when(usuarioService.findById(1L)).thenReturn(usuario);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Producto creado = productoService.crearProducto(nuevo, 1L, List.of());

        // Assert
        assertEquals("Boca Juniors", creado.getClub());
        assertEquals("Liga Profesional Argentina", creado.getLiga());
        assertEquals("2026", creado.getTemporada());
        assertEquals(TipoProducto.CAMISETA, creado.getTipo());
    }

    @Test
    @DisplayName("Debería crear un producto con variantes de talle y calcular el stock total")
    void testCrearProducto_ConVariantes() {
        // Arrange
        Producto nuevo = Producto.builder()
                .name("Camiseta Titular 2026")
                .club("Boca Juniors").liga("Liga Profesional Argentina")
                .temporada("2026").tipo(TipoProducto.CAMISETA)
                .price(new BigDecimal("45000"))
                .build();

        List<ProductoVarianteDTO> variantes = List.of(
                ProductoVarianteDTO.builder().talle("S").stock(5).sku("BOCA-2026-S").build(),
                ProductoVarianteDTO.builder().talle("M").stock(8).sku("BOCA-2026-M").build()
        );

        when(usuarioService.findById(1L)).thenReturn(usuario);
        when(productoRepository.save(any(Producto.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Producto creado = productoService.crearProducto(nuevo, 1L, variantes);

        // Assert
        assertEquals(2, creado.getVariantes().size());
        assertEquals(13, creado.getVariantes().stream().mapToInt(ProductoVariante::getStock).sum());
    }

    @Test
    @DisplayName("Debería actualizar un producto existente")
    void testActualizarProducto_Existe() {
        // Arrange
        Producto productoActualizado = Producto.builder()
                .name("Camiseta Titular Actualizada")
                .price(new BigDecimal("48000.00"))
                .club("Boca Juniors")
                .liga("Liga Profesional Argentina")
                .temporada("2027")
                .tipo(TipoProducto.CAMISETA)
                .build();

        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        when(productoRepository.save(any(Producto.class))).thenAnswer(invocation -> {
            Producto p = invocation.getArgument(0);
            p.setUpdatedAt(LocalDateTime.now());
            return p;
        });

        // Act
        Optional<Producto> resultado = productoService.actualizarProducto(1L, productoActualizado, List.of(), usuario);

        // Assert
        assertTrue(resultado.isPresent());
        assertEquals(1L, resultado.get().getId());
        assertEquals("Camiseta Titular Actualizada", resultado.get().getName());
        assertEquals("2027", resultado.get().getTemporada());
        // El vendedor propietario no viaja en el body de la request: se preserva el existente
        assertNotNull(resultado.get().getOwnerUser());
        assertEquals(usuario.getId(), resultado.get().getOwnerUser().getId());
        verify(productoRepository, times(1)).findById(1L);
        verify(productoRepository, times(1)).save(any(Producto.class));
    }

    @Test
    @DisplayName("Debería retornar Optional vacío al actualizar un producto que no existe")
    void testActualizarProducto_NoExiste() {
        // Arrange
        Producto productoActualizado = Producto.builder()
                .name("Camiseta Titular Actualizada")
                .build();

        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        Optional<Producto> resultado = productoService.actualizarProducto(999L, productoActualizado, List.of(), usuario);

        // Assert
        assertFalse(resultado.isPresent());
        verify(productoRepository, times(1)).findById(999L);
        verify(productoRepository, never()).save(any(Producto.class));
    }

    @Test
    @DisplayName("Debería eliminar un producto cuando existe")
    void testEliminarProducto_Existe() {
        // Arrange
        when(productoRepository.findById(1L)).thenReturn(Optional.of(producto));
        doNothing().when(productoRepository).delete(producto);

        // Act
        boolean resultado = productoService.eliminarProducto(1L, usuario);

        // Assert
        assertTrue(resultado);
        verify(productoRepository, times(1)).findById(1L);
        verify(productoRepository, times(1)).delete(producto);
    }

    @Test
    @DisplayName("Debería retornar false al eliminar un producto que no existe")
    void testEliminarProducto_NoExiste() {
        // Arrange
        when(productoRepository.findById(999L)).thenReturn(Optional.empty());

        // Act
        boolean resultado = productoService.eliminarProducto(999L, usuario);

        // Assert
        assertFalse(resultado);
        verify(productoRepository, times(1)).findById(999L);
        verify(productoRepository, never()).delete(any(Producto.class));
    }

    @Test
    @DisplayName("Debería buscar productos por nombre")
    void testBuscarProductosPorNombre() {
        // Arrange
        List<Producto> productosEsperados = Arrays.asList(producto);
        when(productoRepository.findByNombreContainingIgnoreCase("camiseta")).thenReturn(productosEsperados);

        // Act
        List<Producto> resultado = productoService.buscarProductosPorNombre("camiseta");

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(productoRepository, times(1)).findByNombreContainingIgnoreCase("camiseta");
    }

    @Test
    @DisplayName("Debería retornar todos los productos cuando el nombre es nulo o vacío")
    void testBuscarProductosPorNombre_NuloOVacio() {
        // Arrange
        List<Producto> todosLosProductos = Arrays.asList(producto);
        when(productoRepository.findAll()).thenReturn(todosLosProductos);

        // Act
        List<Producto> resultado1 = productoService.buscarProductosPorNombre(null);
        List<Producto> resultado2 = productoService.buscarProductosPorNombre("");

        // Assert
        assertNotNull(resultado1);
        assertNotNull(resultado2);
        verify(productoRepository, times(2)).findAll();
    }

    @Test
    @DisplayName("Debería buscar productos por categoría")
    void testBuscarProductosPorCategoria() {
        // Arrange
        List<Producto> productosEsperados = Arrays.asList(producto);
        when(productoRepository.findByCategoriaId(1L)).thenReturn(productosEsperados);

        // Act
        List<Producto> resultado = productoService.buscarProductosPorCategoria(1L);

        // Assert
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        verify(productoRepository, times(1)).findByCategoriaId(1L);
    }

    @Test
    @DisplayName("Debería contar productos correctamente")
    void testContarProductos() {
        // Arrange
        when(productoRepository.count()).thenReturn(5L);

        // Act
        long resultado = productoService.contarProductos();

        // Assert
        assertEquals(5L, resultado);
        verify(productoRepository, times(1)).count();
    }

    @Test
    @DisplayName("Debería verificar si existe un producto")
    void testExisteProducto() {
        // Arrange
        when(productoRepository.existsById(1L)).thenReturn(true);
        when(productoRepository.existsById(999L)).thenReturn(false);

        // Act
        boolean existe1 = productoService.existeProducto(1L);
        boolean existe2 = productoService.existeProducto(999L);

        // Assert
        assertTrue(existe1);
        assertFalse(existe2);
        verify(productoRepository, times(2)).existsById(anyLong());
    }
}

