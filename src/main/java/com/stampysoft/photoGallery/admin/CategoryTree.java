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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author josh
 */
public class CategoryTree extends JTree
{

    private final JPopupMenu _menu = new JPopupMenu();
    private final JMenuItem _deleteMenuItem = new JMenuItem("Delete");
    private final JMenuItem _renameMenuItem = new JMenuItem("Rename");
    private final JCheckBoxMenuItem _privateMenuItem = new JCheckBoxMenuItem("Private");
    private final JMenuItem _exportPhotosMenuItem = new JMenuItem("Export Photos...");
    private final JMenuItem _insertMenuItem = new JMenuItem("Insert");

    /** How many categories the confirmation dialog spells out before it just gives the count. */
    private static final int MAX_CATEGORIES_SHOWN = 15;

    public CategoryTree()
    {
        super(new CategoryTreeNode(null, null));

        DefaultTreeCellRenderer renderer = new CategoryCellRenderer();

        setCellRenderer(renderer);
        setCellEditor(new CategoryCellEditor(this, renderer));

        setEditable(true);

        _menu.setLightWeightPopupEnabled(true);
        _menu.setOpaque(true);

        // This was on the Delete item, where it started an inline rename of the category being deleted; Rename,
        // meanwhile, had no listener at all and did nothing.
        _renameMenuItem.addActionListener(e -> {
            final TreePath[] paths = getSelectionPaths();
            SwingUtilities.invokeLater(() -> {
                if (paths != null && paths.length > 0)
                {
                    startEditingAtPath(paths[0]);
                }
            });
        });

        _deleteMenuItem.addActionListener(e -> {
            List<TreePath> paths = getPathsToDelete();
            if (paths.isEmpty() || !confirmDelete(paths))
            {
                return;
            }
            for (TreePath path : paths) {
                CategoryTreeNode node = (CategoryTreeNode) path.getLastPathComponent();
                CategoryTreeNode parent = (CategoryTreeNode) node.getParent();
                node.delete();
                ((DefaultTreeModel) getModel()).nodeStructureChanged(parent);
            }
        });

        _privateMenuItem.addActionListener(e -> {
            TreePath[] paths = getSelectionPaths();
            for (TreePath path : paths) {
                CategoryTreeNode node = (CategoryTreeNode) path.getLastPathComponent();
                CategoryTreeNode parent = (CategoryTreeNode) node.getParent();
                node.setPrivate(_privateMenuItem.isSelected());
                ((DefaultTreeModel) getModel()).nodeStructureChanged(parent);
            }
        });

        _insertMenuItem.addActionListener(e -> {
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
        });

        _exportPhotosMenuItem.addActionListener(e -> {
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
            okButton.addActionListener(e2 -> {
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
                                int i;
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

            });

            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e3 -> dialog.setVisible(false));

            exportPanel.add(okButton, leftGBC);
            exportPanel.add(cancelButton, rightGBC);

            dialog.getContentPane().add(exportPanel);
            dialog.setVisible(true);
            dialog.pack();
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

    /**
     * The selected categories that are actually going to be deleted.
     * <p>
     * A category takes its whole subtree with it, so anything selected underneath another selection is already
     * covered. Deleting it again afterwards would be worse than redundant: re-attaching a row that no longer exists
     * makes JPA treat it as new and insert it back.
     */
    private List<TreePath> getPathsToDelete()
    {
        TreePath[] paths = getSelectionPaths();
        if (paths == null)
        {
            return List.of();
        }

        List<TreePath> result = new ArrayList<>();
        for (TreePath path : paths)
        {
            // The root stands for the whole gallery rather than a category, and has nothing to delete
            if (path.getPathCount() == 1)
            {
                continue;
            }
            boolean coveredByAnotherSelection = false;
            for (TreePath other : paths)
            {
                if (other != path && other.isDescendant(path))
                {
                    coveredByAnotherSelection = true;
                    break;
                }
            }
            if (!coveredByAnotherSelection)
            {
                result.add(path);
            }
        }
        return result;
    }

    /**
     * Asks before deleting. This is the destructive action in the tree - it cascades through the sub-categories in
     * the database, and there's no undo - so it's worth spelling out how far it reaches before it happens.
     */
    private boolean confirmDelete(List<TreePath> paths)
    {
        List<Category> categories = new ArrayList<>();
        for (TreePath path : paths)
        {
            categories.add(((CategoryTreeNode) path.getLastPathComponent()).getCategory());
        }

        PhotoOperations photoOperations = AdminFrame.getFrame().getPhotoOperations();
        List<Category> descendants = photoOperations.getDescendantCategories(categories);
        List<Category> allAffected = new ArrayList<>(categories);
        allAffected.addAll(descendants);
        int photoCount = photoOperations.countPhotosInCategories(allAffected);

        StringBuilder message = new StringBuilder();
        message.append(categories.size() == 1
                ? "Permanently delete this category?"
                : "Permanently delete these " + categories.size() + " categories?");
        message.append("\n\n").append(describe(categories));

        if (!descendants.isEmpty())
        {
            message.append("\n").append(descendants.size() == 1
                    ? "It also deletes this sub-category:"
                    : "It also deletes these " + descendants.size() + " sub-categories:");
            message.append("\n\n").append(describe(descendants));
        }

        message.append("\n");
        if (photoCount > 0)
        {
            message.append("The photos themselves are not deleted - they only lose these categories (")
                    .append(photoCount).append(photoCount == 1 ? " photo" : " photos").append(" affected).\n");
        }
        message.append("This cannot be undone.");

        return JOptionPane.showConfirmDialog(this, message.toString(),
                categories.size() == 1 ? "Delete Category" : "Delete " + categories.size() + " Categories",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private String describe(List<Category> categories)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(categories.size(), MAX_CATEGORIES_SHOWN); i++)
        {
            result.append("    ").append(categories.get(i).getDescription()).append("\n");
        }
        if (categories.size() > MAX_CATEGORIES_SHOWN)
        {
            result.append("    ... and ").append(categories.size() - MAX_CATEGORIES_SHOWN).append(" more\n");
        }
        return result.toString();
    }

}
