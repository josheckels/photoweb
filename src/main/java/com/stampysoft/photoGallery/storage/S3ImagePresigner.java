package com.stampysoft.photoGallery.storage;

import com.stampysoft.util.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Mints short-lived S3 URLs for image bytes, so the buckets can be private and
 * {@link com.stampysoft.photoGallery.controller.ImageController} can decide who gets a link. See PROXY.md.
 * <p>
 * The read-path counterpart to {@link S3Uploader}, and deliberately shaped like it: same lazy singleton, same
 * four config keys, same "missing config means disabled rather than broken". They stay separate because they
 * need different clients ({@link S3Presigner} against {@code S3Client}) and different permissions - this one
 * only ever needs {@code s3:GetObject}, and on the server it is the only half that runs at all.
 * <p>
 * Presigning is an HMAC over the request. It makes no network call, so a cache miss here costs microseconds;
 * the cache exists for the browser's sake rather than ours.
 */
public final class S3ImagePresigner
{
    /**
     * How long a signature stays valid. Seven days is SigV4's hard ceiling for a query-string signature, not a
     * preference - asking for more is rejected at signing time.
     * <p>
     * Note this also caps out at the lifetime of the credentials that signed it. A long-lived IAM user access
     * key reaches the full seven days; temporary STS credentials expire with the session, whatever we ask for.
     */
    private static final Duration SIGNATURE_LIFETIME = Duration.ofDays(7);

    /**
     * How often a given object's URL changes. Every rotation invalidates that image in every browser cache, so
     * it wants to be as long as the ceiling allows - but shorter than {@link #SIGNATURE_LIFETIME}, so that a URL
     * handed out just before a rotation still has four days left on it and nothing expires mid-page.
     */
    private static final Duration ROTATION = Duration.ofDays(3);

    /**
     * The most heap the URL cache may hold: 5% of the heap, and never more than 40MB. There are ~293k resized
     * objects, so a determined crawl would grow it without limit otherwise.
     * <p>
     * Relative to the heap rather than a flat number because the two machines are nothing alike. The server
     * runs with {@code -Xmx180m}, where a flat 40MB would be nearly a quarter of everything the JVM has; a
     * development machine has room to spare. A cache of derived values has no business being a noticeable
     * fraction of a small heap, and re-deriving costs an HMAC.
     * <p>
     * Enforced by adding up what is actually cached rather than by capping the entry count, because the entry
     * count only bounds memory if you know how long a presigned URL is - and that varies with the bucket name,
     * the key prefix and the credential scope. Counting the strings needs no such assumption.
     * <p>
     * For scale: a signed URL for these two buckets measures 343-370 characters, which works out at 638-692
     * bytes an entry. So the server's 9MB holds roughly 14,000 images and a 4GB heap would hold the full 40MB's
     * ~60,000. The library is ~293k resized objects either way, so a full crawl always overflows - it just
     * costs re-signing rather than the heap.
     */
    private static final long MAX_CACHE_BYTES = Math.min(40L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 20);

    /**
     * Charged per entry on top of the two strings' characters, covering the map node and its table slot, the
     * two {@link String} objects and their array headers, the {@link Signed} record and the {@link Instant}.
     * That comes to roughly 176 bytes under compressed oops; 256 is the same number with room to be wrong.
     * <p>
     * The characters themselves are one byte each: presigned URLs are ASCII, so compact strings apply.
     */
    private static final int ENTRY_OVERHEAD_BYTES = 256;

    private static volatile S3ImagePresigner INSTANCE;

    private final boolean _enabled;
    private final String _originalsBucket;
    private final String _resizedBucket;
    private final String _keyPrefix;
    private final S3Presigner _presigner;
    private final ConcurrentHashMap<String, Signed> _cache = new ConcurrentHashMap<>();

    /** Approximate heap held by {@link #_cache}, kept in step with it by {@link #presign}. */
    private final AtomicLong _cacheBytes = new AtomicLong();

    private record Signed(String url, Instant signedAt) {}

    /** A link, and how long the caller may tell the browser to remember it for. */
    public record PresignedUrl(String url, long maxAgeSeconds) {}

