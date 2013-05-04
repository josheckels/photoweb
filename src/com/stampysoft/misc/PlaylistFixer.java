package com.stampysoft.misc;

import java.io.*;

/**
 * User: Josh
 * Date: 10/29/11
 */
public class PlaylistFixer
{
    public static void main(String... args) throws Exception
    {
        if (args.length < 0)
        {
            System.err.println("Expected one argument - the path to the original file");
            System.exit(1);
        }
        File inFile = new File(args[0]);
        if (!inFile.exists())
        {
            System.err.println("No such file: " + args[0]);
            System.exit(1);
        }
        File outFile;
        if (args.length > 1)
        {
            outFile = new File(args[1]);
        }
        else
        {
            outFile = inFile;
        }
        File tempOutput = File.createTempFile("tempPlaylist", "m3u", inFile.getParentFile());
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inFile)));
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(tempOutput));
        String line;
        while ((line = reader.readLine()) != null)
        {
            if (!line.startsWith("#"))
            {
                String outputLine = line.replace("m:", "/music");
                outputLine = outputLine.replace("M:", "/music");
                outputLine = outputLine.replace('\\', '/');
                writer.write(outputLine);
                writer.write("\n");
            }
        }
        reader.close();
        writer.close();
        if (outFile.exists())
        {
            outFile.delete();
        }
        tempOutput.renameTo(outFile);
    }
}
