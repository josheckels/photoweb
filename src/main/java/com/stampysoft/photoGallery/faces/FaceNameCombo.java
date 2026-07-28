package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.util.List;

/**
 * The one control for putting a name to a face: an editable combo box that completes against the existing People
 * categories.
 * <p>
 * That dual behaviour is the point. An unnamed face or cluster is very often somebody who already has a category
 * but had no seeds, so picking them from the list has to merge into them; only a name that matches nothing creates
 * anybody new. Shared between the cluster review panel and clicking a face on the photo preview.
 */
public class FaceNameCombo
{
    private FaceNameCombo()
    {
    }

    /** An editable, autocompleting combo box over the given people's names. */
    public static JComboBox<String> create(List<Category> people)
    {
        JComboBox<String> combo = new JComboBox<>();
        for (Category person : people)
        {
            combo.addItem(person.getDescription());
        }
        combo.setEditable(true);
        combo.setSelectedItem("");
        installAutoComplete(combo);
        return combo;
    }

    /** Whatever's currently typed or selected, trimmed. */
    public static String getTypedName(JComboBox<String> combo)
    {
        Object value = combo.getEditor().getItem();
        return value == null ? "" : value.toString().trim();
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
     * Makes an editable combo box complete against its own items as you type, selecting the part it filled in so
     * that carrying on typing replaces it.
     */
    public static void installAutoComplete(JComboBox<String> combo)
    {
        JTextComponent editor = (JTextComponent) combo.getEditor().getEditorComponent();
        editor.getDocument().addDocumentListener(new DocumentListener()
        {
            private boolean _completing;

            public void insertUpdate(DocumentEvent e)
            {
                complete();
            }

            public void removeUpdate(DocumentEvent e)
            {
            }

            public void changedUpdate(DocumentEvent e)
            {
            }

            private void complete()
            {
                if (_completing)
                {
                    return;
                }
                // A document can't be edited from inside its own listener, so completion happens on the next tick.
                SwingUtilities.invokeLater(() -> {
                    String typed = editor.getText();
                    if (typed.isEmpty())
                    {
                        return;
                    }
                    for (int i = 0; i < combo.getItemCount(); i++)
                    {
                        String item = combo.getItemAt(i);
                        if (item != null && item.length() > typed.length()
                                && item.toLowerCase().startsWith(typed.toLowerCase()))
                        {
                            _completing = true;
                            try
                            {
                                editor.setText(item);
                                editor.setCaretPosition(item.length());
                                editor.moveCaretPosition(typed.length());
                            }
                            finally
                            {
                                _completing = false;
                            }
                            return;
                        }
                    }
                });
            }
        });
    }
}
