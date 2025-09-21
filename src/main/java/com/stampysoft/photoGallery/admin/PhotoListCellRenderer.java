/*
 * PhotoListCellRenderer.java
 *
 * Created on April 16, 2002, 10:43 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Photo;

import javax.swing.*;
import java.awt.*;


/**
 * @author josh
 */
public class PhotoListCellRenderer extends DefaultListCellRenderer
{

    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean hasCellFocus)
    {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, hasCellFocus);
        Photo photo = (Photo) value;
        String text = photo.getFilename();
        if (text.indexOf('.') != -1)
        {
            text = text.substring(0, text.lastIndexOf('.'));
        }
        label.setText(text);
        if (photo.getPhotographer() == null)
        {
            label.setFont(label.getFont().deriveFont(Font.ITALIC));
        }
        else
        {
            label.setFont(label.getFont().deriveFont(Font.PLAIN));
        }
        boolean hasCategory = AdminFrame.getFrame().getPhotoOperations().photoHasAnyCategory(photo);
        if (!hasCategory)
        {
            label.setForeground(Color.red);
        }
        else
        {
            label.setForeground(Color.black);
        }
        return label;
    }

}
