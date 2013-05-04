package com.stampysoft.photoGallery.admin;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * User: Josh
* Date: Oct 5, 2008
*/
class NextPreviousKeyListener extends KeyAdapter
{
    private PhotoInfoPanel _photoInfoPanel;

    public NextPreviousKeyListener(PhotoInfoPanel photoInfoPanel)
    {
        _photoInfoPanel = photoInfoPanel;
    }

    public void keyPressed(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_F4)
        {
            _photoInfoPanel.saveCurrentPhoto();
            if ((e.getModifiers() & KeyEvent.SHIFT_MASK) != 0)
            {
                AdminModel.getModel().fireRequestPreviousPhotoSelection();
            }
            else
            {
                AdminModel.getModel().fireRequestNextPhotoSelection();
            }
        }
    }
}
