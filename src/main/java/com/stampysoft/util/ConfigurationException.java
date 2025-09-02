/*
 * ConfigurationException.java
 *
 * Created on April 20, 2002, 3:20 PM
 */

package com.stampysoft.util;

/**
 * @author josh
 */
public class ConfigurationException extends RuntimeException
{

    public ConfigurationException(String message)
    {
        super(message);
    }

    public ConfigurationException(String message, Throwable t)
    {
        super(message, t);
    }

}
