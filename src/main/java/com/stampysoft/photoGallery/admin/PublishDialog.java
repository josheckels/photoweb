package com.stampysoft.photoGallery.admin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Runs a {@link DatabasePublisher} and shows what it's doing.
 * <p>
 * The output is on screen rather than only on stdout because publishing is the one operation here that touches the
 * live site, and the interesting part of a failure is usually the last thing scp or ssh said. The dialog closes
 * itself when it works and stays put when it doesn't, so a successful publish costs no clicks and a failed one
 * can't be missed.
 */
public class PublishDialog extends JDialog
{
    private final DatabasePublisher _publisher = new DatabasePublisher();

    private final JLabel _stageLabel = new JLabel("Starting...");
    private final JProgressBar _progressBar = new JProgressBar();
    private final JTextArea _logArea = new JTextArea(16, 74);
    private final JButton _cancelButton = new JButton("Cancel");
    private final JButton _closeButton = new JButton("Close");
    private final JLabel _errorLabel = new JLabel();

    private boolean _succeeded;

    private PublishDialog(Window owner)
    {
        super(owner, "Publish Database to Production", ModalityType.APPLICATION_MODAL);
        addComponents();
        addListeners();
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Publishes, blocking until it has finished or the user has closed the dialog.
     *
     * @return true if production now has a copy of this database
     */
    public static boolean publish(Window owner)
    {
        PublishDialog dialog = new PublishDialog(owner);
        dialog.start();
        dialog.setVisible(true);
        return dialog._succeeded;
    }

    private void addComponents()
    {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new BorderLayout(0, 4));
        topPanel.add(_stageLabel, BorderLayout.NORTH);
        _progressBar.setIndeterminate(true);
        topPanel.add(_progressBar, BorderLayout.CENTER);
        contentPane.add(topPanel, BorderLayout.NORTH);

        _logArea.setEditable(false);
        _logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        contentPane.add(new JScrollPane(_logArea), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        _errorLabel.setForeground(Color.RED.darker());
        bottomPanel.add(_errorLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        _closeButton.setEnabled(false);
        buttonPanel.add(_cancelButton);
        buttonPanel.add(_closeButton);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        contentPane.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(contentPane);
        // The window close button has to go through the same cancel path, rather than leaving a publish running
        // with nothing watching it
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }

    private void addListeners()
    {
        _cancelButton.addActionListener(e -> cancel());
        _closeButton.addActionListener(e -> dispose());
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent e)
            {
                if (_cancelButton.isEnabled())
                {
                    cancel();
                }
                else
                {
                    dispose();
                }
            }
        });
    }

    private void cancel()
    {
        // A restore that has already started on the server can't be called back, so say so rather than implying
        // that cancelling undoes it.
        int answer = JOptionPane.showConfirmDialog(this,
                "Stop publishing?\n\nIf the restore has already started on the server it will finish anyway.",
                "Cancel Publish", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.YES_OPTION)
        {
            _cancelButton.setEnabled(false);
            _stageLabel.setText("Cancelling...");
            _publisher.cancel();
        }
    }

    private void start()
    {
        new PublishWorker().execute();
    }

    private void appendLine(String line)
    {
        _logArea.append(line);
        _logArea.append("\n");
        _logArea.setCaretPosition(_logArea.getDocument().getLength());
    }

    private void setStage(String stage)
    {
        _stageLabel.setText(stage);
        AdminStatusBar statusBar = AdminStatusBar.getStatusBar();
        if (statusBar != null)
        {
            statusBar.setPublishStage(stage);
        }
    }

    private void finished(String failure)
    {
        _progressBar.setIndeterminate(false);
        _cancelButton.setEnabled(false);
        _closeButton.setEnabled(true);
        getRootPane().setDefaultButton(_closeButton);

        AdminStatusBar statusBar = AdminStatusBar.getStatusBar();
        if (statusBar != null)
        {
            statusBar.setPublishStage(null);
        }

        if (failure == null)
        {
            _succeeded = true;
            dispose();
            return;
        }

        // A cancel is a decision, not a fault, so it doesn't get to look like one
        _stageLabel.setText(_publisher.wasCancelled() ? "Publish cancelled" : "Publish failed");
        _errorLabel.setText(failure);
        appendLine("");
        appendLine(failure);
        pack();
    }

    /**
     * Publishes off the event thread, forwarding stages and output back onto it. Both kinds of message go through
     * the same queue so that a stage change can't overtake the lines that led up to it.
     */
    private class PublishWorker extends SwingWorker<String, Message> implements DatabasePublisher.Listener
    {
        @Override
        protected String doInBackground()
        {
            return _publisher.publish(this);
        }

        @Override
        public void stageChanged(String stage)
        {
            publish(new Message(true, stage));
        }

        @Override
        public void lineLogged(String line)
        {
            publish(new Message(false, line));
        }

        @Override
        protected void process(List<Message> messages)
        {
            for (Message message : messages)
            {
                if (message.stage())
                {
                    setStage(message.text());
                }
                else
                {
                    appendLine(message.text());
                }
            }
        }

        @Override
        protected void done()
        {
            try
            {
                finished(get());
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                finished("Interrupted.");
            }
            catch (ExecutionException e)
            {
                finished("The publish failed unexpectedly: " + e.getCause());
            }
        }
    }

    private record Message(boolean stage, String text)
    {
    }
}
