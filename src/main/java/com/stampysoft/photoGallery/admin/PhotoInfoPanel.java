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
import java.util.Iterator;
import java.util.TreeSet;

/**
 * @author josh
 */
public class PhotoInfoPanel extends AbstractPanel
{

    private JLabel _photoLabel = new JLabel();
    private JTextArea _captionTextArea = new JTextArea(2, 80);
    private JCheckBox _privateCheckBox = new JCheckBox("Private");
    private CategoryListModel _categoryListModel = new CategoryListModel();
    private JList _categoryList = new JList(_categoryListModel);
    private JButton _saveButton = new JButton("Save");
    private JButton _deleteButton = new JButton("Delete");
    private JButton _scanForNewPhotosButton = new JButton("Scan for new photos");
    private JButton _renameButton = new JButton("Rename files");
    private PhotographerPanel _photographerPanel = new PhotographerPanel();

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
            public void selectedPhotosChanged(final Photo[] newPhotos, Photo[] oldPhotos)
            {
                saveCurrentPhoto(oldPhotos);

                for (int i = 0; i < newPhotos.length; i++)
                {
                    newPhotos[i] = PhotoOperations.getPhotoOperations().merge(newPhotos[i]);
                }

                enableButtons(newPhotos.length);

                _originalCategories = null;
                _photographerPanel.refreshPhotos(newPhotos);

                if (newPhotos.length == 1)
                {
                    final Photo photo = newPhotos[0];
                    AdminFrame.getFrame().setTitle("Photo Gallery: " + photo.getFilename() + " - " + photo.getWidth() + "x" + photo.getHeight());
                    _captionTextArea.setText(photo.getCaption());
                    _privateCheckBox.setSelected(photo.isPrivate());
                    _categoryListModel.setCategories(photo.getCategories(true));

                    Runnable r = new Runnable()
                    {
                        public void run()
                        {
                            try
                            {
                                photo.ensureAllResized();

                                final Icon thumbnail = createIcon(photo.getRetinaDimensions());

                                Runnable swingRunnable = new Runnable()
                                {
                                    public void run()
                                    {
                                        _photoLabel.setIcon(thumbnail);
                                    }
                                };
                                SwingUtilities.invokeLater(swingRunnable);
                            }
                            catch (IOException e)
                            {
                                handleException(e);
                                _photoLabel.setIcon(null);
                            }
                            catch (PhotoManipulationException e)
                            {
                                handleException(e);
                                _photoLabel.setIcon(null);
                            }
                        }
                    };
                    new Thread(r).start();
                }
                else
                {
                    if (newPhotos.length > 1)
                    {
                        AdminFrame.getFrame().setTitle("Photo Gallery: " + newPhotos.length + " photos selected");
                        TreeSet<Category> commonCategories = new TreeSet<>();
                        commonCategories.addAll(newPhotos[0].getCategories(true));
                        for (int i = 1; i < newPhotos.length; i++)
                        {
                            for (Iterator<Category> common = commonCategories.iterator(); common.hasNext();)
                            {
                                Category category = common.next();
                                if (!newPhotos[i].getCategories(true).contains(category))
                                {
                                    common.remove();
                                }
                            }
                        }
                        _categoryListModel.setCategories(commonCategories);
                        _originalCategories = new TreeSet<>(commonCategories);
                    }
                    else
                    {
                        AdminFrame.getFrame().setTitle("Photo Gallery");
                        _categoryListModel.setCategories(new TreeSet<Category>());
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
                    Object[] selectedObjects = _categoryList.getSelectedValues();
                    for (Object selectedObject : selectedObjects)
                    {
                        _categoryListModel.removeCategory((Category) selectedObject);
                    }
                }
            }
        });

        _saveButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                saveCurrentPhoto();
            }
        });

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

        _deleteButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                AdminModel.getModel().deleteCurrentPhotos();
                AdminModel.getModel().firePhotoListChanged();
            }
        });

        _scanForNewPhotosButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                try
                {
                    AdminModel.getModel().scanForNewPhotos();
                }
                catch (SystemException ex)
                {
                    handleException(ex);
                }
                catch (PhotoManipulationException ex)
                {
                    handleException(ex);
                }
            }
        });

        _renameButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                JFrame parent = AdminFrame.getFrame();
                ImageRenamer renamer = new ImageRenamer(parent);
                renamer.setVisible(true);
            }
        });
    }

    public void saveCurrentPhoto()
    {
        saveCurrentPhoto(AdminModel.getModel().getCurrentPhotos());
    }

    private void saveCurrentPhoto(Photo[] photos)
    {
        for (int i = 0; i < photos.length; i++)
        {
            photos[i] = PhotoOperations.getPhotoOperations().merge(photos[i]);
        }

        try
        {
            if (!_photographerPanel.isMixed())
            {
                for (Photo photo : photos)
                {
                    photo.setPhotographer(_photographerPanel.getSelected());
                }
            }

            if (photos.length == 1)
            {
                photos[0].setCaption(_captionTextArea.getText());
                photos[0].setPrivate(_privateCheckBox.isSelected());
                if (_categoryListModel.isChanged())
                {
                    photos[0].setCategories(_categoryListModel.getCategories());
                }
            }
            else if (photos.length > 1)
            {
                for (Category category : _categoryListModel.getCategories())
                {
                    if (!_originalCategories.contains(category))
                    {
                        for (Photo photo : photos)
                        {

                            photo.getCategories(true).add(category);
                        }
                    }
                }

                for (Object _originalCategory : _originalCategories)
                {
                    Category category = (Category) _originalCategory;
                    if (!_categoryListModel.getCategories().contains(category))
                    {
                        for (Photo photo : photos)
                        {
                            photo.getCategories(true).remove(category);
                        }
                    }
                }

                _originalCategories = new TreeSet<Category>(_categoryListModel.getCategories());
            }
            for (Photo photo : photos)
            {
                AdminModel.getModel().savePhoto(photo, _categoryListModel.isChanged());
            }
        }
        catch (SystemException ex)
        {
            handleException(ex);
        }
        catch (PhotoNotFoundException ex)
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