/*
 * AbstractServlet.java
 *
 * Created on April 14, 2002, 3:16 PM
 */

package com.stampysoft.servlet;

import com.stampysoft.photoGallery.PhotoOperations;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @author josh
 */
public abstract class AbstractServlet extends HttpServlet
{
    private static final String INCLUDE_PRIVATE = "includePrivate";

    /**
     * Creates a new instance of AbstractServlet
     */
    public AbstractServlet()
    {
    }

    public static boolean includePrivate(HttpServletRequest request)
    {
        HttpSession session = request.getSession(true);
        Boolean includePrivate = (Boolean) session.getAttribute(INCLUDE_PRIVATE);
        return includePrivate != null && includePrivate.booleanValue();
    }

    public static void setIncludePrivate(HttpServletRequest request, boolean includePrivate)
    {
        HttpSession session = request.getSession(true);
        session.setAttribute(INCLUDE_PRIVATE, includePrivate);
    }

    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);
    }

    /**
     * Handles the HTTP <code>GET</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {
        internalProcessRequest(request, response);
    }

    /**
     * Handles the HTTP <code>POST</code> method.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {
        internalProcessRequest(request, response);
    }

    private void internalProcessRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {
        PhotoOperations.getPhotoOperations().beginTransaction();
        try
        {
            processRequest(request, response);
        }
        finally
        {
            PhotoOperations.getPhotoOperations().commit();
        }
    }

    protected abstract void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException;
}
