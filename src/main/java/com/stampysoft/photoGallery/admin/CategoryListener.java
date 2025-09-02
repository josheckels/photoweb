/*
 * CategoryListener.java
 *
 * Created on April 15, 2002, 9:12 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;

/**
 * @author josh
 */
public interface CategoryListener
{

    public void categoryChanged(Category category);

    public void requestAddCategory(Category category);

}
