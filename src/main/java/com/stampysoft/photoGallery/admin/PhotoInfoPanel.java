/*
 * PhotoInfoPanel.java
 *
 * Created on April 15, 2002, 9:57 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.gui.AbstractPanel;
import com.stampysoft.photoGallery.*;
import com.stampysoft.photoGallery.common.Resolution;
import com.stampysoft.util.SystemException;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.TreeSet;

/**
 * @author josh
 */
public class PhotoInfoPanel extends AbstractPanel
{

    private final JLabel _photoLabel = new JLabel();
    private final JTextArea _captionTextArea = new JTextArea(2, 80);
    private final JCheckBox _privateCheckBox = new JCheckBox("Private");
    private final CategoryListModel _categoryListModel = new CategoryListModel();
    private final JList<Category> _categoryList = new JList<>(_categoryListModel);
    private final JButton _saveButton = new JButton("Save");
    private final JButton _deleteButton = new JButton("Delete");
    private final JButton _scanForNewPhotosButton = new JButton("Scan for new photos");
    private final JButton _renameButton = new JButton("Rename files");
    private final PhotographerPanel _photographerPanel = new PhotographerPanel();

    private TreeSet<Category> _originalCategories = null;

    public PhotoInfoPanel()
    {
        addComponents();
        addListeners();

        _categoryList.setTransferHandler(new CategorySelection());
        _categoryList.setDragEnabled(true);
    }

    private void addComponents()
    {
        setLayout(new BorderLayout());

        JPanel immutablePanel = new JPanel(new BorderLayout());

		_photoLabel.setMinimumSize( new Dimension( Photo.DEFAULT_MAX_DIMENSION, Photo.DEFAULT_MAX_DIMENSION) );
        _photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        _photoLabel.setVerticalAlignment(SwingConstants.CENTER);
        _photoLabel.setPreferredSize(new Dimension(Photo.DEFAULT_MAX_DIMENSION, Photo.DEFAULT_MAX_DIMENSION));
        immutablePanel.add(new JScrollPane(_photoLabel), BorderLayout.CENTER);

        JPanel mutablePanel = new JPanel(new GridBagLayout());
        GridBagConstraints labelGBC = new GridBagConstraints();
        labelGBC.anchor = GridBagConstraints.WEST;
        GridBagConstraints valueGBC = new GridBagConstraints();
        valueGBC.weightx = 1.0;
        valueGBC.weighty = 1.0;
        valueGBC.gridwidth = GridBagConstraints.REMAINDER;
        valueGBC.fill = GridBagConstraints.HORIZONTAL;
        valueGBC.fill = GridBagConstraints.BOTH;

        JScrollPane captionScrollPane = new JScrollPane(_captionTextArea);
        _captionTextArea.setWrapStyleWord(true);
        _captionTextArea.setLineWrap(true);

        JLabel captionLabel = new JLabel("Caption: ");
        captionLabel.setDisplayedMnemonic('a');
        captionLabel.setLabelFor(_captionTextArea);
        mutablePanel.add(captionLabel, labelGBC);
        mutablePanel.add(captionScrollPane, valueGBC);

        mutablePanel.add(new JLabel(), labelGBC);
        mutablePanel.add(_privateCheckBox, valueGBC);

        _categoryList.setToolTipText("Drag and drop categories from the tree to add, select and hit Delete to remove");
//        _categoryList.setFont(new Font("Arial", Font.PLAIN, 10));
        JScrollPane categoryListScrollPane = new JScrollPane(_categoryList);
        JLabel categoryListLabel = new JLabel("Categories: ");
        categoryListLabel.setDisplayedMnemonic('g');
        categoryListLabel.setLabelFor(_categoryList);
        mutablePanel.add(categoryListLabel, labelGBC);
        valueGBC.weighty = 2.0;
        mutablePanel.add(categoryListScrollPane, valueGBC);
        valueGBC.weighty = 1.0;


        JLabel photographerLabel = new JLabel("Photographer:");
        photographerLabel.setDisplayedMnemonic('P');
        photographerLabel.setLabelFor(_photographerPanel);
        mutablePanel.add(photographerLabel, labelGBC);
        mutablePanel.add(_photographerPanel, valueGBC);

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(_saveButton);
        _saveButton.setMnemonic('S');
        buttonPanel.add(_deleteButton);
        _deleteButton.setMnemonic('D');
        buttonPanel.add(_scanForNewPhotosButton);
        _scanForNewPhotosButton.setMnemonic('n');
        buttonPanel.add(_renameButton);
        _renameButton.setMnemonic('R');

        mutablePanel.add(buttonPanel, valueGBC);

        enableButtons(0);

        add(immutablePanel, BorderLayout.NORTH);
        add(mutablePanel, BorderLayout.CENTER);
    }

