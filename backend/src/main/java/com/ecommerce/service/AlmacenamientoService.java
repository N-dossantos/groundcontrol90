package com.ecommerce.service;

import com.ecommerce.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Guarda las imágenes de producto que suben los vendedores.
 *
 * Antes el frontend hacía URL.createObjectURL(file) y persistía esa URL blob:, que sólo
 * es válida en la pestaña que la creó: la imagen se veía al cargarla y desaparecía al
 * recargar o al entrar desde otro dispositivo.
 *
 * Reglas de seguridad, todas deliberadas:
 * - El nombre que manda el cliente NO se usa nunca. Se genera un UUID: así no hay
 *   path traversal (../../etc/passwd), ni colisiones, ni forma de pisar un archivo ajeno.
 * - La extensión sale del tipo detectado por los magic bytes, no del Content-Type ni del
 *   nombre, que los controla el atacante.
 * - Sólo se aceptan cuatro formatos de imagen. Un .svg, por ejemplo, queda afuera a
 *   propósito: es XML y puede llevar <script> adentro, y se sirve desde nuestro origen.
 * - El tamaño lo limita además spring.servlet.multipart.max-file-size, que corta antes
 *   de que el archivo entre entero en memoria.
 */
@Service
public class AlmacenamientoService {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoService.class);

    /** Ruta pública bajo la que se sirven los archivos guardados. */
    public static final String URL_PUBLICA = "/uploads/";

    private static final long TAMANIO_MAXIMO_BYTES = 5L * 1024 * 1024;

    /**
     * Firmas de archivo (magic bytes) de los formatos aceptados, mapeadas a su extensión.
     * Se valida el contenido real y no el Content-Type porque ese header lo pone el
     * cliente: renombrar un .html a .jpg y declararlo image/jpeg es trivial.
     */
    private static final Map<String, String> FIRMAS = Map.of(
            "FFD8FF", ".jpg",           // JPEG
            "89504E470D0A1A0A", ".png", // PNG
            "474946383961", ".gif",     // GIF89a
            "474946383761", ".gif"      // GIF87a
    );

    private final Path directorio;

    public AlmacenamientoService(@Value("${app.uploads.dir}") String uploadsDir) {
        this.directorio = Paths.get(uploadsDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void prepararDirectorio() {
        try {
            Files.createDirectories(directorio);
            log.info("Directorio de uploads: {}", directorio);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo crear el directorio de uploads: " + directorio, e);
        }
    }

    /**
     * Valida y guarda el archivo. Devuelve la URL pública relativa (ej. /uploads/ab12.jpg),
     * que es lo que se persiste como imagen del producto.
     */
    public String guardarImagen(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ValidationException("No se recibió ningún archivo");
        }
        if (archivo.getSize() > TAMANIO_MAXIMO_BYTES) {
            throw new ValidationException("La imagen supera el máximo de 5 MB");
        }

        byte[] cabecera;
        try (InputStream in = archivo.getInputStream()) {
            cabecera = in.readNBytes(12);
        } catch (IOException e) {
            throw new ValidationException("No se pudo leer el archivo");
        }

        String extension = extensionSegunContenido(cabecera);
        if (extension == null) {
            throw new ValidationException(
                    "Formato no soportado. Se aceptan JPG, PNG, GIF y WEBP");
        }

        // El nombre lo generamos nosotros: el del cliente no se usa en ningún momento.
        String nombre = UUID.randomUUID().toString().replace("-", "") + extension;
        Path destino = directorio.resolve(nombre).normalize();

        // Cinturón y tiradores: aunque el nombre es un UUID, se confirma que el destino
        // no se escapó del directorio. Si un cambio futuro reintroduce el nombre del
        // cliente, esto lo frena en vez de dejar escribir en cualquier lado del disco.
        if (!destino.startsWith(directorio)) {
            throw new ValidationException("Nombre de archivo inválido");
        }

        try (InputStream in = archivo.getInputStream()) {
            Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("Error guardando la imagen en {}", destino, e);
            throw new IllegalStateException("No se pudo guardar la imagen", e);
        }

        return URL_PUBLICA + nombre;
    }

    /** Directorio donde se guardan los archivos; lo usa el handler que los sirve. */
    public Path getDirectorio() {
        return directorio;
    }

    /**
     * Devuelve la extensión según los magic bytes, o null si no es un formato aceptado.
     *
     * WEBP se chequea aparte porque su firma no es un prefijo contiguo: son los bytes
     * "RIFF", cuatro bytes de tamaño, y recién ahí "WEBP". Mirar sólo el "RIFF" del
     * principio aceptaría también .wav y .avi, que comparten ese contenedor.
     */
    private String extensionSegunContenido(byte[] cabecera) {
        String hex = aHex(cabecera);

        for (Map.Entry<String, String> firma : FIRMAS.entrySet()) {
            if (hex.startsWith(firma.getKey())) {
                return firma.getValue();
            }
        }
        // "RIFF" en 0..3 y "WEBP" en 8..11 (24 caracteres hex = 12 bytes)
        if (hex.length() >= 24 && hex.startsWith("52494646") && hex.startsWith("57454250", 16)) {
            return ".webp";
        }
        return null;
    }

    private String aHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }
}
