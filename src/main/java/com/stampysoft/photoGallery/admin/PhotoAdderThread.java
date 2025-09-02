package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.*;
import com.stampysoft.photoGallery.common.Resolution;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.FilenameFilter;
import java.util.*;
import java.util.List;
import java.net.URISyntaxException;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;

/**
 * User: Josh
 * Date: Sep 26, 2008
 */
public class PhotoAdderThread extends Thread
{
    private int _photosAdded = -1;
    private final JDialog _dialog;
    private final JProgressBar _progressBar;
    private final JLabel _label;

    public PhotoAdderThread(JDialog dialog, JProgressBar progressBar, JLabel label)
    {
        _dialog = dialog;
        _progressBar = progressBar;
        _label = label;
    }

    public int getPhotosAdded()
    {
        return _photosAdded;
    }

    public void run()
    {
        try
        {
            File[] photoFiles = getPhotoFiles();
            SwingUtilities.invokeLater(new CountTotalSetter(photoFiles.length, "Scanning files", _progressBar, _label));

            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -7);
            long newerThanDate = cal.getTime().getTime();

            Set<Photo> photosToResize = new HashSet<Photo>();

            Map<String, Photo> photos = new HashMap<String, Photo>();
            final List<Photo> photoList = new ArrayList<Photo>();
            SwingUtilities.invokeAndWait(new Runnable()
            {
                public void run()
                {
                    photoList.addAll(AdminFrame.getFrame().getPhotoOperations().getAllPhotos());
                }
            });
            for (Photo photo : photoList)
            {
                photos.put(photo.getFilename(), photo);
            }

            for (int i = 0; i < photoFiles.length; i++)
            {
                File photoFile = photoFiles[i];
                SwingUtilities.invokeLater(new CountSetter(i, _progressBar));

                try
                {
                    Photo photo = photos.get(photoFile.getName());
                    if (photo == null)
                    {
                        photosToResize.add(addNewPhoto(photoFile));
                    }
                    else
                    {
                        if (photoFile.lastModified() > newerThanDate)
                        {
                            checkForUpdates(photosToResize, photoFile, photo);
                        }
                    }
                }
                catch (PhotoManipulationException e)
                {
                    System.err.println("Failed to process " + photoFile.getName());
                    e.printStackTrace();
                }
            }

            SwingUtilities.invokeLater(new CountTotalSetter(photosToResize.size(), "Resizing " + photosToResize.size() + " image(s)", _progressBar, _label));
            int i = 0;
            for (Photo photoToResize : photosToResize)
            {
                try
                {
                    photoToResize.ensureAllResized();
                    SwingUtilities.invokeLater(new CountSetter(++i, _progressBar));
                }
                catch (IOException e)
                {
                    System.err.println("Failed to resize " + photoToResize.getFilename());
                    e.printStackTrace();
                }
                catch (PhotoManipulationException e)
                {
                    System.err.println("Failed to resize " + photoToResize.getFilename());
                    e.printStackTrace();
                }
                catch (RuntimeException e)
                {
                    System.err.println("Failed to resize " + photoToResize.getFilename());
                    e.printStackTrace();
                }

            }
            _photosAdded = photosToResize.size();

            SwingUtilities.invokeLater(new Runnable()
            {
                public void run()
                {
                    _dialog.dispose();
                }
            });
        }
        catch (InvocationTargetException e)
        {
            e.printStackTrace();
        }
        catch (URISyntaxException e)
        {
            e.printStackTrace();
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }

    private void checkForUpdates(Set<Photo> photosToResize, File photoFile, final Photo photo) throws PhotoManipulationException, InterruptedException, InvocationTargetException
    {
        for (Resolution resized :  photo.getResizedDimensions())
        {
            File resizedFile = new File(AdminFrame.getFrame().getPhotoOperations().toURI(resized.getURI()));
            if (!resizedFile.exists() || resizedFile.lastModified() < photoFile.lastModified())
            {
                resizedFile.delete();
                if (!photosToResize.contains(photo))
                {
                    photosToResize.add(photo);
                    setPhotoSize(photo, photoFile);
                    SwingUtilities.invokeAndWait(new Runnable()
                    {
                        public void run()
                        {
                            AdminFrame.getFrame().getPhotoOperations().savePhoto(photo);
                        }
                    });
                }
            }
        }
    }

    private Photo addNewPhoto(File photoFile) throws URISyntaxException, PhotoManipulationException, InterruptedException, InvocationTargetException
    {
        final Photo photoToSave = new Photo();
        photoToSave.setFilename(photoFile.getName());
        File aviFile = new File(ResolutionUtil.getPhotosDirectory(), photoToSave.getMovieFilename());
        photoToSave.setMovie(aviFile.exists());
        setPhotoSize(photoToSave, photoFile);
        photoToSave.setCategories(new TreeSet<Category>());

        final Photo[] returnValue = new Photo[1];

        SwingUtilities.invokeAndWait(new Runnable()
        {
            public void run()
            {
                returnValue[0] = AdminFrame.getFrame().getPhotoOperations().savePhoto(photoToSave);
            }
        });
        return returnValue[0];
    }

    private void setPhotoSize(Photo photo, File f) throws PhotoManipulationException
    {
        try
        {
            Image image = new ImageIcon(f.getCanonicalPath()).getImage();
            photo.setWidth(image.getWidth(null));
            photo.setHeight(image.getHeight(null));
        }
        catch (IOException ex)
        {
            throw new PhotoManipulationException(ex);
        }
    }

    private File[] getPhotoFiles() throws URISyntaxException
    {
        File dir = ResolutionUtil.getPhotosDirectory();
        File[] results = dir.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.toLowerCase().endsWith(".jpg");
            }
        });
        if (results == null)
        {
            results = new File[0];
        }
        return results;
    }
    
    private class CountSetter implements Runnable
    {
        private final JProgressBar _progressBar;
        private final int _count;

        private CountSetter(int count, JProgressBar progressBar)
        {
            _progressBar = progressBar;
            _count = count;
        }

        public void run()
        {
            _progressBar.setValue(_count);
        }
    }

    private class CountTotalSetter implements Runnable
    {
        private final int _count;
        private final String _message;
        private final JProgressBar _progressBar;
        private final JLabel _label;

        public CountTotalSetter(int count, String message, JProgressBar progressBar, JLabel label)
        {
            _count = count;
            _message = message;
            _progressBar = progressBar;
            _label = label;
        }

        public void run()
        {
            _progressBar.setMaximum(_count);
            _label.setText(_message);
            _progressBar.setValue(0);
        }
    }


}
