package com.stampysoft.photoGallery.servlet;

import com.stampysoft.servlet.AbstractServlet;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.ResolutionUtil;
import com.stampysoft.photoGallery.common.Resolution;
import com.stampysoft.util.Configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.io.*;

/**
 * User: Josh
 * Date: Nov 3, 2008
 */
public class ImageMapper extends AbstractServlet
{
    protected void processRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        boolean movieRequest = request.getParameter("movie") != null;
        String uri = request.getRequestURI();
        int slashIndex = uri.lastIndexOf("/");
        if (slashIndex != -1)
        {
            String filename = uri.substring(slashIndex + 1);
            Photo photo = PhotoOperations.getPhotoOperations().getPhotoByFilename(filename, includePrivate(request));
            if (photo != null)
            {
                if (movieRequest)
                {
                    streamFile(photo, photo.getMovieFilename(), ResolutionUtil.PHOTOS_DIRECTORY_VALUE, response);
                }
                else
                {
                    streamFile(photo, filename, ResolutionUtil.PHOTOS_DIRECTORY_VALUE, response);
                }
                return;
            }
            else
            {
                int dashIndex = filename.indexOf("-");
                if (dashIndex != -1)
                {
                    try
                    {
                        Long photoId = Long.parseLong(filename.substring(0, dashIndex));
                        photo = PhotoOperations.getPhotoOperations().getPhoto(photoId, includePrivate(request));
                        if (photo != null)
                        {
                            for (Resolution res : photo.getResizedDimensions())
                            {
                                if (res.getFilename().equalsIgnoreCase(filename))
                                {
                                    streamFile(photo, filename, ResolutionUtil.RESIZED_PHOTOS_DIRECTORY_VALUE, response);
                                    return;
                                }
                            }
                        }
                    }
                    catch (NumberFormatException e) {}
                }
            }
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void streamFile(Photo photo, String filename, String directory, HttpServletResponse response) throws IOException
    {
        File f = new File(directory, filename);
        if (!f.exists())
        {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        else
        {
            if (filename.toLowerCase().endsWith(".avi"))
            {
                response.setContentType("video/avi");
            }
            else
            {
                response.setContentType("image/jpeg");
            }
            response.addDateHeader("Last-Modified", f.lastModified());
            int cacheLength = 60 * 60 * 24 * 14;
            response.addDateHeader("Expires", f.lastModified() + cacheLength);
            response.addHeader("Cache-Control", "public, max-age=" + cacheLength);
            response.setContentLength((int) f.length());
            InputStream in = null;
            try
            {
                in = new BufferedInputStream(new FileInputStream(f), 64000);
                OutputStream out = response.getOutputStream();
                byte[] b = new byte[64000];
                int i;
                while ((i = in.read(b)) != -1)
                {
                    out.write(b, 0, i);
                }
            }
            finally
            {
                if (in != null) { try { in.close(); } catch (IOException e) {} }
            }
        }
    }
}
