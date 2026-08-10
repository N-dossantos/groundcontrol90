package com.ecommerce.service;

import com.ecommerce.dto.ProductoFiltroDTO;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.ProductoVariante;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.TipoProducto;
import com.ecommerce.entity.Usuario;
import com.ecommerce.repository.ProductoRepository;
import com.ecommerce.repository.ProductoVarianteRepository;
import com.ecommerce.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Tests de Integración - Filtro de catálogo")
class ProductoFiltroTest {

    @Autowired private ProductoService productoService;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ProductoVarianteRepository productoVarianteRepository;
    @Autowired private UsuarioRepository usuarioRepository;

    private Usuario vendedor;

    @BeforeEach
    void setUp() {
        // El DataInitializer siembra el catálogo de ejemplo al levantar el contexto:
        // se limpia para que las aserciones cuenten solo los productos de este test.
        // La transacción del test hace rollback al terminar, así que el resto de la
        // suite sigue viendo los datos sembrados.
        productoVarianteRepository.deleteAllInBatch();
        productoRepository.deleteAllInBatch();

        vendedor = usuarioRepository.save(Usuario.builder()
                .nombre("Vendedor").apellido("Test").username("vendedor-filtro")
                .email("vendedor-filtro@test.com")
                .password("x").role(Role.USER).build());

        crearProducto("Camiseta Boca", "Boca Juniors", "Liga Profesional Argentina",
                TipoProducto.CAMISETA, "M", new BigDecimal("45000"));
        crearProducto("Short River", "River Plate", "Liga Profesional Argentina",
                TipoProducto.SHORT, "L", new BigDecimal("22000"));
    }

    private void crearProducto(String nombre, String club, String liga, TipoProducto tipo,
                               String talle, BigDecimal precio) {
        Producto producto = productoRepository.save(Producto.builder()
                .name(nombre).club(club).liga(liga).temporada("2026").tipo(tipo)
                .price(precio).ownerUser(vendedor).createdAt(java.time.LocalDateTime.now())
                .build());
        productoVarianteRepository.save(ProductoVariante.builder()
                .producto(producto).talle(talle).stock(10).sku(nombre + "-" + talle).build());
    }

    @Test
    @DisplayName("Debería filtrar productos por club")
    void testFiltrarPorClub() {
        List<Producto> resultado = productoService.filtrarProductos(
                ProductoFiltroDTO.builder().club("Boca Juniors").build());

        assertEquals(1, resultado.size());
        assertEquals("Camiseta Boca", resultado.get(0).getName());
    }

    @Test
    @DisplayName("Debería filtrar productos por tipo y talle combinados")
    void testFiltrarPorTipoYTalle() {
        List<Producto> resultado = productoService.filtrarProductos(
                ProductoFiltroDTO.builder().tipo(TipoProducto.SHORT).talle("L").build());

        assertEquals(1, resultado.size());
        assertEquals("Short River", resultado.get(0).getName());
    }

    @Test
    @DisplayName("Debería filtrar productos por rango de precio")
    void testFiltrarPorRangoDePrecio() {
        List<Producto> resultado = productoService.filtrarProductos(
                ProductoFiltroDTO.builder().precioMax(new BigDecimal("30000")).build());

        assertEquals(1, resultado.size());
        assertEquals("Short River", resultado.get(0).getName());
    }

    @Test
    @DisplayName("Debería devolver todos los productos cuando el filtro viene vacío")
    void testFiltroVacio() {
        List<Producto> resultado = productoService.filtrarProductos(ProductoFiltroDTO.builder().build());

        assertEquals(2, resultado.size());
    }
}
