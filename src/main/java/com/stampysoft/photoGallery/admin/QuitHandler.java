package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.config.DatabaseChangeTracker;
import com.stampysoft.photoGallery.storage.S3Uploader;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * What happens between closing the admin window and the JVM going away.
 * <p>
 * Two things have to be settled first, in this order. S3 uploads run on a daemon thread, so anything still queued
 * when the JVM exits is thrown away without a word - waiting for it is the difference between a photo being on the
 * live site and being a broken image there. And the database is only on this machine until it's published, so
 * quitting is the last chance to offer.
 * <p>
 * Every step can be declined and any of them can put the window back, which is why this returns to the event thread
 * instead of exiting from inside a dialog.
 */
public class QuitHandler
{
    /** How often the drain dialog re-reads the queue. Short enough that the count looks live. */
    private static final int DRAIN_POLL_MILLIS = 250;

    /** Set once the user has been told publishing isn't configured, so it's said once a session and not on every quit. */
    private static boolean _warnedAboutConfiguration;

    private QuitHandler()
    {
    }

    /** Wires the frame up so that closing it comes through here rather than exiting immediately. */
    public static void install(JFrame frame)
    {
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                confirmAndQuit(frame);
            }
        });
        installQuitHandler(frame);
    }

    /**
     * Catches Cmd-Q as well as the window's close button.
     * <p>
     * Without this the whole thing is easy to walk straight past: on macOS the application menu quits the JVM
     * without ever sending windowClosing, and Cmd-Q is how this app usually gets closed.
     */
    private static void installQuitHandler(JFrame frame)
    {
        try
        {
            if (!Desktop.isDesktopSupported())
            {
                return;
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER))
            {
                return;
            }
            desktop.setQuitHandler((event, response) -> {
                // Always refuse the OS request and run our own sequence instead: the answer has to be given
                // straight away, and the prompts below can take as long as a publish takes.
                response.cancelQuit();
                SwingUtilities.invokeLater(() -> confirmAndQuit(frame));
            });
        }
        catch (Throwable t)
        {
            // Not macOS, or the desktop integration isn't available - the window listener still covers closing
            System.out.println("[QuitHandler] Could not install the application quit handler: " + t);
        }
    }

    public static void confirmAndQuit(JFrame frame)
    {
        if (!awaitUploads(frame))
        {
            return;
        }
        if (!offerPublish(frame))
        {
            return;
        }
        System.exit(0);
    }

    /**
     * @return true to carry on quitting - either the queue drained or the user chose to abandon it
     */
    private static boolean awaitUploads(JFrame frame)
    {
        S3Uploader uploader = S3Uploader.getInstance();
        if (uploader.getPendingCount() == 0)
        {
            return true;
        }
        return new DrainDialog(frame, uploader).run();
    }

    /**
     * @return true to carry on quitting
     */
    private static boolean offerPublish(JFrame frame)
    {
        if (!DatabaseChangeTracker.hasUnpublishedChanges())
        {
            return true;
        }

        String problem = DatabasePublisher.getConfigurationProblem();
        if (problem != null)
        {
            if (!_warnedAboutConfiguration)
            {
                _warnedAboutConfiguration = true;
                JOptionPane.showMessageDialog(frame,
                        "The database has changes that haven't been published, but publishing isn't set up:\n\n"
                                + problem + "\n\nSee PUBLISHING.md. Quitting anyway.",
                        "Cannot Publish", JOptionPane.WARNING_MESSAGE);
            }
            return true;
        }

        int answer = JOptionPane.showConfirmDialog(frame,
                "The database has changes that " + DatabasePublisher.describeTarget() + " doesn't have yet.\n\n"
                        + "Publish them before quitting?",
                "Publish Before Quitting?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (answer == JOptionPane.NO_OPTION)
        {
            return true;
        }
        if (answer != JOptionPane.YES_OPTION)
        {
            // Cancel, or the dialog was dismissed - stay in the app
            return false;
        }

        if (PublishDialog.publish(frame))
        {
            return true;
        }
        // The publish dialog has already shown what went wrong, so this only has to decide what to do about it
        return JOptionPane.showConfirmDialog(frame,
                "The database was not published. Quit anyway?",
                "Publish Failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Holds the quit while the S3 queue drains, showing how much is left.
     * <p>
     * "Quit Anyway" is offered because a bucket that's unreachable would otherwise make the app impossible to close
     * without killing it; it discards whatever is still queued, which the button says.
     */
    private static class DrainDialog extends JDialog
    {
        private final S3Uploader _uploader;
        private final JLabel _label = new JLabel();
        private boolean _quit;

        DrainDialog(JFrame owner, S3Uploader uploader)
        {
            super(owner, "Finishing S3 Uploads", ModalityType.APPLICATION_MODAL);
            _uploader = uploader;

            JPanel contentPane = new JPanel(new BorderLayout(0, 8));
            contentPane.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            contentPane.add(_label, BorderLayout.NORTH);

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);
            contentPane.add(progressBar, BorderLayout.CENTER);

            JButton quitAnywayButton = new JButton("Quit Anyway");
            JButton cancelButton = new JButton("Don't Quit");
            quitAnywayButton.setToolTipText("Discards the uploads that haven't finished. Their photos will be missing from the live site.");
            quitAnywayButton.addActionListener(e -> {
                _quit = true;
                dispose();
            });
            cancelButton.addActionListener(e -> dispose());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttonPanel.add(quitAnywayButton);
            buttonPanel.add(cancelButton);
            contentPane.add(buttonPanel, BorderLayout.SOUTH);

            setContentPane(contentPane);
            // Closing the window is "don't quit": the safe reading, since it leaves the uploads running
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            getRootPane().setDefaultButton(cancelButton);

            refreshLabel();
            setSize(420, getPreferredSize().height);
            setLocationRelativeTo(owner);
        }

        /**
         * @return true if quitting should continue
         */
        boolean run()
        {
            Timer timer = new Timer(DRAIN_POLL_MILLIS, e -> {
                if (_uploader.getPendingCount() == 0)
                {
                    _quit = true;
                    dispose();
                }
                else
                {
                    refreshLabel();
                }
            });
            timer.start();
            setVisible(true);
            timer.stop();
            return _quit;
        }

        private void refreshLabel()
        {
            int pending = _uploader.getPendingCount();
            _label.setText("Waiting for " + pending + " S3 transfer" + (pending == 1 ? "" : "s") + " to finish...");
        }
    }
}
