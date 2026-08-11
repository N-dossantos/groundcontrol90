package com.ecommerce.service;

import com.ecommerce.entity.DetallePedido;
import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Async
    public void enviarConfirmacionCuenta(Usuario usuario) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(usuario.getEmail());
        mensaje.setSubject("¡Bienvenido a la tienda!");
        mensaje.setText("Hola " + usuario.getNombre() + ",\n\nTu cuenta fue creada exitosamente.");
        enviarSeguro(mensaje);
    }

    @Async
    public void enviarConfirmacionPedido(Pedido pedido) {
        String detalle = pedido.getItems().stream()
                .map(item -> "- " + item.getProductoNombre() + " (talle " + item.getTalle() + ") x" + item.getCantidad())
                .collect(Collectors.joining("\n"));

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(pedido.getUsuario().getEmail());
        mensaje.setSubject("Pedido #" + pedido.getId() + " confirmado");
        mensaje.setText("Hola " + pedido.getUsuario().getNombre() + ",\n\n"
                + "Tu pedido #" + pedido.getId() + " fue confirmado:\n\n" + detalle
                + "\n\nTotal: $" + pedido.getTotal());
        enviarSeguro(mensaje);
    }

    @Async
    public void enviarCambioEstadoPedido(Pedido pedido, EstadoPedido nuevoEstado) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(pedido.getUsuario().getEmail());
        mensaje.setSubject("Pedido #" + pedido.getId() + " actualizado");
        mensaje.setText("Hola " + pedido.getUsuario().getNombre() + ",\n\n"
                + "Tu pedido #" + pedido.getId() + " ahora está en estado: " + nuevoEstado);
        enviarSeguro(mensaje);
    }

    private void enviarSeguro(SimpleMailMessage mensaje) {
        try {
            mailSender.send(mensaje);
        } catch (Exception e) {
            // Un email que no sale no debe tumbar el pedido/pago que ya se confirmó en la DB.
            log.error("Error enviando email a {}", (Object[]) mensaje.getTo(), e);
        }
    }
}
