/*
 * PhotoListener.java
 *
 * Created on April 15, 2002, 9:54 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Photo;

import java.util.List;

/**
 * @author josh
 */
public abstract class PhotoListener
{
    public void selectedPhotosChanged(List<Photo> newPhotos, List<Photo> oldPhotos) {}

    public void photoChanged(Photo photo, boolean categoriesChanged) {}

    public void photoListChanged() {}

    public void requestNextPhotoSelection() {}
    public void requestPreviousPhotoSelection() {}

}
