package com.stampysoft.photoGallery.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.swing.*;

@SpringBootApplication(scanBasePackages = "com.stampysoft.photoGallery")
@EnableJpaRepositories
public class PhotoGalleryApp {
    public static void main(String[] args) {
        // Start the Spring Application Context
        ConfigurableApplicationContext context = SpringApplication.run(PhotoGalleryApp.class, args);

        // Get AdminFrame Bean from the Spring Context
        SwingUtilities.invokeLater(() -> {
            AdminFrame adminFrame = context.getBean(AdminFrame.class);
        });

    }
}