    private S3ImagePresigner()
    {
        String regionStr = null;
        String originals = null;
        String resized = null;
        String prefix = "";
        S3Presigner presigner = null;
        String auth = "unresolved";
        boolean ok = false;
        try
        {
            Configuration cfg = Configuration.getConfiguration();
            regionStr = getOrNull(cfg, "S3Region");
            originals = getOrNull(cfg, "S3OriginalsBucket");
            resized = getOrNull(cfg, "S3ResizedBucket");
            String p = getOrNull(cfg, "S3KeyPrefix");
            if (p != null && !p.isEmpty())
            {
                prefix = p.endsWith("/") ? p : (p + "/");
            }
            if (regionStr != null && originals != null && resized != null)
            {
                presigner = S3Presigner.builder()
                        .region(Region.of(regionStr))
                        .credentialsProvider(S3Credentials.provider(cfg))
                        .build();
                auth = S3Credentials.describe(cfg);
                ok = true;
            }
        }
        catch (Throwable e)
        {
            e.printStackTrace();
        }
        _enabled = ok;
        _originalsBucket = ok ? originals : "";
        _resizedBucket = ok ? resized : "";
        _keyPrefix = ok ? prefix : "";
        _presigner = presigner;

        if (_enabled)
        {
            log("enabled (auth=" + auth + ", region=" + regionStr + "). originalsBucket=" + _originalsBucket + ", resizedBucket=" + _resizedBucket);
        }
        else
        {
            log("disabled (missing config or AWS SDK issue). Image requests will fail while photoweb.image.source=s3.");
        }
    }

    private static String getOrNull(Configuration cfg, String key)
    {
        try
        {
            return cfg.getProperty(key);
        }
        catch (Throwable t)
        {
            return null;
        }
    }

    public static S3ImagePresigner getInstance()
    {
        if (INSTANCE == null)
        {
            synchronized (S3ImagePresigner.class)
            {
                if (INSTANCE == null)
                {
                    INSTANCE = new S3ImagePresigner();
                }
            }
        }
        return INSTANCE;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }

    /** A link to a resized image, named {@code {photoId}-{w}x{h}.jpg}. */
    public PresignedUrl resized(String filename)
    {
        return presign(_resizedBucket, _keyPrefix + filename);
    }

    /** A link to an original, named by the photo's own filename. */
    public PresignedUrl original(String filename)
    {
        return presign(_originalsBucket, _keyPrefix + filename);
    }

    /**
     * The same object gets the same URL for {@link #ROTATION}, which is what lets the browser cache the image
     * rather than re-fetching it on every page view. A restart re-signs everything, costing returning visitors
     * one round of downloads - the reason not to make the cache smarter than this is that nothing else about it
     * matters.
     */
    private PresignedUrl presign(String bucket, String key)
    {
        Instant now = Instant.now();
        String cacheKey = bucket + '/' + key;

        Signed signed = _cache.get(cacheKey);
        if (signed == null || !signed.signedAt().plus(ROTATION).isAfter(now))
        {
            String url = _presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(SIGNATURE_LIFETIME)
                    .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                    .build()).url().toString();
            signed = new Signed(url, now);

            Signed replaced = _cache.put(cacheKey, signed);
            long delta = entryBytes(cacheKey, url) - (replaced == null ? 0 : entryBytes(cacheKey, replaced.url()));
            if (_cacheBytes.addAndGet(delta) > MAX_CACHE_BYTES)
            {
                evict();
            }
        }

        long remaining = Duration.between(now, signed.signedAt().plus(ROTATION)).toSeconds();
        return new PresignedUrl(signed.url(), Math.max(0, remaining));
    }

    private static long entryBytes(String cacheKey, String url)
    {
        return (long) cacheKey.length() + url.length() + ENTRY_OVERHEAD_BYTES;
    }

    /**
     * Emptied wholesale rather than evicted least-recently-used, because overflowing costs almost nothing: a
     * re-sign is an HMAC against an in-process key, so the price of throwing the cache away is microseconds per
     * image plus one round of browser re-downloads.
     * <p>
     * The counter and the map are updated separately, so a thread that stores an entry just before another
     * clears the map can add bytes for something no longer there. That drift is always in the direction of
     * over-counting - a put always precedes its own {@code addAndGet} - so it can only make the next eviction
     * happen sooner. The ceiling never leaks.
     */
    private synchronized void evict()
    {
        if (_cacheBytes.get() <= MAX_CACHE_BYTES)
        {
            // Another thread got here first.
            return;
        }
        _cache.clear();
        _cacheBytes.set(0);
    }

    private static void log(String msg)
    {
        System.out.println("[S3ImagePresigner] " + Instant.now() + " - " + msg);
    }
}
