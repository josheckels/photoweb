package com.stampysoft.photoGallery;

import java.util.Set;

/**
 * Who is allowed to see what, for the duration of one request. Replaces the {@code boolean includePrivate}
 * that used to be threaded through {@link PhotoOperations}, because there are now three ways a photo can be
 * withheld rather than one:
 * <ul>
 *   <li>the photo's own {@code private} flag, as before;</li>
 *   <li>a person in it having opted out of appearing publicly ({@code category.public_opt_out}), which hides
 *       every photo tagged with that person;</li>
 *   <li>nothing at all, if the visitor holds a share token for a category the photo is in - a token lifts the
 *       opt-out hiding on the photos in that category's subtree, and on nothing else.</li>
 * </ul>
 * A token is deliberately scoped by <em>which photos</em> it reaches, not by where they are shown. Once a photo
 * is unlocked it is unlocked wherever it appears, including in another category the visitor holds no token for.
 * Photos are many-to-many with categories, so the alternative - keying the exemption on the (photo, category)
 * pair - would mean a guest who was sent the wedding link sees a photo under "Wedding 2024" and then a gap where
 * the same photo sits under "Family". The unit being protected is the person's face, and by the time a link has
 * been sent that photo is out; hiding it again a page later buys nothing and reads as a bug.
 * The rule lives in {@link #canSee(Photo)} and nowhere else. Both id sets are computed once per request by
 * {@link PhotoOperations#createVisibility}, so every caller filters in memory against the same answer instead
 * of each re-deriving it in SQL.
 * <p>
 * Note what a token deliberately does <em>not</em> do: it never reveals a {@code private} photo or category.
 * See PRIVACY.md.
 */
public final class Visibility
{
    private enum Mode
    {
        /** Everything. The admin Swing UI, and the owner's browser session. */
        OWNER_MODE,
        /** Filtered by the two id sets below. */
        ANONYMOUS,
        /** Nothing at all, whatever the id sets say. */
        DENY_ALL
    }

    /** Everything. What the admin Swing UI runs as, and what the owner's browser session gets. */
    public static final Visibility OWNER = new Visibility(Mode.OWNER_MODE, Set.of(), Set.of());

    /**
     * Nothing at all. This is the default on the entities that reach JSON, so that a code path which forgets
     * to set a real visibility renders empty rather than unfiltered - a visible bug instead of a silent leak.
     */
    public static final Visibility NONE = new Visibility(Mode.DENY_ALL, Set.of(), Set.of());

    private final Mode _mode;
    private final Set<Integer> _hiddenPhotoIds;
    private final Set<Integer> _unlockedPhotoIds;

    private Visibility(Mode mode, Set<Integer> hiddenPhotoIds, Set<Integer> unlockedPhotoIds)
    {
        _mode = mode;
        _hiddenPhotoIds = hiddenPhotoIds;
        _unlockedPhotoIds = unlockedPhotoIds;
    }

    /**
     * An anonymous visitor. {@code hiddenPhotoIds} is every photo tagged with someone who has opted out;
     * {@code unlockedPhotoIds} is every photo in the subtree of a category this visitor holds a share token
     * for, which is empty for a visitor without one.
     */
    static Visibility anonymous(Set<Integer> hiddenPhotoIds, Set<Integer> unlockedPhotoIds)
    {
        return new Visibility(Mode.ANONYMOUS, hiddenPhotoIds, unlockedPhotoIds);
    }

    public boolean isOwner()
    {
        return _mode == Mode.OWNER_MODE;
    }

    public boolean canSee(Photo photo)
    {
        // Null before anything else: a category with no cover photo has nothing to show anybody
        if (photo == null || _mode == Mode.DENY_ALL)
        {
            return false;
        }
        if (_mode == Mode.OWNER_MODE)
        {
            return true;
        }
        if (photo.isPrivate())
        {
            return false;
        }
        // By photo id, not by (photo, category): a photo a token reaches is visible everywhere it appears. See
        // the class comment for why.
        Integer photoId = photo.getPhotoId();
        return !_hiddenPhotoIds.contains(photoId) || _unlockedPhotoIds.contains(photoId);
    }

    /**
     * Categories are hidden by the {@code private} flag alone. A person's category is private, which is what
     * keeps People tags off the public site; {@code public_opt_out} on that same category is a separate thing
     * that hides the person's <em>photos</em>, and never hides a category from anyone who could already see it.
     */
    public boolean canSee(Category category)
    {
        if (category == null || _mode == Mode.DENY_ALL)
        {
            return false;
        }
        return _mode == Mode.OWNER_MODE || !category.isPrivate();
    }
}
