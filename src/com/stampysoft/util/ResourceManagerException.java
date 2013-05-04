package com.stampysoft.util;

public class ResourceManagerException extends SystemException
{

    public ResourceManagerException(String msg)
    {
        super(msg);
    }

    public ResourceManagerException(String msg, Throwable e)
    {
        super(msg, e);
    }

    public ResourceManagerException(Throwable e)
    {
        super(e);
    }
}