package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.photoGallery.admin.AdminModel;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asks one question about an import: which of these people were actually there?
 * <p>
 * The list is people, not faces, because that's the judgement an import actually needs. A batch of new photos is one
 * event, so a person either was at it or wasn't - and if they weren't, every match proposed for them is wrong at
 * once, no matter how good the individual crops look. Unchecking somebody rejects all of it in one click, which is
 * remembered, so the next round of matching doesn't offer the same wrong answer again.
 * <p>
 * Nothing here confirms anything. Whoever survives the list stays a pending proposal, to be accepted per photo -
 * where the overlay shows which face was matched to whom and the accept button takes a photo's worth at a time. This
 * dialog is only about throwing away the batches that are wrong wholesale, which is the part that doesn't need a
 * look at the photos at all.
 */
public class FaceProposalReviewDialog extends JDialog
{
    /** Wide enough for a name and a count, and what the header text is wrapped to. */
    private static final int MINIMUM_LIST_WIDTH = 400;

    /** Past this the list is scrolled rather than grown, however many people an import proposes. */
    private static final int MAXIMUM_LIST_HEIGHT = 320;

    /** One person, the faces proposed as them in this import, and the checkbox that keeps or rejects the lot. */
    private record PersonRow(String name, List<PhotoFace> faces, JCheckBox checkBox) {}

    private final List<PersonRow> _rows = new ArrayList<>();
    private final JButton _applyButton = new JButton();
    private boolean _apply;

    /**
     * Reviews the people an import proposed and rejects whoever wasn't there. Must be called on the EDT, and only
     * with a non-empty list - with nothing proposed there's nothing to review.
     */
    public static void review(int photosAdded, List<PhotoFace> proposals)
    {
        FaceProposalReviewDialog dialog = new FaceProposalReviewDialog(photosAdded, proposals);
        dialog.setVisible(true);
        if (dialog._apply)
        {
            dialog.applyDecisions();
        }
    }

