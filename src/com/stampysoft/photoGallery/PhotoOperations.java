/*
 * PhotoOperations.java
 *
 * Created on April 1, 2002, 7:58 PM
 */

package com.stampysoft.photoGallery;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PhotoOperations
{

    private static PhotoOperations g_photoOperations = new PhotoOperations();

    public static PhotoOperations getPhotoOperations()
    {
        return g_photoOperations;
    }

    private PhotoOperations()
    {
    }

    public List<Category> getCategoriesByParentId(Long parentId, boolean includePrivate)
    {
        if (parentId == null)
        {
            return getRootCategories(includePrivate);
        }
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Category where parentCategory = :parentId " + (includePrivate ? "" : "and private = :includePrivate ") + " order by description ");
        query.setLong("parentId", parentId);
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        return (List<Category>) query.list();
    }

    public Category getCategoryByCategoryId(Long categoryId, boolean includePrivate)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Category where categoryId " + (categoryId == null ? "IS NULL" : "= :categoryId ") + (includePrivate ? "" : " and private = :includePrivate") + " order by description ");
        if (categoryId != null)
        {
            query.setLong("categoryId", categoryId);
        }
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        return (Category) query.uniqueResult();
    }

    public List<Category> getRootCategories(boolean includePrivate)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Category where parentCategory is null " + (includePrivate ? "" : " and private = :includePrivate ") + "order by description ");
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        return (List<Category>) query.list();
    }

    public List<Comment> getNewestComments(int count)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Comment order by createdOn desc ");
        List<Comment> comments = (List<Comment>) query.list();
        List<Comment> result = new ArrayList<Comment>();
        for (int i = 0; i < count && i < comments.size(); i++)
        {
            result.add(comments.get(i));
        }
        return result;
    }

    public List<Category> getNewestCategories(int count, boolean includePrivate)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Category " + (includePrivate ? "" : " where private = :includePrivate") + " order by createdOn desc ");
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        List<Category> newestCategories = (List<Category>) query.list();

        List<Category> result = new ArrayList<Category>();
        long cutoffMillis = System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 14;
        for (Category c : newestCategories)
        {
            if (result.size() < count || c.getCreatedOn().getTime() > cutoffMillis)
            {
                result.add(c);
            }
        }
        return result;
    }

    public Photo getPhoto(Long photoId, boolean includePrivate)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Photo where photoId = :photoId " + (includePrivate ? "" : " and private = :includePrivate"));
        query.setLong("photoId", photoId);
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        return (Photo) query.uniqueResult();
    }

    public Photo getPhotoByFilename(String filename, boolean includePrivate)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Photo where filename = :filename " + (includePrivate ? "" : " and private = :includePrivate"));
        query.setString("filename", filename);
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        return (Photo) query.uniqueResult();
    }

    public Photo savePhoto(Photo photo)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        photo = (Photo)session.merge(photo);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
        return photo;
    }

    public Category saveCategory(Category category)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        category = (Category)session.merge(category);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
        return category;
    }

    public void deleteCategory(Category category)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        session.delete(category);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
    }

    public void deletePhoto(Photo photo)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        session.delete(photo);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
    }

    public List<Photo> getAllPhotos()
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Photo order by filename ");
        return (List<Photo>) query.list();
    }

    public Comment saveComment(Comment comment)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        session.saveOrUpdate(comment);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
        return comment;
    }

    public List<Photographer> getAllPhotographers()
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Photographer order by name ");
        return (List<Photographer>) query.list();
    }

    public void savePhotographer(Photographer photographer)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        session.saveOrUpdate(photographer);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
    }

    public void deletePhotographer(Photographer photographer)
    {
        Session session = PhotoOperations.getSessionFactory().getCurrentSession();
        session.delete(photographer);
        session.getTransaction().commit();
        PhotoOperations.getSessionFactory().getCurrentSession().beginTransaction();
    }

    public Photographer getPhotographerById(Long photographerId)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Photographer where photographerId = :photographerId");
        query.setLong("photographerId", photographerId);
        return (Photographer) query.uniqueResult();
    }

    public URI toURI(String uri)
    {
        try
        {
            return new URI(uri);
        }
        catch (URISyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static final SessionFactory sessionFactory;

    static
    {
        try
        {
            // Create the SessionFactory from hibernate.cfg.xml
            sessionFactory = new Configuration().configure().buildSessionFactory();
        }
        catch (Throwable ex)
        {
            // Make sure you log the exception, as it might be swallowed
            System.err.println("Initial SessionFactory creation failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public <T> T merge(T object)
    {
        if (!getSessionFactory().getCurrentSession().contains(object))
        {
            return (T)getSessionFactory().getCurrentSession().merge(object);
        }
        return object;
    }

    public void beginTransaction()
    {
        getSessionFactory().getCurrentSession().beginTransaction();
    }

    public void commit()
    {
        getSessionFactory().getCurrentSession().getTransaction().commit();
    }

    private static SessionFactory getSessionFactory()
    {
        return sessionFactory;
    }

    public List<Category> getAllCategories(boolean includePrivate, boolean sortByDate)
    {
        Session session = getSessionFactory().getCurrentSession();
        Query query = session.createQuery("from Category " + (includePrivate ? "" : " where private = :includePrivate ") + "order by " + (sortByDate ? " categoryId" : " description"));
        if (!includePrivate)
        {
            query.setBoolean("includePrivate", includePrivate);
        }
        return (List<Category>) query.list();
    }
}