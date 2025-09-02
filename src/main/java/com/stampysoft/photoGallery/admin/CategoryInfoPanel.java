/*
 * CategoryInfoPanel.java
 *
 * Created on April 15, 2002, 9:18 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;

import javax.swing.*;
import java.awt.*;

/**
 * @author josh
 */
public class CategoryInfoPanel extends JPanel
{

    private JTextField _descriptionTextField = new JTextField(30);

    public CategoryInfoPanel()
    {
        addComponents();
        addListeners();
    }

    private void addComponents()
    {
        setLayout(new BorderLayout());
        add(_descriptionTextField, BorderLayout.NORTH);
    }

    public void addListeners()
    {
        AdminModel.getModel().addCategoryListener(new CategoryListener()
        {
            public void categoryChanged(Category category)
            {
                if (category != null)
                {
                    _descriptionTextField.setText(category.getDescription());
                }
                else
                {
                    _descriptionTextField.setText("");
                }
            }

            public void requestAddCategory(Category category)
            {
            }
        });
    }

}
