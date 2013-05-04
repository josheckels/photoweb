package com.stampysoft.photoGallery.servlet;

import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.servlet.AbstractServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class HomepageServlet extends AbstractServlet
{

    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, java.io.IOException
    {
        if ("true".equals(request.getParameter("includePrivate")))
        {
            setIncludePrivate(request, true);
        }

        boolean includePrivate = includePrivate(request);
        request.setAttribute("Subcategories", PhotoOperations.getPhotoOperations().getRootCategories(includePrivate));
        request.setAttribute("NewestCategories", PhotoOperations.getPhotoOperations().getNewestCategories(5, includePrivate));

        getServletContext().getRequestDispatcher("/homepage.jsp").forward(request, response);
    }

}
