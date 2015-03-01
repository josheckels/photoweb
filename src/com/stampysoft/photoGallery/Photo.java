/*
 * Photo.java
 *
 * Created on February 10, 2002, 4:28 PM
 */

package com.stampysoft.photoGallery;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataException;
import com.drew.metadata.Tag;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.stampysoft.photoGallery.common.BasePhoto;
import com.stampysoft.photoGallery.common.Resolution;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

//import com.sun.image.codec.jpeg.JPEGCodec;
//import com.sun.image.codec.jpeg.JPEGEncodeParam;
//import com.sun.image.codec.jpeg.JPEGImageDecoder;
//import com.sun.image.codec.jpeg.JPEGImageEncoder;

@JsonAutoDetect(fieldVisibility= JsonAutoDetect.Visibility.NONE,
        getterVisibility= JsonAutoDetect.Visibility.NONE, isGetterVisibility= JsonAutoDetect.Visibility.NONE)
public class Photo extends BasePhoto implements Comparable<Photo>
{
    private Set<Category> _categories;
    private boolean _private;
    private Set<Comment> _comments;
    private Photographer _photographer;
    private Set<Category> _defaultPhotoForCategories;

    public Photo()
    {
    }

    public Photographer getPhotographer()
    {
        return _photographer;
    }

    public void setPhotographer(Photographer photographer)
    {
        _photographer = photographer;
    }

    public Set<Category> getCategories()
    {
        return getCategories(true);
    }

    public Set<Category> getCategories(boolean includePrivate)
    {
        if (includePrivate)
        {
            return _categories;
        }
        Set<Category> result = new TreeSet<Category>();
        for (Category category : _categories)
        {
            if (!category.isPrivate())
            {
                result.add(category);
            }
        }
        return result;
    }

    public Set<Comment> getComments()
    {
        return _comments;
    }

    public void setComments(Set<Comment> comments)
    {
        _comments = comments;
    }

    public void setCategories(Set<Category> categories)
    {
        _categories = categories;
    }

    private Iterator<Directory> getMetadataDirectories() throws PhotoManipulationException
    {
        try
        {
            File file = new File(ResolutionUtil.getPhotosDirectory(), getFilename());
            if (file.exists())
            {
                try
                {
                    Metadata metadata = JpegMetadataReader.readMetadata(file);
                    return metadata.getDirectories().iterator();
                }
                catch (JpegProcessingException | IOException e)
                {
                    throw new PhotoManipulationException(file.toString(), e);
                }
            }
            return Collections.<Directory>emptyList().iterator();
        }
        catch (URISyntaxException e)
        {
            throw new PhotoManipulationException(e);
        }
    }

    public List<Tag> getDisplayMetadata() throws PhotoManipulationException
    {
        Iterator<Directory> directories = getMetadataDirectories();
        List<Tag> result = new ArrayList<Tag>();
        while (directories.hasNext())
        {
            Directory directory = directories.next();
            Iterator<Tag> tags = directory.getTags().iterator();
            while (tags.hasNext())
            {
                Tag tag = tags.next();
                try
                {
                    if (showTag(tag))
                    {
                        result.add(tag);
                    }
                }
                catch (MetadataException e)
                {
                    throw new PhotoManipulationException(e);
                }
            }
        }
        return result;
    }

    private boolean showTag(Tag tag) throws MetadataException
    {
        String directoryName = tag.getDirectoryName();
        String tagName = tag.getTagName();

        if (directoryName.equals("Exif"))
        {
            return tagName.equals("F-Number") ||
                tagName.equals("ISO Speed Ratings") ||
                tagName.equals("Focal Length") ||
                tagName.equals("Exposure Time") ||
                tagName.equals("Model");
        }

        if (directoryName.equals("Canon Makernote"))
        {
            return tagName.equals("White Balance") && !tag.getDescription().startsWith("Unknown");
        }

        return false;
    }


    public void ensureAllResized() throws PhotoManipulationException, IOException
    {
        Resolution[] resolutions = getResizedDimensions();
        Arrays.sort(resolutions, REVERSE_DIMENSION_COMPARATOR);

        Resolution oldResolution = getOriginalDimensions();

        for (Resolution res : resolutions)
        {
            ensureResized(res, oldResolution);
            oldResolution = res;
        }
    }

    public void ensureResized(Resolution newResolution, Resolution oldResolution)
        throws PhotoManipulationException, IOException
    {
        throw new UnsupportedOperationException();
//        File file = new File(PhotoOperations.getPhotoOperations().toURI(newResolution.getURI()));
//
//        String filename = file.getCanonicalPath().intern();
//        synchronized (filename)
//        {
//
//            if (!file.exists() || file.length() == 0)
//            {
//                FileOutputStream fOut = new FileOutputStream(file);
//                BufferedOutputStream bOut = new BufferedOutputStream(fOut, 10000);
//                try
//                {
//
//                    File originalFile = new File(PhotoOperations.getPhotoOperations().toURI(oldResolution.getURI()));
//
//                    FileInputStream fIn = new FileInputStream(originalFile);
//                    JPEGImageDecoder decoder = JPEGCodec.createJPEGDecoder(fIn);
//                    Image inImage = decoder.decodeAsBufferedImage();
//                    fIn.close();
//
//                    AreaAveragingScaleFilter filter = new AreaAveragingScaleFilter(newResolution.width, newResolution.height);
//                    FilteredImageSource filterSource = new FilteredImageSource(inImage.getSource(), filter);
//                    Image output = new JPanel().createImage(filterSource);
//
//                    BufferedImage outImage = new BufferedImage(newResolution.width, newResolution.height, BufferedImage.TYPE_INT_RGB);
//                    // Paint image.
//                    Graphics2D g2d = outImage.createGraphics();
//                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
//                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
//                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//
//                    g2d.drawImage(output, 0, 0, null);
//                    g2d.dispose();
//
//                    // JPEG-encode the image and write to file.
//                    JPEGImageEncoder encoder = JPEGCodec.createJPEGEncoder(bOut);
//                    JPEGEncodeParam p = encoder.getDefaultJPEGEncodeParam(outImage);
//                    p.setQuality(0.9f, true);
//                    encoder.encode(outImage, p);
//
//                    System.gc();
//                }
//                finally
//                {
//                    bOut.close();
//                }
//            }
//        }
    }

    public boolean isPrivate()
    {
        return _private;
    }

    public void setPrivate(boolean b)
    {
        _private = b;
    }

    @SuppressWarnings({"JpaModelErrorInspection"})
    public Set<Category> getDefaultPhotoForCategories()
    {
        return _defaultPhotoForCategories;
    }

    public void setDefaultPhotoForCategories(Set<Category> defaultPhotoForCategories)
    {
        _defaultPhotoForCategories = defaultPhotoForCategories;
    }

    public int compareTo(Photo p)
    {
        return getFilename().compareToIgnoreCase(p.getFilename());
    }
}
