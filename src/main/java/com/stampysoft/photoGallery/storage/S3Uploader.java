package com.stampysoft.photoGallery.storage;

import com.stampysoft.util.Configuration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal background S3 uploader (and deleter) used by the admin tooling.
 * <p>
 * - Reads configuration from src/config.properties via Configuration
 *   Keys:
 *     S3Region (e.g. us-east-1)
 *     S3OriginalsBucket
 *     S3ResizedBucket
 *     S3KeyPrefix (optional, e.g. "photoGallery/")
 * <p>
 * - If required config is missing, all methods become no-ops.
 */
public final class S3Uploader {
    /**
     * What a resized image is served with. A resized object is content-addressed - the key carries the photo id
     * and the exact pixel dimensions, and {@link com.stampysoft.photoGallery.common.Resolution} never rewrites
     * one - so a year and {@code immutable} are honest. PROXY.md has said so since the redirect was designed.
     * <p>
     * Setting it is not an optimisation. With no {@code Cache-Control} at all a browser falls back to heuristic
     * freshness, which is roughly a tenth of the object's age: a photo imported a fortnight ago goes stale after
     * a day, so every thumbnail on the site revalidates against S3 about daily, forever. Safari can lose an image
     * on such a revalidation - it sends the conditional request, takes the 304 and then renders the broken-image
     * placeholder - and because the redirect in front of it is cached too, that sticks for days across every
     * page. {@code immutable} removes the revalidation, and with it the whole failure mode.
     * <p>
     * {@code public} rather than {@code private} because the URL a shared cache would key on carries the
     * signature, so anything it could serve the bytes to already holds a working link for them; and because
     * PROXY.md's upgrade path puts an nginx {@code proxy_cache} in front of exactly this.
     */
    private static final String RESIZED_CACHE_CONTROL = "public, max-age=31536000, immutable";

    /**
     * Originals get a plain day instead, and no {@code immutable}, because unlike a resized image an original
     * <em>is</em> rewritten: {@link com.stampysoft.photoGallery.admin.PhotoAdderThread} copies over the previous
     * file so that a corrected re-export wins, and the S3 key is the source filename, so the new bytes land on
     * the old key. A year would mean a re-export nobody sees. They are fetched one at a time by someone who
     * asked for the full size, rather than fifty at a time in a grid, so the revalidation traffic this leaves in
     * place is small - and a day is still far tighter than the heuristic it replaces.
     */
    private static final String ORIGINAL_CACHE_CONTROL = "public, max-age=86400";

    private static volatile S3Uploader INSTANCE;

    private final boolean enabled;
    private final String originalsBucket;
    private final String resizedBucket;
    private final String keyPrefix;
    private final S3Client s3;
    private final ExecutorService executor;

    /** Guards the three counters below, and is what awaitIdle waits on. */
    private final Object progressLock = new Object();
    private int pending;
    private int completedInBatch;
    private int batchTotal;
    private final List<ProgressListener> progressListeners = new CopyOnWriteArrayList<>();

    private S3Uploader() {
        String regionStr = null;
        String originals = null;
        String resized = null;
        String prefix = "";
        Region region;
        S3Client client = null;
        String auth = "unresolved";
        boolean ok = false;
        try {
            Configuration cfg = Configuration.getConfiguration();
            regionStr = getOrNull(cfg, "S3Region");
            originals = getOrNull(cfg, "S3OriginalsBucket");
            resized = getOrNull(cfg, "S3ResizedBucket");
            String p = getOrNull(cfg, "S3KeyPrefix");
            if (p != null && !p.isEmpty()) {
                prefix = p.endsWith("/") ? p : (p + "/");
            }
            if (regionStr != null && originals != null && resized != null) {
                region = Region.of(regionStr);
                client = S3Client.builder()
                        .region(region)
                        .credentialsProvider(S3Credentials.provider(cfg))
                        .build();
                auth = S3Credentials.describe(cfg);
                ok = true;
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        this.enabled = ok;
        this.originalsBucket = ok ? originals : "";
        this.resizedBucket = ok ? resized : "";
        this.keyPrefix = ok ? prefix : "";
        this.s3 = client;
        this.executor = ok ? Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "S3Uploader-Thread");
            t.setDaemon(true);
            return t;
        }) : null;

