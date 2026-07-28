package com.stampysoft.photoGallery.faces;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * A {@link FlowLayout} that reports the height it actually needs once its rows have wrapped.
 * <p>
 * Plain FlowLayout wraps its children when it lays them out but reports a single row's height as its preferred
 * size. In a {@code BorderLayout.NORTH} slot - which grants exactly the preferred height - that means every
 * control past the first row's worth is laid out somewhere the container has no room to paint, so it simply
 * vanishes. In the People tab, which lives in the narrow left column, that silently hid the Reject button.
 */
public class WrapLayout extends FlowLayout
{
    public WrapLayout(int align, int hgap, int vgap)
    {
        super(align, hgap, vgap);
    }

    /**
     * Installs a WrapLayout on the panel and keeps its reported height correct as the column is resized.
     * <p>
     * The revalidate is what makes it work in practice: the first preferred-size query happens before the
     * container has a width, so it can only answer for one row. Asking again once a real width exists - and after
     * every resize - is what lets the row count settle.
     */
    public static void install(Container container, int hgap, int vgap)
    {
        container.setLayout(new WrapLayout(LEFT, hgap, vgap));
        container.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent e)
            {
                container.revalidate();
            }
        });
    }

    @Override
    public Dimension preferredLayoutSize(Container target)
    {
        return layoutSize(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target)
    {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred)
    {
        synchronized (target.getTreeLock())
        {
            // Before the first layout there's no width to wrap against, so answer for a single row and let the
            // resize listener ask again once there is one.
            int targetWidth = target.getSize().width;
            if (targetWidth == 0)
            {
                targetWidth = Integer.MAX_VALUE;
            }

            Insets insets = target.getInsets();
            int horizontalInsetsAndGap = insets.left + insets.right + getHgap() * 2;
            int maxWidth = targetWidth - horizontalInsetsAndGap;

            Dimension size = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;

            for (int i = 0; i < target.getComponentCount(); i++)
            {
                Component member = target.getComponent(i);
                if (!member.isVisible())
                {
                    continue;
                }
                Dimension memberSize = preferred ? member.getPreferredSize() : member.getMinimumSize();
                if (rowWidth + memberSize.width > maxWidth && rowWidth > 0)
                {
                    size = addRow(size, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0)
                {
                    rowWidth += getHgap();
                }
                rowWidth += memberSize.width;
                rowHeight = Math.max(rowHeight, memberSize.height);
            }
            size = addRow(size, rowWidth, rowHeight);

            size.width += horizontalInsetsAndGap;
            size.height += insets.top + insets.bottom + getVgap() * 2;

            // Inside a scroll pane the viewport hands back the full width, which would otherwise grow by a gap
            // on every pass and never settle.
            if (SwingUtilities.getAncestorOfClass(JScrollPane.class, target) != null && target.isValid())
            {
                size.width -= getHgap() + 1;
            }
            return size;
        }
    }

    private Dimension addRow(Dimension size, int rowWidth, int rowHeight)
    {
        size.width = Math.max(size.width, rowWidth);
        if (size.height > 0)
        {
            size.height += getVgap();
        }
        size.height += rowHeight;
        return size;
    }
}
