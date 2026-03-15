package com.keyjolt.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to log HTTP request method, path and status.
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            String uri = request.getRequestURI();
            if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/")
                    || uri.endsWith(".ico") || uri.endsWith(".png") || uri.endsWith(".webmanifest")) {
                logger.debug("{} {} {} {}ms", request.getMethod(), uri, response.getStatus(), duration);
            } else {
                logger.info("{} {} {} {}ms", request.getMethod(), uri, response.getStatus(), duration);
            }
        }
    }
}
