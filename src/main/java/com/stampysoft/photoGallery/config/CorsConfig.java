package com.stampysoft.photoGallery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://192.168.68.109:3000")
                .allowCredentials(true)
                .allowedHeaders("Content-Type")
                .allowedMethods("GET", "POST", "PUT", "DELETE");

        // An <img> tag needs no CORS, but the photo page downloads an original with fetch(), and that does.
        // Only matters for the split-port development setup; in production the SPA and the images share an
        // origin. Note that a cross-origin <img> also sends no pw_unlock cookie, so a restricted photo will
        // 404 when the SPA is served from :3000 - browse :8080 directly to see those.
        registry.addMapping("/img/**")
                .allowedOrigins("http://localhost:3000", "http://192.168.68.109:3000")
                .allowCredentials(true)
                .allowedMethods("GET");
    }
}
