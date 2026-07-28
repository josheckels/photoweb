package com.stampysoft.photoGallery.faces;

import com.stampysoft.gui.HiDpi;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Loads face crops off the EDT and hands them back once they're ready.
 * <p>
 * A review grid asks for a hundred crops at once and each one means decoding a 1400px JPEG, so doing this
 * synchronously in a cell renderer would lock the UI solid. Renderers ask for an icon, get null the first time,
 * and get a repaint when it arrives.
 */
public class FaceThumbnailCache
{
    /**
     * How much crop imagery to keep. A face's crop never changes - the box and the source image are fixed once
     * detected, and face ids are never reused - so entries only ever need evicting to bound memory, never for
     * correctness. Holding this much buys confirm and reject being instant instead of re-decoding a screenful of
     * 1400px JPEGs.
     * <p>
     * Counted in bytes rather than entries because a crop's size depends on the display: the same 96 point cell is
     * four times the pixels on a retina monitor as on an ordinary one, so a fixed entry count would mean wildly
     * different memory on different machines.
     */
    private static final long MAX_BYTES = 96L * 1024 * 1024;

    /** Bytes currently held. Guarded by the lock on _icons, which is also what removeEldestEntry runs under. */
    private long _bytes;

    private final Map<String, CachedIcon> _icons = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true)
            {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedIcon> eldest)
                {
                    if (_bytes <= MAX_BYTES)
                    {
                        return false;
                    }
                    _bytes -= eldest.getValue().bytes();
                    return true;
                }
            });

    private final Set<String> _loading = ConcurrentHashMap.newKeySet();
    private final ExecutorService _executor;

    /** An icon and what it costs to keep, since eviction has to know the size of what it drops. */
    private record CachedIcon(Icon icon, long bytes) {}

    public FaceThumbnailCache()
    {
        // A small pool: the work is JPEG decoding, and queueing beats thrashing when a grid scrolls fast.
        _executor = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = Executors.defaultThreadFactory().newThread(runnable);
            thread.setName("Face thumbnail loader");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /**
     * The crop for this face at this size, or null if it isn't loaded yet. When it returns null it has started
     * loading, and will run onLoaded on the EDT when the icon becomes available.
     * <p>
     * size is in logical pixels; the crop is rendered at whatever resolution the component's display actually has,
     * so the same call gives a sharp thumbnail on a retina monitor and an unchanged one elsewhere. Must be called
     * on the EDT, since that's what owns the component.
     */
    public Icon get(PhotoFace face, int size, Component component, Runnable onLoaded)
    {
        double deviceScale = HiDpi.getDeviceScale(component);
        // The scale is part of the key: dragging the window to a monitor with a different one has to re-render
        // rather than reuse crops built for the old one.
        String key = face.getFaceId() + "@" + size + "@" + deviceScale;
        CachedIcon cached = _icons.get(key);
        if (cached != null)
        {
            return cached.icon();
        }
        if (!_loading.add(key))
        {
            return null;
        }

        _executor.execute(() -> {
            CachedIcon loaded = null;
            try
            {
                FaceImages.Crop crop = FaceImages.renderCrop(face, size, deviceScale);
                if (crop != null)
                {
                    BufferedImage image = crop.image();
                    loaded = new CachedIcon(
                            HiDpi.createIcon(image, crop.logicalWidth(), crop.logicalHeight()),
                            (long) image.getWidth() * image.getHeight() * 4);
                }
            }
            catch (RuntimeException ignored)
            {
                // A crop that won't render shows as a blank cell rather than taking the panel down
            }
            CachedIcon entry = loaded == null ? MISSING : loaded;
            // Synchronized as a unit so the byte count can't drift against an eviction running under the same lock
            synchronized (_icons)
            {
                _bytes += entry.bytes();
                _icons.put(key, entry);
            }
            SwingUtilities.invokeLater(onLoaded);
        });
        return null;
    }

    /**
     * Forgets everything. Only needed when detection has re-run, which deletes faces and reinserts them under new
     * ids - confirming, rejecting or reassigning a face never changes its crop, so those must not clear this.
     */
    public void clear()
    {
        synchronized (_icons)
        {
            _icons.clear();
            _bytes = 0;
        }
        _loading.clear();
    }

    /** Stands in for a crop that couldn't be rendered, so it isn't retried on every repaint. */
    private static final CachedIcon MISSING =
            new CachedIcon(new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)), 0);
}
