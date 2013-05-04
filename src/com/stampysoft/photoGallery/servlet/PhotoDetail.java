/*
 * PhotoDetail.java
 *
 * Created on March 13, 2002, 9:37 PM
 */

package com.stampysoft.photoGallery.servlet;

import com.stampysoft.photoGallery.*;
import com.stampysoft.photoGallery.common.Resolution;
import com.stampysoft.servlet.AbstractServlet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class PhotoDetail extends AbstractServlet
{
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,4}", Pattern.CASE_INSENSITIVE);

    public static class PreviewImage
    {
        private final int _index;
        private final Photo _photo;

        public PreviewImage(int index, Photo photo)
        {
            _index = index;
            _photo = photo;
        }

        public int getIndex()
        {
            return _index;
        }

        public Resolution getPreview()
        {
            return _photo.getTinyPreviewDimensions();
        }

        public String getCaption()
        {
            return _photo.getCaption();
        }

        public Long getPhotoId()
        {
            return _photo.getPhotoId();
        }
    }

    /**
     * Processes requests for both HTTP <code>GET</code> and <code>POST</code> methods.
     */
    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, java.io.IOException
    {
        boolean includePrivate = includePrivate(request);
        Long photoId = new Long(request.getParameter("PhotoId"));

        Photo photo = PhotoOperations.getPhotoOperations().getPhoto(photoId, includePrivate);
        if (photo == null)
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String requestedAction = request.getParameter("requestedAction");
        String submitCommentError = "";
        Comment userComment = new Comment();
        if ("submitComment".equals(requestedAction))
        {
            userComment.setName(request.getParameter("name"));
            userComment.setEmail(request.getParameter("email"));
            userComment.setComment(request.getParameter("comment"));

            String kaptchaExpected = (String)request.getSession()
                .getAttribute(com.google.code.kaptcha.Constants.KAPTCHA_SESSION_KEY);
            String kaptchaReceived = request.getParameter("kaptcha");

            if (kaptchaReceived == null || !kaptchaReceived.equalsIgnoreCase(kaptchaExpected))
            {
                submitCommentError += "Invalid validation code.";
            }

            if (userComment.getName() == null || userComment.getName().trim().length() == 0)
            {
                submitCommentError += " You must include your name.";
            }
            if (userComment.getEmail() == null || userComment.getEmail().trim().length() == 0)
            {
                submitCommentError += " You must include your email address.";
            }
            else
            {
                Matcher matcher = EMAIL_PATTERN.matcher(userComment.getEmail().trim());
                if (!matcher.matches())
                {
                    submitCommentError += " You must include a valid email address.";
                }
            }
            if (userComment.getComment() == null || userComment.getComment().trim().length() == 0)
            {
                submitCommentError += " You must include a comment.";
            }

            if (submitCommentError.length() == 0)
            {
                userComment.setCreatedOn(new Date());
                userComment.setPhoto(photo);
                userComment.setRemoteIP(request.getRemoteAddr());
                userComment.setRemoteHost(request.getRemoteHost());
                PhotoOperations.getPhotoOperations().saveComment(userComment);
                
                response.sendRedirect(request.getRequestURI() + "?" + request.getQueryString());
                return;
            }
        }

        List<PreviewImage> nextPreviews = new ArrayList<PreviewImage>();
        List<PreviewImage> previousPreviews = new ArrayList<PreviewImage>();

        String stringCategoryId = request.getParameter("ReferringCategoryId");
        if (stringCategoryId != null)
        {
            try
            {
                Long categoryId = new Long(stringCategoryId);
                Category category = PhotoOperations.getPhotoOperations().getCategoryByCategoryId(categoryId, includePrivate);
                if (category != null)
                {
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
                }
            }
            catch (NumberFormatException e)
            {
                // Invalid active category, just don't try to show to the user
            }
        }

        request.setAttribute("NextPreviews", nextPreviews);
        request.setAttribute("PreviousPreviews", previousPreviews);

        request.setAttribute("UserComment", userComment);
        request.setAttribute("UserCommentError", submitCommentError);
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
