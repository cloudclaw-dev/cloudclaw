package run.cloudclaw.auth.security;

import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.ArrayList;
import java.util.List;

/**
 * Spring Security configuration for CloudClaw.
 *
 * <p>Configures a stateless, JWT-based security filter chain with the following rules:</p>
 * <ul>
 *   <li>POST /api/v1/auth/** - public (login, refresh)</li>
 *   <li>POST /api/admin/** - requires ADMIN role</li>
 *   <li>GET/PUT/DELETE /api/admin/** - requires ADMIN role</li>
 *   <li>/api/v1/** - requires authentication</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Inherit SecurityContext in async dispatch threads
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);

        http
                // Enable CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // Disable CSRF since this is a stateless REST API using JWT
                .csrf(AbstractHttpConfigurer::disable)
                // Set session management to stateless
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                // Configure authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Allow ASYNC and ERROR dispatches (needed for SSE streaming)
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // Static resources and frontend pages: public access
                        .requestMatchers("/", "/index.html").permitAll()
                        .requestMatchers("/chat/**", "/admin/**").permitAll()
                        .requestMatchers("/static/**", "/assets/**", "/favicon.ico").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // Auth endpoints: public access
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/auth/**").permitAll()
                        // Admin POST endpoints: require ADMIN role
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/admin/**").hasRole("ADMIN")
                        // Admin GET/PUT/DELETE endpoints: require ADMIN role
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(org.springframework.http.HttpMethod.DELETE, "/api/admin/**").hasRole("ADMIN")
                        // All other /api/v1/** endpoints: require authentication
                        .requestMatchers("/api/v1/**").authenticated()
                        // Any other requests
                        .anyRequest().permitAll()
                )
                // Add JWT filter before the standard username/password filter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // Load allowed origins from application config: cloudclaw.cors.allowed-origins[0], etc.
        List<String> allowedOrigins = new ArrayList<>();
        // Try list-style properties first
        int i = 0;
        while (true) {
            String origin = environment.getProperty("cloudclaw.cors.allowed-origins[" + i + "]");
            if (origin == null) break;
            allowedOrigins.add(origin);
            i++;
        }
        // Fallback to defaults if none configured
        if (allowedOrigins.isEmpty()) {
            allowedOrigins = List.of(
                "https://cloudclaw.run",
                "https://*.cloudclaw.run",
                "http://localhost:*",
                "http://192.168.1.*:*"
            );
        }
        for (String origin : allowedOrigins) {
            config.addAllowedOriginPattern(origin);
        }
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
