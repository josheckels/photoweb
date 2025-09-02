/*
 * PhotoDetail.java
 *
 * Created on March 13, 2002, 9:37 PM
 */

package com.stampysoft.photoGallery.servlet;

import com.stampysoft.photoGallery.*;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.servlet.AbstractServlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

public class PhotoDetail extends AbstractServlet
{
    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {
        boolean includePrivate = includePrivate(request);
        Long photoId = Long.parseLong(request.getParameter("PhotoId"));

        Photo photo = AdminFrame.getFrame().getPhotoOperations().getPhoto(photoId, includePrivate);
        if (photo == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String submitCommentError = "";

        List<PreviewImage> nextPreviews = new ArrayList<>();
        List<PreviewImage> previousPreviews = new ArrayList<>();

        String stringCategoryId = request.getParameter("ReferringCategoryId");
        if (stringCategoryId != null)
        {
            try
            {
                Long categoryId = Long.parseLong(stringCategoryId);
                Category category = AdminFrame.getFrame().getPhotoOperations().getCategoryByCategoryId(categoryId, includePrivate);
                if (category != null)
                {
                    List<Photo> allPhotos = new ArrayList<>(category.getPhotos(includePrivate));

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
                }
            }
            catch (NumberFormatException e)
            {
                // Invalid active category, just don't try to show to the user
            }
        }

        request.setAttribute("NextPreviews", nextPreviews);
        request.setAttribute("PreviousPreviews", previousPreviews);

        request.setAttribute("UserCommentError", submitCommentError);
        request.setAttribute("photo", photo);
        request.setAttribute("Categories", photo.getCategories(includePrivate));
        Photographer photographer = photo.getPhotographer();
        if (photographer == null)
        {
            photographer = Photographer.UNKNOWN_PHOTOGRAPHER;
        }

        request.setAttribute("Photographer", photographer);

        StringBuffer sb = request.getRequestURL();
        sb.append("?");
        sb.append(request.getQueryString());
        request.setAttribute("ReturnURL", sb.toString());
        getServletContext().getRequestDispatcher("/photoDetail.jsp").forward(request, response);
    }

    /**
     * Returns a short description of the servlet.
     */
    public String getServletInfo()
    {
        return "Photo Detail";
	}
	
}
