@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Add security headers
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp
                .policyDirectives("default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self'"))
            .referrerPolicy(referrer -> referrer
                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            .xssProtection(xss -> xss
                .block(true))
            .frameOptions(frame -> frame
                .deny())
        )

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
        );

    return http.build();
}