    private FaceProposalReviewDialog(int photosAdded, List<PhotoFace> proposals)
    {
        super(AdminFrame.getFrame(), "Who was there?", true);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(buildHeader(photosAdded, proposals), BorderLayout.NORTH);
        content.add(buildPeopleList(proposals), BorderLayout.CENTER);
        content.add(buildButtons(), BorderLayout.SOUTH);
        setContentPane(content);

        updateApplyButton();
        getRootPane().setDefaultButton(_applyButton);
        // Escape decides nothing, the same as closing the window
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        getRootPane().getActionMap().put("cancel", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                dispose();
            }
        });

        pack();
        setLocationRelativeTo(AdminFrame.getFrame());
    }

    private JComponent buildHeader(int photosAdded, List<PhotoFace> proposals)
    {
        // The div's width is what makes this wrap: an HTML label is laid out on one line otherwise, and the whole
        // dialog then comes out as wide as this sentence is long.
        JLabel label = new JLabel("<html><div width=\"" + (MINIMUM_LIST_WIDTH - 20) + "\">" +
                "<b>Found " + photosAdded + " new photo(s), with " + proposals.size() +
                " proposed face match(es).</b><br>" +
                "Uncheck anybody who wasn't there and their matches are rejected. Everybody else stays pending, to " +
                "accept a photo at a time from the photo's own details.</div></html>");
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return label;
    }

    private JComponent buildPeopleList(List<PhotoFace> proposals)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        Color background = UIManager.getColor("List.background");
        if (background != null)
        {
            panel.setBackground(background);
        }

        for (List<PhotoFace> faces : groupByPerson(proposals).values())
        {
            String name = describePerson(faces.get(0));
            JCheckBox checkBox = new JCheckBox(name + " - " + faces.size() + " match(es)", true);
            checkBox.setOpaque(false);
            checkBox.setAlignmentX(0f);
            checkBox.setToolTipText("Uncheck to reject all " + faces.size() +
                    " proposed match(es) for " + name + " in this import");
            checkBox.addActionListener(e -> updateApplyButton());
            panel.add(checkBox);
            _rows.add(new PersonRow(name, faces, checkBox));
        }

        JScrollPane scrollPane = new JScrollPane(panel);
        // Sized to the content within limits, so three people get a small dialog and thirty get a scrollbar
        Dimension preferred = panel.getPreferredSize();
        scrollPane.setPreferredSize(new Dimension(
                Math.max(MINIMUM_LIST_WIDTH, preferred.width + 30),
                Math.min(MAXIMUM_LIST_HEIGHT, preferred.height + 8)));
        return scrollPane;
    }

    private JComponent buildButtons()
    {
        JButton checkAllButton = new JButton("Check all");
        checkAllButton.addActionListener(e -> setAllChecked(true));
        JButton uncheckAllButton = new JButton("Uncheck all");
        uncheckAllButton.setToolTipText("Rejects everything this import proposed");
        uncheckAllButton.addActionListener(e -> setAllChecked(false));

        _applyButton.addActionListener(e -> {
            _apply = true;
            dispose();
        });
        JButton cancelButton = new JButton("Cancel");
        cancelButton.setToolTipText("Rejects nothing, leaving every proposal pending");
        cancelButton.addActionListener(e -> dispose());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(checkAllButton);
        left.add(uncheckAllButton);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        right.add(cancelButton);
        right.add(_applyButton);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private void setAllChecked(boolean checked)
    {
        for (PersonRow row : _rows)
        {
            row.checkBox().setSelected(checked);
        }
        updateApplyButton();
    }

    /** The button says exactly what pressing it will do, and says so in matches, since that's what gets rejected. */
    private void updateApplyButton()
    {
        int people = 0;
        int matches = 0;
        for (PersonRow row : _rows)
        {
            if (!row.checkBox().isSelected())
            {
                people++;
                matches += row.faces().size();
            }
        }
        _applyButton.setText(people == 0
                ? "Keep all, reject nothing"
                : "Reject " + matches + " match(es) from " + people + (people == 1 ? " person" : " people"));
    }

    /**
     * Rejects every match proposed for the people who were unchecked.
     * <p>
     * Behind a progress dialog because an import of a few hundred photos can propose enough matches that recording
     * them all takes long enough to look like a hang. Nothing is confirmed and no photo is tagged, so the photo list
     * needs no refresh - only the face-driven views do.
     */
    private void applyDecisions()
    {
        List<Long> toReject = new ArrayList<>();
        List<String> rejectedPeople = new ArrayList<>();
        for (PersonRow row : _rows)
        {
            if (row.checkBox().isSelected())
            {
                continue;
            }
            rejectedPeople.add(row.name());
            for (PhotoFace face : row.faces())
            {
                toReject.add(face.getFaceId());
            }
        }
        if (toReject.isEmpty())
        {
            return;
        }

        Throwable failure = FaceProgressDialog.run("Rejecting face matches", listener -> {
            listener.progress("Rejecting " + toReject.size() + " match(es)", 0, 0);
            FaceOperations.getFaceOperations().rejectFaces(toReject);
        });
        AdminModel.getModel().fireFacesChanged();

        if (failure != null)
        {
            JOptionPane.showMessageDialog(AdminFrame.getFrame(),
                    "Rejecting the matches for " + String.join(", ", rejectedPeople) + " failed.\n\n" +
                    failure.getClass().getSimpleName() + ": " + failure.getMessage() + "\n\n" +
                    "Whatever wasn't rejected is still pending in the People tab.",
                    "Face matches", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Proposals grouped by the person they were proposed as, in name order. Keyed by category id rather than by
     * name, so that two people who happen to share a description stay two people.
     */
    private Map<Integer, List<PhotoFace>> groupByPerson(List<PhotoFace> proposals)
    {
        List<PhotoFace> sorted = new ArrayList<>(proposals);
        sorted.sort(Comparator.comparing((PhotoFace face) -> describePerson(face), String.CASE_INSENSITIVE_ORDER));

        Map<Integer, List<PhotoFace>> byPerson = new LinkedHashMap<>();
        for (PhotoFace face : sorted)
        {
            Integer personId = face.getPersonCategory() == null ? null : face.getPersonCategory().getCategoryId();
            byPerson.computeIfAbsent(personId, k -> new ArrayList<>()).add(face);
        }
        return byPerson;
    }

    private String describePerson(PhotoFace face)
    {
        Category person = face.getPersonCategory();
        return person == null || person.getDescription() == null ? "Unknown" : person.getDescription();
    }
}
