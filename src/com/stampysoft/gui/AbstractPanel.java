/*
 * AbstractPanel.java
 *
 * Created on April 15, 2002, 9:42 AM
 */

package com.stampysoft.gui;

import javax.swing.*;
import java.awt.*;

/**
 * @author josh
 */
public class AbstractPanel extends JPanel
{

    public AbstractPanel()
    {
        super();
    }

    public AbstractPanel(LayoutManager layout)
    {
        super(layout);
    }

    protected void handleException(Throwable t)
    {
        t.printStackTrace();
    }
}
