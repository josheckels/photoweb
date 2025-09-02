/*
 * CategoryBrowser.java
 *
 * Created on February 27, 2002, 7:10 PM
 */

package com.stampysoft.photoGallery.servlet;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.Photographer;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.servlet.AbstractServlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author josh
 */
public class CategoryBrowser extends AbstractServlet
{

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     *
     * @param request  servlet request
     * @param response servlet response
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {
        boolean includePrivate = includePrivate(request);

        String categoryString = request.getParameter("CategoryId");
        if (categoryString == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long categoryId;
        try
        {
            categoryId = Long.parseLong(request.getParameter("CategoryId"));
        }
        catch (NumberFormatException e)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<Category> subCategories = AdminFrame.getFrame().getPhotoOperations().getCategoriesByParentId(categoryId, includePrivate);
        Category category = AdminFrame.getFrame().getPhotoOperations().getCategoryByCategoryId(categoryId, includePrivate);
        if (category == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<Photo> photos = new ArrayList<>(category.getPhotos(includePrivate));
        List<Category> pathToRoot = category.getPathToRoot();

        request.setAttribute("Category", category);
        request.setAttribute("SubCategories", subCategories);
        request.setAttribute("PathToRoot", pathToRoot);
        request.setAttribute("Photos", photos);

        Set<Photographer> photographers = new HashSet<Photographer>();
        for (Photo photo : photos)
        {
            photographers.add(photo.getPhotographer());
        }

        Photographer photographer;
        if (photographers.isEmpty())
        {
            photographer = null;
        }
        else if (photographers.size() > 1)
        {
            photographer = Photographer.VARIOUS_PHOTOGRAPHER;
        }
        else
        {
            photographer = photographers.iterator().next();
            if (photographer == null)
            {
                photographer = Photographer.UNKNOWN_PHOTOGRAPHER;
            }
        }

        request.setAttribute("Photographer", photographer);

        getServletContext().getRequestDispatcher("/categoryBrowser.jsp").forward(request, response);
    }

    public String getServletInfo()
    {
        return "Category Browser for Photo Gallery";
	}	
}
