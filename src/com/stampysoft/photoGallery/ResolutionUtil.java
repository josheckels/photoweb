/*
 * ResolutionUtil.java
 *
 * Created on June 1, 2003, 3:11 PM
 */

package com.stampysoft.photoGallery;

import com.stampysoft.photoGallery.common.Resolution;
import com.stampysoft.util.Configuration;

import java.io.File;
import java.net.URISyntaxException;

/**
 * @author josh
 */
public class ResolutionUtil
{

    public static void init()
    {
        Resolution.PHOTO_BASE_URL = Configuration.getConfiguration().getProperty(PHOTOS_URL_PROPERTY);
        Resolution.RESIZED_BASE_URL = Configuration.getConfiguration().getProperty(RESIZED_URL_PROPERTY);
    }

    private static final String PHOTOS_DIRECTORY_PROPERTY = "PhotosDirectory";
    private static final String RESIZED_PHOTOS_DIRECTORY_PROPERTY = "ResizedDirectory";

    private static final String PHOTOS_URL_PROPERTY = "PhotosURL";
    private static final String RESIZED_URL_PROPERTY = "ResizedURL";

    public static final String PHOTOS_DIRECTORY_VALUE = Configuration.getConfiguration().getProperty(PHOTOS_DIRECTORY_PROPERTY);
    public static final String RESIZED_PHOTOS_DIRECTORY_VALUE = Configuration.getConfiguration().getProperty(RESIZED_PHOTOS_DIRECTORY_PROPERTY);

    public static File getPhotosDirectory() throws URISyntaxException
    {
        return new File(PHOTOS_DIRECTORY_VALUE);
    }
}
