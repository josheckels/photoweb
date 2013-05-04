/*
 * CategoryCellRenderer.java
 *
 * Created on April 16, 2002, 10:28 PM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

/**
 * @author josh
 */
public class CategoryCellRenderer extends DefaultTreeCellRenderer
{

    public java.awt.Component getTreeCellRendererComponent(
        JTree tree, Object value, boolean selected, boolean expanded,
        boolean leaf, int row, boolean hasFocus)
    {

        JLabel label = (JLabel) super.getTreeCellRendererComponent(tree, value,
            selected, expanded, leaf, row, hasFocus);
        Category category = ((CategoryTreeNode) value).getCategory();
        if (category != null && category.getDefaultPhoto() == null)
        {
            label.setForeground(Color.RED);
        }
        label.setIcon(null);
        return label;
    }

}
