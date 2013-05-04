/*
 * PhotoCategory.java
 *
 * Created on February 10, 2002, 4:45 PM
 */

package com.stampysoft.photoGallery;

import com.stampysoft.photoGallery.common.BaseCategory;

import java.util.*;

/**
 * @author josh
 */
public class Category extends BaseCategory implements Comparable<Category>
{
    protected Category _parentCategory;
    private Set<Photo> _photos;
    private Photo _defaultPhoto;
    private Set<Category> _childCategories;

    public Category()
    {
    }

    public Long getParentCategoryId()
    {
        return getParentCategory().getCategoryId();
    }

    public List<Category> getPathToRoot()
    {
        Category mergedThis = PhotoOperations.getPhotoOperations().merge(this);
        Category parentCategory = mergedThis.getParentCategory();
        if (parentCategory == null)
        {
            return new ArrayList<Category>();
        }
        List<Category> result = parentCategory.getPathToRoot();
        result.add(parentCategory);
        return result;
    }

    public Category getParentCategory()
    {
        return _parentCategory;
    }

    public void setParentCategory(Category parentCategory)
    {
        _parentCategory = parentCategory;
    }


    public void setPhotos(Set<Photo> photos)
    {
        _photos = photos;
    }

    public Set<Photo> getPhotos()
    {
        return getPhotos(true);
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        List<Category> pathToRoot = getPathToRoot();
        for (Category parent : pathToRoot)
        {
            sb.append(parent.getDescription());
            sb.append(": ");
        }
        sb.append(getDescription());
        return sb.toString();
    }

    public int compareTo(Category category)
    {
        List<Category> thisPath = getPathToRoot();
        thisPath.add(this);
        Iterator path1 = thisPath.iterator();
        List<Category> otherPath = category.getPathToRoot();
        otherPath.add(category);
        Iterator path2 = otherPath.iterator();
        while (path1.hasNext() && path2.hasNext())
        {
            Category c1 = (Category) path1.next();
            Category c2 = (Category) path2.next();
            int value = c1.getDescription().compareTo(c2.getDescription());
            if (value != 0)
            {
                return value;
            }
        }
        if (path2.hasNext())
        {
            return -1;
        }
        if (path1.hasNext())
        {
            return 1;
        }
        return 0;
    }

    public Photo getDefaultPhoto()
    {
        return _defaultPhoto;
    }

    public void setDefaultPhoto(Photo defaultPhoto)
    {
        _defaultPhoto = defaultPhoto;
    }

    public Set<Photo> getPhotos(boolean includePrivate)
    {
        if (includePrivate)
        {
            return _photos;
        }
        Set<Photo> result = new LinkedHashSet<Photo>();
        for (Photo photo : _photos)
        {
            if (!photo.isPrivate())
            {
                result.add(photo);
            }
        }
        return result;
    }

    public void setChildCategories(Set<Category> childCategories)
    {
        _childCategories = childCategories;
    }

    public Set<Category> getChildCategories()
    {
        return _childCategories;
    }
}