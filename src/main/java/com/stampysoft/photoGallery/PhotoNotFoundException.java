/*
 * PhotoNotFoundException.java
 *
 * Created on April 1, 2002, 8:12 PM
 */

package com.stampysoft.photoGallery;

/**
 * @author josh
 */
public class PhotoNotFoundException extends Exception
{

    public PhotoNotFoundException(Long photoId)
    {
        super("Could not find Photo with photo_id " + photoId);
    }

    public PhotoNotFoundException(String filename, Throwable t)
    {
        super("Could not find Photo with filename " + filename, t);
    }

    public PhotoNotFoundException(Throwable t)
    {
        super(t);
    }

}
