package com.stampysoft.photoGallery.admin;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.photoGallery.common.Resolution;
import com.stampysoft.photoGallery.storage.S3Uploader;
import com.stampysoft.util.Configuration;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deletes photos from everywhere they exist: the database, the local originals and resized directories, and S3.
 * <p>
 * The order matters. The database goes first, because if it fails nothing has been lost, whereas a file removed
 * ahead of a row that then survives leaves a photo the gallery still lists but can no longer show. Files that fail
 * to delete afterwards are reported rather than retried - by then they're orphans, and orphans are only clutter.
 * <p>
 * S3 deletes go through the same background queue as uploads, so they don't hold up the UI and, like uploads, are
 * skipped entirely when S3 isn't configured.
 */
public class PhotoDeleter
{
    /** How many filenames the confirmation dialog spells out before it just gives the count. */
    private static final int MAX_FILENAMES_SHOWN = 12;

    /** How many categories the confirmation dialog names when they're about to lose their default photo. */
    private static final int MAX_CATEGORIES_SHOWN = 8;

    /** A resized file, whatever size it is: {@code <photoId>-<width>x<height>.jpg}, per {@link Resolution}. */
    private static final Pattern RESIZED_FILENAME = Pattern.compile("(\\d+)-\\d+x\\d+\\.jpg", Pattern.CASE_INSENSITIVE);

    private PhotoDeleter()
    {
    }

