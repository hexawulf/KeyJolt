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
                .csrf(csrf -> csrf.disable())
                // Configure authorization rules for HTTP requests.
                .authorizeHttpRequests(authorize -> authorize
                        // Allow public access (permitAll) to these specific paths.
                        .requestMatchers(
                                "/",
                                "/index", // Assuming /index also serves content like /
                                "/css/**",
                                "/js/**",
                                "/images/**", // For general image assets
                                "/favicon.ico",
                                "/favicon-16x16.png",
                                "/favicon-32x32.png",
                                "/apple-touch-icon.png",
                                "/android-chrome-*.png", // Wildcard for different sizes
                                "/site.webmanifest",
                                "/robots.txt"
                        ).permitAll()
                        // Require authentication for all other requests.
                        .anyRequest().authenticated()
                )
                // Disable form-based login.
                .formLogin(formLogin -> formLogin.disable())
                // Disable HTTP Basic authentication.
                .httpBasic(httpBasic -> httpBasic.disable())
                // Configure session management to be stateless.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build(); // Builds the SecurityFilterChain
    }

    // Bean for an in-memory UserDetailsService.
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails adminUser = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123")) // Password must be encoded
                .roles("ADMIN") // Roles are automatically prefixed with "ROLE_" by Spring Security
                .build();
        return new InMemoryUserDetailsManager(adminUser);
    }
}
