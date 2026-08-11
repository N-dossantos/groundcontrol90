package com.ecommerce.service;

import com.ecommerce.entity.EstadoPedido;
import com.ecommerce.entity.Pedido;
import com.ecommerce.entity.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class EmailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    /**
     * Datos ya resueltos de un pedido, listos para armar el mail.
     *
     * Los envíos son @Async: cuando corren, la sesión de Hibernate del request que los
     * disparó puede estar cerrada, y ahí cualquier relación LAZY del Pedido (usuario,
     * items) explota con LazyInitializationException adentro del hilo async — el mail
     * nunca sale y el que lo pidió ni se entera, porque el método es void y la excepción
     * queda solo en el log. Por eso los envíos reciben este snapshot en vez de la entidad:
     * armalo con {@link #de(Pedido)} desde el método @Transactional que dispara el mail,
     * que es donde la sesión está viva.
     */
    public record ResumenPedido(Long pedidoId, String email, String nombre, BigDecimal total, List<String> lineas) {

        /** Ojo: hay que llamarlo con la sesión abierta, lee las relaciones LAZY del pedido. */
        public static ResumenPedido de(Pedido pedido) {
            return new ResumenPedido(
                    pedido.getId(),
                    pedido.getUsuario().getEmail(),
                    pedido.getUsuario().getNombre(),
                    pedido.getTotal(),
                    pedido.getItems().stream()
                            .map(item -> "- " + item.getProductoNombre()
                                    + " (talle " + item.getTalle() + ") x" + item.getCantidad())
                            .toList());
        }
    }

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
    public void enviarConfirmacionPedido(ResumenPedido pedido) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(pedido.email());
        mensaje.setSubject("Pedido #" + pedido.pedidoId() + " confirmado");
        mensaje.setText("Hola " + pedido.nombre() + ",\n\n"
                + "Tu pedido #" + pedido.pedidoId() + " fue confirmado:\n\n"
                + String.join("\n", pedido.lineas())
                + "\n\nTotal: $" + pedido.total());
        enviarSeguro(mensaje);
    }

    @Async
    public void enviarCambioEstadoPedido(ResumenPedido pedido, EstadoPedido nuevoEstado) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(from);
        mensaje.setTo(pedido.email());
        mensaje.setSubject("Pedido #" + pedido.pedidoId() + " actualizado");
        mensaje.setText("Hola " + pedido.nombre() + ",\n\n"
                + "Tu pedido #" + pedido.pedidoId() + " ahora está en estado: " + nuevoEstado);
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
