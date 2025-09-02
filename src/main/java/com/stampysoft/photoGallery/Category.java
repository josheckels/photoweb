/*
 * PhotoCategory.java
 *
 * Created on February 10, 2002, 4:45 PM
 */

package com.stampysoft.photoGallery;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.photoGallery.common.BaseCategory;
import jakarta.persistence.*;

import java.util.*;

/**
 * @author josh
 */
@Entity
@Table(name = "category")
@JsonFilter("categoryFilter")
@JsonAutoDetect(fieldVisibility= JsonAutoDetect.Visibility.NONE,
        getterVisibility= JsonAutoDetect.Visibility.NONE, isGetterVisibility= JsonAutoDetect.Visibility.NONE)
public class Category extends BaseCategory implements Comparable<Category>
{
    @ManyToOne @JoinColumn(name = "parent_category_id")
    protected Category parentCategory;
    @ManyToMany(mappedBy = "_categories")
    @OrderBy("filename ASC")
    private Set<Photo> photos;
    @ManyToOne  @JoinColumn(name = "default_photo_id")
    private Photo defaultPhoto;
    @OneToMany(mappedBy = "parentCategory")
    private Set<Category> childCategories;
    @Transient
    private boolean includePrivate;

    public Category()
    {
    }

    @JsonSerialize
    public Integer getParentCategoryId()
    {
        return getParentCategory() == null ? null : getParentCategory().getCategoryId();
    }

    public List<Category> getPathToRoot()
    {
        if (categoryId == null)
        {
            return Collections.emptyList();
        }
        Category mergedThis = AdminFrame.getFrame().getPhotoOperations().merge(this);
        Category parentCategory = mergedThis.getParentCategory();
        if (parentCategory == null)
        {
            return new ArrayList<>();
        }
        List<Category> result = parentCategory.getPathToRoot();
        result.add(parentCategory);
        return result;
    }

    public Category getParentCategory()
    {
        return parentCategory;
    }

    public void setParentCategory(Category parentCategory)
    {
        this.parentCategory = parentCategory;
    }


    public void setPhotos(Set<Photo> photos)
    {
        this.photos = photos;
    }

    @JsonProperty
    public Set<Photo> getPhotos()
    {
        return getPhotos(includePrivate);
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
        Iterator<Category> path1 = thisPath.iterator();
        List<Category> otherPath = category.getPathToRoot();
        otherPath.add(category);
        Iterator<Category> path2 = otherPath.iterator();
        while (path1.hasNext() && path2.hasNext())
        {
            Category c1 = path1.next();
            Category c2 = path2.next();
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

    @JsonGetter
    public Photo getDefaultPhoto()
    {
        return defaultPhoto;
    }

    public void setDefaultPhoto(Photo defaultPhoto)
    {
        this.defaultPhoto = defaultPhoto;
    }

    public Set<Photo> getPhotos(boolean includePrivate)
    {
        if (includePrivate)
        {
            return photos;
        }
        Set<Photo> result = new LinkedHashSet<>();
        for (Photo photo : photos)
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
        this.childCategories = childCategories;
    }

    @JsonGetter("subcategories")
    public List<Map<String, Object>> getCategoriesNonRecursive()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category category : getChildCategories()) {
            result.add(category.toNonRecursiveMap());
        }
        return result;
    }

    public Set<Category> getChildCategories()
    {
        return childCategories;
    }

    public Map<String, Object> toNonRecursiveMap() {
        Map<String, Object> result = new HashMap<>();
        result.put("id", getCategoryId());
        result.put("description", getDescription());
        result.put("defaultPhoto", getDefaultPhoto());
        return result;
    }

    public void setIncludePrivate(boolean includePrivate) {
        this.includePrivate = includePrivate;
    }
}