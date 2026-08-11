/*
 * Configuration.java
 *
 * Created on April 20, 2002, 3:19 PM
 */

package com.stampysoft.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author josh
 */
public class Configuration
{

    private final Properties _properties = new Properties();

    private static String _configFileName = "./src/config.properties";

    private Configuration() throws ConfigurationException
    {
        try
        {
            InputStream in = getClass().getClassLoader().getResourceAsStream(_configFileName);
            if (in == null)
            {
                File f = new File(_configFileName);
                if (!f.exists())
                {
                    throw new ConfigurationException("Unable to find configuration file - " + _configFileName);
                }
                in = new FileInputStream(f);
            }
            _properties.load(in);
            in.close();
        }
        catch (IOException e)
        {
            throw new ConfigurationException("Unable to load from config.properties", e);
        }
    }

    /**
     * Values are trimmed. {@link Properties#load} discards whitespace <em>before</em> a value but keeps every
     * character after it, so a line editor that pads to the end of the line silently produces a bucket named
     * {@code "photo.jeckels.com          "} - which S3 answers with a 404 that looks like a missing object
     * rather than a missing space. No setting here has ever wanted a leading or trailing space, and the file is
     * hand-edited on a server, so trimming is the safe reading.
     */
    public String getProperty(String key)
    {
        String result = _properties.getProperty(key);
        if (result == null)
        {
            throw new ConfigurationException("Configuration property \"" + key + "\" was not set in config.properties");
        }
        return result.trim();
    }

    /**
     * Returns the configured value, or defaultValue if the property isn't set. Use this for optional settings, so
     * that a missing property degrades a feature instead of failing the whole application. Trimmed, as above.
     */
    public String getProperty(String key, String defaultValue)
    {
        String result = _properties.getProperty(key);
        return result == null ? defaultValue : result.trim();
    }

    private static Configuration g_configuration;

    public static synchronized Configuration getConfiguration()
    {
        if (g_configuration == null)
        {
            if (System.getProperty("configPath") != null)
            {
                _configFileName = System.getProperty("configPath");
            }
            g_configuration = new Configuration();
        }

        return g_configuration;
    }

    public static void setConfigFileName(String name)
    {
        _configFileName = name;
    }
}
