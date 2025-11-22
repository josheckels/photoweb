/*
 * ResizeAll.java
 *
 * Created on May 29, 2003, 9:50 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.util.Configuration;

import java.util.List;

/**
 * @author josh
 */
public class ResizeAll
{
    private static volatile int _index = 1;

    public static void main(String... args) throws Exception
    {
        if (args.length == 1)
        {
            Configuration.setConfigFileName(args[0]);
        }

        ResolutionUtil.init();

        PhotoOperations.getPhotoOperations().beginTransaction();
        final List<Photo> photos = PhotoOperations.getPhotoOperations().getAllPhotos();

        Runnable r = () -> {
            Photo photo = getNextPhoto(photos);
            while (photo != null)
            {
                try
                {
                    System.out.println(Thread.currentThread().getName() + " resizing " + (_index + 1) + " of " + photos.size() + ", " + photo.getFilename());
                    photo.ensureAllResized();
                }
                catch (Exception e)
                {
                    System.err.println("Failed to size " + photo.getFilename());
                    e.printStackTrace();
                }
                photo = getNextPhoto(photos);
            }
        };

        new Thread(r, "Thread 1").start();
        new Thread(r, "Thread 2").start();
        new Thread(r, "Thread 3").start();
    }

    public static synchronized Photo getNextPhoto(List<Photo> photos)
    {
        if (photos.size() > _index)
        {
            return photos.get(_index++);
        }
        return null;
    }
}
