package com.stampysoft.photoGallery.common;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import jakarta.persistence.*;

import java.sql.Date;

/**
 * User: Josh
 * Date: Sep 30, 2007
 */
@MappedSuperclass
public abstract class BaseCategory //implements IsSerializable
{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "category_id")
    protected Integer categoryId;
    @Column
    protected String description;
    @Column (name = "created_on")
    protected Date createdOn = new Date(System.currentTimeMillis());
    @Column (name = "private")
    protected boolean _private;

    @JsonGetter("id")
    public Integer getCategoryId()
    {
        return categoryId;
    }

    @JsonSerialize
    public String getDescription()
    {
        return description;
    }

    @JsonSerialize
    public Date getCreatedOn()
    {
        return createdOn;
    }

    public void setCategoryId(Integer id)
    {
        categoryId = id;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public void setCreatedOn(Date d)
    {
        createdOn = d;
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
        return (categoryId != null ? categoryId.hashCode() : 0);
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
