package com.ecommerce.config;

import com.ecommerce.service.AlmacenamientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sirve las imágenes subidas desde el directorio de uploads.
 *
 * Se sirven por el backend y no por nginx porque el directorio es un volumen del
 * contenedor del backend, que es quien escribe en él; el frontend es una imagen estática
 * distinta y no lo monta. Caddy rutea /uploads/* hacia acá.
 */
@Configuration
public class UploadsWebConfig implements WebMvcConfigurer {

    @Autowired
    private AlmacenamientoService almacenamientoService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // El "/" final no es opcional: sin él, Spring trata el valor como un prefijo de
        // nombre y "/uploads/x.jpg" resolvería a "<dir>x.jpg", fuera del directorio.
        String ubicacion = almacenamientoService.getDirectorio().toUri().toString();
        registry.addResourceHandler(AlmacenamientoService.URL_PUBLICA + "**")
                .addResourceLocations(ubicacion.endsWith("/") ? ubicacion : ubicacion + "/");
    }
}
