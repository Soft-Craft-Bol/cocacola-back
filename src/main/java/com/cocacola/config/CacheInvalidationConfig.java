package com.cocacola.config;

import com.cocacola.utils.ReadCache;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Vacía la caché de lecturas después de cualquier cambio hecho por la API (no GET). */
@Configuration
@RequiredArgsConstructor
public class CacheInvalidationConfig implements WebMvcConfigurer {

    private final ReadCache cache;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
                String method = request.getMethod();
                if (!"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method)) cache.invalidate();
            }
        });
    }
}
