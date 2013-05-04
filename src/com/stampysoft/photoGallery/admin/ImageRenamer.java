/*
 * ImageRenamer.java
 *
 * Created on August 11, 2002, 11:45 AM
 */

package com.stampysoft.photoGallery.admin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author josh
 */
public class ImageRenamer extends JDialog
{

    private JTextField _directoryTextField = new JTextField(40);
    private JTextField _prefixTextField = new JTextField(20);
    private JTextField _startingIndexTextField = new JTextField("1", 3);

    public ImageRenamer(JFrame parent)
    {
        super(parent, "Image Renamer");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        addComponents();
    }

    private void addComponents()
    {
        Container contentPane = getContentPane();

        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        contentPane.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);

        gbc.gridwidth = GridBagConstraints.RELATIVE;
        contentPane.add(new JLabel("Source directory: "), gbc);
        contentPane.add(_directoryTextField);
        JButton browseButton = new JButton("Browse");
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        browseButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.setFileFilter(new javax.swing.filechooser.FileFilter()
                {
                    public boolean accept(File f)
                    {
                        return f.isDirectory();
                    }

                    public String getDescription()
                    {
                        return "Directories";
                    }
                });
                if (chooser.showOpenDialog(ImageRenamer.this) == JFileChooser.APPROVE_OPTION)
                {
                    _directoryTextField.setText(chooser.getSelectedFile().getAbsolutePath());
                }
            }
        });
        contentPane.add(browseButton, gbc);

        gbc.gridwidth = GridBagConstraints.RELATIVE;
        contentPane.add(new JLabel("Prefix"), gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        contentPane.add(_prefixTextField, gbc);

        gbc.gridwidth = GridBagConstraints.RELATIVE;
        contentPane.add(new JLabel("Starting index"), gbc);
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        contentPane.add(_startingIndexTextField, gbc);

        JButton renameButton = new JButton("Rename");
        renameButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                File directory = new File(_directoryTextField.getText());
                File[] images = directory.listFiles(new FilenameFilter()
                {
                    public boolean accept(File dir, String name)
                    {
                        return name.toLowerCase().endsWith(".jpg");
                    }
                });

                Arrays.sort(images);

                int fileIndex = Integer.parseInt(_startingIndexTextField.getText());

                int desiredDigitCount = (int) Math.floor(Math.log(images.length) / Math.log(10)) + 1;

                Set<String> goodRawFileNames = new HashSet<String>();

                for (int i = 0; i < images.length; i++)
                {
                    String name = _prefixTextField.getText();
                    int digitCount = (int) Math.floor(Math.log(fileIndex) / Math.log(10)) + 1;
                    while (digitCount < desiredDigitCount)
                    {
                        name = name + "0";
                        digitCount++;
                    }
                    final String baseName = name + (fileIndex++);
                    name = baseName + ".jpg";
                    File destFile = new File(images[i].getParent(), name);
                    images[i].renameTo(destFile);
                    final String originalBaseName = images[i].getName().substring(0, images[i].getName().length() - ".jpg".length());
                    File[] otherFiles = images[i].getParentFile().listFiles(new FilenameFilter()
                    {
                        public boolean accept(File dir, String name)
                        {
                            return name.startsWith(originalBaseName + ".") && !name.toLowerCase().endsWith(".jpg");
                        }
                    });
                    for (File otherFile : otherFiles)
                    {
                        File newOtherFile = new File(images[i].getParentFile(), baseName + "." + otherFile.getName().substring(otherFile.getName().lastIndexOf(".") + 1));
                        if (newOtherFile.getName().toLowerCase().endsWith(".cr2"))
                        {
                            goodRawFileNames.add(newOtherFile.getName());
                        }
                        otherFile.renameTo(newOtherFile);
                    }
                }

                File[] rawFiles = directory.listFiles(new FilenameFilter()
                {
                    public boolean accept(File dir, String name)
                    {
                        return name.toLowerCase().endsWith(".cr2");
                    }
                });
                for (File rawFile : rawFiles)
                {
                    if (!goodRawFileNames.contains(rawFile.getName()))
                    {
                        rawFile.delete();
                    }
                }
            }
        });
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        contentPane.add(renameButton, gbc);

        pack();
    }
}
