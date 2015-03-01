/*
 * CategoryBrowser.java
 *
 * Created on February 27, 2002, 7:10 PM
 */

package com.stampysoft.photoGallery.servlet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.Photographer;
import com.stampysoft.servlet.AbstractServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;
import java.io.IOException;
import java.util.*;

/**
 * @author josh
 */
public class CategoryRESTServlet extends AbstractServlet
{

    private static final Category ROOT_CATEGORY;

    static
    {
        ROOT_CATEGORY = new Category();
        ROOT_CATEGORY.setDescription("All Categories");
        ROOT_CATEGORY.setPhotos(Collections.<Photo>emptySet());
    }

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

        Long categoryId = null;

        String url = request.getRequestURI();
        boolean json = false;
        if (url.endsWith(".json"))
        {
            json = true;
            url = url.substring(0, url.length() - ".json".length());
        }
        String[] parts = url.split("/");
        if (parts.length > 2)
        {
            try
            {
                categoryId = new Long(parts[2]);
            }
            catch (NumberFormatException e)
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }

        List<Category> subCategories = PhotoOperations.getPhotoOperations().getCategoriesByParentId(categoryId, includePrivate);
        Category category;
        if (categoryId == null)
        {
            category = ROOT_CATEGORY;
        }
        else
        {
            category = PhotoOperations.getPhotoOperations().getCategoryByCategoryId(categoryId, includePrivate);
            if (category == null)
            {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        }

        if (parts.length > 3)
        {
            if (showPhoto(request, response, includePrivate, parts, category)) return;
            return;
        }

        showCategory(request, response, includePrivate, subCategories, category, json);
    }

    private void showCategory(HttpServletRequest request, HttpServletResponse response, boolean includePrivate, List<Category> subCategories, Category category, boolean json) throws ServletException, IOException
    {
        List<Photo> photos = new ArrayList<Photo>(category.getPhotos(includePrivate));

        if (json)
        {
            response.setContentType("application/json; charset=utf-8");
            JsonGenerator jg = new JsonFactory().createGenerator(response.getWriter());
            jg.useDefaultPrettyPrinter();
            jg.setCodec(new ObjectMapper());  // makes the generator annotation aware
            jg.writeObject(photos);
            return;
        }

        List pathToRoot = category.getPathToRoot();

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

    private boolean showPhoto(HttpServletRequest request, HttpServletResponse response, boolean includePrivate, String[] parts, Category category) throws ServletException, IOException
    {
        long photoId;
        try
        {
            photoId = Long.parseLong(parts[3]);
        }
        catch (NumberFormatException e)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        Photo photo = PhotoOperations.getPhotoOperations().getPhoto(photoId, includePrivate);
        if (photo == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return true;
        }

        List<PreviewImage> nextPreviews = new ArrayList<>();
        List<PreviewImage> previousPreviews = new ArrayList<>();

        List<Photo> allPhotos = new ArrayList<Photo>(category.getPhotos(includePrivate));

        int categoryIndex = 1;
        for (int i = 0; i < allPhotos.size(); i++)
        {
            if (allPhotos.get(i).getPhotoId().longValue() == photo.getPhotoId().longValue())
            {
                categoryIndex = i + 1;
                break;
            }
        }

        int totalPhotos = allPhotos.size();
        for (int i = categoryIndex; i <= categoryIndex + 3 && i < totalPhotos; i++)
        {
            Photo nextPhoto = allPhotos.get(i);
            nextPreviews.add(new PreviewImage(i + 1, nextPhoto));
        }
        for (int i = categoryIndex - 2; i >= categoryIndex - 5 && i >= 0; i--)
        {
            Photo nextPhoto = allPhotos.get(i);
            previousPreviews.add(0, new PreviewImage(i + 1, nextPhoto));
        }
        request.setAttribute("Category", category);

        request.setAttribute("NextPreviews", nextPreviews);
        request.setAttribute("PreviousPreviews", previousPreviews);

        request.setAttribute("photo", photo);
        request.setAttribute("Comments", photo.getComments());
        request.setAttribute("Categories", photo.getCategories(includePrivate));
        Photographer photographer = photo.getPhotographer();
        if (photographer == null)
        {
            photographer = Photographer.UNKNOWN_PHOTOGRAPHER;
        }

        request.setAttribute("Photographer", photographer);

        StringBuffer sb = HttpUtils.getRequestURL(request);
        sb.append("?");
        sb.append(request.getQueryString());
        request.setAttribute("ReturnURL", sb.toString());
        getServletContext().getRequestDispatcher("/photoDetailNew.jsp").forward(request, response);
        return false;
    }

    public String getServletInfo()
    {
        return "Category Browser for Photo Gallery";
	}	
}
