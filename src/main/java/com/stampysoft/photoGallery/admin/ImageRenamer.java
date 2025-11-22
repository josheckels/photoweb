/*
 * ImageRenamer.java
 *
 * Created on August 11, 2002, 11:45 AM
 */

package com.stampysoft.photoGallery.admin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @author josh
 */
public class ImageRenamer extends JDialog
{

    private final JTextField _directoryTextField = new JTextField(40);
    private final JTextField _prefixTextField = new JTextField(20);
    private final JTextField _startingIndexTextField = new JTextField("1", 3);

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
        browseButton.addActionListener(e -> {
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
        renameButton.addActionListener(e -> {
            File directory = new File(_directoryTextField.getText());
            File[] images = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jpg"));

            Arrays.sort(images);

            int fileIndex = Integer.parseInt(_startingIndexTextField.getText());

            int desiredDigitCount = (int) Math.floor(Math.log(images.length) / Math.log(10)) + 1;

            Set<String> goodRawFileNames = new HashSet<>();

            for (File image : images) {
                String name = _prefixTextField.getText();
                int digitCount = (int) Math.floor(Math.log(fileIndex) / Math.log(10)) + 1;
                while (digitCount < desiredDigitCount) {
                    name = name + "0";
                    digitCount++;
                }
                final String baseName = name + (fileIndex++);
                name = baseName + ".jpg";
                File destFile = new File(image.getParent(), name);
                image.renameTo(destFile);
                final String originalBaseName = image.getName().substring(0, image.getName().length() - ".jpg".length());
                File[] otherFiles = image.getParentFile().listFiles((dir, name1) -> name1.startsWith(originalBaseName + ".") && !name1.toLowerCase().endsWith(".jpg"));
                for (File otherFile : otherFiles) {
                    File newOtherFile = new File(image.getParentFile(), baseName + "." + otherFile.getName().substring(otherFile.getName().lastIndexOf(".") + 1));
                    if (newOtherFile.getName().toLowerCase().endsWith(".cr2")) {
                        goodRawFileNames.add(newOtherFile.getName());
                    }
                    otherFile.renameTo(newOtherFile);
                }
            }

            File[] rawFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".cr2"));
            for (File rawFile : rawFiles)
            {
                if (!goodRawFileNames.contains(rawFile.getName()))
                {
                    rawFile.delete();
                }
            }
        });
        gbc.gridwidth = GridBagConstraints.RELATIVE;
        contentPane.add(renameButton, gbc);

        pack();
    }
}
