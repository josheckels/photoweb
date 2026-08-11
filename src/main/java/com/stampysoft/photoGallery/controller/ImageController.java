package com.stampysoft.photoGallery.controller;

import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.photoGallery.Visibility;
import com.stampysoft.photoGallery.storage.S3ImagePresigner;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Image bytes, behind the same {@link Visibility} check as the JSON. See PROXY.md.
 * <p>
 * Until this existed the browser fetched images straight from two public S3 buckets, so a hidden photo was
 * unlisted rather than unreachable - PRIVACY.md's last known limitation. Every image request now names a photo
 * id in its path, which is checked before any link is handed out.
 * <p>
 * Nothing is served from here: in production the answer is a redirect to a short-lived S3 URL, so the bytes
 * never cross the instance. On a development machine the local resized and originals directories are read
 * directly, which is the same endpoint and the same check with no AWS round trip.
 */
@RestController
public class ImageController extends AbstractController
{
    /**
     * The name {@link com.stampysoft.photoGallery.common.Resolution} generates. The id is captured because it
     * is the whole reason for the URL shape - it means the permission check knows which photo is being asked
     * for without a database lookup.
     */
    private static final Pattern RESIZED_FILENAME = Pattern.compile("(\\d+)-\\d+x\\d+\\.jpg", Pattern.CASE_INSENSITIVE);

    /** Long enough to be useful while browsing, short enough that a re-import shows up the same day. */
    private static final Duration LOCAL_CACHE_LIFETIME = Duration.ofDays(1);

    @Autowired
    private PhotoOperations photoOperations;

    private static final String SOURCE_PROPERTY = "photoweb.image.source";

    /** {@code s3} in production, {@code local} on a machine that has the image directories. */
    @Value("${photoweb.image.source:local}")
    private String _source;

    /**
     * Says which way round it is at startup, because the two modes fail in very different ways and neither
     * announces itself: local mode on a server without the image directories just 404s every image, which looks
     * like a routing or permissions problem rather than a mode problem. This is also the only line that appears
     * before anything is requested - {@link com.stampysoft.photoGallery.storage.S3ImagePresigner} logs nothing
     * until the first signature.
     */
    @PostConstruct
    void logSource()
    {
        log("photoweb.image.source=" + _source + " - serving images "
                + (isS3() ? "by presigned redirect to S3" : "from the local directories"), null);
        warnIfSetInTheWrongFile();
    }

    /**
     * {@code config.properties} holds every other image setting - the buckets, the region, the profile - so it
     * is a natural place to put this one too, and it does nothing there: that file is read by this application's
     * own {@link com.stampysoft.util.Configuration}, while this property comes from Spring's environment. The
     * symptom is an unexplained mode, which is a bad afternoon. Saying so out loud costs one line.
     */
    private static void warnIfSetInTheWrongFile()
    {
        try
        {
            String stray = com.stampysoft.util.Configuration.getConfiguration().getProperty(SOURCE_PROPERTY, null);
            if (stray != null)
            {
                log("WARNING: " + SOURCE_PROPERTY + "=" + stray + " is set in config.properties, where nothing"
                        + " reads it. Pass --" + SOURCE_PROPERTY + "=" + stray + " after the jar instead.", null);
            }
        }
        catch (Throwable t)
        {
            // No config.properties at all, which is a problem for S3 mode but not one this check should raise.
        }
    }

    /**
     * A resized image. The filename carries the photo id, so a photo nobody is hiding is answered without
     * touching the database - see {@link PhotoOperations#getRestrictedPhotoIds()}.
     */
    @GetMapping("/img/r/{filename}")
    public ResponseEntity<?> resized(HttpServletRequest request, HttpServletResponse response,
                                     @PathVariable("filename") String filename)
    {
        Matcher matcher = RESIZED_FILENAME.matcher(filename);
        if (!matcher.matches())
        {
            return ResponseEntity.notFound().build();
        }

        int photoId;
        try
        {
            photoId = Integer.parseInt(matcher.group(1));
        }
        catch (NumberFormatException e)
        {
            // A photo id longer than an int, which no row has.
            return ResponseEntity.notFound().build();
        }

        if (photoOperations.getRestrictedPhotoIds().contains(photoId))
        {
            Visibility visibility = visibility(request, response, photoOperations);
            if (photoOperations.getPhoto((long) photoId, visibility) == null)
            {
                return ResponseEntity.notFound().build();
            }
        }

        if (isS3())
        {
            return redirect(presigner -> presigner.resized(filename));
        }
        return localFile(true, filename);
    }

