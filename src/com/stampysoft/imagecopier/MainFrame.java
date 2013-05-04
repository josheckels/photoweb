package com.stampysoft.imagecopier;

import com.stampysoft.photoGallery.ResolutionUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainFrame extends JFrame
{

    public static final int THUMBNAIL_SIZE = 125;

    public MainFrame() throws InterruptedException
    {
        super("Image Copier");

        setSize(500, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        File rootDir = new File("c:/documents and settings/josh/desktop/pictures to backup");
        List<File> jpegFiles = new ArrayList<File>();

        processDirectory(rootDir, jpegFiles);

        // Sort all images by date, most recent first
        Collections.sort(jpegFiles, new Comparator<File>()
        {
            public int compare(File f1, File f2)
            {
                long l1 = f1.lastModified();
                long l2 = f2.lastModified();
                if (l1 > l2)
                {
                    return -1;
                }
                if (l1 < l2)
                {
                    return 1;
                }
                return 0;
            }
        });

        long cutoff = jpegFiles.size() > 0 ? jpegFiles.get(0).lastModified() : Long.MAX_VALUE;
        cutoff -= 24 * 60 * 60 * 1000;
        int index = 0;
        TimeGrouping group1 = getGrouping("Past 24 hours", jpegFiles, index, cutoff, true);
        index += group1.getCount();
        cutoff -= 24 * 60 * 60 * 1000;
        TimeGrouping group2 = getGrouping("Past 48 hours", jpegFiles, index, cutoff, true);
        cutoff -= 5.5 * 24 * 60 * 60 * 1000;
        index += group2.getCount();
        TimeGrouping group3 = getGrouping("Past week", jpegFiles, index, cutoff, false);

        JPanel photographerInfoPanel = new JPanel(new GridBagLayout());
        JTextField photographerNameTextField = new JTextField(20);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.ipadx = 10;
        photographerInfoPanel.add(new JLabel("Your name:"));
        photographerInfoPanel.add(photographerNameTextField);

        JPanel contentPane = new JPanel(new BorderLayout());
        JLabel label = new JLabel("Found " + jpegFiles.size() + " files.");
        contentPane.add(label, BorderLayout.SOUTH);
        contentPane.add(photographerInfoPanel, BorderLayout.NORTH);


        JPanel photoPanel = new JPanel(new GridLayout(3, 1));
        TimeGroupingPanel panel3 = new TimeGroupingPanel(group3);
        TimeGroupingPanel panel2 = new TimeGroupingPanel(group2, panel3);
        TimeGroupingPanel panel1 = new TimeGroupingPanel(group1, panel2);

        photoPanel.add(panel1);
        photoPanel.add(panel2);
        photoPanel.add(panel3);

        panel1.startGeneratingThumbnails();

        contentPane.add(photoPanel, BorderLayout.CENTER);
        setContentPane(contentPane);
    }

    private TimeGrouping getGrouping(String description, List<File> allFiles, int index, long fileDateCutoff, boolean selected)
    {
        List<File> matchingFiles = new ArrayList<File>();
        while (index < allFiles.size() && allFiles.get(index).lastModified() >= fileDateCutoff)
        {
            matchingFiles.add(allFiles.get(index));
            index++;
        }

        return new TimeGrouping(description, fileDateCutoff, matchingFiles, selected);
    }

    private void processDirectory(File dir, List<File> jpegFiles)
    {
        if (dir.exists())
        {
            File[] files = dir.listFiles();
            for (File f : files)
            {
                if (f.isDirectory())
                {
                    processDirectory(f, jpegFiles);
                }
                else if (f.getName().toLowerCase().endsWith(".jpg"))
                {
                    jpegFiles.add(f);
                }
            }
        }
    }

    public static void main(String... args) throws InterruptedException
    {
        ResolutionUtil.init();
        MainFrame frame = new MainFrame();
        frame.setVisible(true);
    }

}
