/*
 * AdminFrame.java
 *
 * Created on April 14, 2002, 4:35 PM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.photoGallery.config.DatabaseChangeTracker;
import com.stampysoft.photoGallery.faces.FaceOperations;
import com.stampysoft.photoGallery.faces.PeopleService;
import com.stampysoft.util.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * @author josh
 */
@ConditionalOnProperty(name = "photoweb.admin.enabled", havingValue = "true", matchIfMissing = false)
@Lazy(false)
@Component
public class AdminFrame extends JFrame
{
    private final PhotoOperations photoOperations;
    private final FaceOperations faceOperations;
    private final PeopleService peopleService;

    @Autowired
    public AdminFrame(PhotoOperations photoOperations, FaceOperations faceOperations, PeopleService peopleService)
    {
        super("Photo Gallery Admin");
        this.photoOperations = photoOperations;
        this.faceOperations = faceOperations;
        this.peopleService = peopleService;
        ResolutionUtil.init();
        _instance = this;

        SwingUtilities.invokeLater(() -> {
            // Not EXIT_ON_CLOSE: quitting has to wait for S3 and offer to publish first. See QuitHandler.
            QuitHandler.install(this);
            setJMenuBar(createMenuBar());

            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(new PhotoAdminScreen(), BorderLayout.CENTER);

            _instance.setSize(1200, 100);
            _instance.setExtendedState(_instance.getExtendedState() | JFrame.MAXIMIZED_BOTH);
            _instance.setVisible(true);
        });

    }

    private JMenuBar createMenuBar()
    {
        JMenuBar menuBar = new JMenuBar();

        JMenu toolsMenu = new JMenu("Tools");
        toolsMenu.setMnemonic(KeyEvent.VK_T);

        JMenuItem publishMenuItem = new JMenuItem("Publish Database to Production...");
        publishMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_P,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | InputEvent.SHIFT_DOWN_MASK));
        publishMenuItem.addActionListener(e -> publishDatabase());
        toolsMenu.add(publishMenuItem);

        // Checked as the menu opens rather than once at startup, so that fixing a missing key file shows up without
        // having to restart, and so the reason it can't run is on the item itself.
        toolsMenu.addMenuListener(new MenuListener()
        {
            public void menuSelected(MenuEvent e)
            {
                String problem = DatabasePublisher.getConfigurationProblem();
                publishMenuItem.setEnabled(problem == null);
                publishMenuItem.setToolTipText(problem == null
                        ? "Copy this database over the one on " + DatabasePublisher.describeTarget()
                        : problem);
            }

            public void menuDeselected(MenuEvent e) { }

            public void menuCanceled(MenuEvent e) { }
        });

        menuBar.add(toolsMenu);
        return menuBar;
    }

    private void publishDatabase()
    {
        String problem = DatabasePublisher.getConfigurationProblem();
        if (problem != null)
        {
            JOptionPane.showMessageDialog(this, problem + "\n\nSee PUBLISHING.md.",
                    "Cannot Publish", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!DatabaseChangeTracker.hasUnpublishedChanges()
                && JOptionPane.showConfirmDialog(this,
                        "Nothing has been written to the database since it was last published.\n\nPublish anyway?",
                        "Nothing to Publish", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
        {
            return;
        }

        if (PublishDialog.publish(this))
        {
            JOptionPane.showMessageDialog(this, "Published to " + DatabasePublisher.describeTarget() + ".",
                    "Publish Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static AdminFrame _instance;

    public static AdminFrame getFrame()
    {
        return _instance;
    }

    public PhotoOperations getPhotoOperations() {
        return photoOperations;
    }

    public FaceOperations getFaceOperations() {
        return faceOperations;
    }

    public PeopleService getPeopleService() {
        return peopleService;
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

        // Only initialize the AdminFrame when explicitly enabled via property
        boolean adminEnabled = Boolean.parseBoolean(
                context.getEnvironment().getProperty("photoweb.admin.enabled", "false")
        );

        if (adminEnabled) {
            SwingUtilities.invokeLater(() -> {
                // Creating the bean triggers AdminFrame constructor which sets up and shows the UI
                context.getBean(AdminFrame.class);
            });
        } else {
            System.out.println("Admin UI not enabled (set photoweb.admin.enabled=true to show AdminFrame)");
        }
    }

}
