package com.keyjolt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

@Configuration // Indicates that this class contains Spring configuration
@EnableWebSecurity // Enables Spring Security's web security support
public class SecurityConfig {

    // Bean for password encoding.
    // BCryptPasswordEncoder is a strong hashing algorithm for passwords.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Bean for configuring the security filter chain.
    // This is the core of Spring Security configuration in modern Spring Boot.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF (Cross-Site Request Forgery) protection.
                // This is often disabled for stateless APIs or when using other token-based protection.
                // For educational purposes, it's disabled here; in production, ensure proper CSRF handling if not using stateless tokens.
                .csrf(csrf -> csrf.disable())
                // Configure authorization rules for HTTP requests.
                .authorizeHttpRequests(authorize -> authorize
                        // Allow public access (permitAll) to these specific paths.
                        // Useful for static resources, home page, etc.
                        .requestMatchers("/", "/index", "/css/**", "/js/**", "/favicon.ico", "/robots.txt").permitAll()
                        // Require authentication for all other requests.
                        .anyRequest().authenticated()
                )
                // Disable form-based login.
                // The default Spring Security login page will not be generated.
                // This is done because we might implement a custom authentication mechanism later (e.g., token-based).
                .formLogin(formLogin -> formLogin.disable())
                // Configure session management to be stateless.
                // This means the application will not create or use HTTP sessions to store security context.
                // Each request must be authenticated independently, typically using tokens.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build(); // Builds the SecurityFilterChain
    }

    // Bean for an in-memory UserDetailsService.
    // This is a simple way to provide user credentials for testing or small applications.
    // For production, you would typically use a database-backed UserDetailsService.
    @Bean
    public UserDetailsService userDetailsService() {
        // Create an admin user with username "admin", a securely encoded password "admin123", and role "ADMIN".
        // User.builder() provides a fluent API for creating UserDetails objects.
        UserDetails adminUser = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123")) // Password must be encoded
                .roles("ADMIN") // Roles are automatically prefixed with "ROLE_" by Spring Security
                .build();

        // Return an InMemoryUserDetailsManager initialized with the created user(s).
        return new InMemoryUserDetailsManager(adminUser);
    }
}
