package com.stampysoft.imagecopier;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class TimeGroupingPanel extends JPanel
{

    private final TimeGrouping _timeGrouping;
    private final TimeGroupingPanel _nextPanel;
    private JPanel _photoPanel;

    public TimeGroupingPanel(TimeGrouping timeGrouping)
    {
        this(timeGrouping, null);
    }

    public TimeGroupingPanel(TimeGrouping timeGrouping, TimeGroupingPanel nextPanel)
    {
        super(new BorderLayout());
        _timeGrouping = timeGrouping;
        _nextPanel = nextPanel;

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(new JLabel(_timeGrouping.toString() + " - (" + _timeGrouping.getCount() + " photos)"), BorderLayout.WEST);

        add(headerPanel, BorderLayout.NORTH);

        add(createPhotoPanel(_timeGrouping.getFiles()), BorderLayout.CENTER);
    }

    public void startGeneratingThumbnails()
    {
        BackgroundThumbnailGenerator generator = new BackgroundThumbnailGenerator();
        Thread t = new Thread(generator);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    private JComponent createPhotoPanel(List<File> jpegFiles)
    {
        final GridLayout layout = new GridLayout(0, 1, 5, 5);
        _photoPanel = new JPanel(layout);
        JScrollPane scrollPane = new JScrollPane(_photoPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.addComponentListener(new ComponentAdapter()
        {

            @Override
            public void componentResized(ComponentEvent e)
            {
                layout.setColumns(e.getComponent().getWidth() / (MainFrame.THUMBNAIL_SIZE + 5));
                _photoPanel.revalidate();
            }

        });

        return scrollPane;
    }

    private class BackgroundThumbnailGenerator implements Runnable
    {

        public void run()
        {
            for (final File f : _timeGrouping.getFiles())
            {
                Image image = Toolkit.getDefaultToolkit().getImage(f.getPath());
                MediaTracker mediaTracker = new MediaTracker(new Container());
                mediaTracker.addImage(image, 0);
                try
                {
                    mediaTracker.waitForID(0);
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                // determine thumbnail size from WIDTH and HEIGHT
                int thumbWidth = MainFrame.THUMBNAIL_SIZE;
                int thumbHeight = MainFrame.THUMBNAIL_SIZE;
                double thumbRatio = (double) thumbWidth / (double) thumbHeight;
                int imageWidth = image.getWidth(null);
                int imageHeight = image.getHeight(null);
                double imageRatio = (double) imageWidth / (double) imageHeight;
                if (thumbRatio < imageRatio)
                {
                    thumbHeight = (int) (thumbWidth / imageRatio);
                }
                else
                {
                    thumbWidth = (int) (thumbHeight * imageRatio);
                }
                // draw original image to thumbnail image object and
                // scale it to the new size on-the-fly
                BufferedImage thumbImage = new BufferedImage(thumbWidth,
                    thumbHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics2D = thumbImage.createGraphics();
                graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics2D.drawImage(image, 0, 0, thumbWidth, thumbHeight, null);
                final ImageIcon icon = new ImageIcon(thumbImage);
                SwingUtilities.invokeLater(new Runnable()
                {

                    public void run()
                    {
                        ThumbnailInfoPanel infoPanel = new ThumbnailInfoPanel(f, icon, _timeGrouping.isSelected());
                        _photoPanel.add(infoPanel);
                        _photoPanel.revalidate();
                    }

                });
            }
            if (_nextPanel != null)
            {
                _nextPanel.startGeneratingThumbnails();
            }
        }
    }
}
