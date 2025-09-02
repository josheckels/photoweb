/*
 * IteratorBackedEnumeration.java
 *
 * Created on April 14, 2002, 4:41 PM
 */

package com.stampysoft.util;

import java.util.Enumeration;
import java.util.Iterator;

/**
 * @author josh
 */
public class IteratorBackedEnumeration implements Enumeration
{

    private Iterator _iterator;

    public IteratorBackedEnumeration(Iterator iterator)
    {
        _iterator = iterator;
    }

    public boolean hasMoreElements()
    {
        return _iterator.hasNext();
    }

    public Object nextElement()
    {
        return _iterator.next();
    }

}
