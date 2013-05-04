/*
 * SystemException.java
 *
 * Created on February 11, 2002, 7:39 PM
 */

package com.stampysoft.util;

/**
 * @author josh
 */
public class SystemException extends Exception
{

    /**
     * Creates a new instance of SystemException
     */
    public SystemException(String message)
    {
        super(message);
    }

    public SystemException(String message, Throwable t)
    {
        super(message, t);
    }

    public SystemException(Throwable t)
    {
        super(t);
	}	
}