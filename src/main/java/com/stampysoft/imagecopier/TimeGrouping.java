package com.stampysoft.imagecopier;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TimeGrouping
{

    private final String _description;
    private final Date _endTime;
    private final List<File> _files;
    private final boolean _selected;

    private final List<ThumbnailInfoPanel> _thumbnails = new ArrayList<ThumbnailInfoPanel>();

    public TimeGrouping(String description, long endTime, List<File> files, boolean selected)
    {
        _description = description;
        _endTime = new Date(endTime);
        _files = files;
        _selected = selected;
    }

    public List<File> getFiles()
    {
        return _files;
    }

    public String toString()
    {
        return _description;
    }

    public int getCount()
    {
        return _files.size();
    }

    public boolean isSelected()
    {
        return _selected;
    }
}
