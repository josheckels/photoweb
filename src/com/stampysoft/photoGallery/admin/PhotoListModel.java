/*
 * PhotoListModel.java
 *
 * Created on April 15, 2002, 9:44 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.util.SystemException;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author josh
 */
public class PhotoListModel extends AbstractListModel
{

    private List<Photo> _photos = new ArrayList<Photo>();

    public PhotoListModel()
    {
        AdminModel.getModel().addPhotoListener(new PhotoListener()
        {
            public void selectedPhotosChanged(Photo[] newPhotos, Photo[] oldPhotos)
            {
                for (Photo newPhoto : newPhotos)
                {
                    int index = _photos.indexOf(newPhoto);
                    if (index >= 0)
                    {
                        _photos.set(index, newPhoto);
                        fireContentsChanged(this, index, index);
                    }
                }
            }

            public void photoChanged(Photo photo, boolean categoriesChanged)
            {
                for (int i = 0; i < getSize(); i++)
                {
                    if (getElementAt(i).equals(photo))
                    {
                        _photos.set(i, photo);
                        fireContentsChanged(PhotoListModel.this, i, i);
                    }
                }
            }

            public void photoListChanged()
            {
                try
                {
                    reload();
                }
                catch (SystemException e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    public void reload() throws SystemException
    {
        int oldSize = _photos.size();
        if (oldSize > 0)
        {
            _photos.clear();
            fireIntervalRemoved(this, 0, oldSize - 1);
        }
        _photos.addAll(PhotoOperations.getPhotoOperations().getAllPhotos());
        fireIntervalAdded(this, 0, _photos.size());
    }

    public Photo getElementAt(int index)
    {
        return _photos.get(index);
    }

    public int getSize()
    {
        return _photos.size();
    }
}