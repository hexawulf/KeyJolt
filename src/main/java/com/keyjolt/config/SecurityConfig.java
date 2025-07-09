package com.keyjolt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration // Indicates that this class contains Spring configuration
@EnableWebSecurity // Enables Spring Security's web security support
public class SecurityConfig {

    // Bean for password encoding.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Bean for configuring the security filter chain.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Enable CORS with custom configuration
            .cors(Customizer.withDefaults())

            // Disable CSRF (Cross-Site Request Forgery) protection.
            .csrf(csrf -> csrf.disable())

            // Configure authorization rules for HTTP requests.
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/", "/generate", "/api/generate", "/index",
                    "/css/**", "/js/**", "/images/**",
                    "/favicon.ico", "/favicon-16x16.png", "/favicon-32x32.png",
                    "/apple-touch-icon.png", "/android-chrome-*.png",
                    "/site.webmanifest", "/robots.txt",
                    "/download/**"
                ).permitAll()
                .requestMatchers(
                    "/wp-admin/**", "/wordpress/**", "/wp-login.php"
                ).denyAll()
                .anyRequest().authenticated()
            )

            // Disable form-based login and HTTP Basic authentication
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())

            // Configure session management to be stateless.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // Add hardened security headers
            .headers(headers -> headers
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000))
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline'; img-src 'self' data:; object-src 'none'"))
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .xssProtection(Customizer.withDefaults())
            );

        return http.build();
    }

    // Bean for an in-memory UserDetailsService.
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails adminUser = User.builder()
            .username("admin")
            .password(passwordEncoder().encode("admin123")) // Password must be encoded
            .roles("ADMIN") // Roles are automatically prefixed with "ROLE_"
            .build();

        return new InMemoryUserDetailsManager(adminUser);
    }

    // Bean for CORS configuration
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://keyjolt.dev", "https://keyjolt.piapps.dev"));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
