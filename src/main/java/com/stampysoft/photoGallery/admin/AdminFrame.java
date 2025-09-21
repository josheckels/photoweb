/*
 * AdminFrame.java
 *
 * Created on April 14, 2002, 4:35 PM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;

/**
 * @author josh
 */
@Component
public class AdminFrame extends JFrame
{
    private final PhotoOperations photoOperations;

    @Autowired
    public AdminFrame(PhotoOperations photoOperations)
    {
        super("Photo Gallery Admin");
        this.photoOperations = photoOperations;
        ResolutionUtil.init();
        _instance = this;

        SwingUtilities.invokeLater(() -> {
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(new PhotoAdminScreen(), BorderLayout.CENTER);

            _instance.setSize(1200, 100);
            _instance.setExtendedState(_instance.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            _instance.setVisible(true);
        });

    }

    private static AdminFrame _instance;

    public static AdminFrame getFrame()
    {
        return _instance;
    }

    public PhotoOperations getPhotoOperations() {
        return photoOperations;
    }

    public static void main(String... args)
    {
        if (args.length == 1)
        {
            Configuration.setConfigFileName(args[0]);
        }

        System.setProperty("java.awt.headless", "false");
        ConfigurableApplicationContext context = new SpringApplicationBuilder(PhotoGalleryApp.class)
                .headless(false)
                .run(args);

        SwingUtilities.invokeLater(() -> {
            // Creating the bean triggers AdminFrame constructor which sets up and shows the UI
            context.getBean(AdminFrame.class);
        });
    }

}
