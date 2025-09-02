package com.stampysoft.photoGallery.servlet;

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
    }
}
