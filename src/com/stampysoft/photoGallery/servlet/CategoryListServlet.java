package com.stampysoft.photoGallery.servlet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.servlet.AbstractServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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

        if ("true".equals(request.getParameter("json")))
        {
            response.setContentType("application/json; charset=utf-8");
            JsonGenerator jg = new JsonFactory().createGenerator(response.getWriter());
            jg.useDefaultPrettyPrinter();
            jg.setCodec(new ObjectMapper());  // makes the generator annotation aware
            jg.writeObject(categories);
        }
        else
        {
            request.setAttribute("DescriptionSort", !sortByCreation);
            request.setAttribute("CreationSort", sortByCreation);
            request.setAttribute("Categories", categories);

            getServletContext().getRequestDispatcher("/categoryList.jsp").forward(request, response);
        }
    }
}
