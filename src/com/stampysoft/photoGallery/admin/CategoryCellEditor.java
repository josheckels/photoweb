/*
 * CategoryCellEditor.java
 *
 * Created on April 16, 2002, 10:56 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * @author josh
 */
public class CategoryCellEditor extends DefaultTreeCellEditor
{

    private Category _category;

    /**
     * Creates a new instance of CategoryCellEditor
     */
    public CategoryCellEditor(JTree tree, DefaultTreeCellRenderer renderer)
    {
        super(tree, renderer);
    }

    public Object getCellEditorValue()
    {
        String description = (String) super.getCellEditorValue();
        _category.setDescription(description);
        return _category;
    }

    public Component getTreeCellEditorComponent(JTree jTree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row)
    {
        Component result = super.getTreeCellEditorComponent(jTree, value, isSelected, expanded, leaf, row);
        _category = ((CategoryTreeNode) value).getCategory();
        return result;
    }
	
}
