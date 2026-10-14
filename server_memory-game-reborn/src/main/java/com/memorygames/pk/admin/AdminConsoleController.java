package com.memorygames.pk.admin;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, data-free login shell; business APIs still use AdminGuard. */
@RestController
public class AdminConsoleController {

    @GetMapping(value = "/api/admin/console", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> console() {
        Resource shell = new ClassPathResource("static/admin/index.html");
        if (!shell.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(shell);
    }

    @Bean
    public FilterRegistrationBean<Filter> adminConsoleSecurityHeaders() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter((request, response, chain) -> {
            HttpServletResponse http = (HttpServletResponse) response;
            http.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self'; "
                    + "style-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'");
            http.setHeader("X-Content-Type-Options", "nosniff");
            http.setHeader("X-Frame-Options", "DENY");
            http.setHeader("Referrer-Policy", "no-referrer");
            http.setHeader("Cache-Control", "no-store");
            chain.doFilter(request, response);
        });
        registration.addUrlPatterns("/api/admin/*", "/admin/*");
        return registration;
    }
}
