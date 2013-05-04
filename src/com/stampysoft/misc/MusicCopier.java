package com.stampysoft.misc;

import java.io.*;

public class MusicCopier
{

    /**
     * @param args
     */
    public static void main(String[] args) throws Exception
    {
        File playlistFile = new File(args[0]);
        BufferedReader reader = new BufferedReader(new FileReader(playlistFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter("C:\\Music\\" + playlistFile.getName()));
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (!line.startsWith("#"))
            {
                writer.write("\\Music");
                writer.write(line.substring("K:".length()));
                writer.write("\r\n");
                File originalFile = new File(line);
                File targetFile = new File("C:\\Music" + line.substring("K:".length()));
                targetFile.getParentFile().mkdirs();
                System.out.print("Copying " + line + "...");
                if (originalFile.length() != targetFile.length())
                {
                    BufferedInputStream bIn = new BufferedInputStream(new FileInputStream(originalFile));
                    BufferedOutputStream bOut = new BufferedOutputStream(new FileOutputStream(targetFile));
                    byte[] b = new byte[5000000];
                    int i;
                    while ((i = bIn.read(b)) != -1)
                    {
                        bOut.write(b, 0, i);
                    }
                    bIn.close();
                    bOut.close();
                }
                System.out.println("  done.");
            }
            else
            {
                writer.write(line);
                writer.write("\r\n");
            }
        }
        reader.close();
        writer.close();
    }

}
