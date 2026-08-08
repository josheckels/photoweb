package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.photoGallery.admin.AdminModel;
import com.stampysoft.photoGallery.admin.FaceListener;
import com.stampysoft.photoGallery.faces.FaceOperations.ClusterSummary;
import com.stampysoft.photoGallery.faces.FaceOperations.PersonPendingCount;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * The People tab: where the value of the whole face pipeline actually lands, since nothing upstream ever writes a
 * confirmed tag unattended.
 * <p>
 * Three sub-views, matching the three things there are to do: work through the proposals, name the recurring people
 * who were never tagged, and see who still needs seeding.
 */
public class PeoplePanel extends JPanel
{
    private static final int REVIEW_CROP_SIZE = 96;
    private static final int CLUSTER_CROP_SIZE = 72;
    private static final int MAX_REVIEW_FACES = 300;
    private static final int MAX_CLUSTER_FACES = 200;

    private final FaceOperations _faceOperations;
    private final PeopleService _peopleService;
    private final FaceMatcher _faceMatcher;
    private final FaceThumbnailCache _thumbnails = new FaceThumbnailCache();

    /** Called with the number of outstanding proposals so the tab itself can carry the badge. */
    private final IntConsumer _pendingCountConsumer;

    private final JLabel _statusLabel = new JLabel(" ");
    private final JButton _scanButton = new JButton("Scan for faces...");
    private final JButton _matchButton = new JButton("Match faces");
    private final JButton _rematchButton = new JButton("Re-run matching");
    private final JButton _rescanButton = new JButton("Re-scan everything...");

    private final DefaultComboBoxModel<PersonPendingCount> _reviewPeopleModel = new DefaultComboBoxModel<>();
    private final JComboBox<PersonPendingCount> _reviewPeopleCombo = new JComboBox<>(_reviewPeopleModel);
    private final DefaultListModel<PhotoFace> _reviewFacesModel = new DefaultListModel<>();
    private final JList<PhotoFace> _reviewFacesList = new JList<>(_reviewFacesModel);
    private final JButton _confirmButton = new JButton("Confirm selected");
    private final JButton _rejectButton = new JButton("Reject selected");
    private final JButton _reassignButton = new JButton("Someone else...");
    private final JButton _selectAllButton = new JButton("Select all");
    private final JButton _selectCorroboratedButton = new JButton("Select ✓");

    private final DefaultListModel<ClusterSummary> _clustersModel = new DefaultListModel<>();
    private final JList<ClusterSummary> _clustersList = new JList<>(_clustersModel);
    private final DefaultListModel<PhotoFace> _clusterFacesModel = new DefaultListModel<>();
    private final JList<PhotoFace> _clusterFacesList = new JList<>(_clusterFacesModel);
    private final FaceNameCombo _clusterNameCombo = new FaceNameCombo();
    private final JButton _assignClusterButton = new JButton("This is...");
    private final JButton _ignoreClusterButton = new JButton("Nobody");

    private final PeopleTableModel _peopleTableModel = new PeopleTableModel();
    private final JTable _peopleTable = new JTable(_peopleTableModel);
    private final JButton _reviewPersonButton = new JButton("Review");
    private final JButton _findMoreButton = new JButton("Find more");
    private final JButton _mergeButton = new JButton("Merge into...");

    /** People by category id, refreshed with the rest of the panel so lookups don't need a query each. */
    private Map<Integer, Category> _peopleById = new LinkedHashMap<>();

    /** One face per cluster to show as its thumbnail, resolved up front rather than from inside a cell renderer. */
    private Map<Integer, PhotoFace> _clusterRepresentatives = new HashMap<>();

    /** Suppresses the combo box's own change events while the model is being rebuilt. */
    private boolean _refreshing;

    private final JTabbedPane _subTabs = new JTabbedPane();

    /**
     * Whether the two sub-tabs the user isn't looking at need recomputing.
     * <p>
     * Their queries are the expensive ones - a GROUP BY over every face for the clusters, and a NOT EXISTS
     * aggregate over the whole photo_category_link table for the "tagged, no face" column - and rejecting one face
     * doesn't change either enough to be worth paying for. They catch up when they're next shown.
     */
    private boolean _clustersStale = true;
    private boolean _peopleTableStale = true;

    /** Summed while rebuilding the review combo, so the status line doesn't need its own count query. */
    private int _pendingTotal;

    /**
     * The photos in the current review queue that are already tagged with the person being reviewed.
     * <p>
     * A proposal on one of these is corroborated by hand-tagging rather than resting on the embedding alone, so it
     * is about as close to certain as this pipeline gets - worth making visible at a glance.
     */
    private Set<Integer> _corroboratedPhotoIds = new HashSet<>();

    private static final Color CORROBORATED_COLOR = new Color(0, 140, 60);
    private static final Border CORROBORATED_BORDER = BorderFactory.createMatteBorder(1, 1, 1, 1, CORROBORATED_COLOR);
    /** Same thickness as the corroborated border, so cells don't shift depending on which they get. */
    private static final Border PLAIN_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    public PeoplePanel(FaceOperations faceOperations, PeopleService peopleService, IntConsumer pendingCountConsumer)
    {
        _faceOperations = faceOperations;
        _peopleService = peopleService;
        _faceMatcher = new FaceMatcher(faceOperations, peopleService);
        _pendingCountConsumer = pendingCountConsumer;

        addComponents();
        addListeners();
        refresh();
    }

