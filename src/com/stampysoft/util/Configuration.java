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

    private Properties _properties = new Properties();

    private static String _configFileName = "config.properties";

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

    public String getProperty(String key)
    {
        String result = _properties.getProperty(key);
        if (result == null)
        {
            throw new ConfigurationException("Configuration property \"" + key + "\" was not set in config.properties");
        }
        return result;
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
