package com.ecommerce.controller;

import com.ecommerce.dto.DireccionDTO;
import com.ecommerce.entity.Direccion;
import com.ecommerce.entity.Usuario;
import com.ecommerce.exception.ForbiddenException;
import com.ecommerce.repository.DireccionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Address book del usuario autenticado. Todas las operaciones quedan acotadas
 * a las direcciones propias.
 */
@RestController
@RequestMapping("/api/direcciones")
public class DireccionController {

    @Autowired
    private DireccionRepository direccionRepository;

    @GetMapping
    public ResponseEntity<List<DireccionDTO>> listar(@AuthenticationPrincipal Usuario usuario) {
        List<DireccionDTO> direcciones = direccionRepository.findByUsuarioId(usuario.getId())
                .stream().map(DireccionDTO::new).collect(Collectors.toList());
        return ResponseEntity.ok(direcciones);
    }

    @PostMapping
    public ResponseEntity<DireccionDTO> crear(@RequestBody Direccion direccion,
                                              @AuthenticationPrincipal Usuario usuario) {
        direccion.setId(null);
        direccion.setUsuario(usuario);
        Direccion guardada = direccionRepository.save(direccion);
        return ResponseEntity.status(HttpStatus.CREATED).body(new DireccionDTO(guardada));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DireccionDTO> actualizar(@PathVariable Long id, @RequestBody Direccion direccion,
                                                   @AuthenticationPrincipal Usuario usuario) {
        Direccion existente = obtenerPropia(id, usuario);

        existente.setCalle(direccion.getCalle());
        existente.setNumero(direccion.getNumero());
        existente.setCiudad(direccion.getCiudad());
        existente.setProvincia(direccion.getProvincia());
        existente.setCodigoPostal(direccion.getCodigoPostal());
        existente.setPais(direccion.getPais());
        existente.setEsPredeterminada(direccion.isEsPredeterminada());

        return ResponseEntity.ok(new DireccionDTO(direccionRepository.save(existente)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuario) {
        obtenerPropia(id, usuario);
        direccionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Direccion obtenerPropia(Long id, Usuario usuario) {
        Direccion existente = direccionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Dirección no encontrada"));
        if (!existente.getUsuario().getId().equals(usuario.getId())) {
            throw new ForbiddenException("No tenés permiso para modificar esta dirección");
        }
        return existente;
    }
}
