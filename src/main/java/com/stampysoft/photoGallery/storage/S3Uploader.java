package com.stampysoft.photoGallery.storage;

import com.stampysoft.util.Configuration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.time.Instant;
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
    private static volatile S3Uploader INSTANCE;

    private final boolean enabled;
    private final String originalsBucket;
    private final String resizedBucket;
    private final String keyPrefix;
    private final S3Client s3;
    private final ExecutorService executor;

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
        submit(file, finalKey, originalsBucket);
    }

    public void enqueueUploadResized(File file, String key) {
        if (!enabled || file == null || !file.exists()) return;
        String finalKey = keyPrefix + key;
        submit(file, finalKey, resizedBucket);
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
        executor.submit(() -> {
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

    private void submit(File file, String key, String bucket) {
        if (!enabled) return;
        executor.submit(() -> {
            try {
                log("Uploading to S3 bucket=" + bucket + " key=" + key + " (" + file.length() + " bytes)");
                PutObjectRequest req = PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("image/jpeg")
                        .build();
                s3.putObject(req, RequestBody.fromFile(file.toPath()));
                log("Upload complete bucket=" + bucket + " key=" + key);
            } catch (Throwable t) {
                log("Upload FAILED bucket=" + bucket + " key=" + key + " error=" + t.getMessage());
            }
        });
    }

    private static void log(String msg) {
        System.out.println("[S3Uploader] " + Instant.now() + " - " + msg);
    }
}
