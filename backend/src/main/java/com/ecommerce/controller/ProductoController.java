package com.ecommerce.controller;

import com.ecommerce.dto.ProductoDTO;
import com.ecommerce.dto.ProductoFiltroDTO;
import com.ecommerce.dto.ProductoRequestDTO;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.TipoProducto;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.ProductoNotFoundException;
import com.ecommerce.service.ProductoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/productos")
public class ProductoController {
    
    @Autowired
    private ProductoService productoService;

    /**
     * GET /api/productos
     * Obtiene todos los productos
     */
    @GetMapping
    public ResponseEntity<List<ProductoDTO>> obtenerTodosLosProductos() {
        List<ProductoDTO> productos = productoService.obtenerTodosLosProductos()
                .stream()
                .map(ProductoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }
    
    /**
     * GET /api/productos/{id}
     * Obtiene un producto por su ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProductoDTO> obtenerProductoPorId(@PathVariable Long id) {
        Optional<Producto> producto = productoService.obtenerProductoPorId(id);
        return producto.map(p -> ResponseEntity.ok(new ProductoDTO(p)))
                     .orElseThrow(() -> new ProductoNotFoundException(id));
    }
    
    /**
     * POST /api/productos
     * Crea un nuevo producto con sus variantes de talle
     * Asigna automáticamente el producto al vendedor autenticado
     */
    @PostMapping
    public ResponseEntity<ProductoDTO> crearProducto(
            @RequestBody ProductoRequestDTO request,
            @AuthenticationPrincipal Usuario usuario) {
        // Crear el producto asignándolo al usuario autenticado
        Producto productoCreado = productoService.crearProducto(
                request.getProducto(), usuario.getId(), request.getVariantes());
        return ResponseEntity.status(HttpStatus.CREATED).body(new ProductoDTO(productoCreado));
    }

    /**
     * PUT /api/productos/{id}
     * Actualiza un producto existente y su lista de variantes
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProductoDTO> actualizarProducto(@PathVariable Long id,
                                                          @RequestBody ProductoRequestDTO request) {
        Optional<Producto> productoActualizado = productoService.actualizarProducto(
                id, request.getProducto(), request.getVariantes());
        return productoActualizado.map(p -> ResponseEntity.ok(new ProductoDTO(p)))
                                .orElseThrow(() -> new ProductoNotFoundException(id));
    }
    
    /**
     * DELETE /api/productos/{id}
     * Elimina un producto por su ID
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarProducto(@PathVariable Long id) {
        boolean eliminado = productoService.eliminarProducto(id);
        if (!eliminado) {
            throw new ProductoNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
    
    /**
     * GET /api/productos/buscar?nombre={nombre}
     * Busca productos por nombre
     */
    @GetMapping("/buscar")
    public ResponseEntity<List<ProductoDTO>> buscarProductosPorNombre(@RequestParam(required = false) String nombre) {
        List<ProductoDTO> productos = productoService.buscarProductosPorNombre(nombre)
                .stream()
                .map(ProductoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }
    
    /**
     * GET /api/productos/categoria/{categoryId}
     * Busca productos por categoría
     */
    @GetMapping("/categoria/{categoryId}")
    public ResponseEntity<List<ProductoDTO>> buscarProductosPorCategoria(@PathVariable Long categoryId) {
        List<ProductoDTO> productos = productoService.buscarProductosPorCategoria(categoryId)
                .stream()
                .map(ProductoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }
    
    /**
     * GET /api/productos/filtrar?club=&liga=&tipo=&talle=&precioMin=&precioMax=
     * Filtra el catálogo combinando los criterios informados
     */
    @GetMapping("/filtrar")
    public ResponseEntity<List<ProductoDTO>> filtrarProductos(
            @RequestParam(required = false) String club,
            @RequestParam(required = false) String liga,
            @RequestParam(required = false) TipoProducto tipo,
            @RequestParam(required = false) String talle,
            @RequestParam(required = false) BigDecimal precioMin,
            @RequestParam(required = false) BigDecimal precioMax) {
        ProductoFiltroDTO filtro = ProductoFiltroDTO.builder()
                .club(club).liga(liga).tipo(tipo).talle(talle)
                .precioMin(precioMin).precioMax(precioMax).build();

        List<ProductoDTO> productos = productoService.filtrarProductos(filtro)
                .stream()
                .map(ProductoDTO::new)
                .collect(Collectors.toList());
        return ResponseEntity.ok(productos);
    }

    /**
     * GET /api/productos/health
     * Endpoint de salud para verificar que el servicio está funcionando
     */
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Servicio de productos funcionando correctamente");
    }
}
