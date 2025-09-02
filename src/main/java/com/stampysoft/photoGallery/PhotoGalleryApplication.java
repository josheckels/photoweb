package com.stampysoft.photoGallery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableJpaRepositories
@EnableTransactionManagement
@SpringBootApplication
public class PhotoGalleryApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhotoGalleryApplication.class, args);
    }
}
