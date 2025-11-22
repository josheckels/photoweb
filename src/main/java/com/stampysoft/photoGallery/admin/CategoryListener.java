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

    void categoryChanged(Category category);

    void requestAddCategory(Category category);
}
