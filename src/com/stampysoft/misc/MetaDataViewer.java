package com.stampysoft.misc;

import com.drew.imaging.jpeg.JpegMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;

import java.io.File;
import java.util.Iterator;

public class MetaDataViewer
{

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        File file = new File("C:\\Documents and Settings\\josh\\Desktop\\Pictures\\Pro\\02_Ceremony", "SteveSmith0154.jpg");
        if (file.exists())
        {
            Metadata metadata = JpegMetadataReader.readMetadata(file);


            Iterator<Directory> directories = metadata.getDirectoryIterator();
            while (directories.hasNext())
            {
                Directory directory = directories.next();
                Iterator<Tag> tags = directory.getTagIterator();
                while (tags.hasNext())
                {
                    Tag tag = tags.next();
                    System.out.println(tag.getDirectoryName() + " - " + tag.getTagName() + " - " + tag.getDescription());
                }
            }
        }
    }

}