    private void enableButtons(int photoCount)
    {
        _saveButton.setEnabled(photoCount > 0);
        _deleteButton.setEnabled(photoCount > 0);
        _captionTextArea.setEnabled(photoCount == 1);
        _privateCheckBox.setEnabled(photoCount == 1);
    }

    private void addListeners()
    {
        AdminModel.getModel().addPhotoListener(new PhotoListener()
        {
            @Override
            public void selectedPhotosChanged(final java.util.List<Photo> newPhotos, java.util.List<Photo> oldPhotos)
            {
                saveCurrentPhoto(oldPhotos);

                newPhotos.replaceAll(object -> PhotoOperations.getPhotoOperations().merge(object));

                enableButtons(newPhotos.size());

                _originalCategories = null;
                _photographerPanel.refreshPhotos(newPhotos);

                if (newPhotos.size() == 1)
                {
                    final Photo photo = newPhotos.get(0);
                    AdminFrame.getFrame().setTitle("Photo Gallery: " + photo.getFilename() + " - " + photo.getWidth() + "x" + photo.getHeight());
                    _captionTextArea.setText(photo.getCaption());
                    _privateCheckBox.setSelected(photo.isPrivate());
                    _categoryListModel.setCategories(new java.util.TreeSet<>(AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(photo, true)));

                    Runnable r = () -> {
                        try
                        {
                            photo.ensureAllResized();

                            final Icon thumbnail = createIcon(photo.getRetinaDimensions());

                            Runnable swingRunnable = () -> _photoLabel.setIcon(thumbnail);
                            SwingUtilities.invokeLater(swingRunnable);
                        }
                        catch (IOException | PhotoManipulationException e)
                        {
                            handleException(e);
                            _photoLabel.setIcon(null);
                        }
                    };
                    new Thread(r).start();
                }
                else
                {
                    if (newPhotos.size() > 1)
                    {
                        AdminFrame.getFrame().setTitle("Photo Gallery: " + newPhotos.size() + " photos selected");
                        java.util.Set<Category> firstCats = AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(newPhotos.get(0), true);
                        TreeSet<Category> commonCategories = new TreeSet<>(firstCats);
                        for (int i = 1; i < newPhotos.size(); i++)
                        {
                            java.util.Set<Category> cats = AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(newPhotos.get(i), true);
                            commonCategories.removeIf(category -> !cats.contains(category));
                        }
                        _categoryListModel.setCategories(commonCategories);
                        _originalCategories = new TreeSet<>(commonCategories);
                    }
                    else
                    {
                        AdminFrame.getFrame().setTitle("Photo Gallery");
                        _categoryListModel.setCategories(new TreeSet<>());
                    }
                    _photoLabel.setIcon(null);
                    _captionTextArea.setText("");
                }
            }
        });

        _categoryList.addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_DELETE)
                {
                    for (Category selectedObject : _categoryList.getSelectedValuesList())
                    {
                        _categoryListModel.removeCategory(selectedObject);
                    }
                }
            }
        });

        _saveButton.addActionListener(e -> saveCurrentPhoto());

        KeyListener nextPhotoListener = new NextPreviousKeyListener(this);

        _captionTextArea.addKeyListener(nextPhotoListener);
        _categoryList.addKeyListener(nextPhotoListener);

        AdminModel.getModel().addCategoryListener(new CategoryListener()
        {

            public void categoryChanged(Category category)
            {
            }

            public void requestAddCategory(Category category)
            {
                _categoryListModel.addCategory(category);
            }
        });

        _deleteButton.addActionListener(e -> {
            AdminModel.getModel().deleteCurrentPhotos();
            AdminModel.getModel().firePhotoListChanged();
        });

        _scanForNewPhotosButton.addActionListener(e -> {
            try
            {
                AdminModel.getModel().scanForNewPhotos();
            }
            catch (SystemException | PhotoManipulationException ex)
            {
                handleException(ex);
            }
        });

        _renameButton.addActionListener(e -> {
            JFrame parent = AdminFrame.getFrame();
            ImageRenamer renamer = new ImageRenamer(parent);
            renamer.setVisible(true);
        });
    }

    public void saveCurrentPhoto()
    {
        saveCurrentPhoto(AdminModel.getModel().getCurrentPhotos());
    }

    private void saveCurrentPhoto(java.util.List<Photo> photos)
    {
        photos.replaceAll(object -> PhotoOperations.getPhotoOperations().merge(object));

        try
        {
            if (!_photographerPanel.isMixed())
            {
                for (Photo photo : photos)
                {
                    photo.setPhotographer(_photographerPanel.getSelected());
                }
            }

            if (photos.size() == 1)
            {
                photos.get(0).setCaption(_captionTextArea.getText());
                photos.get(0).setPrivate(_privateCheckBox.isSelected());
                if (_categoryListModel.isChanged())
                {
                    photos.get(0).setCategories(_categoryListModel.getCategories());
                }
            }
            else if (photos.size() > 1)
            {
                for (Category category : _categoryListModel.getCategories())
                {
                    if (!_originalCategories.contains(category))
                    {
                        for (Photo photo : photos)
                        {
                            AdminFrame.getFrame().getPhotoOperations().addCategoryToPhoto(photo, category);
                        }
                    }
                }

                for (Category category : _originalCategories)
                {
                    if (!_categoryListModel.getCategories().contains(category))
                    {
                        for (Photo photo : photos)
                        {
                            AdminFrame.getFrame().getPhotoOperations().removeCategoryFromPhoto(photo, category);
                        }
                    }
                }

                _originalCategories = new TreeSet<>(_categoryListModel.getCategories());
            }
            for (Photo photo : photos)
            {
                AdminModel.getModel().savePhoto(photo, _categoryListModel.isChanged());
            }
        }
        catch (SystemException | PhotoNotFoundException ex)
        {
            handleException(ex);
        }
    }


    private Icon createIcon(Resolution res) throws IOException
    {
        BufferedImage img = ImageIO.read(new File(PhotoOperations.getPhotoOperations().toURI(res.getURI())));
        Dimension dim = getScaledDimension(new Dimension(img.getWidth(), img.getHeight()), new Dimension(_photoLabel.getWidth(), _photoLabel.getHeight()));


        Image dimg = img.getScaledInstance(dim.width, dim.height, Image.SCALE_SMOOTH);
        return new ImageIcon(dimg);
    }

    public static Dimension getScaledDimension(Dimension imgSize, Dimension boundary) {

        int original_width = imgSize.width;
        int original_height = imgSize.height;
        int bound_width = boundary.width;
        int bound_height = boundary.height;
        int new_width = original_width;
        int new_height = original_height;

        // first check if we need to scale width
        if (original_width > bound_width) {
            //scale width to fit
            new_width = bound_width;
            //scale height to maintain aspect ratio
            new_height = (new_width * original_height) / original_width;
        }

        // then check if we need to scale even with the new height
        if (new_height > bound_height) {
            //scale height to fit instead
            new_height = bound_height;
            //scale width to maintain aspect ratio
            new_width = (new_height * original_width) / original_height;
        }

        return new Dimension(new_width, new_height);
    }

    public void reload() throws SystemException
    {
        _photographerPanel.reload();
    }

}