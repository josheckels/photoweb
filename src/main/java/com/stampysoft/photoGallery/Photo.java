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
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.photoGallery.common.Resolution;
import jakarta.persistence.*;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;
import java.awt.*;
import java.awt.image.AreaAveragingScaleFilter;
import java.awt.image.BufferedImage;
import java.awt.image.FilteredImageSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.List;

@Entity @Table(name="photo")
@JsonFilter("photoFilter")
@JsonAutoDetect(fieldVisibility= JsonAutoDetect.Visibility.NONE,
        getterVisibility= JsonAutoDetect.Visibility.NONE, isGetterVisibility= JsonAutoDetect.Visibility.NONE)
public class Photo implements Comparable<Photo>
{
    public static final int THUMBNAIL_MAX_DIMENSION = 210;
    public static final int TINY_PREVIEW_MAX_DIMENSION = 70;
    public static final int DEFAULT_MAX_DIMENSION = 700;
    public static final int RETINA_DEFAULT_MAX_DIMENSION = 1400;
    public static final int LARGE_RETINA_DEFAULT_MAX_DIMENSION = 2100;
    public static final int RETINA_THUMBNAIL_MAX_DIMENSION = 420;
    protected static final Comparator<Resolution> DIMENSION_COMPARATOR = Comparator.comparingInt(r -> (r.height * r.width));
    protected static final Comparator<Resolution> REVERSE_DIMENSION_COMPARATOR = (o1, o2) -> -1 * DIMENSION_COMPARATOR.compare(o1, o2);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "photo_id")
    protected Integer photoId = Integer.MIN_VALUE;
    @Column
    protected String filename;
    @Column
    protected String caption;
    @Column
    protected int width = -1;
    @Column
    protected int height = -1;
    @Column
    protected boolean movie = false;
    @ManyToMany
    @JoinTable(
            name = "photo_category_link",
            inverseJoinColumns = @JoinColumn(         // column in junction table that references this entity
                    name = "category_id",          // column name in junction table
                    referencedColumnName = "category_id"    // referenced column in this entity
            ),
            joinColumns = @JoinColumn(  // column in junction table that references other entity
                    name = "photo_id",            // column name in junction table
                    referencedColumnName = "photo_id"   // referenced column in other entity
            )
    )
    private Set<Category> _categories;
    @Column(name = "private")
    private boolean _private;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "photographer_id")
    private Photographer _photographer;
    @OneToMany(mappedBy = "defaultPhoto")
    private Set<Category> _defaultPhotoForCategories;

    @Column(name = "iso")
    private Integer _iso;

    @Column(name = "exposure_time")
    private Float _exposureTime;

    @Column(name = "focal_length")
    private Float _focalLength;

    @Column(name = "camera_model")
    private String _cameraModel;

    @Column(name = "aperture")
    private Float _aperture;

    @Column(name = "lens_model")
    private String _lensModel;

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

    @JsonGetter("categories")
    public List<Integer> getCategoriesNonRecursive()
    {
        List<Integer> result = new ArrayList<>();
        for (Category category : getCategories()) {
            result.add(category.getCategoryId());
        }
        return result;
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
        Set<Category> result = new TreeSet<>();
        for (Category category : _categories)
        {
            if (!category.isPrivate())
            {
                result.add(category);
            }
        }
        return result;
    }

    @JsonProperty
    public List<Resolution> getResolutions() {
        return getResizedDimensions();
    }

    public void setCategories(Set<Category> categories)
    {
        _categories = categories;
    }

    @JsonGetter
    public Map<String, Object> getMetadata()
    {
        Map<String, Object> result = new HashMap<>();
        if (getPhotographer() != null)
        {
            result.put("photographer", getPhotographer().getName());
            result.put("license", getPhotographer().getCopyright());
        }
        result.put("iso", getIso());
        result.put("cameraModel", getCameraModel());
        result.put("lensModel", getLensModel());
        result.put("focalLength", getFocalLength());
        result.put("aperture", getAperture());
        result.put("exposureTime", getExposureTime());
        return result;
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
            return Collections.emptyIterator();
        }
        catch (URISyntaxException e)
        {
            throw new PhotoManipulationException(e);
        }
    }

    public List<Tag> getDisplayMetadata() throws PhotoManipulationException
    {
        Iterator<Directory> directories = getMetadataDirectories();
        List<Tag> result = new ArrayList<>();
        while (directories.hasNext())
        {
            Directory directory = directories.next();
            for (Tag tag : directory.getTags()) {
                try {
                    if (showTag(tag)) {
                        result.add(tag);
                    }
                } catch (MetadataException e) {
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

        if (directoryName.startsWith("Exif"))
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
        List<Resolution> resolutions = new ArrayList<>(getResizedDimensions());
        resolutions.sort(REVERSE_DIMENSION_COMPARATOR);

        Resolution oldResolution = getOriginalDimensions();

        for (Resolution res : resolutions)
        {
            ensureResized(res, oldResolution);
            oldResolution = res;
        }
    }

    public void ensureResized(Resolution newResolution, Resolution oldResolution)
        throws IOException
    {
        File file = new File(AdminFrame.getFrame().getPhotoOperations().toURI(newResolution.getURI()));

        String filename = file.getCanonicalPath().intern();
        synchronized (filename)
        {

            if (!file.exists() || file.length() == 0)
            {
                File originalFile = new File(AdminFrame.getFrame().getPhotoOperations().toURI(oldResolution.getURI()));

                try (FileInputStream fIn = new FileInputStream(originalFile))
                {
                    Image inImage = ImageIO.read(fIn);

                    AreaAveragingScaleFilter filter = new AreaAveragingScaleFilter(newResolution.width, newResolution.height);
                    FilteredImageSource filterSource = new FilteredImageSource(inImage.getSource(), filter);
                    Image output = new JPanel().createImage(filterSource);

                    BufferedImage outImage = new BufferedImage(newResolution.width, newResolution.height, BufferedImage.TYPE_INT_RGB);
                    // Paint image.
                    Graphics2D g2d = outImage.createGraphics();
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

                    g2d.drawImage(output, 0, 0, null);
                    g2d.dispose();

                    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
                    ImageWriteParam param = writer.getDefaultWriteParam();
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.9f);
                    IIOImage iioImage = new IIOImage(outImage, null, null);
                    try (ImageOutputStream out = ImageIO.createImageOutputStream(file))
                    {
                        writer.setOutput(out);
                        writer.write(null, iioImage, param);
                    }

                    System.gc();
                }
            }
        }
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

    @JsonGetter("id")
    public Integer getPhotoId()
    {
        return photoId;
    }

    @JsonSerialize
    public String getFilename()
    {
        return filename;
    }

    @JsonSerialize
    public String getCaption()
    {
        return caption;
    }

    @JsonSerialize
    public int getWidth()
    {
        return width;
    }

    @JsonSerialize
    public int getHeight()
    {
        return height;
    }

    public String getMovieFilename()
    {
        if (!getFilename().contains(".jpg"))
        {
            return getFilename() + ".avi";
        }
        return getFilename().substring(0, getFilename().lastIndexOf(".jpg")) + ".avi";
    }

    public boolean isMovie()
    {
        return movie;
    }

    public void setPhotoId(Integer id)
    {
        photoId = id;
    }

    public void setFilename(String filename)
    {
        this.filename = filename;
    }

    public void setCaption(String caption)
    {
        this.caption = caption;
    }

    public void setWidth(int width)
    {
        this.width = width;
    }

    public void setHeight(int height)
    {
        this.height = height;
    }

    public void setMovie(boolean movie)
    {
        this.movie = movie;
    }

    public Integer getIso()
    {
        return _iso;
    }

    public void setIso(Integer iso)
    {
        _iso = iso;
    }

    public Float getExposureTime()
    {
        return _exposureTime;
    }

    public void setExposureTime(Float exposureTime)
    {
        _exposureTime = exposureTime;
    }

    public Float getFocalLength()
    {
        return _focalLength;
    }

    public void setFocalLength(Float focalLength)
    {
        _focalLength = focalLength;
    }

    public String getCameraModel()
    {
        return _cameraModel;
    }

    public void setCameraModel(String cameraModel)
    {
        _cameraModel = cameraModel;
    }

    public Float getAperture()
    {
        return _aperture;
    }

    public void setAperture(Float aperture)
    {
        _aperture = aperture;
    }

    public String getLensModel()
    {
        return _lensModel;
    }

    public void setLensModel(String lensModel)
    {
        _lensModel = lensModel;
    }

    protected float getScale(int pixels)
    {
        float scale;
        if (getWidth() > getHeight())
        {
            scale = (float) pixels / (float) getWidth();
        }
        else
        {
            scale = (float) pixels / (float) getHeight();
        }
        return scale;
    }

    public Resolution getThumbnailDimensions()
    {
        return getDimensionForMaxSize(THUMBNAIL_MAX_DIMENSION);
    }

    public Resolution getTinyPreviewDimensions()
    {
        return getDimensionForMaxSize(TINY_PREVIEW_MAX_DIMENSION);
    }

    public Resolution getDefaultDimensions()
    {
        return getDimensionForMaxSize(DEFAULT_MAX_DIMENSION);
    }

    public Resolution getRetinaDimensions()
    {
        return getDimensionForMaxSize(RETINA_DEFAULT_MAX_DIMENSION);
    }

    public Resolution getLargeRetinaDimensions()
    {
        return getDimensionForMaxSize(LARGE_RETINA_DEFAULT_MAX_DIMENSION);
    }

    public Resolution getRetinaThumbnailDimensions()
    {
        return getDimensionForMaxSize(RETINA_THUMBNAIL_MAX_DIMENSION);
    }

    public String getMovieURI()
    {
        return getDefaultDimensions().getURI() + "?movie=true";
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Photo p))
        {
            return false;
        }

        return Objects.equals(filename, p.filename);
    }

    public int hashCode()
    {
        return (filename != null ? filename.hashCode() : 0);
    }


    public Resolution getOriginalDimensions()
    {
        return new Resolution(getWidth(), getHeight(), this);
    }

    private Resolution getDimensionForMaxSize(int pixels)
    {
        float scale = getScale(pixels);
        if (scale < 1.0)
        {
            return new Resolution((int) (scale * getWidth()), (int) (scale * getHeight()), this);
        }
        return getOriginalDimensions();
    }

    public List<Resolution> getResizedDimensions()
    {
        Set<Resolution> available = new HashSet<>();

        available.add(getDefaultDimensions());
        available.add(getRetinaDimensions());
        available.add(getLargeRetinaDimensions());
        available.add(getTinyPreviewDimensions());
        available.add(getThumbnailDimensions());
        available.add(getRetinaThumbnailDimensions());

        if (getWidth() >= getHeight())
        {
            if (getWidth() > 900)
            {
                available.add(getDimensionForMaxSize(900));
            }
            if (getWidth() > 1100)
            {
                available.add(getDimensionForMaxSize(1100));
            }
        }

        List<Resolution> result = new ArrayList<>(available);
        result.sort(DIMENSION_COMPARATOR);
        return Collections.unmodifiableList(result);
    }
}
