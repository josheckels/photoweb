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

    private final PhotoListModel _photoListModel = new PhotoListModel();
    private final JList<Photo> _photoList = new JList<>(_photoListModel);

    private final CategoryTree _categoryTree = new CategoryTree();
    private final RecentCategoryList _recentCategoryList = new RecentCategoryList();
    private final JButton _setDefaultPhotoButton = new JButton("Set Default Photo");

    private final PhotoInfoPanel _infoPanel = new PhotoInfoPanel();
    private JTabbedPane _categoryTabbedPane;

    private final JTextField _filterField = new JTextField(12);
    private final JCheckBox _uncategorizedOnlyCheckBox = new JCheckBox("Uncategorized only");
    private final JCheckBox _lastScanOnlyCheckBox = new JCheckBox("New from last scan");
    private final JLabel _filterStatusLabel = new JLabel();

    // Type-to-search state for the photo list
    private final StringBuilder _photoListTypeBuffer = new StringBuilder();
    private long _photoListLastTypeTime = 0L;
    private static final int TYPE_AHEAD_RESET_MS = 1200;

    /** How much of the left column the filter panel and the photo list get before the user drags the divider. */
    private static final double PHOTO_LIST_HEIGHT_FRACTION = 0.5;

    private boolean _dividerMovedByUser = false;
    private boolean _movingDivider = false;

    public PhotoAdminScreen()
    {
        addComponents();
        addListeners();

        try
        {
            _photoListModel.reload();
            _infoPanel.reload();
            if (_photoListModel.getSize() > 0)
            {
                _photoList.setPrototypeCellValue(_photoListModel.getElementAt(0));
            }
            refreshFilterStatus();
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
        _photoList.setCellRenderer(new PhotoListCellRenderer(_photoListModel));

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
        JScrollPane photoListScrollPane = new JScrollPane(_photoList);

        JPanel filterPanel = createFilterPanel();
        JPanel photoListPanel = new JPanel(new BorderLayout());
        photoListPanel.add(filterPanel, BorderLayout.NORTH);
        photoListPanel.add(photoListScrollPane, BorderLayout.CENTER);
        // The split pane bounds how far the divider can travel by this minimum, so ask only for the filter controls
        // plus a few rows of list. Demanding more leaves the divider with nowhere to go on a short screen, and
        // squeezes this whole side of the window. keepDividerCentered decides the size we actually start at.
        photoListPanel.setMinimumSize(new Dimension(200, filterPanel.getPreferredSize().height + 60));

        splitPane.setTopComponent(photoListPanel);
        splitPane.setBottomComponent(categoryTabbedPane);
        splitPane.setResizeWeight(PHOTO_LIST_HEIGHT_FRACTION);
        keepDividerCentered(splitPane);
        add(splitPane, BorderLayout.WEST);

        add(_infoPanel, BorderLayout.CENTER);

        refreshDefaultPhotoButtonStatus();
    }

    /**
     * Keeps the photo list at its share of the left column until the user drags the divider themselves.
     * <p>
     * Setting the divider location up front doesn't survive startup: AdminFrame sizes the window to 100 pixels tall
     * before maximizing it, and the divider gets clamped to the photo list's minimum height while the window is that
     * small. Every later pixel of height then goes to the category tree, leaving a couple of rows of photos. So wait
     * for the real size to arrive instead, which also covers the user resizing the window afterwards.
     */
    private void keepDividerCentered(JSplitPane splitPane)
    {
        splitPane.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                if (!_dividerMovedByUser)
                {
                    _movingDivider = true;
                    try
                    {
                        splitPane.setDividerLocation(PHOTO_LIST_HEIGHT_FRACTION);
                    }
                    finally
                    {
                        _movingDivider = false;
                    }
                }
            }
        });

        // Resizing on its own never touches this property, so anything we didn't do ourselves is the user dragging
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, e -> {
            if (!_movingDivider)
            {
                _dividerMovedByUser = true;
            }
        });
    }

    private JPanel createFilterPanel()
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        GridBagConstraints labelGBC = new GridBagConstraints();
        labelGBC.anchor = GridBagConstraints.WEST;

        GridBagConstraints valueGBC = new GridBagConstraints();
        valueGBC.anchor = GridBagConstraints.WEST;
        valueGBC.gridwidth = GridBagConstraints.REMAINDER;
        valueGBC.fill = GridBagConstraints.HORIZONTAL;
        valueGBC.weightx = 1.0;

        JLabel filterLabel = new JLabel("Filter: ");
        filterLabel.setDisplayedMnemonic('F');
        filterLabel.setLabelFor(_filterField);
        _filterField.setToolTipText("Show only photos whose filename contains this text. * and ? match as wildcards, and Escape clears the filter.");
        panel.add(filterLabel, labelGBC);
        panel.add(_filterField, valueGBC);

        _uncategorizedOnlyCheckBox.setMnemonic('U');
        _uncategorizedOnlyCheckBox.setToolTipText("Show only the photos that aren't in any category yet - the ones listed in red");
        panel.add(_uncategorizedOnlyCheckBox, valueGBC);

        _lastScanOnlyCheckBox.setMnemonic('L');
        _lastScanOnlyCheckBox.setToolTipText("Show only the photos added by the most recent scan");
        _lastScanOnlyCheckBox.setEnabled(false);
        panel.add(_lastScanOnlyCheckBox, valueGBC);

        panel.add(_filterStatusLabel, valueGBC);

        return panel;
    }

    private void addListeners()
    {
        _photoList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
            {
                java.util.List<Photo> photos = _photoList.getSelectedValuesList();
                AdminModel.getModel().fireSelectedPhotosChanged(photos, getLeadPhoto());
            }
        });

        _filterField.getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e) { SwingUtilities.invokeLater(PhotoAdminScreen.this::applyFilter); }
            public void removeUpdate(DocumentEvent e) { SwingUtilities.invokeLater(PhotoAdminScreen.this::applyFilter); }
            public void changedUpdate(DocumentEvent e) { SwingUtilities.invokeLater(PhotoAdminScreen.this::applyFilter); }
        });

        _filterField.addKeyListener(new KeyAdapter()
        {
            public void keyPressed(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
                {
                    _filterField.setText("");
                }
            }
        });

        _uncategorizedOnlyCheckBox.addActionListener(e -> applyFilter());
        _lastScanOnlyCheckBox.addActionListener(e -> applyFilter());

        // Type-to-search: when user types, jump to the first photo whose filename starts with the typed text
        _photoList.addKeyListener(new KeyAdapter()
        {
            public void keyTyped(KeyEvent e)
            {
                char ch = e.getKeyChar();
                long now = System.currentTimeMillis();
                if (now - _photoListLastTypeTime > TYPE_AHEAD_RESET_MS)
                {
                    _photoListTypeBuffer.setLength(0);
                }
                _photoListLastTypeTime = now;

                if (ch == KeyEvent.VK_ESCAPE)
                {
                    _photoListTypeBuffer.setLength(0);
                    return;
                }
                if (ch == KeyEvent.VK_BACK_SPACE)
                {
                    int len = _photoListTypeBuffer.length();
                    if (len > 0)
                    {
                        _photoListTypeBuffer.deleteCharAt(len - 1);
                    }
                }
                else if (!Character.isISOControl(ch))
                {
                    _photoListTypeBuffer.append(ch);
                }

                String prefix = _photoListTypeBuffer.toString().toLowerCase();
                if (prefix.isEmpty())
                {
                    return;
                }

                ListModel<Photo> model = _photoList.getModel();
                int bestIndex = -1;
                for (int i = 0; i < model.getSize(); i++)
                {
                    Photo obj = model.getElementAt(i);
                    if (obj != null)
                    {
                        String name = obj.getFilename();
                        if (name != null && name.toLowerCase().startsWith(prefix))
                        {
                            bestIndex = i;
                            break;
                        }
                    }
                }
                if (bestIndex >= 0)
                {
                    selectPhotoIndex(bestIndex);
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

        _categoryTree.getSelectionModel().addTreeSelectionListener(e -> refreshDefaultPhotoButtonStatus());

        AdminModel.getModel().addPhotoListener(new PhotoListener()
        {
            @Override
            public void selectedPhotosChanged(java.util.List<Photo> newPhotos, java.util.List<Photo> oldPhotos)
            {
                refreshDefaultPhotoButtonStatus();
            }

            @Override
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

            @Override
            public void photoListChanged()
            {
                // The list model has already reloaded by the time we get here, since it registered first
                refreshFilterStatus();
            }

            @Override
            public void requestPhotoSelection(java.util.Collection<Photo> photos)
            {
                ListModel<Photo> model = _photoList.getModel();
                for (int i = 0; i < model.getSize(); i++)
                {
                    if (photos.contains(model.getElementAt(i)))
                    {
                        selectPhotoIndex(i);
                        return;
                    }
                }
            }
        });

        _setDefaultPhotoButton.addActionListener(e -> {
            java.util.List<Photo> currentPhotos = AdminModel.getModel().getCurrentPhotos();
            if (currentPhotos.size() != 1)
            {
                return;
            }
            Photo currentPhoto = currentPhotos.get(0);

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
        });

        _categoryTabbedPane.addChangeListener(e -> SwingUtilities.invokeLater(() -> _categoryTabbedPane.getSelectedComponent().requestFocus()));
    }

    /**
     * The photo the user most recently clicked or arrowed onto, or null if that cell isn't part of the selection,
     * which happens when the most recent click deselected a photo.
     */
    private Photo getLeadPhoto()
    {
        int lead = _photoList.getLeadSelectionIndex();
        if (lead >= 0 && lead < _photoListModel.getSize() && _photoList.isSelectedIndex(lead))
        {
            return _photoListModel.getElementAt(lead);
        }
        return null;
    }

    private void applyFilter()
    {
        java.util.List<Photo> previousSelection = _photoList.getSelectedValuesList();

        // Refiltering drops the selection and reselecting it adds the photos back one at a time, each of which
        // would otherwise be a separate selection change - and every selection change saves the photos being
        // navigated away from. Marking the whole thing as adjusting collapses it into a single change at the end.
        ListSelectionModel selectionModel = _photoList.getSelectionModel();
        selectionModel.setValueIsAdjusting(true);
        try
        {
            _photoListModel.setFilter(_filterField.getText(), _uncategorizedOnlyCheckBox.isSelected(), _lastScanOnlyCheckBox.isSelected());
            restoreSelection(previousSelection);
        }
        finally
        {
            selectionModel.setValueIsAdjusting(false);
        }

        refreshFilterStatus();
    }

    /** Reselects whichever of the given photos survived the filter, so that narrowing the list isn't destructive. */
    private void restoreSelection(java.util.List<Photo> photos)
    {
        if (photos.isEmpty())
        {
            return;
        }

        java.util.List<Integer> indices = new java.util.ArrayList<>();
        for (int i = 0; i < _photoListModel.getSize(); i++)
        {
            if (photos.contains(_photoListModel.getElementAt(i)))
            {
                indices.add(i);
            }
        }
        if (indices.isEmpty())
        {
            return;
        }

        int[] array = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++)
        {
            array[i] = indices.get(i);
        }
        _photoList.setSelectedIndices(array);

        Rectangle bounds = _photoList.getCellBounds(array[0], array[0]);
        if (bounds != null)
        {
            _photoList.scrollRectToVisible(bounds);
        }
    }

    private void refreshFilterStatus()
    {
        int shown = _photoListModel.getSize();
        int total = _photoListModel.getTotalSize();
        _filterStatusLabel.setText(shown == total ? total + " photos" : shown + " of " + total + " photos");

        int newPhotoCount = AdminModel.getModel().getLastScanPhotos().size();
        _lastScanOnlyCheckBox.setText(newPhotoCount == 0 ? "New from last scan" : "New from last scan (" + newPhotoCount + ")");
        _lastScanOnlyCheckBox.setEnabled(newPhotoCount > 0);
    }

    private void selectPhotoIndex(int index)
    {
        _photoList.setSelectedIndex(index);
        Rectangle bounds = _photoList.getCellBounds(index, index);
        if (bounds != null)
        {
            _photoList.scrollRectToVisible(bounds);
        }
    }

    private void refreshDefaultPhotoButtonStatus()
    {
        _setDefaultPhotoButton.setEnabled(AdminModel.getModel().getCurrentPhotos().size() == 1 && _categoryTree.getSelectionCount() > 0);
    }
}