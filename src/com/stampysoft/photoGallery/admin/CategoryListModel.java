/*
 * CategoryListModel.java
 *
 * Created on April 15, 2002, 10:36 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;

import javax.swing.*;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author josh
 */
public class CategoryListModel extends AbstractListModel<Category>
{
    private boolean _changed;
    private Set<Category> _categories = new TreeSet<>();
    private Category[] _array = new Category[0];

    public CategoryListModel()
    {
    }

    public void addCategory(Category category)
    {
        TreeSet<Category> newCategories = new TreeSet<>(_categories);
        newCategories.add(category);
        setCategories(newCategories);
        _changed = true;
    }

    public void removeCategory(Category category)
    {
        TreeSet<Category> newCategories = new TreeSet<>(_categories);
        newCategories.remove(category);
        setCategories(newCategories);
        _changed = true;
    }

    public void setCategories(Set<Category> treeSet)
    {
        int oldSize = _array.length;
        if (oldSize > 0)
        {
            _array = new Category[0];
            fireIntervalRemoved(this, 0, oldSize - 1);
        }
        _categories = treeSet;
        _array = _categories.toArray(new Category[_categories.size()]);
        if (_array.length > 0)
        {
            fireIntervalAdded(this, 0, _array.length - 1);
        }
        _changed = false;
    }

    public Category getElementAt(int index)
    {
        return _array[index];
    }

    public int getSize()
    {
        return _array.length;
    }

    public Set<Category> getCategories()
    {
        return _categories;
    }

    public boolean isChanged()
    {
        return _changed;
    }
}
