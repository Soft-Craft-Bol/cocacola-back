package com.cocacola.config.security;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final BiApiKeyFilter biApiKeyFilter;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "Sesión no válida o expirada"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "No tienes permiso para esta acción")))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Publico: login y registro de asistentes desde el QR del evento
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/events/*", "/products", "/experiences").permitAll()
                        .requestMatchers(HttpMethod.POST, "/products", "/experiences").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/products/*", "/experiences/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/participants").permitAll()
                        .requestMatchers("/public/**").permitAll()
                        // Inteligencia y comunicaciones: datos de participantes, solo admin y marketing
                        .requestMatchers("/communications/**", "/crm/**", "/integrations/**", "/insights/**", "/chat/**").hasAnyRole("ADMIN", "MARKETING")
                        // Power BI: solo lectura (sesion admin/marketing o clave de API)
                        .requestMatchers("/bi", "/bi/**").hasAnyRole("ADMIN", "MARKETING", "BI")
                        // Administracion
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/events").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/events/*").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/events/*/image").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/events/*/image").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/events/*", "/participants/*", "/interactions/*").hasRole("ADMIN")
                        // Operacion del evento
                        .requestMatchers(HttpMethod.POST, "/activities", "/interactions", "/surveys",
                                "/participants/*/checkin", "/participants/*/checkout").hasAnyRole("ADMIN", "ORGANIZER")
                        .requestMatchers(HttpMethod.DELETE, "/activities/*").hasAnyRole("ADMIN", "ORGANIZER")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(biApiKeyFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse res, int status, String message) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
