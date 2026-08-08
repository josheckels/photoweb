package com.stampysoft.photoGallery.admin;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

/**
 * User: Josh
* Date: Oct 5, 2008
*/
class NextPreviousKeyListener extends KeyAdapter
{
    private final PhotoInfoPanel _photoInfoPanel;

    public NextPreviousKeyListener(PhotoInfoPanel photoInfoPanel)
    {
        _photoInfoPanel = photoInfoPanel;
    }

    public void keyPressed(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_F4)
        {
            // Accepting the amber matches is part of saving here: F4 is how a run of photos gets worked through, and
            // the proposals are on the preview the user has just looked at, so leaving them for a separate click
            // would mean going back over the same photos a second time. Anything wrong gets rejected on the overlay
            // before moving on, exactly as it would before pressing the Accept button.
            _photoInfoPanel.saveAndAcceptProposedFaces();
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
