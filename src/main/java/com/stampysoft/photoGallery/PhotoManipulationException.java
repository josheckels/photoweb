/*
 * PhotoManipulationException.java
 *
 * Created on February 24, 2002, 1:54 PM
 */

package com.stampysoft.photoGallery;

/**
 * @author josh
 */
public class PhotoManipulationException extends Exception
{

    /**
     * Creates a new instance of PhotoManipulationException
     */
    public PhotoManipulationException(Throwable t)
    {
        super(t);
    }

    /**
     * Creates a new instance of PhotoManipulationException
     */
    public PhotoManipulationException(String message, Throwable t)
    {
        super(message, t );
	}

}