    private void addComponents()
    {
        setLayout(new BorderLayout());

        // WrapLayout, not FlowLayout: this tab lives in the narrow left column, and a plain FlowLayout would lay
        // the overflow out below the one row of height BorderLayout.NORTH grants it, hiding it completely.
        JPanel toolbar = new JPanel();
        WrapLayout.install(toolbar, 4, 2);
        toolbar.add(_scanButton);
        toolbar.add(_matchButton);
        toolbar.add(_rematchButton);
        _rescanButton.setToolTipText("Throw away every detection and start over. Needed after swapping the models.");
        toolbar.add(_rescanButton);

        JPanel header = new JPanel(new BorderLayout());
        header.add(toolbar, BorderLayout.NORTH);
        _statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 4, 6));
        header.add(_statusLabel, BorderLayout.SOUTH);

        _subTabs.addTab("Review queue", createReviewPanel());
        _subTabs.addTab("Unknown clusters", createClustersPanel());
        _subTabs.addTab("People", createPeopleListPanel());
        _subTabs.addChangeListener(e -> refreshStaleVisibleSubTab());

        add(header, BorderLayout.NORTH);
        add(_subTabs, BorderLayout.CENTER);

        // Everything in here scrolls or wraps, so none of it needs a wide column to stay usable. Saying so is what
        // lets the divider between the two halves of the window travel left - a split pane won't shrink a component
        // past its minimum, and left to itself this panel would claim the width of a whole unwrapped toolbar.
        setMinimumSize(new Dimension(240, 200));
    }

    private JPanel createReviewPanel()
    {
        _reviewFacesList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        _reviewFacesList.setVisibleRowCount(-1);
        _reviewFacesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        _reviewFacesList.setFixedCellWidth(REVIEW_CROP_SIZE + 16);
        _reviewFacesList.setFixedCellHeight(REVIEW_CROP_SIZE + 26);
        _reviewFacesList.setCellRenderer(new FaceCellRenderer(REVIEW_CROP_SIZE, true));
        _reviewFacesList.setToolTipText("Proposals for this person, best match first. Select and confirm or reject; double-click to open the photo.");

        _reviewPeopleCombo.setRenderer(new PendingCountRenderer());

        _confirmButton.setMnemonic('C');
        _confirmButton.setToolTipText("These are the right person. Confirms the match and tags the photo. (Enter)");
        _rejectButton.setMnemonic('J');
        _rejectButton.setToolTipText("These are NOT this person. Remembers the rejection so it's never proposed again. (Delete)");

        JPanel controls = new JPanel();
        WrapLayout.install(controls, 4, 2);
        controls.add(new JLabel("Person: "));
        controls.add(_reviewPeopleCombo);
        controls.add(_selectAllButton);
        _selectCorroboratedButton.setForeground(CORROBORATED_COLOR);
        _selectCorroboratedButton.setToolTipText("Select every proposal whose photo you'd already tagged with this " +
                "person. Your own tags agree with these, so they're the safe ones to confirm as a block.");
        controls.add(_selectCorroboratedButton);
        controls.add(_confirmButton);
        controls.add(_rejectButton);
        controls.add(_reassignButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(_reviewFacesList), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createClustersPanel()
    {
        _clustersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        _clustersList.setFixedCellHeight(CLUSTER_CROP_SIZE + 12);
        _clustersList.setCellRenderer(new ClusterCellRenderer());
        _clustersList.setToolTipText("Faces that matched nobody, grouped. Biggest group first.");

        _clusterFacesList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        _clusterFacesList.setVisibleRowCount(-1);
        // Multi-select so that the odd face that doesn't belong with the rest can be marked as nobody in one go
        _clusterFacesList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        _clusterFacesList.setFixedCellWidth(CLUSTER_CROP_SIZE + 16);
        _clusterFacesList.setFixedCellHeight(CLUSTER_CROP_SIZE + 26);
        _clusterFacesList.setCellRenderer(new FaceCellRenderer(CLUSTER_CROP_SIZE, false));

        // Editable and autocompleting on purpose: an "unknown" cluster is very often somebody who already has a
        // category but had no seeds, and picking them from the list merges instead of creating a duplicate.
        JComboBox<String> clusterNameField = _clusterNameCombo.getComponent();
        clusterNameField.setPreferredSize(new Dimension(200, clusterNameField.getPreferredSize().height));

        _ignoreClusterButton.setToolTipText("Nobody at all - a poster, a stranger in the background, or a bad " +
                "detection. Every face in this cluster stops being proposed as anyone.");

        JPanel namePanel = new JPanel();
        WrapLayout.install(namePanel, 4, 2);
        namePanel.add(new JLabel("Name: "));
        namePanel.add(clusterNameField);
        namePanel.add(_assignClusterButton);
        namePanel.add(_ignoreClusterButton);

        JPanel facesPanel = new JPanel(new BorderLayout());
        facesPanel.add(namePanel, BorderLayout.NORTH);
        facesPanel.add(new JScrollPane(_clusterFacesList), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(_clustersList), facesPanel);
        split.setResizeWeight(0.3);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createPeopleListPanel()
    {
        _peopleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        _peopleTable.setAutoCreateRowSorter(true);
        // Seven columns don't fit the left column, so let the table scroll sideways rather than squeezing every
        // number down to an ellipsis.
        _peopleTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int column = 0; column < _peopleTable.getColumnCount(); column++)
        {
            _peopleTable.getColumnModel().getColumn(column).setPreferredWidth(column == 0 ? 160 : 90);
        }
        // Those column widths add up to some 700 pixels, and a scroll pane asks for whatever its table asks for.
        // Left alone that made the People tab the widest thing in the tabbed pane, which in turn set the width of
        // the whole left column. Scrolling sideways is already the plan here, so ask for a column's worth instead.
        _peopleTable.setPreferredScrollableViewportSize(new Dimension(360, 200));

        _reviewPersonButton.setToolTipText("Open this person's proposals in the Review queue tab");
        _reviewPersonButton.addActionListener(e -> reviewSelectedPerson());
        _peopleTable.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1)
                {
                    reviewSelectedPerson();
                }
            }
        });

        JPanel controls = new JPanel();
        WrapLayout.install(controls, 4, 2);
        controls.add(_reviewPersonButton);
        controls.add(_findMoreButton);
        controls.add(_mergeButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(controls, BorderLayout.NORTH);
        panel.add(new JScrollPane(_peopleTable), BorderLayout.CENTER);
        return panel;
    }

    private void addListeners()
    {
        _scanButton.addActionListener(e -> runScan());
        _matchButton.addActionListener(e -> runMatching(false));
        _rematchButton.addActionListener(e -> runMatching(true));
        _rescanButton.addActionListener(e -> resetEverything());

        _reviewPeopleCombo.addActionListener(e -> {
            if (!_refreshing)
            {
                loadReviewQueue();
            }
        });
        _selectAllButton.addActionListener(e -> {
            if (_reviewFacesModel.getSize() > 0)
            {
                _reviewFacesList.setSelectionInterval(0, _reviewFacesModel.getSize() - 1);
                _reviewFacesList.requestFocusInWindow();
            }
        });
        _selectCorroboratedButton.addActionListener(e -> selectCorroborated());
        _confirmButton.addActionListener(e -> confirmSelectedProposals());
        _rejectButton.addActionListener(e -> rejectSelectedProposals());
        _reassignButton.addActionListener(e -> reassignSelectedProposals());
        _reviewFacesList.addMouseListener(new OpenPhotoOnDoubleClick(_reviewFacesList));
        installReviewShortcuts();

        _clustersList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
            {
                loadSelectedCluster();
            }
        });
        _clusterFacesList.addMouseListener(new OpenPhotoOnDoubleClick(_clusterFacesList));
        installClusterFaceMenu(_clusterFacesList);
        _assignClusterButton.addActionListener(e -> assignSelectedCluster());
        _ignoreClusterButton.addActionListener(e -> ignoreSelectedCluster());

        _findMoreButton.addActionListener(e -> findMoreForSelectedPerson());
        _mergeButton.addActionListener(e -> mergeSelectedPerson());

        AdminModel.getModel().addFaceListener(new FaceListener()
        {
            @Override
            public void facesChanged()
            {
                refresh();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------
    // Jobs
    // ------------------------------------------------------------------------------------------------

    private void runScan()
    {
        String missing = FaceEncoder.describeMissingConfiguration();
        if (missing != null)
        {
            JOptionPane.showMessageDialog(AdminFrame.getFrame(), missing, "Face models not configured", JOptionPane.WARNING_MESSAGE);
            return;
        }

        FaceScanJob job = new FaceScanJob(_faceOperations);
        Throwable failure = FaceProgressDialog.run("Detecting faces", job);
        // Detection replaces unconfirmed faces with new ids, so the cached crops are now dead weight
        _thumbnails.clear();
        AdminModel.getModel().fireFacesChanged();

        if (failure != null)
        {
            showError("Face detection failed", failure);
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("Scanned ").append(job.getPhotosScanned()).append(" photo(s) and found ")
                .append(job.getFacesFound()).append(" face(s).");
        List<String> problems = new ArrayList<>();
        List<String> failures = job.getFailures();
        synchronized (failures)
        {
            problems.addAll(failures);
        }
        if (!problems.isEmpty())
        {
            message.append("\n\n").append(problems.size()).append(" problem(s), for example:\n");
            for (String problem : problems.subList(0, Math.min(5, problems.size())))
            {
                message.append("  ").append(problem).append('\n');
            }
        }

        // Scanning nothing while collecting problems means the whole job failed - most likely OpenCV's natives
        // wouldn't load - so say so rather than reporting a cheerful zero.
        if (job.getPhotosScanned() == 0 && !problems.isEmpty())
        {
            message.append("\nNothing was scanned. If this mentions a library that won't load, the OpenCV natives\n" +
                    "for this platform are the problem - check the opencv classifier in pom.xml.");
            JOptionPane.showMessageDialog(AdminFrame.getFrame(), message.toString(), "Face detection failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (job.getPhotosScanned() > 0)
        {
            message.append("\nRun \"Match faces\" next to turn these into proposals.");
        }
        JOptionPane.showMessageDialog(AdminFrame.getFrame(), message.toString(), "Face detection", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Seeds, propagates and clusters in one pass.
     *
     * @param fromScratch when true, throws away the existing proposals and clusters first so that everything is
     *                    recomputed from confirmed faces only. Confirmations and rejections always survive.
     */
    private void runMatching(boolean fromScratch)
    {
        String missing = _peopleService.describeMissingConfiguration();
        if (missing != null)
        {
            JOptionPane.showMessageDialog(AdminFrame.getFrame(), missing, "People category not configured", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int[] results = new int[3];
        Throwable failure = FaceProgressDialog.run(fromScratch ? "Re-running matching" : "Matching faces", listener -> {
            if (fromScratch)
            {
                listener.progress("Clearing previous proposals", 0, 0);
                _faceOperations.clearProposals();
                _faceOperations.clearClusters();
            }
            listener.progress("Seeding from solo photos", 0, 0);
            results[0] = _faceMatcher.seedFromSoloPhotos();
            results[1] = _faceMatcher.propagate(listener);
            results[2] = _faceMatcher.cluster(listener);
        });

        AdminModel.getModel().fireFacesChanged();

        if (failure != null)
        {
            showError("Matching failed", failure);
            return;
        }

        JOptionPane.showMessageDialog(AdminFrame.getFrame(),
                "Seeded " + results[0] + " face(s) from solo photos.\n" +
                "Proposed " + results[1] + " match(es) for review.\n" +
                "Found " + results[2] + " unknown cluster(s) worth naming.",
                "Matching", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Clears the scan stamp and the detections so that the next scan redoes everything.
     * <p>
     * This is the path for swapping the embedding model: {@code model_version} is recorded per face precisely so
     * that better embeddings only cost a re-run of detection. Offers to keep confirmed faces, which is right for a
     * detection tweak and wrong for a model swap, since embeddings from two models aren't comparable.
     */
    private void resetEverything()
    {
        Object[] options = {"Keep confirmed faces", "Delete everything", "Cancel"};
        int answer = JOptionPane.showOptionDialog(this,
                "Clear detections so that the next scan redoes every photo.\n\n" +
                "Keep confirmed faces: keeps the matches you've confirmed and the faces you've marked as\n" +
                "nobody, and re-detects the rest.\n" +
                "Delete everything: also throws away confirmed faces, rejections and ignored faces. Do this after\n" +
                "swapping the embedding model, since embeddings from two models can't be compared.\n\n" +
                "Photo tags are never removed either way.",
                "Re-scan everything", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[2]);
        if (answer != 0 && answer != 1)
        {
            return;
        }
        boolean keepConfirmed = answer == 0;

        int[] cleared = new int[1];
        Throwable failure = FaceProgressDialog.run("Clearing face data", listener -> {
            listener.progress("Clearing face data", 0, 0);
            cleared[0] = _faceOperations.resetScan(keepConfirmed);
        });

        _thumbnails.clear();
        AdminModel.getModel().fireFacesChanged();

        if (failure != null)
        {
            showError("Clearing face data failed", failure);
            return;
        }
        JOptionPane.showMessageDialog(this, cleared[0] + " photo(s) will be re-scanned. Run \"Scan for faces\" next.",
                "Re-scan everything", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------------------------------------
    // Review queue
    // ------------------------------------------------------------------------------------------------

    /**
     * Puts confirm and reject on the keyboard and on a right-click menu over the grid.
     * <p>
     * Working a review queue is a two-key job - yes, no, yes, no - so requiring a trip to a button for each one
     * is the difference between the queue getting cleared and not. The context menu also means the actions are
     * discoverable from the faces themselves rather than only from the toolbar.
     */
    private void installReviewShortcuts()
    {
        _reviewFacesList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "confirmFaces");
        _reviewFacesList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "rejectFaces");
        _reviewFacesList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "rejectFaces");
        // Shift makes the rejection permanent and universal rather than about this one person
        _reviewFacesList.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_DOWN_MASK), "ignoreFaces");
        _reviewFacesList.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_DOWN_MASK), "ignoreFaces");
        _reviewFacesList.getActionMap().put("confirmFaces", new AbstractAction()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                confirmSelectedProposals();
            }
        });
        _reviewFacesList.getActionMap().put("rejectFaces", new AbstractAction()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                rejectSelectedProposals();
            }
        });
        _reviewFacesList.getActionMap().put("ignoreFaces", new AbstractAction()
        {
            public void actionPerformed(java.awt.event.ActionEvent e)
            {
                ignoreFaces(_reviewFacesList.getSelectedValuesList());
            }
        });

        // Rebuilt on every right-click, because the useful shortcuts depend on which face was clicked.
        attachPopupMenu(_reviewFacesList, this::buildReviewMenu);
    }

    private JPopupMenu buildReviewMenu()
    {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem confirmItem = new JMenuItem("Yes, this is them");
        confirmItem.addActionListener(e -> confirmSelectedProposals());
        menu.add(confirmItem);

        JMenuItem rejectItem = new JMenuItem("No, this isn't them");
        rejectItem.addActionListener(e -> rejectSelectedProposals());
        menu.add(rejectItem);

        JMenuItem ignoreItem = new JMenuItem("This is nobody - never match it");
        ignoreItem.setToolTipText("A poster, a stranger in the background, or a bad detection. It stops being " +
                "proposed as anyone at all. (Shift+Delete)");
        ignoreItem.addActionListener(e -> ignoreFaces(_reviewFacesList.getSelectedValuesList()));
        menu.add(ignoreItem);

        menu.addSeparator();
        addPeopleTaggedOnThisPhoto(menu);

        JMenuItem reassignItem = new JMenuItem("This is someone else...");
        reassignItem.addActionListener(e -> reassignSelectedProposals());
        menu.add(reassignItem);

        menu.addSeparator();
        JMenuItem openItem = new JMenuItem("Select this photo");
        openItem.addActionListener(e -> openPhoto(_reviewFacesList.getSelectedValue()));
        menu.add(openItem);

        return menu;
    }

    /**
     * Offers, as one click each, the people already tagged on this photo who don't yet have a face in it.
     * <p>
     * This is the overwhelmingly common shape of a wrong proposal: the photo is tagged Alice and Bob by hand, the
     * face is one of them, and the matcher picked the wrong one. Those people are already known to be present, so
     * naming them needs no typing and no dialog.
     * <p>
     * Only offered for a single selection, since which people are tagged is a property of one photo.
     */
    private void addPeopleTaggedOnThisPhoto(JPopupMenu menu)
    {
        PhotoFace face = _reviewFacesList.getSelectedValue();
        if (_reviewFacesList.getSelectedIndices().length != 1 || face == null || face.getPhoto() == null)
        {
            return;
        }
        Category proposedPerson = getSelectedReviewPerson();
        int photoId = face.getPhoto().getPhotoId();

        // Anyone already confirmed on another face here is out: two faces in one photo are never the same person.
        // Unconfirmed proposals don't count - they're guesses, and this menu exists to correct guesses.
        Set<Integer> spokenFor = new HashSet<>();
        for (PhotoFace other : _faceOperations.getFacesForPhoto(photoId))
        {
            if (!other.getFaceId().equals(face.getFaceId()) && other.isConfirmed() && other.getPersonCategory() != null)
            {
                spokenFor.add(other.getPersonCategory().getCategoryId());
            }
        }

        Set<Integer> taggedPeople = _faceOperations
                .getTaggedPeopleByPhoto(List.of(photoId), _peopleById.keySet())
                .getOrDefault(photoId, Set.of());

        List<Category> candidates = new ArrayList<>();
        for (Integer categoryId : taggedPeople)
        {
            if (spokenFor.contains(categoryId) || (proposedPerson != null && proposedPerson.getCategoryId().equals(categoryId)))
            {
                continue;
            }
            Category candidate = _peopleById.get(categoryId);
            if (candidate != null)
            {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty())
        {
            return;
        }
        candidates.sort(Comparator.comparing(Category::getDescription, String.CASE_INSENSITIVE_ORDER));

        for (Category candidate : candidates)
        {
            JMenuItem item = new JMenuItem("This is " + candidate.getDescription());
            item.setToolTipText("Already tagged on this photo, with no face matched to them yet");
            item.addActionListener(e -> applyReassignment(List.of(face), proposedPerson, candidate));
            menu.add(item);
        }
    }

    /**
     * The cluster grid gets "take me to that photo" and "this is nobody", but no confirm/reject: nothing here has
     * been proposed as anyone, so there's nothing to agree or disagree with yet.
     */
    private void installClusterFaceMenu(JList<PhotoFace> list)
    {
        attachPopupMenu(list, () -> {
            JPopupMenu menu = new JPopupMenu();

            JMenuItem ignoreItem = new JMenuItem(list.getSelectedIndices().length > 1
                    ? "These are nobody - never match them" : "This is nobody - never match it");
            ignoreItem.addActionListener(e -> ignoreFaces(list.getSelectedValuesList()));
            menu.add(ignoreItem);

            menu.addSeparator();
            JMenuItem openItem = new JMenuItem("Select this photo");
            openItem.addActionListener(e -> openPhoto(list.getSelectedValue()));
            menu.add(openItem);
            return menu;
        });
    }

    /**
     * Marks these faces as nobody, so no person is ever proposed for them again.
     * <p>
     * Reversible by naming the face on the photo preview, which is the only place an ignored face still shows up.
     */
    private void ignoreFaces(List<PhotoFace> faces)
    {
        if (faces.isEmpty())
        {
            return;
        }
        List<Long> faceIds = new ArrayList<>();
        for (PhotoFace face : faces)
        {
            faceIds.add(face.getFaceId());
        }
        _faceOperations.ignoreFaces(faceIds);
        AdminModel.getModel().fireFacesChanged();
    }

    /**
     * Marks a whole cluster as nobody, which is the point of having this at cluster granularity at all: a poster on
     * the wall or a face on a cereal box turns up in dozens of photos and clusters beautifully, so it arrives here
     * as one big group that can be dismissed in a single click.
     */
    private void ignoreSelectedCluster()
    {
        ClusterSummary cluster = _clustersList.getSelectedValue();
        if (cluster == null)
        {
            return;
        }
        if (JOptionPane.showConfirmDialog(this,
                "Mark all " + cluster.size() + " face(s) in this cluster as nobody?\n\n" +
                "They'll never be proposed as anyone again. To undo, click the face on the photo preview and name it.",
                "Not a person", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
        {
            return;
        }
        ignoreFaces(_faceOperations.getAllFacesInCluster(cluster.clusterId()));
    }

    /**
     * Shows the menu on a right-click, first moving the selection onto whatever was clicked.
     * <p>
     * The menu is built per click rather than once, so items can depend on which face was clicked. Both
     * mousePressed and mouseReleased are checked because which one is the popup trigger is platform-specific.
     */
    private static void attachPopupMenu(JList<PhotoFace> list, Supplier<JPopupMenu> menuSupplier)
    {
        list.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent e)
            {
                showMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e)
            {
                showMenu(e);
            }

            private void showMenu(MouseEvent e)
            {
                if (!e.isPopupTrigger())
                {
                    return;
                }
                // Right-clicking outside the selection should act on what was clicked, not on a stale selection
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && !list.isSelectedIndex(index))
                {
                    list.setSelectedIndex(index);
                }
                if (!list.isSelectionEmpty())
                {
                    menuSupplier.get().show(list, e.getX(), e.getY());
                }
            }
        });
    }

    /**
     * Moves the selected faces to a different person, for the common case where a proposal is wrong but you can
     * see who it actually is. Rejecting alone would only send them back to the unknown pool.
     */
    private void reassignSelectedProposals()
    {
        List<PhotoFace> selected = _reviewFacesList.getSelectedValuesList();
        Category proposedPerson = getSelectedReviewPerson();
        if (selected.isEmpty())
        {
            return;
        }

        FaceNameCombo combo = new FaceNameCombo(_peopleById.values());
        String name = combo.showDialog(this, "Assign to someone else",
                "Who are these " + selected.size() + " face(s)?");
        if (name == null)
        {
            return;
        }
        Category person = FaceNameCombo.resolvePerson(name, _peopleService, this);
        if (person == null)
        {
            return;
        }
        applyReassignment(selected, proposedPerson, person);
    }

    /** Confirms these faces as one person, recording a rejection against whoever was proposed instead. */
    private void applyReassignment(List<PhotoFace> faces, Category proposedPerson, Category person)
    {
        // Record that they weren't who was proposed, so the next round doesn't offer the same wrong answer again.
        if (proposedPerson != null && !proposedPerson.getCategoryId().equals(person.getCategoryId()))
        {
            for (PhotoFace face : faces)
            {
                _faceOperations.rejectFace(face, proposedPerson.getCategoryId());
            }
        }
        dropPhotoSelectionBeforeTagging();
        _peopleService.assignFaces(faces, person);

        AdminModel.getModel().fireCategoryChanged(person);
        AdminModel.getModel().fireFacesChanged();
        AdminModel.getModel().firePhotoListChanged();
    }

    private void confirmSelectedProposals()
    {
        List<PhotoFace> selected = _reviewFacesList.getSelectedValuesList();
        Category person = getSelectedReviewPerson();
        if (selected.isEmpty() || person == null)
        {
            return;
        }
        dropPhotoSelectionBeforeTagging();
        _peopleService.assignFaces(selected, person);
        AdminModel.getModel().fireFacesChanged();
        AdminModel.getModel().firePhotoListChanged();
    }

    /**
     * Saves and drops the photo selection before tags get written from this tab.
     * <p>
     * PhotoInfoPanel saves whichever photo it was showing every time the selection changes, by merging a detached
     * instance. If one of the photos being tagged here is that photo, the merge would happen after the tag was
     * written and re-synchronize the join table against a stale categories collection, undoing it. Clearing the
     * selection first flushes any pending edits while they're still correct and leaves nothing stale behind.
     */
    private void dropPhotoSelectionBeforeTagging()
    {
        if (!AdminModel.getModel().getCurrentPhotos().isEmpty())
        {
            AdminModel.getModel().fireSelectedPhotosChanged(new ArrayList<>());
        }
    }

    private void rejectSelectedProposals()
    {
        List<PhotoFace> selected = _reviewFacesList.getSelectedValuesList();
        if (selected.isEmpty())
        {
            return;
        }
        List<Long> faceIds = new ArrayList<>();
        for (PhotoFace face : selected)
        {
            faceIds.add(face.getFaceId());
        }
        _faceOperations.rejectFaces(faceIds);
        AdminModel.getModel().fireFacesChanged();
    }

    /**
     * Selects every proposal in the grid that the photo's own tags already agree with.
     * <p>
     * These are the block to confirm in one go: the person was hand-tagged on the photo, so presence isn't in
     * question and propagation only had to decide which face was theirs. Leaves the focus on the grid so Enter
     * confirms straight away.
     */
    private void selectCorroborated()
    {
        int[] indices = findCorroboratedIndices();
        if (indices.length == 0)
        {
            return;
        }
        _reviewFacesList.setSelectedIndices(indices);
        Rectangle bounds = _reviewFacesList.getCellBounds(indices[0], indices[0]);
        if (bounds != null)
        {
            _reviewFacesList.scrollRectToVisible(bounds);
        }
        _reviewFacesList.requestFocusInWindow();
    }

    private int[] findCorroboratedIndices()
    {
        List<Integer> found = new ArrayList<>();
        for (int i = 0; i < _reviewFacesModel.getSize(); i++)
        {
            PhotoFace face = _reviewFacesModel.getElementAt(i);
            if (face.getPhoto() != null && _corroboratedPhotoIds.contains(face.getPhoto().getPhotoId()))
            {
                found.add(i);
            }
        }
        int[] indices = new int[found.size()];
        for (int i = 0; i < found.size(); i++)
        {
            indices[i] = found.get(i);
        }
        return indices;
    }

    /** Puts the count on the button, so the size of the easy win is visible without selecting anything. */
    private void refreshCorroboratedButton()
    {
        int count = findCorroboratedIndices().length;
        _selectCorroboratedButton.setText(count == 0 ? "Select ✓" : "Select ✓ (" + count + ")");
        _selectCorroboratedButton.setEnabled(count > 0);
    }

    private Category getSelectedReviewPerson()
    {
        PersonPendingCount selected = (PersonPendingCount) _reviewPeopleCombo.getSelectedItem();
        return selected == null ? null : _peopleById.get(selected.categoryId());
    }

    private void loadReviewQueue()
    {
        _reviewFacesModel.clear();
        _corroboratedPhotoIds = new HashSet<>();
        PersonPendingCount selected = (PersonPendingCount) _reviewPeopleCombo.getSelectedItem();
        if (selected == null)
        {
            refreshCorroboratedButton();
            return;
        }

        List<PhotoFace> faces = _faceOperations.getPendingFacesForPerson(selected.categoryId(), MAX_REVIEW_FACES);
        List<Integer> photoIds = new ArrayList<>();
        for (PhotoFace face : faces)
        {
            _reviewFacesModel.addElement(face);
            if (face.getPhoto() != null)
            {
                photoIds.add(face.getPhoto().getPhotoId());
            }
        }

        // Asked once for the whole queue rather than per cell, since cell renderers run during painting.
        _corroboratedPhotoIds = _faceOperations
                .getTaggedPeopleByPhoto(photoIds, List.of(selected.categoryId()))
                .keySet();

        refreshCorroboratedButton();
    }

    // ------------------------------------------------------------------------------------------------
    // Clusters
    // ------------------------------------------------------------------------------------------------

    private void loadSelectedCluster()
    {
        _clusterFacesModel.clear();
        ClusterSummary selected = _clustersList.getSelectedValue();
        if (selected == null)
        {
            return;
        }
        for (PhotoFace face : _faceOperations.getFacesInCluster(selected.clusterId(), MAX_CLUSTER_FACES))
        {
            _clusterFacesModel.addElement(face);
        }
        _clusterNameCombo.setTypedName("");
    }

    /**
     * Names a cluster, which is the main way a person gets created from here on.
     * <p>
     * A name that matches an existing person merges into them; anything else creates a new category. Either way it
     * finishes by looking for more of that person, because going from no exemplars to dozens is exactly when that
     * pass pays off.
     */
    private void assignSelectedCluster()
    {
        ClusterSummary cluster = _clustersList.getSelectedValue();
        if (cluster == null)
        {
            return;
        }
        String name = _clusterNameCombo.getTypedName();
        Category person = FaceNameCombo.resolvePerson(name, _peopleService, this);
        if (person == null)
        {
            return;
        }

        dropPhotoSelectionBeforeTagging();
        int assigned = _peopleService.adoptCluster(cluster.clusterId(), person);

        // The category tree needs to hear about a new person, and the photo list about the new tags.
        AdminModel.getModel().fireCategoryChanged(person);
        AdminModel.getModel().firePhotoListChanged();

        int personId = person.getCategoryId();
        int[] proposed = new int[1];
        Throwable failure = FaceProgressDialog.run("Looking for more of " + name,
                listener -> proposed[0] = _faceMatcher.propagateForPerson(personId, listener));

        AdminModel.getModel().fireFacesChanged();

        if (failure != null)
        {
            showError("Looking for more matches failed", failure);
            return;
        }
        JOptionPane.showMessageDialog(AdminFrame.getFrame(),
                "Confirmed " + assigned + " face(s) as " + name + ".\n" +
                "Proposed " + proposed[0] + " more for review.",
                "Named cluster", JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------------------------------------
    // People list
    // ------------------------------------------------------------------------------------------------

    private PersonRow getSelectedPersonRow()
    {
        int viewRow = _peopleTable.getSelectedRow();
        if (viewRow < 0)
        {
            return null;
        }
        return _peopleTableModel.getRow(_peopleTable.convertRowIndexToModel(viewRow));
    }

    /**
     * Jumps to the Review queue with this person selected, which is what makes the People table a place to decide
     * from rather than just a report to read.
     */
    private void reviewSelectedPerson()
    {
        PersonRow row = getSelectedPersonRow();
        if (row == null)
        {
            return;
        }
        for (int i = 0; i < _reviewPeopleModel.getSize(); i++)
        {
            if (_reviewPeopleModel.getElementAt(i).categoryId() == row.person().getCategoryId())
            {
                _reviewPeopleCombo.setSelectedIndex(i);
                _subTabs.setSelectedIndex(0);
                _reviewFacesList.requestFocusInWindow();
                return;
            }
        }
        JOptionPane.showMessageDialog(this,
                row.person().getDescription() + " has no proposals waiting.\n" +
                (row.confirmedFaces() == 0
                        ? "They have no confirmed faces either, so nothing can be matched to them yet - seed them by\n" +
                          "clicking a face on one of their photos, then use \"Find more\"."
                        : "Use \"Find more\" to look for others, or run \"Match faces\" after confirming more people."),
                "Nothing to review", JOptionPane.INFORMATION_MESSAGE);
    }

    private void findMoreForSelectedPerson()
    {
        PersonRow row = getSelectedPersonRow();
        if (row == null)
        {
            return;
        }
        if (row.confirmedFaces() == 0)
        {
            JOptionPane.showMessageDialog(AdminFrame.getFrame(),
                    row.person().getDescription() + " has no confirmed faces yet, so there's nothing to match against.\n" +
                    "Seed them first by clicking an unmatched face on a photo of them, or by naming a cluster.",
                    "No faces yet", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int personId = row.person().getCategoryId();
        int[] proposed = new int[1];
        Throwable failure = FaceProgressDialog.run("Looking for more of " + row.person().getDescription(),
                listener -> proposed[0] = _faceMatcher.propagateForPerson(personId, listener));

        AdminModel.getModel().fireFacesChanged();

        if (failure != null)
        {
            showError("Looking for more matches failed", failure);
            return;
        }
        JOptionPane.showMessageDialog(AdminFrame.getFrame(), "Proposed " + proposed[0] + " match(es) for review.",
                "Find more", JOptionPane.INFORMATION_MESSAGE);
    }

    private void mergeSelectedPerson()
    {
        PersonRow row = getSelectedPersonRow();
        if (row == null)
        {
            return;
        }

        List<Category> others = new ArrayList<>();
        for (Category person : _peopleById.values())
        {
            if (!person.getCategoryId().equals(row.person().getCategoryId()))
            {
                others.add(person);
            }
        }
        if (others.isEmpty())
        {
            return;
        }
        others.sort(Comparator.comparing(Category::getDescription, String.CASE_INSENSITIVE_ORDER));

        Object choice = JOptionPane.showInputDialog(AdminFrame.getFrame(),
                "Move every face and tag from \"" + row.person().getDescription() + "\" into which person?\n" +
                "\"" + row.person().getDescription() + "\" is then deleted.",
                "Merge person", JOptionPane.QUESTION_MESSAGE, null,
                others.stream().map(Category::getDescription).toArray(), null);
        if (choice == null)
        {
            return;
        }

        Category target = _peopleService.findPersonByName(choice.toString());
        if (target == null)
        {
            return;
        }
        try
        {
            _peopleService.mergePeople(row.person(), target);
        }
        catch (IllegalStateException e)
        {
            JOptionPane.showMessageDialog(AdminFrame.getFrame(), e.getMessage(), "Can't merge", JOptionPane.WARNING_MESSAGE);
            return;
        }

        AdminModel.getModel().fireCategoryChanged(target);
        AdminModel.getModel().firePhotoListChanged();
        AdminModel.getModel().fireFacesChanged();
    }

    // ------------------------------------------------------------------------------------------------
    // Refresh
    // ------------------------------------------------------------------------------------------------

    /**
     * Reloads every sub-view from the database. Cheap enough to just do wholesale whenever anything changes.
     * <p>
     * Face tagging is opt-in and its tables are created by hand, so a failure here means the schema isn't there
     * yet. That has to degrade to an explanation in this one tab rather than taking the whole admin UI down.
     */
    public void refresh()
    {
        try
        {
            reload();
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
            _statusLabel.setText("<html><b>Face data unavailable.</b> " + escape(String.valueOf(e.getMessage())) +
                    "<br>Run src/main/resources/sql/faces-schema.sql against the database, then reopen the admin UI.</html>");
            _scanButton.setEnabled(false);
            _matchButton.setEnabled(false);
            _rematchButton.setEnabled(false);
            _rescanButton.setEnabled(false);
        }
    }

    private void reload()
    {
        // Deliberately does not clear the thumbnail cache. A crop is a function of a face id, and confirming or
        // rejecting a face changes neither its box nor its photo, so throwing the crops away here meant every
        // single-face action paid to re-decode a screenful of 1400px JPEGs. Only re-running detection invalidates
        // crops, and those paths clear the cache themselves.
        String peopleProblem = _peopleService.describeMissingConfiguration();
        String modelsProblem = FaceEncoder.describeMissingConfiguration();

        _peopleById = new LinkedHashMap<>();
        for (Category person : _peopleService.getAllPeople())
        {
            _peopleById.put(person.getCategoryId(), person);
        }

        _refreshing = true;
        try
        {
            refreshReviewPeople();
        }
        finally
        {
            _refreshing = false;
        }
        loadReviewQueue();

        _clustersStale = true;
        _peopleTableStale = true;
        refreshStaleVisibleSubTab();

        refreshStatus(peopleProblem, modelsProblem);

        boolean canMatch = peopleProblem == null;
        _matchButton.setEnabled(canMatch);
        _rematchButton.setEnabled(canMatch);
        _scanButton.setEnabled(modelsProblem == null);
        _rescanButton.setEnabled(true);
    }

    /** Recomputes whichever expensive sub-tab is on screen, if it's out of date. Cheap when neither is showing. */
    private void refreshStaleVisibleSubTab()
    {
        int index = _subTabs.getSelectedIndex();
        if (index == 1 && _clustersStale)
        {
            _refreshing = true;
            try
            {
                refreshClusters();
            }
            finally
            {
                _refreshing = false;
            }
            _clustersStale = false;
        }
        else if (index == 2 && _peopleTableStale)
        {
            refreshPeopleTable();
            _peopleTableStale = false;
        }
    }

    private void refreshReviewPeople()
    {
        PersonPendingCount previous = (PersonPendingCount) _reviewPeopleCombo.getSelectedItem();

        List<PersonPendingCount> pending = _faceOperations.getPendingCountsByPerson();
        _reviewPeopleModel.removeAllElements();
        int total = 0;
        for (PersonPendingCount person : pending)
        {
            _reviewPeopleModel.addElement(person);
            total += person.count();
        }
        _pendingTotal = total;
        _pendingCountConsumer.accept(total);

        if (previous != null)
        {
            for (int i = 0; i < _reviewPeopleModel.getSize(); i++)
            {
                if (_reviewPeopleModel.getElementAt(i).categoryId() == previous.categoryId())
                {
                    _reviewPeopleCombo.setSelectedIndex(i);
                    break;
                }
            }
        }
    }

    private void refreshClusters()
    {
        ClusterSummary previous = _clustersList.getSelectedValue();
        _clustersModel.clear();

        List<ClusterSummary> clusters = _faceOperations.getClusterSummaries(FaceMatcher.MINIMUM_CLUSTER_SIZE);
        List<Long> representativeIds = new ArrayList<>();
        for (ClusterSummary cluster : clusters)
        {
            _clustersModel.addElement(cluster);
            representativeIds.add(cluster.representativeFaceId());
        }

        // Resolved here rather than in the cell renderer, which would mean a query per repaint.
        _clusterRepresentatives = new HashMap<>();
        for (PhotoFace face : _faceOperations.getFacesByIds(representativeIds))
        {
            _clusterRepresentatives.put(face.getClusterId(), face);
        }

        // Keep the People categories available as autocomplete targets, since most "unknown" clusters are somebody
        // who already exists.
        _clusterNameCombo.setPeople(_peopleById.values());

        if (previous != null)
        {
            for (int i = 0; i < _clustersModel.getSize(); i++)
            {
                if (_clustersModel.getElementAt(i).clusterId() == previous.clusterId())
                {
                    _clustersList.setSelectedIndex(i);
                    return;
                }
            }
        }
        _clusterFacesModel.clear();
    }

    private void refreshPeopleTable()
    {
        Map<Integer, int[]> faceCounts = _faceOperations.getFaceCountsByPerson();
        Map<Integer, Integer> taggedWithoutFace = new HashMap<>();
        for (PersonPendingCount report : _faceOperations.getTaggedWithoutFaceReport(_peopleById.keySet()))
        {
            taggedWithoutFace.put(report.categoryId(), report.count());
        }

        Map<Integer, FaceOperations.PersonPhotoStats> photoStats =
                _faceOperations.getPersonPhotoStats(_peopleById.keySet());

        List<PersonRow> rows = new ArrayList<>();
        for (Category person : _peopleById.values())
        {
            int[] counts = faceCounts.getOrDefault(person.getCategoryId(), new int[2]);
            FaceOperations.PersonPhotoStats stats = photoStats.get(person.getCategoryId());
            rows.add(new PersonRow(person, counts[0], counts[1],
                    taggedWithoutFace.getOrDefault(person.getCategoryId(), 0),
                    stats == null ? 0 : stats.taggedPhotos(),
                    stats == null ? null : stats.latestMonth()));
        }
        _peopleTableModel.setRows(rows);
    }

    private void refreshStatus(String peopleProblem, String modelsProblem)
    {
        if (peopleProblem != null || modelsProblem != null)
        {
            _statusLabel.setText("<html><b>Not configured yet.</b> " +
                    (peopleProblem == null ? "" : escape(peopleProblem) + "<br>") +
                    (modelsProblem == null ? "" : escape(modelsProblem)) + "</html>");
            return;
        }
        long scanned = _faceOperations.getScannedPhotoCount();
        long faces = _faceOperations.getFaceCount();
        long ignored = _faceOperations.getIgnoredFaceCount();
        // Already summed while rebuilding the review combo, so no second count query for it
        int pending = _pendingTotal;
        _statusLabel.setText(scanned + " photo(s) scanned, " + faces + " face(s) found, " + pending + " awaiting review, " +
                (ignored == 0 ? "" : ignored + " marked as nobody, ") +
                _peopleById.size() + " person categor" + (_peopleById.size() == 1 ? "y" : "ies") + ".");
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void showError(String title, Throwable failure)
    {
        failure.printStackTrace();
        JOptionPane.showMessageDialog(AdminFrame.getFrame(),
                title + ":\n" + (failure.getMessage() == null ? failure.toString() : failure.getMessage()),
                title, JOptionPane.ERROR_MESSAGE);
    }

    // ------------------------------------------------------------------------------------------------
    // Renderers and small models
    // ------------------------------------------------------------------------------------------------

    /** Double-clicking a face selects its photo in the main list, so the normal editing UI is one click away. */
    private class OpenPhotoOnDoubleClick extends MouseAdapter
    {
        private final JList<PhotoFace> _list;

        private OpenPhotoOnDoubleClick(JList<PhotoFace> list)
        {
            _list = list;
        }

        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (e.getClickCount() != 2 || e.getButton() != MouseEvent.BUTTON1)
            {
                return;
            }
            openPhoto(_list.getSelectedValue());
        }
    }

    /**
     * Selects this face's photo in the main photo list, which brings the whole normal editing UI - caption,
     * categories, the preview with its face overlay - to bear on it.
     * <p>
     * Reports rather than silently does nothing when the photo isn't in the list: the filter controls above the
     * photo list can easily be excluding it, and a dead menu item is a confusing way to find that out.
     */
    private void openPhoto(PhotoFace face)
    {
        if (face == null || face.getPhoto() == null)
        {
            return;
        }
        Photo photo = face.getPhoto();
        AdminModel.getModel().fireRequestPhotoSelection(Set.of(photo));

        List<Photo> selected = AdminModel.getModel().getCurrentPhotos();
        if (selected.size() != 1 || !selected.get(0).equals(photo))
        {
            JOptionPane.showMessageDialog(this,
                    photo.getFilename() + " isn't in the photo list at the moment.\n" +
                    "Clear the Filter box and the \"Uncategorized only\" / \"New from last scan\" checkboxes above " +
                    "the list, then try again.",
                    "Photo not in the list", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private class FaceCellRenderer extends JLabel implements ListCellRenderer<PhotoFace>
    {
        private final int _size;
        private final boolean _showScore;

        private FaceCellRenderer(int size, boolean showScore)
        {
            _size = size;
            _showScore = showScore;
            setHorizontalAlignment(SwingConstants.CENTER);
            setHorizontalTextPosition(SwingConstants.CENTER);
            setVerticalTextPosition(SwingConstants.BOTTOM);
            setOpaque(true);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends PhotoFace> list, PhotoFace face, int index,
                                                      boolean isSelected, boolean hasFocus)
        {
            Icon icon = _thumbnails.get(face, _size, list, list::repaint);
            setIcon(icon);

            boolean corroborated = _showScore && face.getPhoto() != null
                    && _corroboratedPhotoIds.contains(face.getPhoto().getPhotoId());

            if (_showScore && face.getMatchScore() != null)
            {
                // The tick marks a match the photo's own tags already agree with, so it reads as "safe to confirm"
                setText((corroborated ? "✓ " : "") + String.format("%.2f", face.getMatchScore()));
            }
            else
            {
                setText(icon == null ? "..." : " ");
            }

            setBorder(corroborated ? CORROBORATED_BORDER : PLAIN_BORDER);
            setToolTipText(buildToolTip(face, corroborated));
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            // Selection colours have to win, or a selected cell becomes unreadable
            setForeground(isSelected ? list.getSelectionForeground()
                    : corroborated ? CORROBORATED_COLOR : list.getForeground());
            return this;
        }

        private String buildToolTip(PhotoFace face, boolean corroborated)
        {
            if (face.getPhoto() == null)
            {
                return null;
            }
            String filename = face.getPhoto().getFilename();
            return corroborated
                    ? filename + " - already tagged with this person, so the tags agree with the match"
                    : filename;
        }
    }

    private class ClusterCellRenderer extends JLabel implements ListCellRenderer<ClusterSummary>
    {
        private ClusterCellRenderer()
        {
            setOpaque(true);
            setHorizontalAlignment(SwingConstants.LEFT);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ClusterSummary> list, ClusterSummary cluster,
                                                      int index, boolean isSelected, boolean hasFocus)
        {
            PhotoFace representative = _clusterRepresentatives.get(cluster.clusterId());
            setIcon(representative == null ? null
                    : _thumbnails.get(representative, CLUSTER_CROP_SIZE, list, list::repaint));
            setText(cluster.size() + " faces");
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }

    private static class PendingCountRenderer extends javax.swing.DefaultListCellRenderer
    {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus)
        {
            Object text = value instanceof PersonPendingCount person
                    ? person.description() + " (" + person.count() + ")"
                    : value;
            return super.getListCellRendererComponent(list, text, index, isSelected, hasFocus);
        }
    }

    private record PersonRow(Category person, int confirmedFaces, int pendingFaces, int taggedWithoutFace,
                             int taggedPhotos, String latestMonth)
    {
        /**
         * Confirmed faces as a percentage of the photos this person is tagged on - the single most useful "does
         * this person need curating" number.
         * <p>
         * Low with a high tagged count means the matcher has little to go on for somebody who turns up
         * constantly, which is exactly who repays attention. High means they're already well represented and
         * further review buys little. Null when they're tagged on nothing, since the ratio is meaningless then.
         */
        private Integer coveragePercent()
        {
            return taggedPhotos == 0 ? null : Math.round(100f * confirmedFaces / taggedPhotos);
        }
    }

    private static class PeopleTableModel extends AbstractTableModel
    {
        private static final String[] COLUMNS =
                {"Person", "Last photo", "Tagged photos", "Confirmed", "Coverage %", "Awaiting review", "Tagged, no face"};

        private List<PersonRow> _rows = new ArrayList<>();

        private void setRows(List<PersonRow> rows)
        {
            _rows = rows;
            fireTableDataChanged();
        }

        private PersonRow getRow(int index)
        {
            return _rows.get(index);
        }

        @Override
        public int getRowCount()
        {
            return _rows.size();
        }

        @Override
        public int getColumnCount()
        {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column)
        {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column)
        {
            // "Last photo" stays a String: YYYY-MM sorts chronologically as text, and nulls sort together
            return column == 0 || column == 1 ? String.class : Integer.class;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex)
        {
            PersonRow row = _rows.get(rowIndex);
            return switch (columnIndex)
            {
                // Flagging zero-face people here is the whole point of the column: those are the ones that still
                // need seeding by hand.
                case 0 -> row.confirmedFaces() == 0 ? row.person().getDescription() + "  (no faces yet)"
                        : row.person().getDescription();
                case 1 -> row.latestMonth();
                case 2 -> row.taggedPhotos();
                case 3 -> row.confirmedFaces();
                case 4 -> row.coveragePercent();
                case 5 -> row.pendingFaces();
                default -> row.taggedWithoutFace();
            };
        }
    }
}