    /**
     * An original. Unlike a resized image the filename is not derivable from the id, so this always loads the
     * photo - and then checks that the filename asked for is <em>that photo's</em>. Without that check,
     * {@code /img/o/{a public id}/{a private photo's filename}} would pass a permission check on one photo and
     * hand back another's bytes.
     */
    @GetMapping("/img/o/{photoId}/{filename}")
    public ResponseEntity<?> original(HttpServletRequest request, HttpServletResponse response,
                                      @PathVariable("photoId") long photoId,
                                      @PathVariable("filename") String filename)
    {
        Photo photo = photoOperations.getPhoto(photoId, visibility(request, response, photoOperations));
        if (photo == null || !filename.equals(photo.getFilename()))
        {
            return ResponseEntity.notFound().build();
        }

        if (isS3())
        {
            return redirect(presigner -> presigner.original(filename));
        }
        return localFile(false, filename);
    }

    private boolean isS3()
    {
        return "s3".equalsIgnoreCase(_source);
    }

    /**
     * 302 to a presigned URL, cached by the browser until that URL is next rotated. The redirect carries no
     * body, so a visitor who has been here this week pays nothing and everyone else pays one small round trip.
     */
    private ResponseEntity<?> redirect(java.util.function.Function<S3ImagePresigner, S3ImagePresigner.PresignedUrl> which)
    {
        S3ImagePresigner presigner = S3ImagePresigner.getInstance();
        if (!presigner.isEnabled())
        {
            // Misconfiguration, not a missing photo: 404 here would quietly turn the whole gallery blank.
            return misconfigured("photoweb.image.source is s3 but S3 config is missing", null);
        }

        S3ImagePresigner.PresignedUrl url;
        try
        {
            url = which.apply(presigner);
        }
        catch (Exception e)
        {
            // Credentials are resolved lazily, at the first signature rather than at startup, so a profile that
            // cannot be found surfaces here rather than in the "enabled" log line.
            return misconfigured("could not sign an S3 URL - check S3Profile and aws.sharedCredentialsFile", e);
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url.url()))
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(url.maxAgeSeconds())).cachePrivate())
                .build();
    }

    /**
     * The directories live in {@code static final} fields that {@link ResolutionUtil} fills from
     * {@code src/config.properties} in a class initialiser, so a missing file or key arrives here as an
     * {@link ExceptionInInitializerError} - an {@link Error}, not an exception, which is why this catches
     * {@link Throwable}. Letting it escape produces a bare 500 that says nothing about the cause.
     */
    private static String localDirectory(boolean resized)
    {
        try
        {
            return resized ? ResolutionUtil.RESIZED_PHOTOS_DIRECTORY_VALUE : ResolutionUtil.PHOTOS_DIRECTORY_VALUE;
        }
        catch (Throwable t)
        {
            log("cannot read " + (resized ? "ResizedDirectory" : "PhotosDirectory") + " from config.properties", t);
            return null;
        }
    }

    private ResponseEntity<?> localFile(boolean resized, String filename)
    {
        String directory = localDirectory(resized);
        if (directory == null)
        {
            return misconfigured("photoweb.image.source is local but the image directories are unreadable", null);
        }

        File file = new File(directory, filename);
        if (!file.isFile())
        {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(contentType(filename))
                .contentLength(file.length())
                .cacheControl(CacheControl.maxAge(LOCAL_CACHE_LIFETIME).cachePrivate())
                .body(new FileSystemResource(file));
    }

    /**
     * A 500 that says what is wrong. The default would be Spring's whitelabel page, which reports only that
     * something threw - and every image on the site failing at once deserves better than that.
     */
    private static ResponseEntity<?> misconfigured(String problem, Throwable cause)
    {
        log(problem, cause);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Image serving misconfigured: " + problem + ". See PROXY.md.\n");
    }

    private static void log(String message, Throwable cause)
    {
        System.out.println("[ImageController] " + Instant.now() + " - " + message);
        if (cause != null)
        {
            cause.printStackTrace();
        }
    }

    /**
     * Resized images are always JPEG, but an original is whatever was imported - {@link Photo} has a movie case,
     * so this doesn't assume.
     */
    private static MediaType contentType(String filename)
    {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
        {
            return MediaType.IMAGE_JPEG;
        }
        if (lower.endsWith(".png"))
        {
            return MediaType.IMAGE_PNG;
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
