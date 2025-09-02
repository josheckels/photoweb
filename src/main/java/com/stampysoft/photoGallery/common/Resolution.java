package com.stampysoft.photoGallery.common;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stampysoft.photoGallery.Photo;

import java.util.Objects;

/**
 * User: Josh
 * Date: Oct 1, 2007
 */
@JsonAutoDetect(fieldVisibility= JsonAutoDetect.Visibility.NONE,
        getterVisibility= JsonAutoDetect.Visibility.NONE, isGetterVisibility= JsonAutoDetect.Visibility.NONE)

public class Resolution
{

    public int width;
    public int height;
    protected String _uri;
    private String _filename;

    public static String PHOTO_BASE_URL = null;
    public static String RESIZED_BASE_URL = null;

    public Resolution(int h, int w)
    {
        height = h;
        width = w;
    }

    public Resolution(int w, int h, Photo photo)
    {
        this(h, w);

        if (width == photo.getWidth() && height == photo.getHeight())
        {
            _filename = photo.getFilename();
            _uri = PHOTO_BASE_URL + _filename;
        }
        else
        {
            _filename = photo.getPhotoId() + "-" + w + "x" + h + ".jpg";
            _uri = RESIZED_BASE_URL + _filename;
        }
    }

    @JsonProperty
    public String getFilename()
    {
        return _filename;
    }

    @JsonProperty
    public int getHeight()
    {
        return height;
    }

    @JsonProperty
    public int getWidth()
    {
        return width;
    }

    public String getURI()
    {
        return _uri;
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Resolution that = (Resolution) o;
        return width == that.width && height == that.height && Objects.equals(_uri, that._uri) && Objects.equals(_filename, that._filename);
    }

    @Override
    public int hashCode() {
        return Objects.hash(width, height, _uri, _filename);
    }
}
