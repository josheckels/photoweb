/*
 * AdminFrame.java
 *
 * Created on April 14, 2002, 4:35 PM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.util.Configuration;

import javax.swing.*;
import java.awt.*;
import java.util.Iterator;

/**
 * @author josh
 */
public class AdminFrame extends JFrame
{

    public AdminFrame()
    {
        super("Photo Gallery Admin");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(new PhotoAdminScreen(), BorderLayout.CENTER);
    }

    public static void main(String... args)
    {
        if (args.length == 1)
        {
            Configuration.setConfigFileName(args[0]);
        }

        for (Object key : UIManager.getLookAndFeelDefaults().keySet()) {
            if(key != null && key.toString().endsWith(".font")) {
                Font font = UIManager.getFont(key);
                Font biggerFont = font.deriveFont(2.0f*font.getSize2D());
                // change ui default to bigger font
                UIManager.put(key,biggerFont);
            }
        }

        ResolutionUtil.init();
        SwingUtilities.invokeLater(new Runnable()
        {
            public void run()
            {
                PhotoOperations.getPhotoOperations().beginTransaction();
                g_frame = new AdminFrame();

                g_frame.setSize(800, 700);
                g_frame.setVisible(true);
                AdminFrame.getFrame().setExtendedState(Frame.MAXIMIZED_BOTH);
            }

        });
    }

    private static AdminFrame g_frame;

    public static AdminFrame getFrame()
    {
        return g_frame;
    }

}
