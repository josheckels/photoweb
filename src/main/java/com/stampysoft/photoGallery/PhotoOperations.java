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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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


    /**
     * Works out what this visitor is allowed to see, once, for the whole request.
     * <p>
     * Two id sets do the work. The first is every photo tagged with somebody who has opted out of appearing
     * publicly; the second is every photo reachable from a category the visitor holds a share token for, which
     * is what puts the first set's photos back for the people who were at the event. Computing them here rather
     * than adding clauses to a dozen queries is what keeps the rule in one place - see {@link Visibility}.
     *
     * @param unlockedCategoryIds categories this visitor has unlocked with a share token; may be empty
     */
    @Transactional(readOnly = true)
    public Visibility createVisibility(java.util.Collection<Integer> unlockedCategoryIds)
    {
        return Visibility.anonymous(getOptOutHiddenPhotoIds(), getPhotoIdsInSubtrees(unlockedCategoryIds));
    }

    /** Every photo tagged with a person who has opted out of appearing publicly. */
    private Set<Integer> getOptOutHiddenPhotoIds()
    {
        Query query = getEntityManager().createQuery("select distinct p.photoId from Photo p join p._categories c where c._optOut = true");
        return new HashSet<>((List<Integer>) query.getResultList());
    }

    /** Every photo marked private. */
    private Set<Integer> getPrivatePhotoIds()
    {
        Query query = getEntityManager().createQuery("select p.photoId from Photo p where p._private = true");
        return new HashSet<>((List<Integer>) query.getResultList());
    }

    /**
     * Every photo whose bytes are not simply public: the {@code private} ones, and the ones tagged with somebody
     * who has opted out. A photo outside this set is visible to every visitor, so knowing the set means an image
     * request for one can be answered without asking the database anything at all.
     * <p>
     * That matters because {@link com.stampysoft.photoGallery.controller.ImageController} runs once per
     * <em>image</em> rather than once per page - around sixty times for a grid - and building a
     * {@link Visibility} costs an opt-out scan every time. Nothing about the rule changes here: a photo in the
     * set still goes through {@link #getPhoto} and {@link Visibility#canSee(Photo)} exactly as before. This is
     * only a way of not asking a question whose answer is already known. See PROXY.md.
     */
    @Transactional(readOnly = true)
    public Set<Integer> getRestrictedPhotoIds()
    {
        Restricted cached = _restrictedPhotoIds;
        if (cached != null && System.currentTimeMillis() - cached.builtAt() < RESTRICTED_CACHE_TTL_MILLIS)
        {
            return cached.ids();
        }

        Set<Integer> ids = new HashSet<>(getOptOutHiddenPhotoIds());
        ids.addAll(getPrivatePhotoIds());
        Restricted rebuilt = new Restricted(Set.copyOf(ids), System.currentTimeMillis());
        _restrictedPhotoIds = rebuilt;
        return rebuilt.ids();
    }

    /**
     * Forget the cached set, so the next image request rebuilds it. For the admin UI to call after changing a
     * photo's private flag or a person's opt-out, which is the only thing that moves the set.
     */
    public void flushRestrictedPhotoIds()
    {
        _restrictedPhotoIds = null;
    }

    private record Restricted(Set<Integer> ids, long builtAt) {}

    /**
     * Rebuilt on a timer rather than invalidated precisely on every opt-out change and photo/category-link
     * write, which is a deliberate downgrade from what PRIVACY.md sketches. It is safe because the inputs barely
     * move - a photo's privacy and a person's opt-out get set once and then left alone - and because listings
     * are unaffected either way: only a direct image URL can lag, and only until the next rebuild.
     */
    private static final long RESTRICTED_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private volatile Restricted _restrictedPhotoIds;

    /**
     * Every photo reachable from any of the given categories: a public category expands to its whole subtree, a
     * private one to itself alone.
     * <p>
     * That asymmetry is the whole safety story for unhide links. The children map is built from the public
     * categories only, so expansion can never descend into a private subtree; a private category named directly
     * is added as a single id and contributes only the photos linked to it. A token on somebody's category
     * therefore unlocks that person's photos and nothing else - never everybody under a shared parent.
     * <p>
     * Which private categories get this far is decided by {@link #getCategoryIdsByShareTokens}, not here.
     * <p>
     * Reaching a photo is not the same as it being visible: {@link Visibility#canSee(Photo)} still refuses a
     * {@code private} photo, and no amount of unlocking makes a category visible.
     */
    private Set<Integer> getPhotoIdsInSubtrees(java.util.Collection<Integer> categoryIds)
    {
        if (categoryIds == null || categoryIds.isEmpty())
        {
            return Set.of();
        }

        List<Category> publicCategories = (List<Category>) getEntityManager()
                .createQuery("from Category where _private = false").getResultList();
        java.util.Map<Integer, List<Integer>> childrenByParent = new java.util.HashMap<>();
        Set<Integer> publicIds = new HashSet<>();
        for (Category category : publicCategories)
        {
            childrenByParent.computeIfAbsent(category.getParentCategoryId(), k -> new ArrayList<>()).add(category.getCategoryId());
            publicIds.add(category.getCategoryId());
        }

        Set<Integer> subtreeIds = new HashSet<>();
        for (Integer categoryId : categoryIds)
        {
            if (categoryId == null)
            {
                continue;
            }
            if (publicIds.contains(categoryId))
            {
                collectSubtreeIds(categoryId, childrenByParent, subtreeIds);
            }
            else
            {
                // Private: itself and no descendants. Not passed to collectSubtreeIds at all, so there is no
                // path by which a private category's children can be walked.
                subtreeIds.add(categoryId);
            }
        }
        if (subtreeIds.isEmpty())
        {
            return Set.of();
        }

        Query query = getEntityManager().createQuery("select distinct p.photoId from Photo p join p._categories c where c.categoryId in :categoryIds");
        query.setParameter("categoryIds", subtreeIds);
        return new HashSet<>((List<Integer>) query.getResultList());
    }

    @Transactional(readOnly = true)
    public boolean hasOwnerToken()
    {
        String token = getSetting(AppSetting.OWNER_TOKEN);
        return token != null && !token.isBlank();
    }

    /** Mints a new owner token, invalidating the previous one, and returns it. */
    public String regenerateOwnerToken()
    {
        String token = ShareTokens.generate();
        setSetting(AppSetting.OWNER_TOKEN, token);
        return token;
    }

    /**
     * The current owner token, or null if there isn't one - which is how owner unlock stays switched off until
     * you deliberately turn it on, there being no default value and no fallback. Callers compare against it with
     * {@link ShareTokens#equalsConstantTime}; it is fetched rather than compared here so that checking a handful
     * of presented tokens is one query rather than one apiece.
     */
    @Transactional(readOnly = true)
    public String getOwnerToken()
    {
        String token = getSetting(AppSetting.OWNER_TOKEN);
        return token == null || token.isBlank() ? null : token.trim();
    }

    @Transactional(readOnly = true)
    public String getSetting(String name)
    {
        AppSetting setting = getEntityManager().find(AppSetting.class, name);
        return setting == null ? null : setting.getValue();
    }

    public void setSetting(String name, String value)
    {
        AppSetting setting = getEntityManager().find(AppSetting.class, name);
        if (setting == null)
        {
            getEntityManager().persist(new AppSetting(name, value));
        }
        else
        {
            setting.setValue(value);
        }
    }

    /**
     * The categories a batch of share links point at, keyed by the token that reaches each one. A token matching
     * nothing is simply absent from the map. One query rather than one apiece, because {@code /unlock} takes
     * several tokens at a time.
     * <p>
     * Private categories are included here, unlike {@link #getCategoryIdsByShareTokens}: the caller is deciding
     * whether to accept a link rather than deciding what it may see, and a token on a private category has to be
     * recognised in order to be turned down.
     */
    @Transactional(readOnly = true)
    public java.util.Map<String, Category> getCategoriesByShareTokens(java.util.Collection<String> shareTokens)
    {
        if (shareTokens == null || shareTokens.isEmpty())
        {
            return java.util.Map.of();
        }
        Query query = getEntityManager().createQuery("from Category where _shareToken in :shareTokens");
        query.setParameter("shareTokens", shareTokens);
        java.util.Map<String, Category> result = new java.util.HashMap<>();
        for (Category category : (List<Category>) query.getResultList())
        {
            result.put(category.getShareToken(), category);
        }
        return result;
    }

    /**
     * The categories unlocked by a batch of share tokens, ignoring any that match nothing. This is what turns the
     * tokens in a visitor's cookie back into an answer on each request, so a token that has since been
     * regenerated or removed stops working the moment it does.
     * <p>
     * A private category counts only when it has opted out, which is the unhide link: a person's category is
     * private, and their token is how the family gets to see somebody who is hidden. Gating on
     * {@code public_opt_out} rather than merely on being private keeps two properties. A token on an event that
     * is later marked private still stops working, as it always did - the category isn't hiding anybody, so
     * there is nothing for its token to put back. And a token on the People root does nothing, because the root
     * isn't a person and has no opt-out.
     * <p>
     * What an accepted private category unlocks is bounded in {@link #getPhotoIdsInSubtrees}, which expands it
     * to itself alone and never descends into it. The category stays invisible either way, since
     * {@link Visibility#canSee(Category)} reads neither id set.
     */
    @Transactional(readOnly = true)
    public Set<Integer> getCategoryIdsByShareTokens(java.util.Collection<String> shareTokens)
    {
        if (shareTokens == null || shareTokens.isEmpty())
        {
            return Set.of();
        }
        Query query = getEntityManager().createQuery(
                "select c.categoryId from Category c where c._shareToken in :shareTokens " +
                        "and (c._private = false or c._optOut = true)");
        query.setParameter("shareTokens", shareTokens);
        return new HashSet<>((List<Integer>) query.getResultList());
    }

    public List<Category> getCategoriesByParentId(Long parentId, Visibility visibility)
    {
        if (parentId == null)
        {
            return getRootCategories(visibility);
        }
        Query query = getEntityManager().createQuery("from Category where parentCategory = :parentId " + privateClause(visibility) + " order by description ");
        query.setParameter("parentId", parentId);
        return visible((List<Category>) query.getResultList(), visibility);
    }

    public Category getCategoryByCategoryId(Long categoryId, Visibility visibility)
    {
        Query query = getEntityManager().createQuery("from Category where categoryId " + (categoryId == null ? "IS NULL" : "= :categoryId ") + privateClause(visibility) + " order by description ");
        if (categoryId != null)
        {
            query.setParameter("categoryId", categoryId);
        }
        // getSingleResult() throws when there is no row, which turned "you may not see this" into a 500
        List<Category> results = (List<Category>) query.getResultList();
        List<Category> visible = visible(results, visibility);
        return visible.isEmpty() ? null : visible.get(0);
    }

    public List<Category> getRootCategories(Visibility visibility)
    {
        Query query = getEntityManager().createQuery("from Category where parentCategory is null " + privateClause(visibility) + "order by description ");
        return visible((List<Category>) query.getResultList(), visibility);
    }

    public List<Category> getAllCategoriesAndDefaultPhotos(Visibility visibility)
    {
        Query query = getEntityManager().createQuery("SELECT c from Category c LEFT JOIN FETCH c.defaultPhoto LEFT JOIN FETCH c.parentCategory " + (visibility.isOwner() ? "" : " WHERE c._private = false "));
        return visible((List<Category>) query.getResultList(), visibility);
    }

    public List<Category> getNewestCategories(int count, Visibility visibility)
    {
        Query query = getEntityManager().createQuery("from Category " + (visibility.isOwner() ? "" : " where _private = false ") + " order by createdOn desc ");
        List<Category> newestCategories = visible((List<Category>) query.getResultList(), visibility);

        List<Category> result = new ArrayList<>();
        long cutoffMillis = System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 14;
        for (Category c : newestCategories)
        {
            if (result.size() < count || c.getCreatedOn().getTime() > cutoffMillis)
            {
                result.add(c);
            }
        }
        return result;
    }

    public Photo getPhoto(Long photoId, Visibility visibility)
    {
        Query query = getEntityManager().createQuery("from Photo where photoId = :photoId");
        query.setParameter("photoId", photoId);
        List<Photo> results = (List<Photo>) query.getResultList();
        return results.isEmpty() ? null : visibleOrNull(results.get(0), visibility);
    }

    public Photo getPhotoByFilename(String filename, Visibility visibility)
    {
        Query query = getEntityManager().createQuery("from Photo where filename = :filename");
        query.setParameter("filename", filename);
        List<Photo> results = (List<Photo>) query.getResultList();
        return results.isEmpty() ? null : visibleOrNull(results.get(0), visibility);
    }

    /** The {@code _private} filter for a category query, as a JPQL fragment. The rest of the rule is {@link #visible}. */
    private String privateClause(Visibility visibility)
    {
        return visibility.isOwner() ? "" : " and _private = false ";
    }

    /**
     * Drops what this visitor may not see and stamps the rest with the visibility, so that each entity filters
     * its own photos, sub-categories and tags the same way when Jackson serializes it.
     */
    private List<Category> visible(List<Category> categories, Visibility visibility)
    {
        List<Category> result = new ArrayList<>();
        for (Category category : categories)
        {
            if (visibility.canSee(category))
            {
                category.setVisibility(visibility);
                result.add(category);
            }
        }
        return result;
    }

    private Photo visibleOrNull(Photo photo, Visibility visibility)
    {
        if (!visibility.canSee(photo))
        {
            return null;
        }
        photo.setVisibility(visibility);
        return photo;
    }

    public Photo savePhoto(Photo photo)
    {
        Photographer photographer = photo.getPhotographer();
        if (photographer != null && photographer.getPhotographerId() == null)
        {
            // Sentinel/transient photographer (e.g. VARIOUS_PHOTOGRAPHER) — don't persist it
            photo.setPhotographer(null);
        }
        else if (photographer != null)
        {
            // Re-attach detached photographer into the current session before merging the photo
            photo.setPhotographer(getEntityManager().merge(photographer));
        }
        photo = getEntityManager().merge(photo);
        // Initialize lazy collections while still inside the transaction
        photo.getCategories(true).size();
        return photo;
    }

    public Category saveCategory(Category category)
    {
        category = getEntityManager().merge(category);
        return category;
    }

    /**
     * Deletes a category, its descendant categories, and its photos' membership of all of them - the parent link and
     * the join table both cascade in the database. The photos themselves are left alone.
     * <p>
     * Note the merge: everything the Swing UI holds is detached, because each call here is its own transaction, and
     * JPA refuses to remove a detached instance outright.
     */
    public void deleteCategory(Category category)
    {
        getEntityManager().remove(merge(category));
    }

    /**
     * Deletes the given photos and everything the database hangs off them, in a single transaction so that a
     * failure part way through leaves the whole selection intact.
     * <p>
     * The join table rows, comments and detected faces all go with the photo through {@code ON DELETE CASCADE}.
     * The one reference that doesn't is {@code category.default_photo_id}, whose foreign key has no cascade rule
     * at all, so a category still pointing at one of these photos would block the delete outright - those
     * categories are left without a default instead.
     */
    public void deletePhotos(java.util.Collection<Photo> photos)
    {
        if (photos.isEmpty())
        {
            return;
        }

        List<Photo> managedPhotos = new ArrayList<>();
        for (Photo photo : photos)
        {
            managedPhotos.add(merge(photo));
        }

        Query query = getEntityManager().createQuery("update Category c set c.defaultPhoto = null where c.defaultPhoto in :photos");
        query.setParameter("photos", managedPhotos);
        query.executeUpdate();

        for (Photo photo : managedPhotos)
        {
            getEntityManager().remove(photo);
        }
    }

    /**
     * The categories that use one of the given photos as their default, which deleting those photos would leave
     * without one. Asked up front so the confirmation dialog can say so rather than letting the user find out later.
     */
    @Transactional(readOnly = true)
    public List<Category> getCategoriesWithDefaultPhoto(java.util.Collection<Photo> photos)
    {
        if (photos.isEmpty())
        {
            return new ArrayList<>();
        }
        Query query = getEntityManager().createQuery("select c from Category c where c.defaultPhoto in :photos order by c.description");
        query.setParameter("photos", photos);
        return (List<Category>) query.getResultList();
    }

    public List<Photo> getAllPhotos()
    {
        Query query = getEntityManager().createQuery("from Photo order by filename ");
        return (List<Photo>) query.getResultList();
    }

    /**
     * Returns the ids of all the photos that aren't in any category. Answering this in bulk lets the admin UI
     * flag and filter uncategorized photos without running a query per photo.
     */
    @Transactional(readOnly = true)
    public Set<Integer> getUncategorizedPhotoIds()
    {
        Query query = getEntityManager().createQuery("select p.photoId from Photo p where p._categories is empty");
        return new HashSet<>((List<Integer>) query.getResultList());
    }

    /**
     * Returns the photos that appear in <em>every</em> one of the supplied categories, where a photo counts
     * toward a category if it is linked to that category directly or to any of that category's descendant
     * categories (recursively). In other words: the intersection, across all selected categories, of each
     * selected category's full subtree of photos.
     */
    @Transactional(readOnly = true)
    public List<Photo> getPhotosInAllCategories(List<Long> categoryIds, Visibility visibility)
    {
        if (categoryIds == null || categoryIds.isEmpty())
        {
            return new ArrayList<>();
        }

        // Build a parent -> children map from all visible categories so each selected category can be
        // expanded into its full subtree (itself plus all descendants). For anyone but the owner the
        // private categories are excluded, so we never descend into private subtrees.
        List<Category> allCategories = getAllCategories(visibility, false);
        java.util.Map<Integer, List<Integer>> childrenByParent = new java.util.HashMap<>();
        java.util.Set<Integer> visibleIds = new java.util.HashSet<>();
        for (Category category : allCategories)
        {
            childrenByParent.computeIfAbsent(category.getParentCategoryId(), k -> new ArrayList<>()).add(category.getCategoryId());
            visibleIds.add(category.getCategoryId());
        }

        java.util.Set<Integer> intersection = null;
        for (Long selectedId : categoryIds)
        {
            // The selected category has to be visible in its own right, not merely reachable. Building
            // childrenByParent from the visible categories stops us descending into a private subtree, but it
            // does nothing about a private category named directly: it simply has no children in the map and
            // gets expanded to itself. Without this check ?categories=<a person's category id> hands an
            // anonymous caller that person's non-private photos - the People-tag membership that marking the
            // category private is there to hide. This is an intersection, so an invisible member empties it.
            if (selectedId == null || !visibleIds.contains(selectedId.intValue()))
            {
                return new ArrayList<>();
            }
            java.util.Set<Integer> subtreeIds = new java.util.HashSet<>();
            collectSubtreeIds(selectedId.intValue(), childrenByParent, subtreeIds);
            java.util.Set<Integer> photoIds = getPhotoIdsInCategories(subtreeIds, visibility);
            if (intersection == null)
            {
                intersection = photoIds;
            }
            else
            {
                intersection.retainAll(photoIds);
            }
            if (intersection.isEmpty())
            {
                return new ArrayList<>();
            }
        }

        Query query = getEntityManager().createQuery("from Photo where photoId in :ids order by filename ");
        query.setParameter("ids", intersection);
        List<Photo> result = new ArrayList<>();
        for (Photo photo : (List<Photo>) query.getResultList())
        {
            if (visibility.canSee(photo))
            {
                photo.setVisibility(visibility);
                result.add(photo);
            }
        }
        return result;
    }

    /**
     * Everything below the given categories in the tree, recursively, and not the given categories themselves.
     * <p>
     * Deleting a category takes its descendants with it - {@code category_parent_category_id} cascades - so the
     * confirmation dialog needs to be able to say how much else is about to go.
     */
    @Transactional(readOnly = true)
    public List<Category> getDescendantCategories(java.util.Collection<Category> categories)
    {
        if (categories.isEmpty())
        {
            return new ArrayList<>();
        }

        java.util.Map<Integer, List<Integer>> childrenByParent = new java.util.HashMap<>();
        java.util.Map<Integer, Category> categoriesById = new java.util.HashMap<>();
        for (Category category : getAllCategories(Visibility.OWNER, false))
        {
            childrenByParent.computeIfAbsent(category.getParentCategoryId(), k -> new ArrayList<>()).add(category.getCategoryId());
            categoriesById.put(category.getCategoryId(), category);
        }

        java.util.Set<Integer> subtreeIds = new java.util.HashSet<>();
        for (Category category : categories)
        {
            collectSubtreeIds(category.getCategoryId(), childrenByParent, subtreeIds);
        }
        // What was passed in is the root of each subtree, not a descendant of it
        for (Category category : categories)
        {
            subtreeIds.remove(category.getCategoryId());
        }

        List<Category> result = new ArrayList<>();
        for (Integer categoryId : subtreeIds)
        {
            Category category = categoriesById.get(categoryId);
            if (category != null)
            {
                result.add(category);
            }
        }
        result.sort(java.util.Comparator.comparing(c -> String.valueOf(c.getDescription()), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** How many distinct photos are linked to any of these categories directly. Descendants are not walked. */
    @Transactional(readOnly = true)
    public int countPhotosInCategories(java.util.Collection<Category> categories)
    {
        if (categories.isEmpty())
        {
            return 0;
        }
        Query query = getEntityManager().createQuery("select count(distinct p.photoId) from Photo p join p._categories c where c in :categories");
        query.setParameter("categories", categories);
        return ((Number) query.getSingleResult()).intValue();
    }

    private void collectSubtreeIds(Integer categoryId, java.util.Map<Integer, List<Integer>> childrenByParent, java.util.Set<Integer> accumulator)
    {
        if (categoryId == null || !accumulator.add(categoryId))
        {
            return;
        }
        List<Integer> children = childrenByParent.get(categoryId);
        if (children != null)
        {
            for (Integer childId : children)
            {
                collectSubtreeIds(childId, childrenByParent, accumulator);
            }
        }
    }

    private java.util.Set<Integer> getPhotoIdsInCategories(java.util.Set<Integer> categoryIds, Visibility visibility)
    {
        if (categoryIds.isEmpty())
        {
            return new java.util.HashSet<>();
        }
        Query query = getEntityManager().createQuery("select distinct p.photoId from Photo p join p._categories c where c.categoryId in :categoryIds " + (visibility.isOwner() ? "" : " and p._private = false"));
        query.setParameter("categoryIds", categoryIds);
        return new java.util.HashSet<>((List<Integer>) query.getResultList());
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

    /** Merged first for the same reason as {@link #deleteCategory}: what the UI hands us is always detached. */
    public void deletePhotographer(Photographer photographer)
    {
        getEntityManager().remove(merge(photographer));
    }

    public Photographer getPhotographerById(Long photographerId)
    {
        Query query = getEntityManager().createQuery("from Photographer where photographerId = :photographerId");
        query.setParameter("photographerId", photographerId);
        return (Photographer) query.getSingleResult();
    }

    public Photographer getPhotographerByName(String name)
    {
        Query query = getEntityManager().createQuery("from Photographer where name = :name");
        query.setParameter("name", name);
        List<Photographer> results = (List<Photographer>) query.getResultList();
        return results.isEmpty() ? null : results.get(0);
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

    public List<Category> getAllCategories(Visibility visibility, boolean sortByDate)
    {
        Query query = getEntityManager().createQuery("from Category " + (visibility.isOwner() ? "" : " where _private = false ") + "order by " + (sortByDate ? " categoryId" : " description"));
        return visible((List<Category>) query.getResultList(), visibility);
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

    /**
     * Applies category additions and removals to a photo and persists them in a single transaction.
     * <p>
     * This intentionally operates on one freshly-merged managed instance and flushes the join-table
     * changes together with any other pending edits (e.g. photographer). It must NOT be followed by a
     * separate {@code merge(photo)} of the original detached instance: that second merge would
     * re-synchronize the {@code @ManyToMany} join table against the detached instance's own (possibly
     * stale) categories collection and silently revert these changes.
     */
    @Transactional
    public Photo updatePhotoCategories(Photo photo, java.util.Collection<Category> toAdd, java.util.Collection<Category> toRemove) {
        Photo managedPhoto = merge(photo);
        java.util.Set<Category> categories = managedPhoto.getCategories(true);
        for (Category category : toRemove) {
            categories.remove(merge(category));
        }
        for (Category category : toAdd) {
            categories.add(merge(category));
        }
        // Initialize the collection while still inside the transaction so callers can read it after detach.
        categories.size();
        return managedPhoto;
    }
}