package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;

import javax.accessibility.Accessible;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.text.JTextComponent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The one control for putting a name to a face: an editable combo box that completes against the existing People
 * categories.
 * <p>
 * That dual behaviour is the point. An unnamed face or cluster is very often somebody who already has a category
 * but had no seeds, so picking them from the list has to merge into them; only a name that matches nothing creates
 * anybody new. Shared between the cluster review panel and clicking a face on the photo preview.
 * <p>
 * Typing matches any part of a name, not just the start, because the categories read "Eckels, Ruth" and the part
 * anyone actually remembers is the first name. Typing narrows the dropdown to the matches, closest first, and a name
 * that carries on from what's been typed is still filled in ahead of the caret the way it always was.
 * <p>
 * The names are held here rather than read back off the combo because the combo's own model is what gets filtered
 * down as you type - {@link #setPeople} is the only way to change them.
 */
public class FaceNameCombo
{
    private final JComboBox<String> _combo = new JComboBox<>();
    private final JTextComponent _editor;
    private final List<String> _names = new ArrayList<>();

    public FaceNameCombo()
    {
        _combo.setEditable(true);
        _editor = (JTextComponent) _combo.getEditor().getEditorComponent();
        _editor.addKeyListener(new KeyAdapter()
        {
            public void keyReleased(KeyEvent e)
            {
                if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)
                {
                    copyHighlightedNameToField();
                    return;
                }
                if (isNavigation(e.getKeyCode()))
                {
                    return;
                }
                // Deleting must not complete, or a name could never be shortened - the filled-in tail would come
                // straight back after every backspace.
                boolean deleting = e.getKeyCode() == KeyEvent.VK_BACK_SPACE || e.getKeyCode() == KeyEvent.VK_DELETE;
                refresh(!deleting);
            }
        });
        setTypedName("");
    }

    public FaceNameCombo(Collection<Category> people)
    {
        this();
        setPeople(people);
    }

    /** The combo box itself, to be laid out by the caller. */
    public JComboBox<String> getComponent()
    {
        return _combo;
    }

    /** Replaces the names that can be completed to, and clears whatever was typed. */
    public void setPeople(Collection<Category> people)
    {
        _names.clear();
        for (Category person : people)
        {
            _names.add(person.getDescription());
        }
        setTypedName("");
    }

    /** Whatever's currently typed or selected, trimmed. */
    public String getTypedName()
    {
        Object value = _combo.getEditor().getItem();
        return value == null ? "" : value.toString().trim();
    }

    /** Puts a name in the field as if it had been picked, and restores the full list to choose from. */
    public void setTypedName(String name)
    {
        setItems(_names);
        _combo.setSelectedItem(name);
        _editor.setText(name);
    }

    /**
     * Asks for a name in an OK/Cancel dialog, returning what was typed or picked, or null if it was cancelled.
     * <p>
     * The field is focused with its contents selected, so naming a face is type-the-name-and-hit-Enter without
     * having to click into anything first.
     */
    public String showDialog(Component parent, String title, String prompt)
    {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel(prompt), BorderLayout.NORTH);
        panel.add(_combo, BorderLayout.CENTER);

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(parent, title);
        // JOptionPane focuses the OK button the first time the dialog gains focus. This listener is added after the
        // one that does it, so it runs second and the field keeps the caret.
        dialog.addWindowFocusListener(new WindowAdapter()
        {
            public void windowGainedFocus(WindowEvent e)
            {
                _editor.requestFocusInWindow();
                _editor.selectAll();
            }
        });
        try
        {
            dialog.setVisible(true);
        }
        finally
        {
            dialog.dispose();
        }

        Object value = pane.getValue();
        boolean confirmed = value instanceof Integer && (Integer) value == JOptionPane.OK_OPTION;
        return confirmed ? getTypedName() : null;
    }

    /**
     * Turns a typed name into a person, creating the category only after asking. Returns null if the name is empty
     * or the user declined to create anybody.
     */
    public static Category resolvePerson(String name, PeopleService peopleService, JComponent parent)
    {
        if (name.isEmpty())
        {
            JOptionPane.showMessageDialog(parent, "Type a name, or pick an existing person from the list.",
                    "Which person?", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        Category existing = peopleService.findPersonByName(name);
        if (existing != null)
        {
            return existing;
        }
        int answer = JOptionPane.showConfirmDialog(parent,
                "There's no person called \"" + name + "\" yet. Create them?", "New person", JOptionPane.YES_NO_OPTION);
        return answer == JOptionPane.YES_OPTION ? peopleService.createPerson(name) : null;
    }

    /**
     * Narrows the dropdown to the names matching what's been typed, fills in the rest of the first name that starts
     * with it, and drops the list down so the other matches can be picked.
     * <p>
     * The list has to come down even when something completed inline, because a match at the start is no reason to
     * hide a match in the middle: "ruth" completes to Ruthford but Eckels, Ruth is just as likely to be who was
     * meant. It stays down until one name is left and it's the one in the field, which is the point at which there's
     * nothing left to choose.
     */
    private void refresh(boolean complete)
    {
        String typed = _editor.getText();
        List<String> matches = findMatches(typed);
        if (setItems(matches))
        {
            // Rebuilding the model puts the model's own selection in the editor, so what was typed goes back.
            _editor.setText(typed);
        }

        String completion = complete ? findCompletion(typed, matches) : null;
        if (completion != null)
        {
            _editor.setText(completion);
            _editor.setCaretPosition(completion.length());
            _editor.moveCaretPosition(typed.length());
        }
        _combo.setPopupVisible(!typed.isEmpty() && !matches.isEmpty() && !isSettled(matches, typed));
    }

    /**
     * Puts the name the arrow keys have moved onto into the field.
     * <p>
     * Swing arrows through an open dropdown by highlighting a row in the popup's list and nothing else, and its Enter
     * handling then declines to take that row on an editable combo - which leaves arrowing down to a name and
     * accepting it doing nothing at all. Following the highlight here is what makes the filtered list pickable.
     */
    private void copyHighlightedNameToField()
    {
        if (!_combo.isPopupVisible())
        {
            // With the popup closed the arrows move the combo's own selection, which fills the field in already
            return;
        }
        JList<Object> list = getPopupList();
        Object highlighted = list == null ? null : list.getSelectedValue();
        if (highlighted == null)
        {
            return;
        }
        String name = highlighted.toString();
        _editor.setText(name);
        _editor.setCaretPosition(name.length());
    }

    /** The list inside the dropdown, which is the only place the arrow keys' idea of "selected" is recorded. */
    private JList<Object> getPopupList()
    {
        Accessible popup = _combo.getUI().getAccessibleChild(_combo, 0);
        //noinspection unchecked
        return popup instanceof ComboPopup ? ((ComboPopup) popup).getList() : null;
    }

    /** Whether what's been typed has arrived at exactly one name, leaving nothing to choose between. */
    private boolean isSettled(List<String> matches, String typed)
    {
        return matches.size() == 1 && matches.get(0).equalsIgnoreCase(typed);
    }

    /** The names containing what's been typed, the ones starting with it first, or all of them for empty text. */
    private List<String> findMatches(String typed)
    {
        if (typed.isEmpty())
        {
            return new ArrayList<>(_names);
        }
        String lower = typed.toLowerCase();
        List<String> starting = new ArrayList<>();
        List<String> containing = new ArrayList<>();
        for (String name : _names)
        {
            String nameLower = name.toLowerCase();
            if (nameLower.startsWith(lower))
            {
                starting.add(name);
            }
            else if (nameLower.contains(lower))
            {
                containing.add(name);
            }
        }
        starting.addAll(containing);
        return starting;
    }

    /** The name to fill in ahead of the caret: the first match that carries on from what's been typed. */
    private String findCompletion(String typed, List<String> matches)
    {
        if (typed.isEmpty())
        {
            return null;
        }
        for (String match : matches)
        {
            if (match.length() > typed.length() && match.toLowerCase().startsWith(typed.toLowerCase()))
            {
                return match;
            }
        }
        return null;
    }

    /** Puts these names in the dropdown, doing nothing if they're the ones already there. Returns whether it did. */
    private boolean setItems(List<String> items)
    {
        List<String> current = new ArrayList<>();
        for (int i = 0; i < _combo.getItemCount(); i++)
        {
            current.add(_combo.getItemAt(i));
        }
        if (current.equals(items))
        {
            return false;
        }

        // An open popup is sized for the list it was opened with, so it has to be closed and re-opened around this.
        boolean wasVisible = _combo.isPopupVisible();
        _combo.hidePopup();
        _combo.setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
        if (wasVisible && !items.isEmpty())
        {
            _combo.showPopup();
        }
        return true;
    }

    /** Keys that move around the field or the dropdown rather than changing what's been typed. */
    private static boolean isNavigation(int keyCode)
    {
        return switch (keyCode)
        {
            case KeyEvent.VK_ENTER, KeyEvent.VK_ESCAPE, KeyEvent.VK_TAB, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
                 KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_PAGE_UP,
                 KeyEvent.VK_PAGE_DOWN, KeyEvent.VK_SHIFT, KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_META,
                 KeyEvent.VK_CAPS_LOCK -> true;
            default -> false;
        };
    }
}
