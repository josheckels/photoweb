/*
 * AdminModel.java
 *
 * Created on April 15, 2002, 9:11 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.*;
import com.stampysoft.util.SystemException;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author josh
 */
public class AdminModel
{

    private Category _currentCategory;
    private List<CategoryListener> _categoryListeners = new ArrayList<CategoryListener>();

    private Photo[] _currentPhotos = new Photo[0];
    private List<PhotoListener> _photoListeners = new ArrayList<PhotoListener>();

    private List<PhotographerListener> _photographerListeners = new ArrayList<PhotographerListener>();

    /**
     * Creates a new instance of AdminModel
     */
    private AdminModel()
    {
    }

    public void addCategoryListener(CategoryListener cl)
    {
        List<CategoryListener> newListeners = new ArrayList<CategoryListener>(_categoryListeners);
        newListeners.add(cl);
        _categoryListeners = newListeners;
    }

    public void fireCategoryChanged(Category category)
    {
        _currentCategory = category;
        for (CategoryListener listener : _categoryListeners)
        {
            listener.categoryChanged(_currentCategory);
        }
    }

    public void addPhotographerListener(PhotographerListener pl)
    {
        List<PhotographerListener> newListeners = new ArrayList<PhotographerListener>(_photographerListeners);
        newListeners.add(pl);
        _photographerListeners = newListeners;
    }

    public void firePhotographersChanged()
    {
        for (PhotographerListener listener : _photographerListeners)
        {
            listener.photographerListChanged();
        }
    }

    public void addPhotoListener(PhotoListener pl)
    {
        List<PhotoListener> newListeners = new ArrayList<PhotoListener>(_photoListeners);
        newListeners.add(pl);
        _photoListeners = newListeners;
    }

    public void fireSelectedPhotosChanged(Photo[] photos)
    {
        Photo[] oldPhotos = _currentPhotos;
        _currentPhotos = photos;
        for (PhotoListener listener : _photoListeners)
        {
            listener.selectedPhotosChanged(_currentPhotos, oldPhotos);
        }
    }

    public void fireRequestNextPhotoSelection()
    {
        for (PhotoListener listener : _photoListeners)
        {
            listener.requestNextPhotoSelection();
        }
    }

    public void fireRequestPreviousPhotoSelection()
    {
        for (PhotoListener listener : _photoListeners)
        {
            listener.requestPreviousPhotoSelection();
        }
    }

    public void fireRequestAddCategory(Category category)
    {
        for (CategoryListener listener : _categoryListeners)
        {
            listener.requestAddCategory(category);
        }
    }

    public void firePhotoChanged(Photo photo, boolean categoriesChanged)
    {
        for (PhotoListener listener : _photoListeners)
        {
            listener.photoChanged(photo, categoriesChanged);
        }
    }

    public void firePhotoListChanged()
    {
        fireSelectedPhotosChanged(new Photo[0]);
        for (PhotoListener listener : _photoListeners)
        {
            listener.photoListChanged();
        }
    }

    private static final AdminModel g_model = new AdminModel();

    public static AdminModel getModel()
    {
        return g_model;
    }

    public Category getCurrentCategory()
    {
        return _currentCategory;
    }

    public void savePhoto(Photo photo, boolean categoriesChanged) throws SystemException, PhotoNotFoundException
    {
        PhotoOperations.getPhotoOperations().savePhoto(photo);
        firePhotoChanged(photo, categoriesChanged);
    }

    public void saveCategory(Category category)
    {
        PhotoOperations.getPhotoOperations().saveCategory(category);
    }

    public void deleteCategory(Category category)
        throws SystemException, CategoryNotFoundException
    {
        PhotoOperations.getPhotoOperations().deleteCategory(category);
    }

    public void deleteCurrentPhotos()
    {
        for (Photo photo : _currentPhotos)
        {
            PhotoOperations.getPhotoOperations().deletePhoto(photo);
        }
        _currentPhotos = new Photo[0];
    }

    public void scanForNewPhotos() throws SystemException, PhotoManipulationException
    {
        final JDialog dialog = new JDialog(AdminFrame.getFrame(), "Scanning...", true);
        JPanel panel = new JPanel(new BorderLayout());
        final JProgressBar progressBar = new JProgressBar();
        final JLabel taskNameLabel = new JLabel("Unknown task");
        progressBar.setPreferredSize(new Dimension(200, 20));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(taskNameLabel, BorderLayout.NORTH);
        dialog.setContentPane(panel);
        dialog.pack();

        PhotoAdderThread t = new PhotoAdderThread(dialog, progressBar, taskNameLabel);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
        dialog.setVisible(true);

        if (t.getPhotosAdded() > 0)
        {
            firePhotoListChanged();
            JOptionPane.showMessageDialog(AdminFrame.getFrame(), "Found " + t.getPhotosAdded() + " new photo(s)");
        }
    }

    public Photo[] getCurrentPhotos()
    {
        return _currentPhotos;
    }
}
