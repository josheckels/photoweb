package com.stampysoft.misc;

import java.io.File;

public class WeddingPhotoRenamer
{

    public static void main(String... args) throws Exception
    {
        File dir = new File("C:/Documents and Settings/josh/Desktop/Pictures/Pro/05_Reception");
        File[] files = dir.listFiles();
        for (File file : files)
        {
            File dest = new File(dir, "SteveSmith" + file.getName().toLowerCase());
            file.renameTo(dest);
        }
    }

}
