package com.stampysoft.photoGallery.common;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.*;

/**
 * User: Josh
 * Date: Sep 30, 2007
 */
public abstract class BasePhoto //implements IsSerializable
{
    public static final int THUMBNAIL_MAX_DIMENSION = 210;
    public static final int TINY_PREVIEW_MAX_DIMENSION = 70;
    public static final int DEFAULT_MAX_DIMENSION = 700;
    public static final int RETINA_DEFAULT_MAX_DIMENSION = 1400;
    public static final int LARGE_RETINA_DEFAULT_MAX_DIMENSION = 2100;
    public static final int RETINA_THUMBNAIL_MAX_DIMENSION = 420;
    protected Long _photoId = Long.MIN_VALUE;
    protected String _filename;
    protected String _caption;
    protected int _width = -1;
    protected int _height = -1;
    protected boolean _movie = false;

    @JsonSerialize
    public Long getPhotoId()
    {
        return _photoId;
    }

    @JsonSerialize
    public String getFilename()
    {
        return _filename;
    }

    @JsonSerialize
    public String getCaption()
    {
        return _caption;
    }

    @JsonSerialize
    public int getWidth()
    {
        return _width;
    }

    @JsonSerialize
    public int getHeight()
    {
        return _height;
    }

    public String getMovieFilename()
    {
        if (getFilename().indexOf(".jpg") == -1)
        {
            return getFilename() + ".avi";
        }
        return getFilename().substring(0, getFilename().lastIndexOf(".jpg")) + ".avi";
    }

    public boolean isMovie()
    {
        return _movie;
    }

    public void setPhotoId(Long id)
    {
        _photoId = id;
    }

    public void setFilename(String filename)
    {
        _filename = filename;
    }

    public void setCaption(String caption)
    {
        _caption = caption;
    }

    public void setWidth(int width)
    {
        _width = width;
    }

    public void setHeight(int height)
    {
        _height = height;
    }

    public void setMovie(boolean movie)
    {
        _movie = movie;
    }

    public String toString()
    {
        return getFilename() + " - " + getWidth() + "x" + getHeight();
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof BasePhoto))
        {
            return false;
        }

        BasePhoto basePhoto = (BasePhoto) o;

        return !(_filename != null ? !_filename.equals(basePhoto._filename) : basePhoto._filename != null);
    }

    public int hashCode()
    {
        return (_filename != null ? _filename.hashCode() : 0);
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


    public Resolution[] getResizedDimensions()
    {
        List<Resolution> result = new ArrayList<Resolution>();

        result.add(getDefaultDimensions());
        result.add(getRetinaDimensions());
        result.add(getLargeRetinaDimensions());
        result.add(getTinyPreviewDimensions());
        result.add(getThumbnailDimensions());
        result.add(getRetinaThumbnailDimensions());

        if (getWidth() >= getHeight())
        {
            if (getWidth() > 900)
            {
                result.add(getDimensionForMaxSize(900));
            }
            if (getWidth() > 1100)
            {
                result.add(getDimensionForMaxSize(1100));
            }
        }

        Collections.sort(result, DIMENSION_COMPARATOR);
        return result.toArray(new Resolution[result.size()]);
    }

    public Resolution[] getPossibleDimensions()
    {
        Resolution[] resized = getResizedDimensions();
        Resolution[] result = new Resolution[resized.length + 1];
        System.arraycopy(resized, 0, result, 0, resized.length);

        result[resized.length] = getOriginalDimensions();

        Arrays.sort(result, DIMENSION_COMPARATOR);
        return result;
    }


    protected static final Comparator<Resolution> DIMENSION_COMPARATOR = new Comparator<Resolution>()
    {
        public int compare(Resolution r1, Resolution r2)
        {
            return (r1.height * r1.width) - (r2.height * r2.width);
        }
    };

    protected static final Comparator<Resolution> REVERSE_DIMENSION_COMPARATOR = new Comparator<Resolution>()
    {
        public int compare(Resolution o1, Resolution o2)
        {
            return -1 * DIMENSION_COMPARATOR.compare(o1, o2);
        }
    };


}
