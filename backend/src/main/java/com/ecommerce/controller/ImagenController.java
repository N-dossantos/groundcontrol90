package com.ecommerce.controller;

import com.ecommerce.service.AlmacenamientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Subida de imágenes de producto.
 *
 * Requiere autenticación (SecurityConfig): sólo un usuario logueado puede escribir en el
 * disco del servidor. No hace falta chequear propiedad de ningún producto porque la
 * imagen se sube antes de crearlo — el vínculo se establece después, al guardar el
 * producto, que sí verifica propiedad.
 */
@RestController
@RequestMapping("/api/imagenes")
public class ImagenController {

    @Autowired
    private AlmacenamientoService almacenamientoService;

    /**
     * POST /api/imagenes
     * Recibe un archivo multipart y devuelve la URL pública con la que quedó guardado.
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> subirImagen(@RequestParam("file") MultipartFile file) {
        String url = almacenamientoService.guardarImagen(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("url", url));
    }
}
