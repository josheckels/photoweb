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
import org.hibernate.Hibernate;
import org.hibernate.annotations.BatchSize;

import java.util.*;

/**
 * @author josh
 */
@Entity
@Table(name = "category")
// Every category drags in its parent and default photo eagerly, so without this loading the whole table issued a
// select per category - about 1,450 of them, several times per face named in the admin UI.
@BatchSize(size = 100)
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

    /**
     * Only meaningful on a person's category (a descendant of {@code PeopleCategoryId}): this person has asked
     * not to appear publicly, so every photo tagged with them is withheld from anonymous visitors. It hides
     * photos, never the category itself - see {@link Visibility}.
     */
    @Column(name = "public_opt_out")
    private boolean _optOut;

    /**
     * The secret in this category's share link, or null if it isn't shared. One per category: regenerating it
     * is what revokes a link that has been sent out too widely.
     */
    @Column(name = "share_token", length = 43)
    private String _shareToken;

    /**
     * Who is asking, for the duration of the request being serialized. Defaults to {@link Visibility#NONE} so
     * that a path which forgets to set it renders empty rather than unfiltered.
     */
    @Transient
    private Visibility _visibility = Visibility.NONE;

    public Category()
    {
    }

    @JsonSerialize
    public Integer getParentCategoryId()
    {
        return getParentCategory() == null ? null : getParentCategory().getCategoryId();
    }

    /**
     * The categories above this one, root first. Mutable, because {@link #compareTo} appends to it.
     * <p>
     * Walks the parent chain already in memory: {@code parentCategory} is an eager {@code @ManyToOne}, so loading
     * a category loads its ancestors along with it and there is nothing here to ask the database for. It used to
     * call {@code PhotoOperations.merge(this)} per level, which was both a transaction per level and a write -
     * merge copies the whole detached copy onto the row and flushes it at commit, so this method, reached from
     * {@link #toString()} and {@link #compareTo}, silently reverted whatever had changed on the row since that
     * copy was loaded. Every {@code TreeSet<Category>} in the admin pays for this through {@code compareTo}, and
     * a category's cover photo was what most visibly got reverted.
     * <p>
     * Reading in-memory state means a copy that predates somebody reparenting a category shows the old path. The
     * chain is only ever used for display and ordering, so that isn't worth a query per comparison.
     */
    public List<Category> getPathToRoot()
    {
        List<Category> result = new ArrayList<>();
        if (categoryId == null)
        {
            return result;
        }
        for (Category ancestor = parentOf(this); ancestor != null; ancestor = parentOf(ancestor))
        {
            result.add(0, ancestor);
        }
        return result;
    }

    /**
     * The given category's parent, or null if it's a root. Goes to the database only for the case the in-memory
     * copy can't answer - a detached instance whose association was never initialized - and never through
     * {@code merge}, so this stays a read.
     */
    private static Category parentOf(Category category)
    {
        if (Hibernate.isInitialized(category.parentCategory))
        {
            return category.parentCategory;
        }
        return AdminFrame.getFrame().getPhotoOperations().getParentCategory(category);
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

    @JsonProperty("photos")
    public Set<Photo> getVisiblePhotos()
    {
        Set<Photo> result = getPhotos(_visibility);
        for (Photo photo : result)
        {
            // So that each photo's own tags are filtered by the same rule when it is serialized in turn.
            // The owner needs this as much as anyone: without it their photos would serialize no tags at all.
            photo.setVisibility(_visibility);
        }
        return result;
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

    /**
     * The cover photo as stored, whether or not the current visitor is allowed to see it. This is what the admin
     * UI wants; JSON goes through {@link #getVisibleDefaultPhoto()} instead.
     */
    public Photo getDefaultPhoto()
    {
        return defaultPhoto;
    }

    /**
     * The cover photo, or null when the visitor can't see it - a category whose cover happens to be a photo of
     * somebody who opted out renders without one rather than disappearing.
     */
    @JsonGetter("defaultPhoto")
    public Photo getVisibleDefaultPhoto()
    {
        if (!_visibility.canSee(defaultPhoto))
        {
            return null;
        }
        defaultPhoto.setVisibility(_visibility);
        return defaultPhoto;
    }

    public void setDefaultPhoto(Photo defaultPhoto)
    {
        this.defaultPhoto = defaultPhoto;
    }

    public Set<Photo> getPhotos(Visibility visibility)
    {
        if (visibility.isOwner())
        {
            return photos;
        }
        Set<Photo> result = new LinkedHashSet<>();
        for (Photo photo : photos)
        {
            if (visibility.canSee(photo))
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

    /**
     * The child categories the visitor is allowed to know about. This used to list every child regardless of the
     * private flag, which meant a private sub-category's name and cover photo were served to anyone who asked for
     * its parent.
     */
    @JsonGetter("subcategories")
    public List<Map<String, Object>> getCategoriesNonRecursive()
    {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category category : getChildCategories()) {
            if (_visibility.canSee(category))
            {
                category.setVisibility(_visibility);
                result.add(category.toNonRecursiveMap());
            }
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
        result.put("defaultPhoto", getVisibleDefaultPhoto());
        return result;
    }

    public boolean isOptOut()
    {
        return _optOut;
    }

    public void setOptOut(boolean optOut)
    {
        _optOut = optOut;
    }

    public String getShareToken()
    {
        return _shareToken;
    }

    public void setShareToken(String shareToken)
    {
        _shareToken = shareToken;
    }

    public Visibility getVisibility() {
        return _visibility;
    }

    public void setVisibility(Visibility visibility) {
        _visibility = visibility == null ? Visibility.NONE : visibility;
    }
}