    /**
     * Asks the user to confirm, and if they do, deletes the given photos everywhere. Must be called on the EDT.
     *
     * @return whether anything was deleted
     */
    public static boolean confirmAndDelete(Component parent, List<Photo> photos)
    {
        if (photos == null || photos.isEmpty())
        {
            return false;
        }

        List<Photo> toDelete = new ArrayList<>(photos);
        List<File> exportedFiles = getExistingExportedFiles(toDelete);
        JCheckBox deleteExportedCheckBox = createDeleteExportedCheckBox(exportedFiles);

        if (JOptionPane.showConfirmDialog(parent, buildConfirmationPanel(toDelete, deleteExportedCheckBox),
                toDelete.size() == 1 ? "Delete Photo" : "Delete " + toDelete.size() + " Photos",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION)
        {
            return false;
        }

        // The info panel saves whatever was selected whenever the selection changes, and saving means merging a
        // detached photo - which would insert the row straight back in. Clearing the selection now makes that save
        // happen while these photos still exist, and leaves nothing behind to merge afterwards.
        AdminModel.getModel().fireSelectedPhotosChanged(Collections.emptyList());

        try
        {
            PhotoOperations.getPhotoOperations().deletePhotos(toDelete);
        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
            // Nothing has been deleted, so put the list back the way it was and leave every file alone
            AdminModel.getModel().firePhotoListChanged();
            JOptionPane.showMessageDialog(parent, "Nothing was deleted:\n" + e.getMessage(),
                    "Delete Failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }

        List<String> failures = deleteFiles(toDelete, deleteExportedCheckBox != null && deleteExportedCheckBox.isSelected());

        AdminModel.getModel().firePhotoListChanged();
        // The People tab counts faces per person, and those faces went with the photos
        AdminModel.getModel().fireFacesChanged();

        if (!failures.isEmpty())
        {
            JOptionPane.showMessageDialog(parent,
                    buildMessage("The photos were deleted, but these files could not be removed and are now orphaned:", failures),
                    "Files Left Behind", JOptionPane.WARNING_MESSAGE);
        }
        return true;
    }

    private static JPanel buildConfirmationPanel(List<Photo> photos, JCheckBox deleteExportedCheckBox)
    {
        List<String> filenames = new ArrayList<>();
        for (Photo photo : photos)
        {
            filenames.add(photo.getFilename());
        }

        StringBuilder message = new StringBuilder();
        message.append(photos.size() == 1
                ? "Permanently delete this photo?"
                : "Permanently delete these " + photos.size() + " photos?");
        message.append("\n\n");
        message.append(join(filenames, MAX_FILENAMES_SHOWN));
        message.append("\n\nThis removes them from the database, along with their categories, comments and\n");
        message.append("detected faces, and deletes the original and resized images locally and from S3.\n");
        message.append("It cannot be undone.");

        String defaultPhotoWarning = describeCategoriesLosingDefaultPhoto(photos);
        if (defaultPhotoWarning != null)
        {
            message.append("\n\n").append(defaultPhotoWarning);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        JTextArea textArea = new JTextArea(message.toString());
        textArea.setEditable(false);
        textArea.setOpaque(false);
        textArea.setFocusable(false);
        textArea.setFont(UIManager.getFont("Label.font"));
        panel.add(textArea, BorderLayout.CENTER);
        if (deleteExportedCheckBox != null)
        {
            panel.add(deleteExportedCheckBox, BorderLayout.SOUTH);
        }
        return panel;
    }

    /**
     * Describes the categories that are about to lose their default photo, or null if there aren't any. Face data
     * and category data are both optional enough that this is best-effort: it must not stop a delete.
     */
    private static String describeCategoriesLosingDefaultPhoto(List<Photo> photos)
    {
        List<Category> categories;
        try
        {
            categories = PhotoOperations.getPhotoOperations().getCategoriesWithDefaultPhoto(photos);
        }
        catch (RuntimeException e)
        {
            return null;
        }
        if (categories.isEmpty())
        {
            return null;
        }

        List<String> descriptions = new ArrayList<>();
        for (Category category : categories)
        {
            descriptions.add(category.getDescription());
        }
        return (categories.size() == 1
                ? "This category uses one of them as its default photo and will be left without one:\n"
                : "These " + categories.size() + " categories use one of them as their default photo and will be left without one:\n")
                + join(descriptions, MAX_CATEGORIES_SHOWN);
    }

    /**
     * The opt-in for removing the copies in the exported folder, or null when none of the photos have one there.
     * <p>
     * It defaults to on because the scan treats that folder as its source: leave a file there and the next scan
     * puts the photo straight back, which is the opposite of what deleting it was for.
     */
    private static JCheckBox createDeleteExportedCheckBox(List<File> exportedFiles)
    {
        if (exportedFiles.isEmpty())
        {
            return null;
        }
        JCheckBox checkBox = new JCheckBox(exportedFiles.size() == 1
                ? "Also delete the source file in the exported photos folder"
                : "Also delete the " + exportedFiles.size() + " source files in the exported photos folder", true);
        checkBox.setToolTipText("Scanning for new photos reads that folder, so a source file left there adds the photo back.");
        return checkBox;
    }

    private static List<File> getExistingExportedFiles(List<Photo> photos)
    {
        List<File> result = new ArrayList<>();
        File directory = getExportedPhotosDirectory();
        if (directory == null)
        {
            return result;
        }
        for (Photo photo : photos)
        {
            File file = new File(directory, photo.getFilename());
            if (file.isFile())
            {
                result.add(file);
            }
        }
        return result;
    }

    /** The folder the scan imports from, or null if it isn't configured - it's optional, like the S3 settings. */
    private static File getExportedPhotosDirectory()
    {
        String path = Configuration.getConfiguration().getProperty("ExportedPhotosDirectory", null);
        return path == null || path.isEmpty() ? null : new File(path);
    }

    /**
     * Deletes every file belonging to these photos and queues the matching S3 deletes.
     *
     * @return a description of each file that couldn't be deleted, empty if they all went
     */
    private static List<String> deleteFiles(List<Photo> photos, boolean deleteExported)
    {
        List<String> failures = new ArrayList<>();

        File photosDirectory = new File(ResolutionUtil.PHOTOS_DIRECTORY_VALUE);
        File resizedDirectory = new File(ResolutionUtil.RESIZED_PHOTOS_DIRECTORY_VALUE);
        File exportedDirectory = deleteExported ? getExportedPhotosDirectory() : null;
        Map<Integer, List<String>> resizedFilesOnDisk = mapResizedFilesByPhotoId(resizedDirectory);
        S3Uploader s3Uploader = S3Uploader.getInstance();

        for (Photo photo : photos)
        {
            for (String resizedFilename : getResizedFilenames(photo, resizedFilesOnDisk))
            {
                delete(new File(resizedDirectory, resizedFilename), failures);
                s3Uploader.enqueueDeleteResized(resizedFilename);
            }

            delete(new File(photosDirectory, photo.getFilename()), failures);
            s3Uploader.enqueueDeleteOriginal(photo.getFilename());

            if (photo.isMovie())
            {
                delete(new File(photosDirectory, photo.getMovieFilename()), failures);
                s3Uploader.enqueueDeleteOriginal(photo.getMovieFilename());
            }

            if (exportedDirectory != null)
            {
                delete(new File(exportedDirectory, photo.getFilename()), failures);
            }
        }

        return failures;
    }

    /**
     * Every resized file this photo has: the sizes the code generates today, plus whatever is actually sitting on
     * disk under its id. The second half is what catches sizes that were generated by an older version and would
     * otherwise be left behind for good.
     */
    private static Set<String> getResizedFilenames(Photo photo, Map<Integer, List<String>> resizedFilesOnDisk)
    {
        Set<String> result = new LinkedHashSet<>();
        for (Resolution resolution : photo.getResizedDimensions())
        {
            // A photo smaller than one of the target sizes is served at its original size, and that Resolution
            // names the original file in the other directory rather than a resized copy of it
            if (!photo.getFilename().equals(resolution.getFilename()))
            {
                result.add(resolution.getFilename());
            }
        }
        if (photo.getPhotoId() != null)
        {
            result.addAll(resizedFilesOnDisk.getOrDefault(photo.getPhotoId(), Collections.emptyList()));
        }
        return result;
    }

    /**
     * Groups the resized files by the photo id their name starts with, so that deleting a whole selection reads the
     * directory once instead of once per photo. There are hundreds of thousands of files in there.
     */
    private static Map<Integer, List<String>> mapResizedFilesByPhotoId(File resizedDirectory)
    {
        Map<Integer, List<String>> result = new HashMap<>();
        String[] filenames = resizedDirectory.list();
        if (filenames == null)
        {
            return result;
        }
        for (String filename : filenames)
        {
            Matcher matcher = RESIZED_FILENAME.matcher(filename);
            if (matcher.matches())
            {
                try
                {
                    result.computeIfAbsent(Integer.valueOf(matcher.group(1)), k -> new ArrayList<>()).add(filename);
                }
                catch (NumberFormatException ignored)
                {
                    // An id too big to be one of ours, so it isn't a file of ours either
                }
            }
        }
        return result;
    }

    private static void delete(File file, List<String> failures)
    {
        if (file.exists() && !file.delete())
        {
            failures.add(file.getAbsolutePath());
        }
    }

    private static String buildMessage(String heading, List<String> lines)
    {
        return heading + "\n\n" + join(lines, MAX_FILENAMES_SHOWN);
    }

    private static String join(List<String> values, int maxShown)
    {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < Math.min(values.size(), maxShown); i++)
        {
            result.append("    ").append(values.get(i)).append("\n");
        }
        if (values.size() > maxShown)
        {
            result.append("    ... and ").append(values.size() - maxShown).append(" more\n");
        }
        return result.toString();
    }
}
