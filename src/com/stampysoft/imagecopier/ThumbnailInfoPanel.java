package com.stampysoft.imagecopier;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ThumbnailInfoPanel extends JPanel
{

    private final File _file;
    private final ImageIcon _icon;
    private final JCheckBox _checkBox;

    public ThumbnailInfoPanel(File file, ImageIcon icon, boolean selected)
    {
        super(new BorderLayout());
        _file = file;
        _icon = icon;

        add(new JLabel(_icon), BorderLayout.CENTER);
        _checkBox = new JCheckBox(_file.getName());
        _checkBox.setSelected(selected);
        add(_checkBox, BorderLayout.SOUTH);
    }

}
