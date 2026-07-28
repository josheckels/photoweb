/*
 * PhotoInfoPanel.java
 *
 * Created on April 15, 2002, 9:57 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.gui.AbstractPanel;
import com.stampysoft.gui.HiDpi;
import com.stampysoft.photoGallery.*;
import com.stampysoft.photoGallery.common.Resolution;
import com.stampysoft.photoGallery.faces.FaceNameCombo;
import com.stampysoft.photoGallery.faces.PeopleService;
import com.stampysoft.photoGallery.faces.PhotoFace;
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
    private final SpellCheckPane _captionTextArea = new SpellCheckPane(2);
    private final JCheckBox _privateCheckBox = new JCheckBox("Private");
    private final JCheckBox _showFacesCheckBox = new JCheckBox("Show faces", true);
    private final CategoryListModel _categoryListModel = new CategoryListModel();
    private final JList<Category> _categoryList = new JList<>(_categoryListModel);
    private final JButton _saveButton = new JButton("Save");
    private final JButton _deleteButton = new JButton("Delete");
    private final JButton _scanForNewPhotosButton = new JButton("Scan for new photos");
    private final JButton _renameButton = new JButton("Rename files");
    private final PhotographerPanel _photographerPanel = new PhotographerPanel();

    private TreeSet<Category> _originalCategories = null;

    /** Bumped on the EDT every time the displayed photo changes, so that a slow load can't overwrite a newer one. */
    private int _thumbnailGeneration = 0;

    /** The photo currently drawn in the preview, which is not always the only selected one. */
    private Photo _displayedPhoto = null;

    /** Where each face landed in the displayed image, so that a click can be turned back into a face. */
    private java.util.List<FaceBox> _faceBoxes = java.util.List.of();

    /** Whether the preview/form divider has been placed at a real window size yet. */
    private boolean _formHeightSet = false;

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

        _photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        _photoLabel.setVerticalAlignment(SwingConstants.CENTER);
        // A starting size, not a fixed one, and no scroll pane around it. The preview is always scaled down to fit
        // the label, so it never has anything to scroll; what the viewport did do was hold the label at exactly its
        // preferred size, which pinned the photo to 700 points however much room the window had.
        _photoLabel.setPreferredSize(new Dimension(Photo.DEFAULT_MAX_DIMENSION, Photo.DEFAULT_MAX_DIMENSION));
        _photoLabel.setMinimumSize(new Dimension(120, 120));
        immutablePanel.add(_photoLabel, BorderLayout.CENTER);

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

        JLabel captionLabel = new JLabel("Caption: ");
        captionLabel.setDisplayedMnemonic('a');
        captionLabel.setLabelFor(_captionTextArea);
        mutablePanel.add(captionLabel, labelGBC);
        mutablePanel.add(captionScrollPane, valueGBC);

        mutablePanel.add(new JLabel(), labelGBC);
        JPanel flagsPanel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        flagsPanel.add(_privateCheckBox);
        _showFacesCheckBox.setToolTipText("Outline detected faces on the preview. Click a face to name it, or " +
                "right-click for the people already tagged on this photo.");
        flagsPanel.add(_showFacesCheckBox);
        mutablePanel.add(flagsPanel, valueGBC);

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

        // A split rather than NORTH/CENTER. NORTH grants the preview exactly its preferred height and not a pixel
        // more, so the photo stayed the same size no matter how much room this side of the window had. Now the
        // photo takes the extra space, and the divider is there to give it back to the form.
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, immutablePanel, mutablePanel);
        splitPane.setResizeWeight(1.0);
        startFormAtPreferredHeight(splitPane, mutablePanel);
        add(splitPane, BorderLayout.CENTER);
    }

    /**
     * Gives the form the height it asks for and the photo everything else, once.
     * <p>
     * AdminFrame sizes the window to 100 pixels tall before maximizing it, and a divider placed while it's that
     * small is clamped to nothing; the resize weight would then hand all the recovered height to the photo and
     * leave the form squashed at its minimum. So wait until there's room for both and place it properly.
     */
    private void startFormAtPreferredHeight(JSplitPane splitPane, JComponent form)
    {
        splitPane.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                int formHeight = form.getPreferredSize().height;
                if (_formHeightSet || splitPane.getHeight() < formHeight + _photoLabel.getMinimumSize().height)
                {
                    return;
                }
                _formHeightSet = true;
                splitPane.setDividerLocation(splitPane.getHeight() - formHeight - splitPane.getDividerSize());
            }
        });
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

                    _originalCategories = new TreeSet<>(AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(photo, true));
                    showThumbnail(photo);
                }
                else
                {
                    if (newPhotos.size() > 1)
                    {
                        // Show the photo the user most recently clicked or arrowed onto, rather than the first one
                        // in the list, so that each photo added to the selection is visible as it's added.
                        final Photo leadPhoto = findLeadPhoto(newPhotos);
                        AdminFrame.getFrame().setTitle("Photo Gallery: " + newPhotos.size() + " photos selected - showing " + leadPhoto.getFilename());
                        java.util.Set<Category> firstCats = AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(newPhotos.get(0), true);
                        TreeSet<Category> commonCategories = new TreeSet<>(firstCats);
                        for (int i = 1; i < newPhotos.size(); i++)
                        {
                            java.util.Set<Category> cats = AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(newPhotos.get(i), true);
                            commonCategories.removeIf(category -> !cats.contains(category));
                        }
                        _categoryListModel.setCategories(commonCategories);
                        _originalCategories = new TreeSet<>(commonCategories);
                        showThumbnail(leadPhoto);
                    }
                    else
                    {
                        AdminFrame.getFrame().setTitle("Photo Gallery");
                        _categoryListModel.setCategories(new TreeSet<>());
                        showThumbnail(null);
                    }
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

        _showFacesCheckBox.addActionListener(e -> {
            if (_displayedPhoto != null)
            {
                showThumbnail(_displayedPhoto);
            }
        });

        // The preview is rendered to fit the label, so now that the label can change size the image has to be built
        // again to match - otherwise dragging a divider just letterboxes the old one. Coalesced through a timer
        // because a drag fires a resize per pixel and each render decodes a JPEG.
        javax.swing.Timer resizeTimer = new javax.swing.Timer(150, e -> {
            if (_displayedPhoto != null)
            {
                showThumbnail(_displayedPhoto);
            }
        });
        resizeTimer.setRepeats(false);
        _photoLabel.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                resizeTimer.restart();
            }
        });

        _photoLabel.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                // Left-click goes straight to the name box; the popup trigger is handled below instead
                if (e.getButton() != MouseEvent.BUTTON1 || e.isPopupTrigger())
                {
                    return;
                }
                PhotoFace face = findFaceAt(e.getPoint());
                if (face != null)
                {
                    assignFace(face);
                }
            }

            @Override
            public void mousePressed(MouseEvent e)
            {
                showFaceMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                showFaceMenu(e);
            }

            /** Which of press and release is the popup trigger is platform-specific, so both are checked. */
            private void showFaceMenu(MouseEvent e)
            {
                if (!e.isPopupTrigger())
                {
                    return;
                }
                PhotoFace face = findFaceAt(e.getPoint());
                if (face != null)
                {
                    buildFaceMenu(face).show(_photoLabel, e.getX(), e.getY());
                }
            }
        });

        AdminModel.getModel().addFaceListener(new FaceListener()
        {
            @Override
            public void facesChanged()
            {
                if (_displayedPhoto != null)
                {
                    showThumbnail(_displayedPhoto);
                }
            }
        });
    }

    /**
     * The face drawn under this point in the label, or null.
     * <p>
     * The icon is centred in the label, so the click has to be shifted by the letterboxing before it means anything
     * in image coordinates.
     */
    private PhotoFace findFaceAt(java.awt.Point point)
    {
        Icon icon = _photoLabel.getIcon();
        if (icon == null || _faceBoxes.isEmpty())
        {
            return null;
        }
        int offsetX = (_photoLabel.getWidth() - icon.getIconWidth()) / 2;
        int offsetY = (_photoLabel.getHeight() - icon.getIconHeight()) / 2;
        int imageX = point.x - offsetX;
        int imageY = point.y - offsetY;

        for (FaceBox box : _faceBoxes)
        {
            if (box.bounds().contains(imageX, imageY))
            {
                return box.face();
            }
        }
        return null;
    }

    /**
     * Returns the element of the selection that the user most recently clicked or arrowed onto, falling back to the
     * first one. The list has already been merged, so we look up the equal element rather than using the model's
     * reference directly.
     */
    private Photo findLeadPhoto(java.util.List<Photo> photos)
    {
        Photo leadPhoto = AdminModel.getModel().getLeadPhoto();
        int index = leadPhoto == null ? -1 : photos.indexOf(leadPhoto);
        return index >= 0 ? photos.get(index) : photos.get(0);
    }

    /**
     * Loads and displays the given photo in the background. Pass null to clear the image. Must be called on the EDT.
     */
    private void showThumbnail(final Photo photo)
    {
        final int generation = ++_thumbnailGeneration;
        _displayedPhoto = photo;
        _faceBoxes = java.util.List.of();

        if (photo == null)
        {
            _photoLabel.setIcon(null);
            return;
        }

        final boolean showFaces = _showFacesCheckBox.isSelected();
        // Both are read here because the EDT owns them, and the worker below needs to know how many device pixels
        // the label really covers before it decides how big to render. Before the first layout there's no size to
        // read, so start from the preferred one and let the resize listener re-render at the real one.
        Dimension size = _photoLabel.getSize();
        final Dimension labelSize = size.width > 0 && size.height > 0 ? size : _photoLabel.getPreferredSize();
        final double deviceScale = HiDpi.getDeviceScale(_photoLabel);

        Runnable r = () -> {
            Icon thumbnail = null;
            java.util.List<FaceBox> boxes = java.util.List.of();
            try
            {
                photo.ensureAllResized();
                Preview preview = createPreview(photo, labelSize, deviceScale);
                BufferedImage scaled = preview.image();
                Dimension logicalSize = preview.logicalSize();
                if (showFaces)
                {
                    java.util.List<PhotoFace> faces = loadFaces(photo);
                    // Laid out in logical pixels so that a click maps straight back onto a box, then drawn through
                    // a scaled transform so the outlines and names come out at the panel's real resolution.
                    boxes = layOutFaces(faces, logicalSize.width, logicalSize.height);
                    if (!boxes.isEmpty())
                    {
                        drawFaceBoxes(scaled, boxes, (double) scaled.getWidth() / logicalSize.width);
                    }
                }
                thumbnail = HiDpi.createIcon(scaled, logicalSize.width, logicalSize.height);
            }
            catch (IOException | PhotoManipulationException e)
            {
                handleException(e);
            }

            final Icon icon = thumbnail;
            final java.util.List<FaceBox> loadedBoxes = boxes;
            SwingUtilities.invokeLater(() -> {
                // Ignore a load that finished after the selection moved on
                if (generation == _thumbnailGeneration)
                {
                    _photoLabel.setIcon(icon);
                    _faceBoxes = loadedBoxes;
                }
            });
        };
        new Thread(r).start();
    }

    /**
     * The faces on this photo, or nothing at all if face data isn't available - the tables are created by hand, so
     * a missing schema has to leave the rest of this panel working.
     */
    private java.util.List<PhotoFace> loadFaces(Photo photo)
    {
        if (photo.getPhotoId() == null)
        {
            return java.util.List.of();
        }
        try
        {
            return AdminFrame.getFrame().getFaceOperations().getFacesForPhoto(photo.getPhotoId());
        }
        catch (RuntimeException e)
        {
            return java.util.List.of();
        }
    }

    /** Turns each face's normalized box into pixel coordinates in the displayed image. */
    private java.util.List<FaceBox> layOutFaces(java.util.List<PhotoFace> faces, int width, int height)
    {
        java.util.List<FaceBox> boxes = new java.util.ArrayList<>();
        for (PhotoFace face : faces)
        {
            int x = Math.round(face.getX() * width);
            int y = Math.round(face.getY() * height);
            int faceWidth = Math.max(1, Math.round(face.getW() * width));
            int faceHeight = Math.max(1, Math.round(face.getH() * height));
            boxes.add(new FaceBox(face, new Rectangle(x, y, faceWidth, faceHeight)));
        }
        return boxes;
    }

    /**
     * Outlines every face on the preview: named and confirmed in green, proposed in amber, and unmatched in red
     * with a question mark. Doubles as a check on detection quality while editing photos normally.
     */
    private void drawFaceBoxes(BufferedImage image, java.util.List<FaceBox> boxes, double overlayScale)
    {
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            // The boxes are in logical pixels, so everything below - including the stroke width and the font - is
            // written as though the image were that size and comes out at full resolution instead of being blown up.
            graphics.scale(overlayScale, overlayScale);
            graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 12f));
            for (FaceBox box : boxes)
            {
                PhotoFace face = box.face();
                Color color;
                String label;
                if (face.getPersonCategory() == null)
                {
                    color = new Color(220, 60, 60);
                    label = "?";
                }
                else if (face.isConfirmed())
                {
                    color = new Color(60, 190, 90);
                    label = face.getPersonCategory().getDescription();
                }
                else
                {
                    color = new Color(240, 180, 40);
                    label = face.getPersonCategory().getDescription() +
                            (face.getMatchScore() == null ? "?" : String.format(" %.2f?", face.getMatchScore()));
                }

                Rectangle bounds = box.bounds();
                // In logical units, so this is 3 device pixels of crisp line on a retina screen rather than 2 fat ones
                graphics.setStroke(new BasicStroke(1.5f));
                graphics.setColor(color);
                graphics.drawRect(bounds.x, bounds.y, bounds.width, bounds.height);

                int textWidth = graphics.getFontMetrics().stringWidth(label) + 6;
                int textHeight = graphics.getFontMetrics().getHeight();
                int textY = Math.max(0, bounds.y - textHeight);
                graphics.fillRect(bounds.x, textY, textWidth, textHeight);
                graphics.setColor(Color.BLACK);
                graphics.drawString(label, bounds.x + 3, textY + graphics.getFontMetrics().getAscent());
            }
        }
        finally
        {
            graphics.dispose();
        }
    }

    /**
     * Asks who a clicked face is and confirms the answer.
     * <p>
     * This is the path for somebody who appears in two photos and will never form a cluster, and the way to seed a
     * person who came out of the feasibility check with no solo photos.
     */
    private void assignFace(PhotoFace face)
    {
        PeopleService peopleService = AdminFrame.getFrame().getPeopleService();
        String missing = peopleService.describeMissingConfiguration();
        if (missing != null)
        {
            JOptionPane.showMessageDialog(this, missing, "People category not configured", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JComboBox<String> combo = FaceNameCombo.create(peopleService.getAllPeople());
        if (face.getPersonCategory() != null)
        {
            combo.setSelectedItem(face.getPersonCategory().getDescription());
        }

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Who is this?"), BorderLayout.NORTH);
        panel.add(combo, BorderLayout.CENTER);

        if (JOptionPane.showConfirmDialog(this, panel, "Assign face", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }

        Category person = FaceNameCombo.resolvePerson(FaceNameCombo.getTypedName(combo), peopleService, this);
        if (person == null)
        {
            return;
        }
        assignFaceTo(face, person);
    }

    /**
     * Confirms this face as this person, recording a rejection first if it had been proposed as somebody else so
     * that the next propagation round doesn't offer the same wrong answer again.
     */
    private void assignFaceTo(PhotoFace face, Category person)
    {
        Category previous = face.getPersonCategory();
        if (previous != null && !previous.getCategoryId().equals(person.getCategoryId()))
        {
            AdminFrame.getFrame().getFaceOperations().rejectFace(face, previous.getCategoryId());
        }

        // Confirming tags the photo, so flush whatever's pending in this panel first rather than having the
        // category list reloaded out from under unsaved edits.
        saveCurrentPhoto();
        AdminFrame.getFrame().getPeopleService().assignFace(face, person);
        keepSelectionInStepWithTag(person);

        AdminModel.getModel().fireCategoryChanged(person);
        refreshAfterFaceChange();
    }

    /** Records that this face is not who it was matched to, sending it back to the unknown pool. */
    private void rejectFaceAssignment(PhotoFace face)
    {
        Category previous = face.getPersonCategory();
        if (previous == null)
        {
            return;
        }
        AdminFrame.getFrame().getFaceOperations().rejectFace(face, previous.getCategoryId());
        refreshAfterFaceChange();
    }

    /**
     * Brings the category list back in line with what was just written, then lets the face-changed event redraw the
     * overlay. Redrawing isn't done here as well, because that would re-read and re-scale the 1400px preview twice.
     */
    private void refreshAfterFaceChange()
    {
        Photo photo = _displayedPhoto;
        if (photo != null)
        {
            _categoryListModel.setCategories(new TreeSet<>(AdminFrame.getFrame().getPhotoOperations().getInitializedCategories(photo, true)));
            _originalCategories = new TreeSet<>(_categoryListModel.getCategories());
        }
        AdminModel.getModel().fireFacesChanged();
    }

    /**
     * The right-click menu for a face on the preview, mirroring the review grid's: confirm or reject what was
     * proposed, or name the face outright.
     * <p>
     * The one-click names come from the people already tagged on this photo who don't yet have a face here - which
     * is the most useful list there is at this point, since the photo's own category list is the ground truth
     * sitting a few pixels away.
     */
    private JPopupMenu buildFaceMenu(PhotoFace face)
    {
        JPopupMenu menu = new JPopupMenu();
        Category current = face.getPersonCategory();

        if (current != null && !face.isConfirmed())
        {
            JMenuItem confirmItem = new JMenuItem("Yes, this is " + current.getDescription());
            confirmItem.addActionListener(e -> assignFaceTo(face, current));
            menu.add(confirmItem);
        }
        if (current != null)
        {
            JMenuItem rejectItem = new JMenuItem("No, this isn't " + current.getDescription());
            rejectItem.addActionListener(e -> rejectFaceAssignment(face));
            menu.add(rejectItem);
        }
        if (menu.getComponentCount() > 0)
        {
            menu.addSeparator();
        }

        for (Category candidate : getTaggedPeopleWithoutAFace(face))
        {
            JMenuItem item = new JMenuItem("This is " + candidate.getDescription());
            item.setToolTipText("Tagged on this photo, with no face matched to them yet");
            item.addActionListener(e -> assignFaceTo(face, candidate));
            menu.add(item);
        }

        JMenuItem otherItem = new JMenuItem("This is someone else...");
        otherItem.addActionListener(e -> assignFace(face));
        menu.add(otherItem);
        return menu;
    }

    /**
     * The person categories on this photo that no confirmed face has claimed yet.
     * <p>
     * Everything needed is already on screen: the photo's categories are in the list model and every face is in
     * {@link #_faceBoxes}, so this costs one query for the People subtree rather than anything per face. People
     * already <em>confirmed</em> on another face here are excluded by the one-person-per-photo rule; unconfirmed
     * proposals are not, since correcting those is the point.
     */
    private java.util.List<Category> getTaggedPeopleWithoutAFace(PhotoFace face)
    {
        java.util.Set<Integer> personIds = AdminFrame.getFrame().getPeopleService().getPersonCategoryIds();

        java.util.Set<Integer> spokenFor = new java.util.HashSet<>();
        for (FaceBox box : _faceBoxes)
        {
            PhotoFace other = box.face();
            if (!other.getFaceId().equals(face.getFaceId()) && other.isConfirmed() && other.getPersonCategory() != null)
            {
                spokenFor.add(other.getPersonCategory().getCategoryId());
            }
        }

        Category current = face.getPersonCategory();
        java.util.List<Category> result = new java.util.ArrayList<>();
        for (Category category : _categoryListModel.getCategories())
        {
            Integer categoryId = category.getCategoryId();
            if (!personIds.contains(categoryId) || spokenFor.contains(categoryId))
            {
                continue;
            }
            if (current != null && current.getCategoryId().equals(categoryId))
            {
                continue;
            }
            result.add(category);
        }
        result.sort(java.util.Comparator.comparing(Category::getDescription, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Adds the newly tagged person to the in-memory copies of the selected photos.
     * <p>
     * Confirming a face writes the category link straight to the database, which leaves the detached Photo objects
     * this panel is holding one category out of date. The next selection change saves them, and merging a detached
     * photo whose categories collection is initialized but stale makes Hibernate re-synchronize the join table
     * against it - quietly deleting the link that was just written. That's the second-merge hazard
     * {@link PhotoOperations#updatePhotoCategories} warns about, reached the long way round.
     */
    private void keepSelectionInStepWithTag(Category person)
    {
        for (Photo selectedPhoto : AdminModel.getModel().getCurrentPhotos())
        {
            try
            {
                selectedPhoto.getCategories(true).add(person);
            }
            catch (RuntimeException ignored)
            {
                // An uninitialized collection can't go stale - merge leaves those alone - so there's nothing to fix
            }
        }
    }

    /** A face and where it was drawn, so a click on the preview can be resolved back to it. */
    private record FaceBox(PhotoFace face, Rectangle bounds)
    {
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
                for (Photo photo : photos)
                {
                    AdminModel.getModel().savePhoto(photo, _categoryListModel.isChanged());
                }
            }
            else if (photos.size() > 1)
            {
                // Compute the category delta relative to the categories common to all selected photos.
                java.util.List<Category> toAdd = new java.util.ArrayList<>();
                for (Category category : _categoryListModel.getCategories())
                {
                    if (!_originalCategories.contains(category))
                    {
                        toAdd.add(category);
                    }
                }
                java.util.List<Category> toRemove = new java.util.ArrayList<>();
                for (Category category : _originalCategories)
                {
                    if (!_categoryListModel.getCategories().contains(category))
                    {
                        toRemove.add(category);
                    }
                }

                // Apply the delta and persist each photo in a single transaction on one managed instance.
                // Doing this here (rather than mutating the join table and then merging the detached photo
                // again via savePhoto) avoids a second merge that would revert these category changes.
                for (int i = 0; i < photos.size(); i++)
                {
                    Photo saved = AdminFrame.getFrame().getPhotoOperations().updatePhotoCategories(photos.get(i), toAdd, toRemove);
                    photos.set(i, saved);
                    AdminModel.getModel().firePhotoChanged(saved, _categoryListModel.isChanged());
                }

                _originalCategories = new TreeSet<>(_categoryListModel.getCategories());
            }
        }
        catch (SystemException | PhotoNotFoundException ex)
        {
            handleException(ex);
        }
    }


    /**
     * The rendered preview, along with the size it will occupy on screen. The image itself is at the display's
     * device resolution, which on a retina monitor is bigger than the logical size by the backing scale factor.
     */
    private record Preview(BufferedImage image, Dimension logicalSize) {}

    /**
     * Reads a resized copy of the photo and scales it to fill the preview at the monitor's real resolution.
     * <p>
     * The image is deliberately rendered larger than the space it occupies: a label 700 points wide on a retina
     * screen is 1400 pixels of glass, and handing Swing a 700 pixel image just makes the compositor blow it up.
     * Returns a BufferedImage rather than an Icon so that face boxes can be painted onto it before it's shown.
     */
    private Preview createPreview(Photo photo, Dimension labelSize, double deviceScale) throws IOException
    {
        // The source has to have at least as many pixels as the screen will show, or the extra resolution is
        // wasted upscaling. ensureAllResized has already written both of these out.
        int wanted = (int) Math.ceil(Math.max(labelSize.width, labelSize.height) * deviceScale);
        Resolution res = wanted > Photo.RETINA_DEFAULT_MAX_DIMENSION ? photo.getLargeRetinaDimensions() : photo.getRetinaDimensions();
        BufferedImage img = ImageIO.read(new File(PhotoOperations.getPhotoOperations().toURI(res.getURI())));

        Dimension imageSize = new Dimension(img.getWidth(), img.getHeight());
        Dimension logical = getScaledDimension(imageSize, labelSize);
        // A label that hasn't been laid out yet has no size, and everything below divides by this
        logical.width = Math.max(1, logical.width);
        logical.height = Math.max(1, logical.height);

        // Render at device resolution, but never resample beyond what the source actually holds - past that point
        // the icon just draws the native pixels into a larger box, which is no worse than before.
        int width = Math.min(imageSize.width, HiDpi.toDevicePixels(logical.width, deviceScale));
        int height = Math.min(imageSize.height, HiDpi.toDevicePixels(logical.height, deviceScale));
        return new Preview(resample(img, width, height), logical);
    }

    /**
     * Scales an image down with reasonable quality.
     * <p>
     * Halving repeatedly before the last step keeps a big reduction from aliasing, which a single bicubic pass on
     * its own would do; it's also much faster than Image.SCALE_SMOOTH on images this size.
     */
    private static BufferedImage resample(BufferedImage img, int targetWidth, int targetHeight)
    {
        BufferedImage current = img;
        int width = img.getWidth();
        int height = img.getHeight();

        while (width > targetWidth * 2 && height > targetHeight * 2)
        {
            width /= 2;
            height /= 2;
            current = drawResized(current, width, height);
        }
        return drawResized(current, targetWidth, targetHeight);
    }

    private static BufferedImage drawResized(BufferedImage img, int width, int height)
    {
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(img, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }
        return scaled;
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