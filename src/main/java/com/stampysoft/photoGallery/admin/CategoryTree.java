/*
 * CategoryTree.java
 *
 * Created on April 16, 2002, 11:16 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * @author josh
 */
public class CategoryTree extends JTree
{

    private JPopupMenu _menu = new JPopupMenu();
    private JMenuItem _deleteMenuItem = new JMenuItem("Delete");
    private JMenuItem _renameMenuItem = new JMenuItem("Rename");
    private JCheckBoxMenuItem _privateMenuItem = new JCheckBoxMenuItem("Private");
    private JMenuItem _exportPhotosMenuItem = new JMenuItem("Export Photos...");
    private JMenuItem _insertMenuItem = new JMenuItem("Insert");

    public CategoryTree()
    {
        super(new CategoryTreeNode(null, null));

        DefaultTreeCellRenderer renderer = new CategoryCellRenderer();

        setCellRenderer(renderer);
        setCellEditor(new CategoryCellEditor(this, renderer));

        setEditable(true);

        _menu.setLightWeightPopupEnabled(true);
        _menu.setOpaque(true);

        _deleteMenuItem.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                final TreePath[] paths = getSelectionPaths();
                SwingUtilities.invokeLater(new Runnable()
                {
                    public void run()
                    {
                        if (paths != null && paths.length > 0)
                        {
                            startEditingAtPath(paths[0]);
                        }
                    }
                });
            }
        });

        _deleteMenuItem.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                TreePath[] paths = getSelectionPaths();
                for (int i = 0; i < paths.length; i++)
                {
                    CategoryTreeNode node = (CategoryTreeNode) paths[i].getLastPathComponent();
                    CategoryTreeNode parent = (CategoryTreeNode) node.getParent();
                    node.delete();
                    ((DefaultTreeModel) getModel()).nodeStructureChanged(parent);
                }
            }
        });

        _privateMenuItem.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                TreePath[] paths = getSelectionPaths();
                for (int i = 0; i < paths.length; i++)
                {
                    CategoryTreeNode node = (CategoryTreeNode) paths[i].getLastPathComponent();
                    CategoryTreeNode parent = (CategoryTreeNode) node.getParent();
                    node.setPrivate(_privateMenuItem.isSelected());
                    ((DefaultTreeModel) getModel()).nodeStructureChanged(parent);
                }
            }
        });

        _insertMenuItem.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                TreePath path = getSelectionPath();
                CategoryTreeNode parent = (CategoryTreeNode) path.getLastPathComponent();
                Category newCategory = new Category();
                newCategory.setDescription("New category");
                newCategory.setParentCategory(parent.getCategory());
                newCategory = AdminFrame.getFrame().getPhotoOperations().saveCategory(newCategory);
                CategoryTreeNode newNode = new CategoryTreeNode(parent, newCategory);
                parent.add(newNode);
                ((DefaultTreeModel) getModel()).nodeStructureChanged(parent);
                startEditingAtPath(path.pathByAddingChild(newNode));
            }
        });

        _exportPhotosMenuItem.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent e)
            {
                JPanel exportPanel = new JPanel(new GridBagLayout());
                final JTextField destinationTextField = new JTextField(30);
                final JCheckBox landscapeCheckBox = new JCheckBox("Landscape");
                final JCheckBox portraitCheckBox = new JCheckBox("Portrait");
                landscapeCheckBox.setSelected(true);
                portraitCheckBox.setSelected(true);

                GridBagConstraints leftGBC = new GridBagConstraints();
                GridBagConstraints rightGBC = new GridBagConstraints();
                rightGBC.gridwidth = GridBagConstraints.REMAINDER;

                exportPanel.add(new JLabel("Destination: "), leftGBC);
                exportPanel.add(destinationTextField);

                exportPanel.add(landscapeCheckBox, rightGBC);
                exportPanel.add(portraitCheckBox, rightGBC);

                final CategoryTreeNode node = (CategoryTreeNode) getSelectionPath().getLastPathComponent();

                final JDialog dialog = new JDialog(AdminFrame.getFrame(), "Export Photos for " + node.getCategory().getDescription(), true);

                JButton okButton = new JButton("OK");
                okButton.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        try
                        {
                            Set<Photo> photos = node.getCategory().getPhotos(true);
                            File directory = new File(destinationTextField.getText());
                            directory.mkdirs();
                            for (Photo photo : photos)
                            {
                                if ((portraitCheckBox.isSelected() && photo.getWidth() <= photo.getHeight()) ||
                                    (landscapeCheckBox.isSelected() && photo.getWidth() >= photo.getHeight()))
                                {
                                    File originalFile = new File(new URI(photo.getOriginalDimensions().getURI()));
                                    FileInputStream fIn = null;
                                    FileOutputStream fOut = null;
                                    try
                                    {
                                        fIn = new FileInputStream(originalFile);
                                        fOut = new FileOutputStream(new File(directory, photo.getFilename()));
                                        byte[] b = new byte[4096];
                                        int i = 0;
                                        while ((i = fIn.read(b)) != -1)
                                        {
                                            fOut.write(b, 0, i);
                                        }
                                    }
                                    catch (IOException e1)
                                    {
                                        e1.printStackTrace();
                                    }
                                    finally
                                    {
                                        if (fIn != null)
                                        {
                                            try
                                            {
                                                fIn.close();
                                            }
                                            catch (IOException ignored)
                                            {
                                            }
                                        }
                                        if (fIn != null)
                                        {
                                            try
                                            {
                                                fOut.close();
                                            }
                                            catch (IOException ignored)
                                            {
                                            }
                                        }
                                    }
                                }
                            }
                            dialog.setVisible(false);
                        }
                        catch (URISyntaxException e1)
                        {
                            e1.printStackTrace();
                        }

                    }
                });

                JButton cancelButton = new JButton("Cancel");
                cancelButton.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent e)
                    {
                        dialog.setVisible(false);
                    }
                });

                exportPanel.add(okButton, leftGBC);
                exportPanel.add(cancelButton, rightGBC);

                dialog.getContentPane().add(exportPanel);
                dialog.setVisible(true);
                dialog.pack();
            }
        });

        addMouseListener(new MouseAdapter()
        {
            public void mouseReleased(MouseEvent e)
            {
                if (SwingUtilities.isRightMouseButton(e))
                {
                    if (getSelectionCount() > 0)
                    {

                        // Insert
                        if (getSelectionCount() == 1)
                        {
                            _menu.add(_insertMenuItem);

                            Category selectedCategory = ((CategoryTreeNode) getSelectionPath().getLastPathComponent()).getCategory();
                            if (selectedCategory != null)
                            {
                                _menu.add(_exportPhotosMenuItem);
                                _menu.add(_renameMenuItem);
                                _menu.add(_privateMenuItem);
                                _privateMenuItem.setSelected(selectedCategory != null && selectedCategory.isPrivate());
                            }
                            else
                            {
                                _menu.remove(_exportPhotosMenuItem);
                                _menu.remove(_renameMenuItem);
                                _menu.remove(_privateMenuItem);
                            }
                        }
                        else
                        {
                            _menu.remove(_exportPhotosMenuItem);
                            _menu.remove(_insertMenuItem);
                            _menu.remove(_renameMenuItem);
                            _menu.remove(_privateMenuItem);
                        }

                        // Delete
                        if (getSelectionPath().getPathCount() == 1)
                        {
                            _menu.remove(_deleteMenuItem);
                        }
                        else
                        {
                            _menu.add(_deleteMenuItem);
                        }

                        _menu.show((JComponent) e.getSource(), e.getX(), e.getY());
                    }
                }
            }

        });
    }

}
