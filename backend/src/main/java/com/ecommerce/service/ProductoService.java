package com.ecommerce.service;

import com.ecommerce.dto.ProductoFiltroDTO;
import com.ecommerce.dto.ProductoVarianteDTO;
import com.ecommerce.entity.Categoria;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.ProductoVariante;
import com.ecommerce.entity.Role;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.CategoriaNotFoundException;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.exception.UsuarioNotFoundException;
import com.ecommerce.exception.ValidationException;
import com.ecommerce.repository.CategoriaRepository;
import com.ecommerce.repository.DetallePedidoRepository;
import com.ecommerce.repository.ProductoRepository;
import com.ecommerce.repository.spec.ProductoSpecifications;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProductoService {

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private DetallePedidoRepository detallePedidoRepository;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private CategoriaRepository categoriaRepository;

    /**
     * Obtener todos los productos
     */
    @Transactional(readOnly = true)
    public List<Producto> obtenerTodosLosProductos() {
        return productoRepository.findAll();
    }

    /**
     * Obtener producto por ID
     */
    @Transactional(readOnly = true)
    public Optional<Producto> obtenerProductoPorId(Long id) {
        return productoRepository.findById(id);
    }

    /**
     * Crear nuevo producto con sus variantes de talle
     * Asigna automáticamente el producto al usuario (vendedor) que lo crea
     */
    public Producto crearProducto(Producto producto, Long ownerUserId, List<ProductoVarianteDTO> variantes) {
        // Obtener el usuario propietario
        Usuario ownerUser = usuarioService.findById(ownerUserId);
        if (ownerUser == null) {
            throw new UsuarioNotFoundException(ownerUserId);
        }

        // Asignar el usuario como propietario del producto
        producto.setOwnerUser(ownerUser);
        producto.setCategoria(categoriaDe(producto));
        producto.setCreatedAt(LocalDateTime.now());

        Producto guardado = productoRepository.save(producto);
        reemplazarVariantes(guardado, variantes);

        return guardado;
    }

    /**
     * Actualizar producto existente y su lista de variantes
     * Se modifica la entidad ya persistida en vez de guardar la que llega en el body,
     * porque el body no trae el vendedor propietario ni la fecha de creación.
     *
     * `usuario` es quien hace el pedido: sin verificar su propiedad sobre el producto,
     * cualquier cuenta registrada podría reescribir el precio o el stock de otro vendedor.
     */
    public Optional<Producto> actualizarProducto(Long id, Producto productoActualizado,
                                                 List<ProductoVarianteDTO> variantes,
                                                 Usuario usuario) {
        return productoRepository.findById(id)
                .map(productoExistente -> {
                    verificarPropiedad(productoExistente, usuario);
                    productoExistente.setName(productoActualizado.getName());
                    productoExistente.setDescription(productoActualizado.getDescription());
                    productoExistente.setPrice(productoActualizado.getPrice());
                    productoExistente.setClub(productoActualizado.getClub());
                    productoExistente.setLiga(productoActualizado.getLiga());
                    productoExistente.setTemporada(productoActualizado.getTemporada());
                    productoExistente.setTipo(productoActualizado.getTipo());
                    productoExistente.setImages(productoActualizado.getImages());
                    productoExistente.setCategoria(categoriaDe(productoActualizado));
                    productoExistente.setUpdatedAt(LocalDateTime.now());

                    Producto guardado = productoRepository.save(productoExistente);
                    reemplazarVariantes(guardado, variantes);

                    return guardado;
                });
    }

    /**
     * Desde la API la categoría llega como id ("categoriaId" en el JSON) y hay que resolverla;
     * sin id, vale la que trae el producto: null si el vendedor no eligió ninguna, o la
     * entidad que arman los llamadores internos como DataInitializer.
     */
    private Categoria categoriaDe(Producto datos) {
        if (datos.getCategoriaElegidaId() == null) {
            return datos.getCategoria();
        }
        return categoriaRepository.findById(datos.getCategoriaElegidaId())
                .orElseThrow(() -> new CategoriaNotFoundException(datos.getCategoriaElegidaId()));
    }

    /**
     * Deja las variantes (talles) del producto igual a la lista recibida.
     *
     * Las variantes se aparean por talle y se actualizan en su lugar en vez de borrarlas
     * y recrearlas: su id es lo que referencian el historial de pedidos y los carritos
     * de los compradores, así que recrearlas rompería ambos.
     */
    public void reemplazarVariantes(Producto producto, List<ProductoVarianteDTO> variantesDTO) {
        List<ProductoVarianteDTO> deseadas = variantesDTO == null ? List.of() : variantesDTO;

        // La colección se muta en vez de sustituirla por otra instancia: con orphanRemoval,
        // Hibernate falla al commit si el producto deja de referenciar la que él administra.
        if (producto.getVariantes() == null) {
            producto.setVariantes(new ArrayList<>());
        }
        List<ProductoVariante> actuales = producto.getVariantes();

        Set<String> tallesDeseados = deseadas.stream()
                .map(ProductoVarianteDTO::getTalle)
                .collect(Collectors.toSet());

        List<ProductoVariante> aEliminar = actuales.stream()
                .filter(variante -> !tallesDeseados.contains(variante.getTalle()))
                .collect(Collectors.toList());

        for (ProductoVariante variante : aEliminar) {
            // Un talle ya vendido no se puede borrar: el detalle del pedido lo referencia.
            // Para dejar de ofrecerlo, el vendedor le pone stock 0.
            if (variante.getId() != null && detallePedidoRepository.existsByVarianteId(variante.getId())) {
                throw new ValidationException("No se puede eliminar el talle " + variante.getTalle()
                        + " porque ya tiene ventas. Poné su stock en 0 para dejar de ofrecerlo.");
            }
        }
        actuales.removeAll(aEliminar);

        // El flush materializa los DELETE antes de los INSERT de más abajo: el sku es único
        // y una edición puede reutilizar en un talle el sku de otro que se está eliminando.
        productoRepository.flush();

        for (ProductoVarianteDTO dto : deseadas) {
            actuales.stream()
                    .filter(variante -> dto.getTalle().equals(variante.getTalle()))
                    .findFirst()
                    .ifPresentOrElse(
                            existente -> {
                                existente.setStock(dto.getStock());
                                existente.setSku(dto.getSku());
                            },
                            () -> actuales.add(ProductoVariante.builder()
                                    .producto(producto)
                                    .talle(dto.getTalle())
                                    .stock(dto.getStock())
                                    .sku(dto.getSku())
                                    .build()));
        }
    }

    /**
     * Eliminar producto. Sólo puede borrarlo su vendedor propietario, o un admin.
     */
    public boolean eliminarProducto(Long id, Usuario usuario) {
        return productoRepository.findById(id)
                .map(producto -> {
                    verificarPropiedad(producto, usuario);
                    productoRepository.delete(producto);
                    return true;
                })
                .orElse(false);
    }

    /**
     * El dueño del producto o un admin. Sin este chequeo cualquier cuenta registrada
     * puede reescribir el precio o borrar el producto de otro vendedor: siendo un
     * marketplace multi-vendedor, `authenticated()` en SecurityConfig no alcanza porque
     * todos los vendedores tienen el mismo rol.
     */
    private void verificarPropiedad(Producto producto, Usuario usuario) {
        if (usuario == null) {
            throw new ForbiddenException("No tenés permiso sobre este producto");
        }
        if (usuario.getRole() == Role.ADMIN) {
            return;
        }
        Usuario dueño = producto.getOwnerUser();
        if (dueño == null || !dueño.getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso sobre este producto");
        }
    }

    /**
     * Buscar productos por nombre
     */
    @Transactional(readOnly = true)
    public List<Producto> buscarProductosPorNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            return obtenerTodosLosProductos();
        }
        
        return productoRepository.findByNombreContainingIgnoreCase(nombre);
    }

    /**
     * Buscar productos por categoría
     */
    @Transactional(readOnly = true)
    public List<Producto> buscarProductosPorCategoria(Long categoryId) {
        return productoRepository.findByCategoriaId(categoryId);
    }

    /**
     * Filtrar el catálogo combinando club, liga, tipo, talle y rango de precio.
     * Los filtros no informados se ignoran.
     */
    @Transactional(readOnly = true)
    public List<Producto> filtrarProductos(ProductoFiltroDTO filtro) {
        return productoRepository.findAll(ProductoSpecifications.conFiltro(filtro));
    }

    /**
     * Buscar productos por propietario
     */
    @Transactional(readOnly = true)
    public List<Producto> buscarProductosPorPropietario(Long ownerUserId) {
        return productoRepository.findByOwnerUserId(ownerUserId);
    }

    /**
     * Buscar productos por rango de precio
     */
    @Transactional(readOnly = true)
    public List<Producto> buscarProductosPorPrecio(BigDecimal precioMin, BigDecimal precioMax) {
        return productoRepository.findByPriceBetween(precioMin, precioMax);
    }

    /**
     * Contar total de productos
     */
    @Transactional(readOnly = true)
    public long contarProductos() {
        return productoRepository.count();
    }

    /**
     * Verificar si existe un producto
     */
    @Transactional(readOnly = true)
    public boolean existeProducto(Long id) {
        return productoRepository.existsById(id);
    }
}