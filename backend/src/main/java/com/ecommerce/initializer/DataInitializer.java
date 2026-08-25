package com.ecommerce.initializer;

import com.ecommerce.entity.Categoria;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.ProductoVariante;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.TipoProducto;
import com.ecommerce.entity.Usuario;
import com.ecommerce.repository.CategoriaRepository;
import com.ecommerce.repository.ProductoRepository;
import com.ecommerce.repository.ProductoVarianteRepository;
import com.ecommerce.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Siembra datos de ejemplo (categorías, productos y usuarios de prueba) para desarrollo.
 *
 * Está limitado al perfil `dev` a propósito: su única guarda es que la base esté vacía,
 * que es exactamente el estado del primer arranque en producción. Sin el @Profile, la
 * tienda real nacería con un admin de credenciales públicas y seis productos demo.
 * En producción el esquema y los datos de arranque los provee Flyway (db/migration/).
 */
@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ProductoVarianteRepository productoVarianteRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Override
    public void run(String... args) throws Exception {
        // Solo inicializar si no hay datos
        if (categoriaRepository.count() == 0) {
            initializeData();
        }
    }

    private void initializeData() {
        // Crear categorías
        Categoria clubesArgentinos = Categoria.builder()
                .nombre("Clubes Argentinos")
                .descripcion("Camisetas y shorts de clubes del fútbol argentino")
                .build();

        Categoria clubesEuropeos = Categoria.builder()
                .nombre("Clubes Europeos")
                .descripcion("Camisetas y shorts de clubes de las principales ligas europeas")
                .build();

        Categoria selecciones = Categoria.builder()
                .nombre("Selecciones")
                .descripcion("Camisetas y shorts de selecciones nacionales")
                .build();

        Categoria retro = Categoria.builder()
                .nombre("Retro")
                .descripcion("Reediciones de camisetas históricas")
                .build();

        categoriaRepository.saveAll(Arrays.asList(clubesArgentinos, clubesEuropeos, selecciones, retro));

        // Crear usuarios
        Usuario admin = Usuario.builder()
                .nombre("Admin")
                .apellido("User")
                .username("admin")
                .email("admin@test.com")
                .password("admin123")  // Se encriptará en el service
                .role(Role.ADMIN)
                .build();

        Usuario usuario1 = Usuario.builder()
                .nombre("User")
                .apellido("One")
                .username("user1")
                .email("user1@test.com")
                .password("user123")  // Se encriptará en el service
                .role(Role.USER)
                .build();

        Usuario usuario2 = Usuario.builder()
                .nombre("Test")
                .apellido("User")
                .username("testuser")
                .email("test@test.com")
                .password("test123")  // Se encriptará en el service
                .role(Role.USER)
                .build();

        // Guardar usuarios usando el service para encriptar contraseñas
        usuarioService.saveUsuario(admin);
        usuarioService.saveUsuario(usuario1);
        usuarioService.saveUsuario(usuario2);

        // Crear productos de ejemplo (el stock vive en las variantes de talle)
        crearProducto(
                "Camiseta Titular Boca Juniors 2026",
                "Camiseta titular oficial temporada 2026, tela liviana con tecnología de secado rápido",
                new BigDecimal("45000.00"), "Boca Juniors", "Liga Profesional Argentina", "2026",
                TipoProducto.CAMISETA, clubesArgentinos, usuario1,
                "https://images.unsplash.com/photo-1517466787929-bc90951d0974?w=800&h=600&fit=crop&crop=center",
                new String[]{"S", "M", "L", "XL"}, new int[]{6, 10, 8, 4}, "BOCA-2026-TIT");

        crearProducto(
                "Camiseta Titular River Plate 2026",
                "Camiseta titular oficial temporada 2026 con la banda roja clásica",
                new BigDecimal("45000.00"), "River Plate", "Liga Profesional Argentina", "2026",
                TipoProducto.CAMISETA, clubesArgentinos, usuario1,
                "https://images.unsplash.com/photo-1580087433295-ab2600c1030e?w=800&h=600&fit=crop&crop=center",
                new String[]{"S", "M", "L", "XL"}, new int[]{5, 12, 9, 3}, "RIVER-2026-TIT");

        crearProducto(
                "Short Titular Boca Juniors 2026",
                "Short titular oficial temporada 2026, cintura elástica con cordón ajustable",
                new BigDecimal("22000.00"), "Boca Juniors", "Liga Profesional Argentina", "2026",
                TipoProducto.SHORT, clubesArgentinos, usuario2,
                "https://images.unsplash.com/photo-1562183241-b937e95585b6?w=800&h=600&fit=crop&crop=center",
                new String[]{"S", "M", "L"}, new int[]{7, 11, 6}, "BOCA-2026-SHORT");

        crearProducto(
                "Camiseta Titular Real Madrid 2026",
                "Camiseta titular blanca temporada 2026, corte atlético",
                new BigDecimal("78000.00"), "Real Madrid", "LaLiga", "2026",
                TipoProducto.CAMISETA, clubesEuropeos, usuario1,
                "https://images.unsplash.com/photo-1577212017184-80cc0da11082?w=800&h=600&fit=crop&crop=center",
                new String[]{"S", "M", "L", "XL", "XXL"}, new int[]{4, 8, 7, 5, 2}, "RMA-2026-TIT");

        crearProducto(
                "Camiseta Selección Argentina 2026",
                "Camiseta titular de la selección argentina, edición con las tres estrellas",
                new BigDecimal("89000.00"), "Selección Argentina", "Selecciones", "2026",
                TipoProducto.CAMISETA, selecciones, usuario2,
                "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=800&h=600&fit=crop&crop=center",
                new String[]{"S", "M", "L", "XL"}, new int[]{10, 15, 12, 6}, "ARG-2026-TIT");

        crearProducto(
                "Camiseta Retro Argentina 1986",
                "Reedición de la camiseta campeona del mundo en México 1986",
                new BigDecimal("65000.00"), "Selección Argentina", "Selecciones", "1986",
                TipoProducto.CAMISETA, retro, usuario2,
                "https://images.unsplash.com/photo-1571945153237-4929e783af4a?w=800&h=600&fit=crop&crop=center",
                new String[]{"M", "L", "XL"}, new int[]{3, 5, 2}, "ARG-1986-RETRO");
    }

    private void crearProducto(String nombre, String descripcion, BigDecimal precio, String club, String liga,
                               String temporada, TipoProducto tipo, Categoria categoria, Usuario owner,
                               String imagen, String[] talles, int[] stocks, String skuBase) {
        Producto producto = productoRepository.save(Producto.builder()
                .name(nombre)
                .description(descripcion)
                .price(precio)
                .club(club)
                .liga(liga)
                .temporada(temporada)
                .tipo(tipo)
                .images(Arrays.asList(imagen))
                .categoria(categoria)
                .ownerUser(owner)
                .build());

        List<ProductoVariante> variantes = new ArrayList<>();
        for (int i = 0; i < talles.length; i++) {
            variantes.add(ProductoVariante.builder()
                    .producto(producto)
                    .talle(talles[i])
                    .stock(stocks[i])
                    .sku(skuBase + "-" + talles[i])
                    .build());
        }
        productoVarianteRepository.saveAll(variantes);
    }
}
