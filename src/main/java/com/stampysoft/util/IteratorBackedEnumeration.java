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
public class IteratorBackedEnumeration<T> implements Enumeration<T>
{
    private final Iterator<T> _iterator;

    public IteratorBackedEnumeration(Iterator<T> iterator)
    {
        _iterator = iterator;
    }

    public boolean hasMoreElements()
    {
        return _iterator.hasNext();
    }

    public T nextElement()
    {
        return _iterator.next();
    }
}
