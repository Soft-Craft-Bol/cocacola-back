package com.cocacola.config.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Permite a Power BI leer /bi/** con una clave de API (rol BI, solo lectura). Desactivado si bi.api-key está vacío. */
@Component
public class BiApiKeyFilter extends OncePerRequestFilter {

    private final byte[] expected;

    public BiApiKeyFilter(@Value("${bi.api-key:}") String apiKey) {
        this.expected = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (expected.length > 0 && (path.equals("/bi") || path.startsWith("/bi/"))) {
            String provided = request.getHeader("X-API-Key");
            if (provided == null) provided = request.getParameter("key");
            if (provided != null && MessageDigest.isEqual(expected, provided.getBytes(StandardCharsets.UTF_8))) {
                var principal = new AuthUser("bi", "powerbi", "BI");
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_BI"))));
            }
        }
        chain.doFilter(request, response);
    }
}
