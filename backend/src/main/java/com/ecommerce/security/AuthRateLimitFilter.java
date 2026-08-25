package com.ecommerce.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final Set<String> RUTAS_LIMITADAS = Set.of("/api/auth/login", "/api/auth/register");

    /**
     * Cuánto tiempo sin usarse tiene que estar una entrada para poder descartarla.
     *
     * Es holgadamente mayor que el intervalo de recarga del bucket (1 minuto), y eso es
     * lo que vuelve segura la evicción: pasados 10 minutos sin requests el bucket ya se
     * recargó del todo, así que descartarlo es indistinguible de conservarlo. Con una
     * ventana más corta que la recarga, en cambio, un atacante recuperaría sus intentos
     * simplemente esperando a que lo desalojen.
     */
    private static final Duration INACTIVIDAD_PARA_EVICCION = Duration.ofMinutes(10);

    /**
     * Un bucket por IP. Antes era un ConcurrentHashMap sin evicción: crecía una entrada
     * por cada IP que tocara /api/auth/* y no liberaba ninguna nunca, así que un barrido
     * desde muchas IPs lo hacía crecer sin techo hasta agotar el heap.
     */
    private final Map<String, Entrada> buckets = new ConcurrentHashMap<>();

    private record Entrada(Bucket bucket, long ultimoUso) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        if (RUTAS_LIMITADAS.contains(request.getRequestURI())) {
            long ahora = System.currentTimeMillis();
            Entrada entrada = buckets.compute(request.getRemoteAddr(), (key, actual) ->
                    new Entrada(actual == null ? nuevoBucket() : actual.bucket(), ahora));

            if (!entrada.bucket().tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Demasiados intentos, esperá unos minutos\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** Descarta los buckets que no se usaron en la última ventana de inactividad. */
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    void evictarBucketsInactivos() {
        evictarBucketsUsadosAntesDe(System.currentTimeMillis() - INACTIVIDAD_PARA_EVICCION.toMillis());
    }

    /**
     * Descarta todo bucket cuyo último uso sea anterior al corte.
     * Separado de evictarBucketsInactivos() para que los tests puedan fijar el corte en
     * vez de tener que esperar los 10 minutos reales de la ventana.
     */
    void evictarBucketsUsadosAntesDe(long corte) {
        int antes = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().ultimoUso() < corte);
        int descartados = antes - buckets.size();
        if (descartados > 0) {
            log.debug("Rate limiter: {} buckets inactivos descartados, quedan {}",
                    descartados, buckets.size());
        }
    }

    /** Cantidad de buckets vivos. Existe para que los tests puedan verificar la evicción. */
    int cantidadDeBuckets() {
        return buckets.size();
    }

    private Bucket nuevoBucket() {
        Bandwidth limite = Bandwidth.builder()
                .capacity(5)
                .refillIntervally(5, Duration.ofMinutes(1))
                .build();
        return Bucket.builder().addLimit(limite).build();
    }
}
