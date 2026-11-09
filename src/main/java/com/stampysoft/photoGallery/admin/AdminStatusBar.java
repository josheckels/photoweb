package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.config.DatabaseChangeTracker;
import com.stampysoft.photoGallery.storage.S3Uploader;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;

/**
 * The strip along the bottom of the admin window: what background work is running, and whether the database has
 * changes production hasn't seen.
 * <p>
 * Both of the things it reports used to be invisible. S3 uploads happen on a daemon thread that logs to stdout and
 * is dropped on exit, so the only way to know whether it was safe to quit was to watch the console; and whether
 * there was anything worth publishing was a matter of remembering what you'd done. Answering both in the corner of
 * the window is also what makes the prompt on quit predictable rather than a surprise.
 * <p>
 * The progress bar is only shown while something is running. The label is anchored west and the bar east, so the
 * bar appearing and disappearing doesn't shift the text around.
 */
public class AdminStatusBar extends JPanel
{
    /** How often the unpublished-changes text is refreshed. Nothing fires an event when a write happens. */
    private static final int REFRESH_INTERVAL_MILLIS = 2000;

    private static AdminStatusBar _instance;

    private final JLabel _label = new JLabel();
    private final JProgressBar _progressBar = new JProgressBar();

    /** The publish stage being shown, or null when no publish is running. Takes precedence over S3 progress. */
    private String _publishStage;

    public AdminStatusBar()
    {
        super(new BorderLayout(8, 0));
        _instance = this;

        Color separator = UIManager.getColor("Separator.foreground");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, separator == null ? Color.GRAY : separator),
                BorderFactory.createEmptyBorder(3, 6, 3, 6)));

        _label.setFont(_label.getFont().deriveFont(_label.getFont().getSize2D() - 1f));
        _progressBar.setPreferredSize(new Dimension(180, 14));
        _progressBar.setVisible(false);

        add(_label, BorderLayout.CENTER);
        add(_progressBar, BorderLayout.EAST);

        // Listeners fire on the S3 upload thread, so everything they touch has to be handed to the event thread
        S3Uploader.getInstance().addProgressListener(progress -> SwingUtilities.invokeLater(this::refresh));
        new Timer(REFRESH_INTERVAL_MILLIS, e -> refresh()).start();

        refresh();
    }

    /** Null once the admin UI has been built; callers outside it have to cope with there being no status bar. */
    public static AdminStatusBar getStatusBar()
    {
        return _instance;
    }

    /**
     * Shows a publish in progress, or clears it when given null. Safe to call from any thread.
     */
    public void setPublishStage(String stage)
    {
        SwingUtilities.invokeLater(() -> {
            _publishStage = stage;
            refresh();
        });
    }

    private void refresh()
    {
        if (_publishStage != null)
        {
            _label.setText("Publishing to production: " + _publishStage);
            _label.setToolTipText(null);
            _progressBar.setIndeterminate(true);
            _progressBar.setVisible(true);
            return;
        }

        S3Uploader.Progress progress = S3Uploader.getInstance().getProgress();
        if (!progress.isIdle())
        {
            _label.setText("Syncing to S3 (" + progress.completed() + " of " + progress.total() + ")");
            _label.setToolTipText("Originals and resized images being copied to S3. Quitting waits for these.");
            _progressBar.setIndeterminate(false);
            _progressBar.setMinimum(0);
            _progressBar.setMaximum(progress.total());
            _progressBar.setValue(progress.completed());
            _progressBar.setVisible(true);
            return;
        }

        _progressBar.setVisible(false);
        _progressBar.setIndeterminate(false);
        if (DatabaseChangeTracker.hasUnpublishedChanges())
        {
            _label.setText("Database changes not yet published");
            _label.setToolTipText(describeUnpublishedChanges());
        }
        else
        {
            _label.setText("No database changes to publish");
            _label.setToolTipText("Nothing has been written to the database since it was last published.");
        }
    }

    private static String describeUnpublishedChanges()
    {
        StringBuilder description = new StringBuilder();
        description.append(DatabaseChangeTracker.getUnpublishedWriteCount())
                .append(" database write(s) since the last publish");

        Instant lastWrite = DatabaseChangeTracker.getLastWriteTime();
        if (lastWrite != null)
        {
            long minutes = Duration.between(lastWrite, Instant.now()).toMinutes();
            description.append(minutes < 1 ? ", the most recent just now" : ", the most recent " + minutes + " minute(s) ago");
        }
        description.append(". Tools > Publish Database to Production sends them.");
        return description.toString();
    }
}
