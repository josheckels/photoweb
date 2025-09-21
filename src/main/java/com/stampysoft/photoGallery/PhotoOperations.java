/*
 * PhotoOperations.java
 *
 * Created on April 1, 2002, 7:58 PM
 */

package com.stampysoft.photoGallery;

import com.stampysoft.photoGallery.admin.AdminFrame;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

@Repository
@Transactional
@Service
public class PhotoOperations
{
    public PhotoOperations() {
    }

    public static PhotoOperations getPhotoOperations() {
        return AdminFrame.getFrame().getPhotoOperations();
    }

//    @Bean
//    public OpenSessionInViewInterceptor openSessionInViewInterceptor(EntityManagerFactory emf) {
//        OpenSessionInViewInterceptor interceptor = new OpenSessionInViewInterceptor();
//        interceptor.setEntityManagerFactory(emf);
//        return interceptor;
//    }


    public List<Category> getCategoriesByParentId(Long parentId, boolean includePrivate)
    {
        if (parentId == null)
        {
            return getRootCategories(includePrivate);
        }
        Query query = getEntityManager().createQuery("from Category where parentCategory = :parentId " + (includePrivate ? "" : "and _private = :includePrivate ") + " order by description ");
        query.setParameter("parentId", parentId);
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        return (List<Category>) query.getResultList();
    }

    public Category getCategoryByCategoryId(Long categoryId, boolean includePrivate)
    {
        Query query = getEntityManager().createQuery("from Category where categoryId " + (categoryId == null ? "IS NULL" : "= :categoryId ") + (includePrivate ? "" : " and _private = :includePrivate") + " order by description ");
        if (categoryId != null)
        {
            query.setParameter("categoryId", categoryId);
        }
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        Category result = (Category) query.getSingleResult();
        if (result != null)
        {
            result.setIncludePrivate(includePrivate);
        }
        return result;
    }

    public List<Category> getRootCategories(boolean includePrivate)
    {
        Query query = getEntityManager().createQuery("from Category where parentCategory is null " + (includePrivate ? "" : " and _private = :includePrivate ") + "order by description ");
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        return (List<Category>) query.getResultList();
    }

    public List<Category> getAllCategoriesAndDefaultPhotos(boolean includePrivate)
    {
        Query query = getEntityManager().createQuery("SELECT c from Category c LEFT JOIN FETCH c.defaultPhoto LEFT JOIN FETCH c.parentCategory " + (includePrivate ? "" : " WHERE c._private = :includePrivate "));
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        return (List<Category>) query.getResultList();
    }

    public List<Category> getNewestCategories(int count, boolean includePrivate)
    {
        Query query = getEntityManager().createQuery("from Category " + (includePrivate ? "" : " where _private = :includePrivate") + " order by createdOn desc ");
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        List<Category> newestCategories = (List<Category>) query.getResultList();

        List<Category> result = new ArrayList<>();
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
        Query query = getEntityManager().createQuery("from Photo where photoId = :photoId " + (includePrivate ? "" : " and _private = :includePrivate"));
        query.setParameter("photoId", photoId);
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        return (Photo) query.getSingleResult();
    }

    public Photo getPhotoByFilename(String filename, boolean includePrivate)
    {
        Query query = getEntityManager().createQuery("from Photo where filename = :filename " + (includePrivate ? "" : " and _private = :includePrivate"));
        query.setParameter("filename", filename);
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        return (Photo) query.getSingleResult();
    }

    public Photo savePhoto(Photo photo)
    {
        photo = getEntityManager().merge(photo);
        return photo;
    }

    public Category saveCategory(Category category)
    {
        category = getEntityManager().merge(category);
        return category;
    }

    public void deleteCategory(Category category)
    {
        getEntityManager().remove(category);
    }

    public void deletePhoto(Photo photo)
    {
        getEntityManager().remove(photo);
    }

    public List<Photo> getAllPhotos()
    {
        Query query = getEntityManager().createQuery("from Photo order by filename ");
        return (List<Photo>) query.getResultList();
    }

    public List<Photographer> getAllPhotographers()
    {
        Query query = getEntityManager().createQuery("from Photographer order by name ");
        return (List<Photographer>) query.getResultList();
    }

    public void savePhotographer(Photographer photographer)
    {
        getEntityManager().merge(photographer);
    }

    public void deletePhotographer(Photographer photographer)
    {
        getEntityManager().remove(photographer);
    }

    public Photographer getPhotographerById(Long photographerId)
    {
        Query query = getEntityManager().createQuery("from Photographer where photographerId = :photographerId");
        query.setParameter("photographerId", photographerId);
        return (Photographer) query.getSingleResult();
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

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public <T> T merge(T object) {
        if (!getEntityManager().contains(object)) {
            return getEntityManager().merge(object);
        }
        return object;
    }

    public void beginTransaction() {
        // No-op: transactions are managed by Spring's @Transactional
    }

    public void commit() {
        // No-op: transactions are managed by Spring's @Transactional
    }

    private EntityManager getEntityManager() {
        return entityManager;
    }

    public List<Category> getAllCategories(boolean includePrivate, boolean sortByDate)
    {
        Query query = getEntityManager().createQuery("from Category " + (includePrivate ? "" : " where _private = :includePrivate ") + "order by " + (sortByDate ? " categoryId" : " description"));
        if (!includePrivate)
        {
            query.setParameter("includePrivate", includePrivate);
        }
        return (List<Category>) query.getResultList();
    }

    @Transactional(readOnly = true)
    public List<Category> getInitializedChildCategories(Category category) {
        Category managed = merge(category);
        // Ensure the collection is initialized inside the transactional context
        managed.getChildCategories().size();
        return new ArrayList<>(managed.getChildCategories());
    }

    @Transactional(readOnly = true)
    public boolean photoHasAnyCategory(Photo photo) {
        Photo managed = merge(photo);
        // Initialize categories and check emptiness inside transaction
        return !managed.getCategories(true).isEmpty();
    }

    @Transactional(readOnly = true)
    public Integer getPhotographerIdOfPhoto(Photo photo) {
        Photo managed = merge(photo);
        Photographer p = managed.getPhotographer();
        return p == null ? null : p.getPhotographerId();
    }

    @Transactional(readOnly = true)
    public java.util.Set<Category> getInitializedCategories(Photo photo, boolean includePrivate) {
        Photo managed = merge(photo);
        // Initialize categories inside transaction
        managed.getCategories(includePrivate).size();
        return new java.util.LinkedHashSet<>(managed.getCategories(includePrivate));
    }

    @Transactional
    public void addCategoryToPhoto(Photo photo, Category category) {
        Photo managedPhoto = merge(photo);
        Category managedCategory = merge(category);
        managedPhoto.getCategories(true).add(managedCategory);
    }

    @Transactional
    public void removeCategoryFromPhoto(Photo photo, Category category) {
        Photo managedPhoto = merge(photo);
        Category managedCategory = merge(category);
        managedPhoto.getCategories(true).remove(managedCategory);
    }
}