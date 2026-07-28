package com.stampysoft.photoGallery.admin;

/**
 * Notified when face data changes - a scan finished, matching ran, or somebody confirmed or rejected a proposal -
 * so that the People tab and the overlay on the photo preview can refresh.
 * <p>
 * Same shape as {@link PhotoListener}: an abstract class of no-ops, so adding an event doesn't break implementors.
 */
public abstract class FaceListener
{
    /** Faces were added, removed, confirmed, rejected, reassigned or re-clustered. */
    public void facesChanged() {}
}
