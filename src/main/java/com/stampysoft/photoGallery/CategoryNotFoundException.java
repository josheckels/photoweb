/*
 * CategoryNotFoundException.java
 *
 * Created on April 1, 2002, 8:07 PM
 */

package com.stampysoft.photoGallery;

/**
 * @author josh
 */
public class CategoryNotFoundException extends java.lang.Exception
{

    public CategoryNotFoundException(Long categoryId)
    {
        super("Could not find Category with category_id = " + categoryId);
    }

    public CategoryNotFoundException(String message)
    {
        super(message);
    }

    public CategoryNotFoundException(Throwable t)
    {
        super(t);
    }
}
