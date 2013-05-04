package com.stampysoft.photoGallery.servlet;

import com.stampysoft.servlet.AbstractServlet;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;

/**
 * User: Josh
 * Date: Nov 23, 2008
 */
public class CategoryListServlet extends AbstractServlet
{
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        boolean includePrivate = includePrivate(request);
        boolean sortByCreation = "creation".equalsIgnoreCase(request.getParameter("sort"));

        List<Category> categories = PhotoOperations.getPhotoOperations().getAllCategories(includePrivate, sortByCreation);

        request.setAttribute("DescriptionSort", !sortByCreation);
        request.setAttribute("CreationSort", sortByCreation);
        request.setAttribute("Categories", categories);

        getServletContext().getRequestDispatcher("/categoryList.jsp").forward(request, response);
    }
}