        if (enabled) {
            log("S3Uploader enabled (auth=" + auth + ", region=" + regionStr + "). originalsBucket=" + this.originalsBucket + ", resizedBucket=" + this.resizedBucket);
        } else {
            log("S3Uploader disabled (missing config or AWS SDK issue). Uploads will be skipped.");
        }
    }

    private static String getOrNull(Configuration cfg, String key) {
        try {
            return cfg.getProperty(key);
        } catch (Throwable t) {
            return null;
        }
    }

    public static S3Uploader getInstance() {
        if (INSTANCE == null) {
            synchronized (S3Uploader.class) {
                if (INSTANCE == null) {
                    INSTANCE = new S3Uploader();
                }
            }
        }
        return INSTANCE;
    }

    public void enqueueUploadOriginal(File file, String key) {
        if (!enabled || file == null || !file.exists()) return;
        String finalKey = keyPrefix + key;
        submit(file, finalKey, originalsBucket, ORIGINAL_CACHE_CONTROL);
    }

    public void enqueueUploadResized(File file, String key) {
        if (!enabled || file == null || !file.exists()) return;
        String finalKey = keyPrefix + key;
        submit(file, finalKey, resizedBucket, RESIZED_CACHE_CONTROL);
    }

    public void enqueueDeleteOriginal(String key) {
        if (!enabled || key == null || key.isEmpty()) return;
        submitDelete(keyPrefix + key, originalsBucket);
    }

    public void enqueueDeleteResized(String key) {
        if (!enabled || key == null || key.isEmpty()) return;
        submitDelete(keyPrefix + key, resizedBucket);
    }

    private void submitDelete(String key, String bucket) {
        enqueue(() -> {
            try {
                log("Deleting from S3 bucket=" + bucket + " key=" + key);
                DeleteObjectRequest req = DeleteObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build();
                // S3 treats deleting a key that isn't there as a success, so a photo whose upload never happened
                // (or whose resized variants predate S3) doesn't need to be special-cased here.
                s3.deleteObject(req);
                log("Delete complete bucket=" + bucket + " key=" + key);
            } catch (Throwable t) {
                log("Delete FAILED bucket=" + bucket + " key=" + key + " error=" + t.getMessage());
            }
        });
    }

    private void submit(File file, String key, String bucket, String cacheControl) {
        if (!enabled) return;
        enqueue(() -> {
            try {
                log("Uploading to S3 bucket=" + bucket + " key=" + key + " (" + file.length() + " bytes)");
                PutObjectRequest req = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/jpeg")
                        .cacheControl(cacheControl)
                        .build();
                s3.putObject(req, RequestBody.fromFile(file.toPath()));
                log("Upload complete bucket=" + bucket + " key=" + key);
            } catch (Throwable t) {
                log("Upload FAILED bucket=" + bucket + " key=" + key + " error=" + t.getMessage());
            }
        });
    }

    /**
     * Runs a task on the upload thread, counted in and out so that the status bar can show how far the queue has
     * got and {@link #awaitIdle} can tell when it has drained.
     * <p>
     * The counting brackets the executor rather than living inside each task, so the "finished" side runs even when
     * a task throws - the bodies catch Throwable themselves today, but a count that could get stuck above zero
     * would leave the UI claiming an upload forever and hang the wait on quit.
     */
    private void enqueue(Runnable task) {
        taskQueued();
        boolean submitted = false;
        try {
            executor.submit(() -> {
                try {
                    task.run();
                }
                finally {
                    taskFinished();
                }
            });
            submitted = true;
        }
        finally {
            if (!submitted) {
                taskFinished();
            }
        }
    }

    private void taskQueued() {
        synchronized (progressLock) {
            pending++;
            batchTotal++;
        }
        fireProgress();
    }

    private void taskFinished() {
        synchronized (progressLock) {
            pending--;
            completedInBatch++;
            if (pending == 0) {
                // Draining resets the denominator, so the next burst of uploads reads "1 of 12" rather than
                // continuing to count from every upload done since the app started.
                batchTotal = 0;
                completedInBatch = 0;
            }
            progressLock.notifyAll();
        }
        fireProgress();
    }

    private void fireProgress() {
        if (progressListeners.isEmpty()) {
            return;
        }
        Progress progress = getProgress();
        for (ProgressListener listener : progressListeners) {
            try {
                listener.s3ProgressChanged(progress);
            } catch (Throwable t) {
                log("Progress listener failed: " + t);
            }
        }
    }

    /** Snapshot of the queue. Listeners are called on the upload thread, so a UI listener has to hop to the EDT. */
    public Progress getProgress() {
        synchronized (progressLock) {
            return new Progress(pending, completedInBatch, batchTotal);
        }
    }

    public int getPendingCount() {
        synchronized (progressLock) {
            return pending;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Blocks until every queued upload and delete has finished.
     * <p>
     * The upload thread is a daemon, so anything still queued when the JVM exits is dropped silently. That matters
     * most at the moment the database is published: the rows would reach production describing photos whose JPEGs
     * never reached S3, and the site would show them as broken with nothing in any log to say why. So both the
     * publish and the quit path wait here first.
     *
     * @return true if the queue drained, false if the timeout ran out or the wait was interrupted first
     */
    public boolean awaitIdle(long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        synchronized (progressLock) {
            while (pending > 0) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    progressLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return pending == 0;
                }
            }
            return true;
        }
    }

    public void addProgressListener(ProgressListener listener) {
        progressListeners.add(listener);
    }

    public void removeProgressListener(ProgressListener listener) {
        progressListeners.remove(listener);
    }

    /**
     * @param pending tasks queued or running
     * @param completed tasks finished in the current burst of work
     * @param total tasks in the current burst, finished and outstanding together. Zero when idle.
     */
    public record Progress(int pending, int completed, int total) {
        public boolean isIdle() {
            return pending == 0;
        }
    }

    public interface ProgressListener {
        void s3ProgressChanged(Progress progress);
    }

    private static void log(String msg) {
        System.out.println("[S3Uploader] " + Instant.now() + " - " + msg);
    }
}
