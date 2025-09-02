package com.stampysoft.photoGallery.servlet;

import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.common.Resolution;

/**
* Created by Josh on 2/28/2015.
*/
public class PreviewImage
{
    private final int _index;
    private final Photo _photo;

    public PreviewImage(int index, Photo photo)
    {
        _index = index;
        _photo = photo;
    }

    public int getIndex()
    {
        return _index;
    }

    public Resolution getPreview()
    {
        return _photo.getRetinaThumbnailDimensions();
    }

    public Resolution getRenderDimensions()
    {
        return _photo.getTinyPreviewDimensions();
    }

    public String getCaption()
    {
        return _photo.getCaption();
    }

    public Integer getPhotoId()
    {
        return _photo.getPhotoId();
    }
}
