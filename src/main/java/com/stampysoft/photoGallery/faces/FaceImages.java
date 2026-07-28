package com.stampysoft.photoGallery.faces;

import com.stampysoft.gui.HiDpi;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.common.Resolution;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Picks which image on disk to use for face work, and turns a stored bounding box back into a crop.
 */
public class FaceImages
{
    /** How much bigger than the bounding box a review crop is, so that a face has some context around it. */
    private static final float CROP_EXPANSION = 0.4f;

    private FaceImages()
    {
    }

    /**
     * The file to run detection against: the 1400px variant, falling back to the original.
     * <p>
     * The 700px default loses small faces in group shots and the original is wasteful, so 1400px is the compromise.
     * Detection normalizes its boxes by whatever it was handed, so which one this returns doesn't affect the
     * stored coordinates.
     */
    public static File getDetectionFile(Photo photo)
    {
        File retina = toFile(photo.getRetinaDimensions());
        if (retina != null && retina.isFile() && retina.length() > 0)
        {
            return retina;
        }
        return toFile(photo.getOriginalDimensions());
    }

    private static File toFile(Resolution resolution)
    {
        String uri = resolution.getURI();
        if (uri == null)
        {
            return null;
        }
        try
        {
            return new File(PhotoOperations.getPhotoOperations().toURI(uri));
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    /**
     * A rendered crop, along with the size it should occupy on screen. The image itself is at the display's device
     * resolution, so on a retina monitor it has more pixels than the logical size it will be drawn at.
     */
    public record Crop(BufferedImage image, int logicalWidth, int logicalHeight) {}

    /**
     * Renders the given face as a square-ish crop no larger than maxSize logical pixels on its long edge, expanded
     * past the bounding box for context. Returns null if the underlying image isn't readable.
     * <p>
     * deviceScale is how many real pixels the screen puts behind each logical one: the crop is rendered that much
     * bigger so a review grid on a retina monitor shows detail rather than an upscaled thumbnail. The source is a
     * 1400px image either way, so there's no extra decoding for the resolution - only a larger crop to hold.
     */
    public static Crop renderCrop(PhotoFace face, int maxSize, double deviceScale)
    {
        Photo photo = face.getPhoto();
        File file = getDetectionFile(photo);
        if (file == null || !file.isFile())
        {
            return null;
        }

        BufferedImage image;
        try
        {
            image = ImageIO.read(file);
        }
        catch (IOException e)
        {
            return null;
        }
        if (image == null)
        {
            return null;
        }

        // The box is normalized against the original, and every resized variant is a uniform scale of it, so the
        // same fractions apply to whichever variant got loaded.
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();

        float expandX = face.getW() * CROP_EXPANSION / 2;
        float expandY = face.getH() * CROP_EXPANSION / 2;

        int left = Math.round((face.getX() - expandX) * imageWidth);
        int top = Math.round((face.getY() - expandY) * imageHeight);
        int width = Math.round((face.getW() + 2 * expandX) * imageWidth);
        int height = Math.round((face.getH() + 2 * expandY) * imageHeight);

        // Clamp into the image, since expanding a face near an edge runs off it
        left = Math.max(0, Math.min(left, imageWidth - 1));
        top = Math.max(0, Math.min(top, imageHeight - 1));
        width = Math.max(1, Math.min(width, imageWidth - left));
        height = Math.max(1, Math.min(height, imageHeight - top));

        // The size it gets laid out at, which is the size the whole crop used to be rendered at
        int logicalWidth = width;
        int logicalHeight = height;
        if (width > maxSize || height > maxSize)
        {
            float scale = Math.min((float) maxSize / width, (float) maxSize / height);
            logicalWidth = Math.max(1, Math.round(width * scale));
            logicalHeight = Math.max(1, Math.round(height * scale));
        }

        // Rendering past what the source region actually holds would just be interpolation, so a face too small to
        // fill the cell at device resolution is rendered at its native size and drawn up by the icon instead.
        int targetWidth = Math.min(width, HiDpi.toDevicePixels(logicalWidth, deviceScale));
        int targetHeight = Math.min(height, HiDpi.toDevicePixels(logicalHeight, deviceScale));

        // Drawn from a source rectangle into a fresh image rather than via getSubimage, which returns a view onto
        // the parent's raster. Returning such a view keeps the whole 1400px source image alive for as long as the
        // crop is referenced - megabytes per cached thumbnail instead of the tens of kilobytes the crop needs.
        BufferedImage crop = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = crop.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, targetWidth, targetHeight,
                    left, top, left + width, top + height, null);
        }
        finally
        {
            graphics.dispose();
        }
        return new Crop(crop, logicalWidth, logicalHeight);
    }
}
