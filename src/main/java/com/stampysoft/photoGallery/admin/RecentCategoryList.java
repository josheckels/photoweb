/*
 * RecentCategoryList.java
 *
 * Created on July 30, 2004, 7:18 PM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author josh
 */
public class RecentCategoryList extends JList
{

    private RecentCategoryListModel _listModel = new RecentCategoryListModel();

    /**
     * Creates a new instance of RecentCategoryList
     */
    public RecentCategoryList()
    {
        super();
        setModel(_listModel);

        setDragEnabled(true);
        setTransferHandler(new CategorySelection());

        setCellRenderer(new DefaultListCellRenderer()
        {
            public Component getListCellRendererComponent(
                JList list,
                Object value,
                int index,
                boolean isSelected,
                boolean cellHasFocus)
            {
                Category category = (Category) value;
                return super.getListCellRendererComponent(list, category.getDescription(), index, isSelected, cellHasFocus);
            }
        });

        AdminModel.getModel().addPhotoListener(new PhotoListener()
        {
            public void photoChanged(Photo photo, boolean categoriesChanged)
            {
                if (!categoriesChanged)
                {
                    return;
                }

                for (Category category : photo.getCategories(true))
                {
                    if (!_listModel.contains(category))
                    {
                        _listModel.addCategory(category);
                    }
                }
            }
        });

        addMouseListener(new MouseAdapter()
        {
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1)
                {
                    for (Object o : getSelectedValues())
                    {
                        Category category = (Category) o;
                        AdminModel.getModel().fireRequestAddCategory(category);
                    }
                }
            }
        });
    }

    private class RecentCategoryListModel extends DefaultListModel
    {
        public void addCategory(Category category)
        {
            int index = size();
            if (size() > 0 && category.getDescription().compareTo(((Category) get(0)).getDescription()) < 0)
            {
                index = 0;
            }
            else
            {
                for (int i = 0; i < size(); i++)
                {
                    Category c = (Category) get(i);
                    if (category.getDescription().compareTo(c.getDescription()) > 0 &&
                        (i == size() - 1 || category.getDescription().compareTo(((Category) get(i + 1)).getDescription()) < 0))
                    {
                        index = i + 1;
                        break;
                    }
                }
            }

            insertElementAt(category, index);
        }

    }
	
}
