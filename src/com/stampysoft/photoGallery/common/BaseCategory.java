package com.stampysoft.photoGallery.common;

import java.util.Date;

/**
 * User: Josh
 * Date: Sep 30, 2007
 */
public abstract class BaseCategory //implements IsSerializable
{

    protected Long _categoryId;
    protected Long _defaultPhotoId = null;
    protected String _description;
    protected Date _createdOn = new Date();
    protected boolean _private;

    public Long getCategoryId()
    {
        return _categoryId;
    }

    public String getDescription()
    {
        return _description;
    }

    public Date getCreatedOn()
    {
        return _createdOn;
    }

    public void setCategoryId(Long id)
    {
        _categoryId = id;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public void setCreatedOn(Date d)
    {
        _createdOn = d;
    }

    public boolean equals(Object o)
    {
        if (!(o instanceof BaseCategory))
        {
            return false;
        }

        BaseCategory c = (BaseCategory) o;
        return c.getCategoryId().equals(getCategoryId());
    }

    public int hashCode()
    {
        return (_categoryId != null ? _categoryId.hashCode() : 0);
    }

    public Long getDefaultPhotoId()
    {
        return _defaultPhotoId;
    }

    public void setDefaultPhotoId(Long id)
    {
        _defaultPhotoId = id;
    }

    public boolean isPrivate()
    {
        return _private;
    }

    public void setPrivate(boolean b)
    {
        _private = b;
    }
}
