package com.groww.smart_watchlist.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// The frontend (Vite dev server, http://localhost:5173) runs on a different
// origin than this API (http://localhost:8080), so the browser blocks calls
// without explicit CORS headers. This is the only backend change made for
// the frontend work — no endpoints, DTOs, or business logic touched.
// 5174 is included too since Vite falls back to it when 5173 is taken.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                                    "http://localhost:5173",
                                    "http://localhost:5174",
                                    "https://smart-watchlist-groww.vercel.app"
                                )
                .allowedMethods("GET", "POST", "DELETE", "PUT", "OPTIONS")
                .allowedHeaders("*");
    }
}
