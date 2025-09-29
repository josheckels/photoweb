package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.*;
import com.stampysoft.photoGallery.common.Resolution;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.net.URISyntaxException;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import com.stampysoft.photoGallery.storage.S3Uploader;
import com.stampysoft.util.Configuration;

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

            Set<Photo> photosToResize = new HashSet<>();

            Map<String, Photo> photos = new HashMap<>();
            final List<Photo> photoList = new ArrayList<>();
            SwingUtilities.invokeAndWait((Runnable) () -> photoList.addAll(AdminFrame.getFrame().getPhotoOperations().getAllPhotos()));
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
                    // If the photo already exists in the destination originals directory,
                    // consider it processed and skip any further work.
                    File destFile = new File(ResolutionUtil.getPhotosDirectory(), photoFile.getName());
                    if (destFile.exists()) {
                        continue;
                    }

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
                catch (IOException | PhotoManipulationException | RuntimeException e)
                {
                    System.err.println("Failed to resize " + photoToResize.getFilename());
                    e.printStackTrace();
                }

            }
            _photosAdded = photosToResize.size();

            SwingUtilities.invokeLater(() -> _dialog.dispose());
        }
        catch (InvocationTargetException | URISyntaxException | InterruptedException e)
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
                    SwingUtilities.invokeAndWait((Runnable) () -> AdminFrame.getFrame().getPhotoOperations().savePhoto(photo));
                }
            }
        }
    }

    private Photo addNewPhoto(File photoFile) throws URISyntaxException, PhotoManipulationException, InterruptedException, InvocationTargetException
    {
        // Copy source image into destination originals directory before saving
        try {
            File destDir = ResolutionUtil.getPhotosDirectory();
            if (!destDir.exists()) {
                destDir.mkdirs();
            }
            File destFile = new File(destDir, photoFile.getName());
            // Always replace to ensure latest export overwrites stale copy
            Files.copy(photoFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Preserve last modified time to help change detection
            destFile.setLastModified(photoFile.lastModified());
        } catch (IOException | URISyntaxException ioe) {
            throw new PhotoManipulationException(ioe);
        }
        // Enqueue background upload of the original to S3
        try {
            File destFile = new File(ResolutionUtil.getPhotosDirectory(), photoFile.getName());
            S3Uploader.getInstance().enqueueUploadOriginal(destFile, photoFile.getName());
        } catch (URISyntaxException ignore) {
            // ignore
        }

        final Photo photoToSave = new Photo();
        photoToSave.setFilename(photoFile.getName());
        File aviFile = new File(ResolutionUtil.getPhotosDirectory(), photoToSave.getMovieFilename());
        photoToSave.setMovie(aviFile.exists());
        setPhotoSize(photoToSave, photoFile);
        photoToSave.setCategories(new TreeSet<>());

        // Populate EXIF metadata (ISO, exposure, etc.) for new photos before saving
        try
        {
            photoToSave.populateMetadataFromFile();
        }
        catch (PhotoManipulationException e)
        {
            // Non-fatal: proceed without metadata
            System.err.println("Failed to read EXIF for " + photoToSave.getFilename() + ": " + e.getMessage());
        }

        return AdminFrame.getFrame().getPhotoOperations().savePhoto(photoToSave);
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
        // Scan the exported photos directory for new files
        File dir = new File(Configuration.getConfiguration().getProperty("ExportedPhotosDirectory"));
        File[] results = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".jpg"));
        if (results == null)
        {
            results = new File[0];
        }
        return results;
    }
    
    private static class CountSetter implements Runnable
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

    private static class CountTotalSetter implements Runnable
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
