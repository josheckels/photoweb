package com.stampysoft.gui;

import javax.swing.Icon;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Helpers for drawing images at a high-DPI display's real resolution.
 * <p>
 * Swing measures and lays out in logical pixels, so an image rendered to the size a component reports gets blown up
 * by the compositor on a retina monitor and comes out soft. The fix is always the same shape: render the image at
 * {@link #getDeviceScale} times the logical size, then hand Swing an icon that still measures as the logical size,
 * so that layout and hit testing are unaffected while the pixels land one-for-one on the glass.
 */
public class HiDpi
{
    private HiDpi()
    {
    }

    /**
     * How many device pixels this component's display puts behind each logical one - 2 on a retina monitor, 1 on an
     * ordinary one. Must be called on the EDT.
     */
    public static double getDeviceScale(Component component)
    {
        GraphicsConfiguration configuration = component == null ? null : component.getGraphicsConfiguration();
        if (configuration == null)
        {
            // Not on a screen yet, so there's nothing better to assume
            return 1.0;
        }
        return Math.max(1.0, configuration.getDefaultTransform().getScaleX());
    }

    /**
     * The number of device pixels to render for something that will occupy the given number of logical ones.
     */
    public static int toDevicePixels(int logical, double deviceScale)
    {
        return Math.max(1, (int) Math.round(logical * deviceScale));
    }

    /**
     * Wraps a device-resolution image in an icon that measures as the smaller logical size.
     */
    public static Icon createIcon(BufferedImage image, int logicalWidth, int logicalHeight)
    {
        return new ScaledIcon(image, logicalWidth, logicalHeight);
    }

    private static class ScaledIcon implements Icon
    {
        private final BufferedImage _image;
        private final int _width;
        private final int _height;

        private ScaledIcon(BufferedImage image, int logicalWidth, int logicalHeight)
        {
            _image = image;
            _width = Math.max(1, logicalWidth);
            _height = Math.max(1, logicalHeight);
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y)
        {
            Graphics2D graphics = (Graphics2D) g.create();
            try
            {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(_image, x, y, _width, _height, null);
            }
            finally
            {
                graphics.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return _width;
        }

        @Override
        public int getIconHeight()
        {
            return _height;
        }
    }
}
