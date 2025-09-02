/*
 * CategoryTreeNode.java
 *
 * Created on April 14, 2002, 4:38 PM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.CategoryNotFoundException;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.util.IteratorBackedEnumeration;
import com.stampysoft.util.SystemException;
import org.hibernate.Hibernate;
import org.hibernate.LockMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;

/**
 * @author josh
 */
//@Component
public class CategoryTreeNode implements MutableTreeNode
{

    private CategoryTreeNode _parent;
    private Category _category;
    private List<CategoryTreeNode> _children;

    public CategoryTreeNode(CategoryTreeNode parent, Category category)
    {
        _parent = parent;
        _category = category;
    }

    @Transactional(readOnly = true)
    protected synchronized List<CategoryTreeNode> loadChildren()
    {
        if (_children == null)
        {
            Collection<Category> categories;
            if (_category == null)
            {
                categories = AdminFrame.getFrame().getPhotoOperations().getRootCategories(true);
            }
            else
            {
                _category = AdminFrame.getFrame().getPhotoOperations().merge(_category);
                Hibernate.initialize(_category.getChildCategories());
                categories = _category.getChildCategories();
            }
            _children = new ArrayList<>();
            if (categories != null)
            {
                for (Category category : categories)
                {
                    _children.add(new CategoryTreeNode(this, category));
                }
            }
        }
        return _children;
    }

    public Enumeration children()
    {
        loadChildren();
        return new IteratorBackedEnumeration(_children.iterator());
    }

    public boolean getAllowsChildren()
    {
        return true;
    }

    public TreeNode getChildAt(int index)
    {
        return (TreeNode) loadChildren().get(index);
    }

    public int getChildCount()
    {
        return loadChildren().size();
    }

    public int getIndex(TreeNode treeNode)
    {
        return loadChildren().indexOf(treeNode);
    }

    public TreeNode getParent()
    {
        return _parent;
    }

    public boolean isLeaf()
    {
        return loadChildren().isEmpty();
    }

    public String toString()
    {
        if (_category == null)
        {
            return "<ROOT>";
        }
        return _category.getDescription();
    }

    public Category getCategory()
    {
        return _category;
    }

    public void insert(MutableTreeNode mutableTreeNode, int param)
    {
        throw new UnsupportedOperationException();
    }

    public void remove(MutableTreeNode mutableTreeNode)
    {
        _children.remove(mutableTreeNode);
    }

    public void remove(int index)
    {
        throw new UnsupportedOperationException();
    }

    public void removeFromParent()
    {
        throw new UnsupportedOperationException();
    }

    public void setPrivate(boolean b)
    {
        _category.setPrivate(b);
        AdminModel.getModel().saveCategory(_category);
    }

    public void delete()
    {
        _parent.remove(this);
        try
        {
            AdminModel.getModel().deleteCategory(_category);
        }
        catch (SystemException e)
        {
            e.printStackTrace();
        }
        catch (CategoryNotFoundException e)
        {
            e.printStackTrace();
        }
    }

    public void add(CategoryTreeNode treeNode)
    {
        _children.add(treeNode);
    }

    public void setParent(MutableTreeNode mutableTreeNode)
    {
        throw new UnsupportedOperationException();
    }

    public void setUserObject(Object obj)
    {
        _category = (Category) obj;
        AdminModel.getModel().saveCategory(_category);
    }

}