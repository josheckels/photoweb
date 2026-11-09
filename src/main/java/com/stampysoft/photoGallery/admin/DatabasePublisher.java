package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.config.DatabaseChangeTracker;
import com.stampysoft.photoGallery.storage.S3Uploader;
import com.stampysoft.util.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copies the local database over the production one by running bin/publish-db.sh. See PUBLISHING.md.
 * <p>
 * The work itself is in the script rather than here so that there is one copy of it: the same thing runs whether
 * it was started from the Tools menu, from the prompt on quit, or by hand in a terminal when something has gone
 * wrong and the app isn't the place to debug it from. This class only assembles the environment, watches the
 * output, and decides what counts as published.
 * <p>
 * Everything it needs is optional configuration, and a missing setting disables the menu item with a reason on it
 * rather than failing when the user asks for it - the same way the People tab reports what it's missing.
 */
public class DatabasePublisher
{
    private static final String SCRIPT_KEY = "PublishScript";
    private static final String DEFAULT_SCRIPT = "bin/publish-db.sh";

    /** Prefix the script marks its stages with. Everything else it prints is log detail. */
    private static final String STAGE_PREFIX = ">>> ";

    /** How long to sit in a single awaitIdle call before reporting the queue again. */
    private static final long DRAIN_POLL_MILLIS = 500;

    private volatile Process _process;
    private volatile boolean _cancelled;

    /**
     * What stops the publish from running, phrased for a tooltip, or null if it can run.
     */
    public static String getConfigurationProblem()
    {
        try
        {
            Configuration configuration = Configuration.getConfiguration();
            if (isBlank(configuration.getProperty("PublishHost", null)))
            {
                return "Set PublishHost in src/config.properties (for example ubuntu@jeckels.com)";
            }
            String key = configuration.getProperty("PublishKeyFile", null);
            if (isBlank(key))
            {
                return "Set PublishKeyFile in src/config.properties (the ssh private key to connect with)";
            }
            if (!new File(key).isFile())
            {
                return "PublishKeyFile does not exist: " + key;
            }
            File script = getScript(configuration);
            if (!script.isFile())
            {
                return "Publish script not found: " + script.getAbsolutePath();
            }
            return null;
        }
        catch (Throwable t)
        {
            return "Could not read src/config.properties: " + t.getMessage();
        }
    }

    public static boolean isConfigured()
    {
        return getConfigurationProblem() == null;
    }

    /** Where production is, for confirmation prompts. Never throws - it's only ever used as label text. */
    public static String describeTarget()
    {
        try
        {
            return Configuration.getConfiguration().getProperty("PublishHost", "production");
        }
        catch (Throwable t)
        {
            return "production";
        }
    }

    private static File getScript(Configuration configuration)
    {
        return new File(configuration.getProperty(SCRIPT_KEY, DEFAULT_SCRIPT));
    }

    /**
     * Waits for S3, runs the script, and marks the database published if it succeeds. Blocks; call it off the event
     * thread.
     *
     * @param listener told about each stage and each line of output, on the calling thread
     * @return null on success, or a message describing how it failed
     */
    public String publish(Listener listener)
    {
        try
        {
            if (!awaitUploads(listener))
            {
                return "Cancelled while waiting for S3 uploads.";
            }

            // Read before the dump starts, not after the publish finishes. A change made while the dump was being
            // taken isn't in it, and treating it as published would leave it stranded on this machine.
            long writeCountAtDump = DatabaseChangeTracker.currentWriteCount();

            int exitCode = runScript(listener);
            if (_cancelled)
            {
                return "Cancelled.";
            }
            if (exitCode != 0)
            {
                return "The publish script exited with status " + exitCode + ".";
            }

            DatabaseChangeTracker.markPublished(writeCountAtDump);
            return null;
        }
        catch (IOException e)
        {
            return "Could not run the publish script: " + e.getMessage();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return "Interrupted.";
        }
    }

    /**
     * Holds the publish until the S3 queue has drained.
     * <p>
     * Publishing rows for photos whose JPEGs are still sitting in the upload queue puts broken images on the live
     * site: the daemon upload thread is dropped when the JVM exits, so "later" may never come.
     *
     * @return false if the user cancelled before it drained
     */
    private boolean awaitUploads(Listener listener)
    {
        S3Uploader uploader = S3Uploader.getInstance();
        if (uploader.getPendingCount() == 0)
        {
            return true;
        }

        listener.stageChanged("Waiting for S3 uploads to finish");
        int lastReported = -1;
        while (!_cancelled)
        {
            int pending = uploader.getPendingCount();
            if (pending == 0)
            {
                listener.lineLogged("S3 queue is empty.");
                return true;
            }
            // Only when it moves: this loop wakes twice a second, and a line each time buries the log.
            if (pending != lastReported)
            {
                listener.lineLogged(pending + " S3 transfer(s) still queued...");
                lastReported = pending;
            }
            uploader.awaitIdle(DRAIN_POLL_MILLIS);
        }
        return false;
    }

    private int runScript(Listener listener) throws IOException, InterruptedException
    {
        Configuration configuration = Configuration.getConfiguration();
        File script = getScript(configuration);

        // bash explicitly rather than executing the script directly, so that it still runs from a checkout where
        // the executable bit didn't survive.
        ProcessBuilder builder = new ProcessBuilder("/bin/bash", script.getAbsolutePath());
        // Interleaved, because the ordering between what the script says and what scp or ssh complains about is
        // most of what makes a failure readable.
        builder.redirectErrorStream(true);
        builder.environment().putAll(buildEnvironment(configuration));

        listener.lineLogged("Running " + script.getAbsolutePath());
        Process process = builder.start();
        _process = process;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (line.startsWith(STAGE_PREFIX))
                {
                    listener.stageChanged(line.substring(STAGE_PREFIX.length()));
                }
                listener.lineLogged(line);
            }
        }
        return process.waitFor();
    }

    private static Map<String, String> buildEnvironment(Configuration configuration)
    {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PUBLISH_HOST", configuration.getProperty("PublishHost", ""));
        environment.put("PUBLISH_KEY", configuration.getProperty("PublishKeyFile", ""));
        putIfSet(environment, "PUBLISH_CONTAINER", configuration.getProperty("PublishDbContainer", null));
        putIfSet(environment, "PUBLISH_DB_USER", configuration.getProperty("PublishDbUser", null));
        putIfSet(environment, "PUBLISH_DB_NAME", configuration.getProperty("PublishDbName", null));
        putIfSet(environment, "PUBLISH_REMOTE_FILE", configuration.getProperty("PublishRemoteFile", null));
        putIfSet(environment, "PUBLISH_REMOTE_RESTORE", configuration.getProperty("PublishRemoteRestore", null));
        return environment;
    }

    /** Leaves unset settings out entirely, so the script's own defaults apply rather than an empty string. */
    private static void putIfSet(Map<String, String> environment, String name, String value)
    {
        if (!isBlank(value))
        {
            environment.put(name, value);
        }
    }

    /**
     * Stops the publish. The local half stops immediately; a restore that has already started on the server runs to
     * completion, because it's the server's own shell running it and killing ssh doesn't reach it.
     */
    public boolean wasCancelled()
    {
        return _cancelled;
    }

    public void cancel()
    {
        _cancelled = true;
        Process process = _process;
        if (process != null)
        {
            process.destroy();
        }
        // awaitUploads may be parked in awaitIdle; it re-checks the flag every poll, so it stops within a poll.
    }

    private static boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    public interface Listener
    {
        void stageChanged(String stage);

        void lineLogged(String line);
    }
}
