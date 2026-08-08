/*
 * CategoryTree.java
 *
 * Created on April 16, 2002, 11:16 AM
 */

package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ShareTokens;
import com.stampysoft.photoGallery.Visibility;
import com.stampysoft.util.Configuration;

import javax.swing.*;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
    private final JCheckBoxMenuItem _optOutMenuItem = new JCheckBoxMenuItem("Hide photos publicly");
    private final JMenuItem _copyShareLinkMenuItem = new JMenuItem("Copy share link");
    private final JMenuItem _regenerateShareLinkMenuItem = new JMenuItem("Regenerate share link");
    private final JMenuItem _removeShareLinkMenuItem = new JMenuItem("Remove share link");
    private final JMenuItem _copyCombinedShareLinkMenuItem = new JMenuItem("Copy combined share link");
    private final JMenuItem _ownerLinkMenuItem = new JMenuItem("Copy owner link");
    private final JMenuItem _regenerateOwnerLinkMenuItem = new JMenuItem("Regenerate owner link");
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

        _optOutMenuItem.addActionListener(e -> {
            TreePath[] paths = getSelectionPaths();
            for (TreePath path : paths) {
                CategoryTreeNode node = (CategoryTreeNode) path.getLastPathComponent();
                node.setOptOut(_optOutMenuItem.isSelected());
            }
        });

        _copyShareLinkMenuItem.addActionListener(e -> copyShareLink(selectedNode(), false));
        _regenerateShareLinkMenuItem.addActionListener(e -> copyShareLink(selectedNode(), true));
        _removeShareLinkMenuItem.addActionListener(e -> removeShareLink(selectedNode()));
        _copyCombinedShareLinkMenuItem.addActionListener(e -> copyCombinedShareLink());

        _ownerLinkMenuItem.addActionListener(e -> copyOwnerLink(false));
        _regenerateOwnerLinkMenuItem.addActionListener(e -> copyOwnerLink(true));

        _insertMenuItem.addActionListener(e -> {
            TreePath path = getSelectionPath();
            CategoryTreeNode parent = (CategoryTreeNode) path.getLastPathComponent();
            Category newCategory = new Category();
            newCategory.setDescription("New category");
            newCategory.setParentCategory(parent.getCategory());
            // Inherit privacy from the parent, so anything added under People is private without having to
            // remember. Still editable afterwards, for a public sub-event of a private parent.
            newCategory.setPrivate(parent.getCategory() != null && parent.getCategory().isPrivate());
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
                    Set<Photo> photos = node.getCategory().getPhotos(Visibility.OWNER);
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
                                _privateMenuItem.setSelected(selectedCategory.isPrivate());
                                showPrivacyItems(selectedCategory);
                            }
                            else
                            {
                                // The root node stands for the gallery as a whole, which is where the owner's own
                                // access lives - it isn't a property of any one category
                                _menu.remove(_exportPhotosMenuItem);
                                _menu.remove(_renameMenuItem);
                                _menu.remove(_privateMenuItem);
                                hidePrivacyItems();
                                _menu.add(_ownerLinkMenuItem);
                                boolean hasOwnerToken = AdminFrame.getFrame().getPhotoOperations().hasOwnerToken();
                                _ownerLinkMenuItem.setText(hasOwnerToken ? "Copy owner link" : "Create owner link");
                                if (hasOwnerToken)
                                {
                                    _menu.add(_regenerateOwnerLinkMenuItem);
                                }
                            }
                        }
                        else
                        {
                            _menu.remove(_exportPhotosMenuItem);
                            _menu.remove(_insertMenuItem);
                            _menu.remove(_renameMenuItem);
                            _menu.remove(_privateMenuItem);
                            hidePrivacyItems();
                            // One link covering several events, for somebody who was at more than one of them.
                            // Offered whenever more than one thing is selected, even when nothing in the
                            // selection can actually be shared - a menu item that silently isn't there is a
                            // worse answer than one that says why.
                            int shareable = shareableSelectedNodes().size();
                            _copyCombinedShareLinkMenuItem.setText(shareable > 1
                                    ? "Copy combined share link (" + shareable + " selected)"
                                    : "Copy combined share link");
                            _menu.add(_copyCombinedShareLinkMenuItem);
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

    private CategoryTreeNode selectedNode()
    {
        TreePath path = getSelectionPath();
        return path == null ? null : (CategoryTreeNode) path.getLastPathComponent();
    }

    /**
     * The privacy items are only offered where they mean something: opting out is a property of a person, and a
     * link is only worth having on a category that either anonymous visitors can reach or somebody is hidden in.
     * <p>
     * The same three menu items serve both kinds of link, because minting, regenerating and removing a token is
     * the same operation either way - only the wording and the warnings differ. See {@link #copyShareLink}.
     */
    private void showPrivacyItems(Category category)
    {
        hidePrivacyItems();

        boolean person = isPerson(category);
        if (person)
        {
            _menu.add(_optOutMenuItem);
            _optOutMenuItem.setSelected(category.isOptOut());
        }

        // A person's category is private and stays that way, but a token on it is the unhide link: the way the
        // family sees somebody who has opted out, across every event rather than one. Any other private category
        // has nothing to offer - no page to land on, and nobody hidden to reveal.
        if (person || !category.isPrivate())
        {
            String noun = person ? "unhide link" : "share link";
            boolean shared = category.getShareToken() != null;
            _copyShareLinkMenuItem.setText((shared ? "Copy " : "Create ") + noun);
            _regenerateShareLinkMenuItem.setText("Regenerate " + noun);
            _removeShareLinkMenuItem.setText("Remove " + noun);

            _menu.add(_copyShareLinkMenuItem);
            if (shared)
            {
                _menu.add(_regenerateShareLinkMenuItem);
                _menu.add(_removeShareLinkMenuItem);
            }
        }
    }

    private boolean isPerson(Category category)
    {
        return category != null && AdminFrame.getFrame().getPeopleService().isPerson(category);
    }

    private void hidePrivacyItems()
    {
        _menu.remove(_optOutMenuItem);
        _menu.remove(_copyShareLinkMenuItem);
        _menu.remove(_regenerateShareLinkMenuItem);
        _menu.remove(_removeShareLinkMenuItem);
        _menu.remove(_copyCombinedShareLinkMenuItem);
        _menu.remove(_ownerLinkMenuItem);
        _menu.remove(_regenerateOwnerLinkMenuItem);
    }

    /**
     * The selected categories a link would actually do something for, in the order they appear in the tree
     * rather than the order they were clicked. Order matters because the visitor lands on the first public
     * category in the link, and "the topmost one you selected" is a rule you can see on screen.
     * <p>
     * People belong here even though they're private - selecting an event and the relatives who were at it is
     * the reason the combined link exists. What's left out is the root, which stands for the gallery rather than
     * a category, and private categories that aren't people, whose token would unlock nothing and land nowhere.
     */
    private List<CategoryTreeNode> shareableSelectedNodes()
    {
        TreePath[] paths = getSelectionPaths();
        if (paths == null)
        {
            return List.of();
        }

        List<TreePath> sorted = new ArrayList<>(Arrays.asList(paths));
        sorted.sort(Comparator.comparingInt(this::getRowForPath));

        // Fetched once rather than per node: working out whether a category is a person is a pass over the
        // whole category table.
        Set<Integer> personIds = AdminFrame.getFrame().getPeopleService().getPersonCategoryIds();

        List<CategoryTreeNode> result = new ArrayList<>();
        for (TreePath path : sorted)
        {
            Category category = ((CategoryTreeNode) path.getLastPathComponent()).getCategory();
            if (category != null && (!category.isPrivate() || personIds.contains(category.getCategoryId())))
            {
                result.add((CategoryTreeNode) path.getLastPathComponent());
            }
        }
        return result;
    }

    /**
     * Puts one link covering several categories on the clipboard, minting tokens for any that don't have one -
     * the same thing {@link #copyShareLink} does, several at a time. This is for the guest who was at three of
     * the weekend's events and shouldn't have to be sent three links.
     * <p>
     * The link is {@code /unlock?t=…&t=…}: every token is stashed, and the redirect follows the first token
     * naming a category the visitor can open, so the visitor lands on the topmost selected event with the rest
     * already unlocked underneath them.
     * <p>
     * Selecting an event together with the relatives who were at it is what this is really for: the event's
     * token shows the event, and each person's shows that person everywhere else as well.
     */
    private void copyCombinedShareLink()
    {
        List<CategoryTreeNode> nodes = shareableSelectedNodes();
        if (nodes.size() < 2)
        {
            JOptionPane.showMessageDialog(this,
                    "There aren't two things here that a link can do anything for.\n\n" +
                            "Of the " + getSelectionCount() + " selected, " + nodes.size() + " can go in a link. That means public categories and\n" +
                            "people; a private category that isn't a person unlocks nothing, and the\n" +
                            "gallery root isn't a category at all.",
                    "Combined Share Link", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Presenting more than a browser holds would evict the earliest tokens, including the one the redirect
        // lands on - a link that silently half-works is worse than one that isn't offered.
        if (nodes.size() > ShareTokens.MAX_HELD)
        {
            JOptionPane.showMessageDialog(this,
                    "A browser holds " + ShareTokens.MAX_HELD + " share links at a time, and this would be " + nodes.size() + ".\n\n" +
                            "Select fewer categories, or share a parent category instead - one link covers\n" +
                            "everything beneath it.",
                    "Too Many Categories", JOptionPane.WARNING_MESSAGE);
            return;
        }

        StringBuilder link = new StringBuilder(siteBaseURL()).append("unlock");
        List<Category> categories = new ArrayList<>();
        String separator = "?t=";
        int minted = 0;
        for (CategoryTreeNode node : nodes)
        {
            String token = node.getCategory().getShareToken();
            if (token == null)
            {
                token = ShareTokens.generate();
                node.setShareToken(token);
                minted++;
            }
            link.append(separator).append(token);
            separator = "&t=";
            categories.add(node.getCategory());
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(link.toString()), null);

        StringBuilder message = new StringBuilder("Copied to the clipboard:\n\n").append(link).append("\n\n");
        message.append("One link, ").append(nodes.size()).append(":\n\n").append(describe(categories));

        // The redirect follows the first token naming a category the visitor can open, which people never are
        Category landing = null;
        for (Category category : categories)
        {
            if (!category.isPrivate())
            {
                landing = category;
                break;
            }
        }
        message.append("\n").append(landing == null
                        ? "It opens on the homepage - nothing in it is a page a visitor can open."
                        : "It opens at \"" + landing.getDescription() + "\".")
                .append(" The rest take effect\nwherever the visitor goes next.\n");

        if (minted > 0)
        {
            message.append("\n").append(minted == 1
                    ? "One of them had no link before and has one now."
                    : minted + " of them had no link before and have one now.").append("\n");
        }
        int people = 0;
        for (Category category : categories)
        {
            if (category.isPrivate())
            {
                people++;
            }
        }
        if (people > 0)
        {
            message.append("\n").append(people == 1 ? "One of them is a person" : people + " of them are people")
                    .append(", so this link also shows their photos from\nevery other event, including ones imported later.\n");
        }
        int skipped = getSelectionCount() - nodes.size();
        if (skipped > 0)
        {
            message.append("\n").append(skipped == 1 ? "One selection was left out" : skipped + " selections were left out")
                    .append(" - the gallery root, and private\ncategories that aren't people.\n");
        }

        JOptionPane.showMessageDialog(this, message.toString(), "Combined Share Link", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Puts the owner's all-access link on the clipboard, minting the token if there isn't one yet. This is the only
     * way into private mode, so a gallery with no owner token has nobody who can see private photos at all.
     */
    private void copyOwnerLink(boolean regenerate)
    {
        PhotoOperations photoOperations = AdminFrame.getFrame().getPhotoOperations();

        if (regenerate && JOptionPane.showConfirmDialog(this,
                "Regenerate the owner link?\n\n" +
                        "The old link stops working immediately, including in browsers you have already unlocked -\n" +
                        "they hold the token itself, and it is checked against the database on every request. You\n" +
                        "will need to open the new link once in each of them.",
                "Regenerate Owner Link", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
        {
            return;
        }

        String token = photoOperations.getOwnerToken();
        if (token == null || regenerate)
        {
            token = photoOperations.regenerateOwnerToken();
        }

        String link = siteBaseURL() + "unlock?t=" + token;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(link), null);
        JOptionPane.showMessageDialog(this,
                "Copied to the clipboard:\n\n" + link + "\n\n" +
                        "Open it once in each browser you want to see private photos and People tags in.\n" +
                        "Keep it to yourself - it shows everything.",
                "Owner Link", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Puts this category's link on the clipboard, minting a token if there isn't one yet. One method for both
     * kinds, because the token handling is identical - what differs is what the link means, and the dialogs say
     * so, because an unhide link reaches much further than an event's.
     * <p>
     * Regenerating is the only way to revoke a link, and it revokes it for everyone who already has it, so it
     * asks first.
     */
    private void copyShareLink(CategoryTreeNode node, boolean regenerate)
    {
        if (node == null || node.getCategory() == null)
        {
            return;
        }
        Category category = node.getCategory();
        boolean person = isPerson(category);
        String noun = person ? "unhide link" : "share link";
        String title = person ? "Unhide Link" : "Share Link";

        if (regenerate && JOptionPane.showConfirmDialog(this,
                "Regenerate the " + noun + " for \"" + category.getDescription() + "\"?\n\n" +
                        "The link you sent out before will stop working, for everyone who has it" +
                        (person ? " -\nincluding anyone holding a combined link that included this person." : ".") ,
                "Regenerate " + title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
        {
            return;
        }

        String token = category.getShareToken();
        if (token == null || regenerate)
        {
            token = ShareTokens.generate();
            node.setShareToken(token);
        }

        String link = siteBaseURL() + "unlock?t=" + token;
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(link), null);

        StringBuilder message = new StringBuilder("Copied to the clipboard:\n\n").append(link).append("\n\n");
        if (person)
        {
            message.append("Anyone with this link sees ").append(category.getDescription())
                    .append("'s photos from every event, not\njust one, and from events imported later too. ")
                    .append("It doesn't reveal People\ntags, private photos, or that ")
                    .append(category.getDescription()).append(" is a category at all.\n\n")
                    .append("On its own it lands on the homepage - it names no page anyone but you\n")
                    .append("can open. Send it alongside an event's share link, or select both here\n")
                    .append("and use Copy combined share link.");
            if (!category.isOptOut())
            {
                message.append("\n\n").append(category.getDescription())
                        .append(" isn't hidden from the public right now, so this link\nchanges nothing until you turn on Hide photos publicly.");
            }
        }
        else
        {
            message.append("Anyone with this link sees the photos in \"").append(category.getDescription())
                    .append("\" of people who\nhave opted out of appearing publicly. It doesn't reveal People tags or private photos.");
        }

        JOptionPane.showMessageDialog(this, message.toString(), title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void removeShareLink(CategoryTreeNode node)
    {
        if (node == null || node.getCategory() == null)
        {
            return;
        }
        Category category = node.getCategory();
        boolean person = isPerson(category);
        if (JOptionPane.showConfirmDialog(this,
                "Remove the " + (person ? "unhide link" : "share link") + " for \"" + category.getDescription() + "\"?\n\n" +
                        "The link stops working for everyone who has it" +
                        (person ? ", including inside\ncombined links that included this person." : "."),
                "Remove " + (person ? "Unhide Link" : "Share Link"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION)
        {
            node.setShareToken(null);
        }
    }

    /** Where the gallery is served from, so a copied link is one somebody can actually open. */
    private String siteBaseURL()
    {
        String base = Configuration.getConfiguration().getProperty("SiteBaseURL", "http://localhost:8080/");
        return base.endsWith("/") ? base : base + "/";
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
