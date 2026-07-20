package com.stampysoft.photoGallery.admin;

import morfologik.speller.Speller;
import morfologik.stemming.Dictionary;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpellCheckPane extends JTextPane
{
    private static final Logger LOG = Logger.getLogger(SpellCheckPane.class.getName());
    private static final Highlighter.HighlightPainter WAVY_PAINTER = new WavyUnderlinePainter();
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b[a-zA-Z']+\\b");
    private static final Speller SPELLER = loadSpeller();

    private final Timer _spellCheckTimer = new Timer(400, e -> recheckSpelling());
    private final int _rows;

    public SpellCheckPane(int rows)
    {
        _rows = rows;
        _spellCheckTimer.setRepeats(false);
        getDocument().addDocumentListener(new DocumentListener()
        {
            public void insertUpdate(DocumentEvent e)  { _spellCheckTimer.restart(); }
            public void removeUpdate(DocumentEvent e)  { _spellCheckTimer.restart(); }
            public void changedUpdate(DocumentEvent e) { }
        });
    }

    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return true;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize()
    {
        Dimension size = super.getPreferredScrollableViewportSize();
        size.height = getFontMetrics(getFont()).getHeight() * _rows;
        return size;
    }

    private void recheckSpelling()
    {
        if (SPELLER == null) return;
        Highlighter h = getHighlighter();
        h.removeAllHighlights();
        String text = getText();
        Matcher m = WORD_PATTERN.matcher(text);
        while (m.find())
        {
            if (SPELLER.isMisspelled(m.group()))
            {
                try
                {
                    h.addHighlight(m.start(), m.end(), WAVY_PAINTER);
                }
                catch (BadLocationException ignored) { }
            }
        }
    }

    private static Speller loadSpeller()
    {
        URL dictURL = SpellCheckPane.class.getResource("/org/languagetool/resource/en/hunspell/en_US.dict");
        if (dictURL == null)
        {
            LOG.warning("en_US.dict not found on classpath — spell checking disabled");
            return null;
        }
        try
        {
            return new Speller(Dictionary.read(dictURL));
        }
        catch (IOException e)
        {
            LOG.log(Level.WARNING, "Failed to load spell-check dictionary", e);
            return null;
        }
    }

    private static class WavyUnderlinePainter implements Highlighter.HighlightPainter
    {
        @Override
        public void paint(Graphics g, int p0, int p1, Shape bounds, JTextComponent c)
        {
            try
            {
                Rectangle2D r0 = c.modelToView2D(p0);
                Rectangle2D r1 = c.modelToView2D(p1);
                int y = (int) (r0.getY() + r0.getHeight()) - 1;
                g.setColor(Color.RED);
                for (int x = (int) r0.getX(); x < (int) r1.getX(); x += 4)
                {
                    g.drawLine(x,     y,     x + 2, y + 2);
                    g.drawLine(x + 2, y + 2, x + 4, y);
                }
            }
            catch (BadLocationException ignored) { }
        }
    }
}
