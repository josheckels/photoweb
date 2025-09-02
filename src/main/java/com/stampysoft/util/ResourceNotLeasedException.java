package com.stampysoft.util;

public class ResourceNotLeasedException extends ResourceManagerException
{

    public ResourceNotLeasedException(String msg)
    {
        super(msg);
    }

}