/*
 * PhotoAdminScreen.java
 *
 * Created on April 15, 2002, 9:34 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.gui.AbstractPanel;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.util.SystemException;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;

/**
 * @author josh
 */
public class PhotoAdminScreen extends AbstractPanel
{

    private PhotoListModel _photoListModel = new PhotoListModel();
    private JList _photoList = new JList(_photoListModel);

    private CategoryTree _categoryTree = new CategoryTree();
    private RecentCategoryList _recentCategoryList = new RecentCategoryList();
    private JButton _setDefaultPhotoButton = new JButton("Set Default Photo");

    private PhotoInfoPanel _infoPanel = new PhotoInfoPanel();
    private JTabbedPane _categoryTabbedPane;

    public PhotoAdminScreen()
    {
        addComponents();
        addListeners();

        try
        {
            _photoListModel.reload();
            _infoPanel.reload();
            Photo photo = _photoListModel.getElementAt(0);
            if (photo != null)
            {
                _photoList.setPrototypeCellValue(photo);
            }
        }
        catch (SystemException e)
        {
            handleException(e);
        }
    }

    private void addComponents()
    {
        setLayout(new BorderLayout());

        _photoList.setSelectionMode(DefaultListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        _photoList.setCellRenderer(new PhotoListCellRenderer());

        JTabbedPane categoryTabbedPane = new JTabbedPane();

        JPanel allCategoriesPanel = new JPanel(new BorderLayout());
        JScrollPane treeScrollPane = new JScrollPane(_categoryTree);
        treeScrollPane.setMinimumSize(new Dimension(200, 200));
        allCategoriesPanel.add(treeScrollPane, BorderLayout.CENTER);
        allCategoriesPanel.add(_setDefaultPhotoButton, BorderLayout.SOUTH);
        _categoryTabbedPane = categoryTabbedPane;
        _categoryTabbedPane.addTab("All Categories", allCategoriesPanel);
        categoryTabbedPane.setMnemonicAt(categoryTabbedPane.getTabCount() - 1, KeyEvent.VK_C);

        JScrollPane recentCategoryListScrollPane = new JScrollPane(_recentCategoryList);
        categoryTabbedPane.addTab("Recent Categories", recentCategoryListScrollPane);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setTopComponent(new JScrollPane(_photoList));
        splitPane.setBottomComponent(categoryTabbedPane);
        add(splitPane, BorderLayout.WEST);

        add(_infoPanel, BorderLayout.CENTER);

        refreshDefaultPhotoButtonStatus();
    }

    private void addListeners()
    {
        _photoList.addListSelectionListener(new ListSelectionListener()
        {
            public void valueChanged(ListSelectionEvent e)
            {
                if (!e.getValueIsAdjusting())
                {
                    Object[] selected = _photoList.getSelectedValues();
                    Photo[] photos = new Photo[selected.length];
                    System.arraycopy(selected, 0, photos, 0, photos.length);
                    AdminModel.getModel().fireSelectedPhotosChanged(photos);
                }
            }
        });

        _categoryTree.setTransferHandler(new CategorySelection());
        _categoryTree.setDragEnabled(true);

        _categoryTree.addMouseListener(new MouseAdapter()
        {

            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1)
                {
                    TreePath[] paths = _categoryTree.getSelectionPaths();
                    if (paths != null)
                    {
                        for (TreePath path : paths)
                        {
                            Category category = ((CategoryTreeNode) path.getLastPathComponent()).getCategory();
                            AdminModel.getModel().fireRequestAddCategory(category);
                        }
                    }
                }
            }
        });

        _categoryTree.addKeyListener(new NextPreviousKeyListener(_infoPanel)
        {
            public void keyTyped(KeyEvent e)
            {
                if (e.getKeyChar() == '\n')
                {
                    TreePath[] paths = _categoryTree.getSelectionPaths();
                    if (paths != null)
                    {
                        for (TreePath path : paths)
                        {
                            Category category = ((CategoryTreeNode) path.getLastPathComponent()).getCategory();
                            AdminModel.getModel().fireRequestAddCategory(category);
                        }
                    }
                }
            }
        });

        _categoryTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener()
        {
            public void valueChanged(TreeSelectionEvent e)
            {
                refreshDefaultPhotoButtonStatus();
            }
        });

        AdminModel.getModel().addPhotoListener(new PhotoListener()
        {
            public void selectedPhotosChanged(Photo[] newPhotos, Photo[] oldPhotos)
            {
                refreshDefaultPhotoButtonStatus();
            }

            public void requestNextPhotoSelection()
            {
                int[] indices = _photoList.getSelectedIndices();
                if (indices.length == 1)
                {
                    _photoList.setSelectedIndex(indices[0] + 1);
                    Rectangle bounds = _photoList.getCellBounds(indices[0] + 1, indices[0] + 1);
                    if (bounds != null)
                    {
                        _photoList.scrollRectToVisible(bounds);
                    }
                }
            }

            public void requestPreviousPhotoSelection()
            {
                int[] indices = _photoList.getSelectedIndices();
                if (indices.length == 1 && indices[0] > 0)
                {
                    _photoList.setSelectedIndex(indices[0] - 1);
                    Rectangle bounds = _photoList.getCellBounds(indices[0] - 1, indices[0] - 1);
                    if (bounds != null)
                    {
                        _photoList.scrollRectToVisible(bounds);
                    }
                }
            }
        });

        _setDefaultPhotoButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                Photo[] currentPhotos = AdminModel.getModel().getCurrentPhotos();
                if (currentPhotos.length != 1)
                {
                    return;
                }
                Photo currentPhoto = currentPhotos[0];

                TreePath[] paths = _categoryTree.getSelectionPaths();
                if (paths != null)
                {
                    for (TreePath path : paths)
                    {
                        Category category = ((CategoryTreeNode) path.getLastPathComponent()).getCategory();
                        if (category.getDefaultPhoto() != null)
                        {
                            if (JOptionPane.showConfirmDialog(AdminFrame.getFrame(), "Are you sure you want to change the category photo?", "Change Category Default", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
                            {
                                return;
                            }
                        }
                        category.setDefaultPhoto(currentPhoto);
                        AdminModel.getModel().saveCategory(category);
                    }
                }
            }
        });

        _categoryTabbedPane.addChangeListener(new ChangeListener()
        {
            public void stateChanged(ChangeEvent e)
            {
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        _categoryTabbedPane.getSelectedComponent().requestFocus();
                    }
                });
            }
        });
    }

    private void refreshDefaultPhotoButtonStatus()
    {
        _setDefaultPhotoButton.setEnabled(AdminModel.getModel().getCurrentPhotos().length == 1 && _categoryTree.getSelectionCount() > 0);
    }
}