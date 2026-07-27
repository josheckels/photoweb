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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author josh
 */
public class PhotoListModel extends AbstractListModel<Photo>
{

    /** Every photo in the database, in filename order. */
    private final List<Photo> _allPhotos = new ArrayList<>();
    /** The subset of {@link #_allPhotos} that passes the current filter - what the list actually shows. */
    private final List<Photo> _photos = new ArrayList<>();

    private String _filenameFilter = "";
    /** Non-null when the filename filter uses wildcards, in which case it must match the whole filename. */
    private Pattern _filenamePattern = null;
    private boolean _uncategorizedOnly = false;
    private boolean _lastScanOnly = false;

    /**
     * The ids of the photos that aren't in any category. Cached so that neither the filter nor the cell renderer
     * has to hit the database for each photo.
     */
    private Set<Integer> _uncategorizedPhotoIds = new HashSet<>();

    public PhotoListModel()
    {
        AdminModel.getModel().addPhotoListener(new PhotoListener()
        {
            public void selectedPhotosChanged(List<Photo> newPhotos, List<Photo> oldPhotos)
            {
                for (Photo newPhoto : newPhotos)
                {
                    replacePhoto(newPhoto);
                }
            }

            public void photoChanged(Photo photo, boolean categoriesChanged)
            {
                if (categoriesChanged)
                {
                    refreshUncategorizedStatus(photo);
                }
                replacePhoto(photo);
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
        _allPhotos.clear();
        _allPhotos.addAll(PhotoOperations.getPhotoOperations().getAllPhotos());
        _uncategorizedPhotoIds = PhotoOperations.getPhotoOperations().getUncategorizedPhotoIds();
        refilter();
    }

    /**
     * Narrows the list to the photos worth choosing from.
     *
     * @param filenameFilter matched case-insensitively against the filename, as a substring, or as a whole-name
     * match if it contains the wildcards * or ?. Empty or null matches everything.
     * @param uncategorizedOnly restrict to photos that aren't in any category yet
     * @param lastScanOnly restrict to the photos added by the most recent scan
     */
    public void setFilter(String filenameFilter, boolean uncategorizedOnly, boolean lastScanOnly)
    {
        _filenameFilter = filenameFilter == null ? "" : filenameFilter.trim().toLowerCase();
        _filenamePattern = compileWildcards(_filenameFilter);
        _uncategorizedOnly = uncategorizedOnly;
        _lastScanOnly = lastScanOnly;
        refilter();
    }

    /** Whether the photo is in no category at all, according to the cache refreshed on reload and on each save. */
    public boolean isUncategorized(Photo photo)
    {
        return photo.getPhotoId() != null && _uncategorizedPhotoIds.contains(photo.getPhotoId());
    }

    /** The number of photos in the database, as opposed to {@link #getSize()}, which counts the visible ones. */
    public int getTotalSize()
    {
        return _allPhotos.size();
    }

    private static Pattern compileWildcards(String filter)
    {
        if (filter.indexOf('*') == -1 && filter.indexOf('?') == -1)
        {
            return null;
        }
        StringBuilder regex = new StringBuilder();
        for (char c : filter.toCharArray())
        {
            switch (c)
            {
                case '*' -> regex.append(".*");
                case '?' -> regex.append(".");
                default -> regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString());
    }

    private void refilter()
    {
        // The photos of the last scan are compared by identity of filename, like everywhere else Photo is compared
        Set<Photo> lastScanPhotos = _lastScanOnly ? new HashSet<>(AdminModel.getModel().getLastScanPhotos()) : Collections.emptySet();

        int oldSize = _photos.size();
        _photos.clear();
        if (oldSize > 0)
        {
            fireIntervalRemoved(this, 0, oldSize - 1);
        }

        for (Photo photo : _allPhotos)
        {
            if (matches(photo, lastScanPhotos))
            {
                _photos.add(photo);
            }
        }

        if (!_photos.isEmpty())
        {
            fireIntervalAdded(this, 0, _photos.size() - 1);
        }
    }

    private boolean matches(Photo photo, Set<Photo> lastScanPhotos)
    {
        if (!_filenameFilter.isEmpty())
        {
            String filename = photo.getFilename() == null ? "" : photo.getFilename().toLowerCase();
            boolean matched = _filenamePattern == null ? filename.contains(_filenameFilter) : _filenamePattern.matcher(filename).matches();
            if (!matched)
            {
                return false;
            }
        }
        if (_uncategorizedOnly && !isUncategorized(photo))
        {
            return false;
        }
        return !_lastScanOnly || lastScanPhotos.contains(photo);
    }

    /** Swaps in the given instance of a photo we're already showing, which may have just been merged or saved. */
    private void replacePhoto(Photo photo)
    {
        int allIndex = _allPhotos.indexOf(photo);
        if (allIndex >= 0)
        {
            _allPhotos.set(allIndex, photo);
        }
        int index = _photos.indexOf(photo);
        if (index >= 0)
        {
            _photos.set(index, photo);
            fireContentsChanged(this, index, index);
        }
    }

    /**
     * Re-checks a single photo after its categories changed. The photo stays visible even if it no longer passes
     * the filter, so that categorizing a photo doesn't yank it out from under the user; the next filter change or
     * reload drops it.
     */
    private void refreshUncategorizedStatus(Photo photo)
    {
        if (photo.getPhotoId() == null)
        {
            return;
        }
        if (PhotoOperations.getPhotoOperations().photoHasAnyCategory(photo))
        {
            _uncategorizedPhotoIds.remove(photo.getPhotoId());
        }
        else
        {
            _uncategorizedPhotoIds.add(photo.getPhotoId());
        }
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
