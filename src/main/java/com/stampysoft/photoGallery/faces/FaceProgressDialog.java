package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.admin.AdminFrame;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The modal progress dialog the long face passes run behind, in the same shape as the scan-for-new-photos dialog:
 * a label, a bar, and here a Cancel button, since a backfill over a whole library takes a while.
 */
public class FaceProgressDialog implements FaceMatcher.ProgressListener
{
    /** A unit of face work. Runs on a background thread and reports through the listener it's handed. */
    public interface Task
    {
        void run(FaceMatcher.ProgressListener listener) throws Exception;
    }

    private final JDialog _dialog;
    private final JProgressBar _progressBar = new JProgressBar();
    private final JLabel _taskLabel = new JLabel(" ");
    private volatile boolean _cancelled;
    private volatile Throwable _failure;

    private FaceProgressDialog(String title)
    {
        _dialog = new JDialog(AdminFrame.getFrame(), title, true);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        _progressBar.setPreferredSize(new Dimension(320, 20));
        _progressBar.setStringPainted(true);
        panel.add(_taskLabel, BorderLayout.NORTH);
        panel.add(_progressBar, BorderLayout.CENTER);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> {
            _cancelled = true;
            _taskLabel.setText("Finishing the current batch...");
        });
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(cancelButton);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        _dialog.setContentPane(panel);
        _dialog.pack();
        _dialog.setLocationRelativeTo(AdminFrame.getFrame());
    }

    /**
     * Runs the task behind a modal dialog and blocks until it finishes or is cancelled. Must be called on the EDT.
     *
     * @return whatever the task failed with, or null if it completed
     */
    public static Throwable run(String title, Task task)
    {
        FaceProgressDialog dialog = new FaceProgressDialog(title);

        Thread thread = new Thread(() -> {
            try
            {
                task.run(dialog);
            }
            catch (Throwable t)
            {
                // Throwable, not Exception: a missing or unloadable native library arrives as an Error, and
                // letting that reach the thread's uncaught handler would leave the caller reporting success.
                dialog._failure = t;
            }
            finally
            {
                SwingUtilities.invokeLater(() -> dialog._dialog.dispose());
            }
        }, "Face " + title);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();

        dialog._dialog.setVisible(true);
        return dialog._failure;
    }

    @Override
    public void progress(String task, int done, int total)
    {
        SwingUtilities.invokeLater(() -> {
            _taskLabel.setText(total > 0 ? task + "..." : task);
            _progressBar.setIndeterminate(total <= 0);
            if (total > 0)
            {
                _progressBar.setMaximum(total);
                _progressBar.setValue(Math.min(done, total));
                _progressBar.setString(done + " of " + total);
            }
        });
    }

    @Override
    public boolean isCancelled()
    {
        return _cancelled;
    }
}
