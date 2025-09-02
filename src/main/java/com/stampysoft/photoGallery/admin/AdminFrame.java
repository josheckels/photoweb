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
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;

/**
 * @author josh
 */
@Component
public class AdminFrame extends JFrame
{
    private PhotoOperations photoOperations;

    @Autowired
    public AdminFrame(PhotoOperations photoOperations)
    {
        super("Photo Gallery Admin");
        this.photoOperations = photoOperations;
        ResolutionUtil.init();
        _instance = this;

        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                getContentPane().setLayout(new BorderLayout());
                getContentPane().add(new PhotoAdminScreen(), BorderLayout.CENTER);

                _instance.setSize(1200, 100);
                _instance.setExtendedState(_instance.getExtendedState() | JFrame.MAXIMIZED_BOTH);
                _instance.setVisible(true);
            }

        });

    }

    private static AdminFrame _instance;

    public static AdminFrame getFrame()
    {
        return _instance;
    }

    public PhotoOperations getPhotoOperations() {
        if (photoOperations == null)
        {
            photoOperations = new PhotoOperations();
        }
        return photoOperations;
    }

    public static void main(String... args)
    {
        if (args.length == 1)
        {
            Configuration.setConfigFileName(args[0]);
        }

//        for (Object key : UIManager.getLookAndFeelDefaults().keySet()) {
//            if(key != null && key.toString().endsWith(".font")) {
//                Font font = UIManager.getFont(key);
//                Font biggerFont = font.deriveFont(2.0f*font.getSize2D());
//                // change ui default to bigger font
//                UIManager.put(key,biggerFont);
//            }
//        }
//
    }

}
