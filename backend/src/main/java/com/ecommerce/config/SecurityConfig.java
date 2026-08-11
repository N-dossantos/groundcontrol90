package com.ecommerce.config;

import com.ecommerce.repository.UsuarioRepository;
import com.ecommerce.security.AuthRateLimitFilter;
import com.ecommerce.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CorsConfigurationSource corsConfigurationSource;

    @Autowired
    private AuthRateLimitFilter authRateLimitFilter;

    // =============================================
    // USER DETAILS SERVICE
    // =============================================
    @Bean
    public UserDetailsService userDetailsService() {
        return email -> usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }
    
    // =============================================
    // PASSWORD ENCODER (Encriptación)
    // =============================================
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
    
    // =============================================
    // AUTHENTICATION PROVIDER
    // =============================================
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService());
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
    
    // =============================================
    // AUTHENTICATION MANAGER
    // =============================================
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    // =============================================
    // SECURITY FILTER CHAIN (Reglas de seguridad)
    // =============================================
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .csrf(csrf -> csrf.disable())  // Desactivar CSRF para APIs REST
            .authorizeHttpRequests(auth -> auth
                // Endpoints públicos (sin autenticación)
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/productos/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/categorias/**").permitAll()

                // El webhook no lleva JWT: Mercado Pago no tiene el token de ningún
                // usuario. Su seguridad es la validación de firma HMAC en PagoController,
                // no Spring Security. /api/pagos/config solo expone la public key.
                .requestMatchers(HttpMethod.POST, "/api/pagos/webhook").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pagos/config").permitAll()
                
                // Endpoints para administradores
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                
                // Endpoints que requieren autenticación
                .requestMatchers(HttpMethod.POST, "/api/productos").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/productos/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/productos/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/categorias").authenticated()
                .requestMatchers(HttpMethod.PUT, "/api/categorias/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/categorias/**").authenticated()
                
                // Cualquier otra petición requiere autenticación
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            // El rate limiter va antes del filtro JWT (ambos anclados en el mismo punto:
            // el orden de inserción se respeta porque List.sort es estable), y después
            // del CorsFilter, para que la respuesta 429 llegue al navegador con headers CORS.
            .addFilterBefore(authRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
            .authenticationProvider(authenticationProvider());
        
        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter();
    }
}
