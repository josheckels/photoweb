/*
 * AbstractServlet.java
 *
 * Created on April 14, 2002, 3:16 PM
 */

package com.stampysoft.servlet;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author josh
 */
public class RedirectorServlet extends HttpServlet
{

    public RedirectorServlet()
    {
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
        processRequest(request, response);
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
        processRequest(request, response);
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {

        if (request.getRequestURI().indexOf("ant/index") != -1)
        {
            response.sendRedirect("http://www.jeckels.com/ant/index.html");
            return;
        }
        else if (request.getRequestURI().indexOf("ant/Stampysoft") != -1)
        {
            response.sendRedirect("http://www.jeckels.com/ant/StampysoftAntTasks1.1.0.zip");
            return;
        }
        else if (request.getRequestURI().indexOf("ant/UserManual") != -1)
        {
            response.sendRedirect("http://www.jeckels.com/ant/UserManual.html");
            return;
        }
        else
        {
            response.sendRedirect("http://www.jeckels.com/" );
			return;
		}
	}
}
