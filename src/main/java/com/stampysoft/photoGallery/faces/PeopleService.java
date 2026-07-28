package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.admin.AdminFrame;
import com.stampysoft.util.Configuration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything about people as people, on top of the fact that a person <em>is</em> a category under the People root.
 * <p>
 * There is deliberately no Person entity: a shadow table would hold nothing but a category id and a display name,
 * and the display name is already {@link Category#getDescription()}. Collapsing them means creating, renaming,
 * hiding, deleting, drag-and-dropping and browsing a person all keep working through the existing category code.
 */
@Service
@Transactional
public class PeopleService
{
    private static final String PEOPLE_CATEGORY_ID_PROPERTY = "PeopleCategoryId";
    private static final String PEOPLE_EXCLUSIONS_PROPERTY = "PeopleExclusions";

    private final PhotoOperations _photoOperations;
    private final FaceOperations _faceOperations;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    public PeopleService(PhotoOperations photoOperations, FaceOperations faceOperations)
    {
        _photoOperations = photoOperations;
        _faceOperations = faceOperations;
    }

    public static PeopleService getPeopleService()
    {
        return AdminFrame.getFrame().getPeopleService();
    }

    /**
     * The People root's category id, identified by id rather than by matching {@code description = 'People'} so
     * that renaming the category doesn't silently break every person. Null when unconfigured.
     */
    public static Integer getPeopleRootId()
    {
        String value = Configuration.getConfiguration().getProperty(PEOPLE_CATEGORY_ID_PROPERTY, null);
        if (value == null || value.isBlank())
        {
            return null;
        }
        try
        {
            return Integer.valueOf(value.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Descriptions of People children that aren't individuals - "Group shots", "Unknown", a pet - lowercased. */
    private static Set<String> getExclusions()
    {
        Set<String> result = new HashSet<>();
        String value = Configuration.getConfiguration().getProperty(PEOPLE_EXCLUSIONS_PROPERTY, null);
        if (value != null)
        {
            for (String part : value.split(","))
            {
                if (!part.isBlank())
                {
                    result.add(part.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return result;
    }

    /**
     * A description of what's missing before people can be matched, or null if everything's in place. Returning a
     * message rather than throwing lets the People tab explain itself when the optional config isn't filled in.
     */
    @Transactional(readOnly = true)
    public String describeMissingConfiguration()
    {
        Integer rootId = getPeopleRootId();
        if (rootId == null)
        {
            return "Set " + PEOPLE_CATEGORY_ID_PROPERTY + " in src/config.properties to the category_id of the " +
                    "People category. Run src/main/resources/sql/faces-phase0-feasibility.sql to find it.";
        }
        if (entityManager.find(Category.class, rootId) == null)
        {
            return PEOPLE_CATEGORY_ID_PROPERTY + " is " + rootId + ", which is not an existing category_id.";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public Category getPeopleRoot()
    {
        Integer rootId = getPeopleRootId();
        return rootId == null ? null : entityManager.find(Category.class, rootId);
    }

    /**
     * Whether this category is a person: a descendant of the People root that isn't on the exclusion list.
     * <p>
     * Tests descendant rather than direct child. The People subtree is flat today so a parent check would do, but
     * this costs nothing and keeps People &gt; Family &gt; Josh working if it's ever nested.
     */
    @Transactional(readOnly = true)
    public boolean isPerson(Category category)
    {
        if (category == null || category.getCategoryId() == null)
        {
            return false;
        }
        return getPersonCategoryIds().contains(category.getCategoryId());
    }

    /** Every person category, sorted by name. Empty when the People root isn't configured. */
    @Transactional(readOnly = true)
    public List<Category> getAllPeople()
    {
        Integer rootId = getPeopleRootId();
        if (rootId == null)
        {
            return new ArrayList<>();
        }

        Set<Integer> personIds = getPersonCategoryIds();
        List<Category> result = new ArrayList<>();
        for (Category category : _photoOperations.getAllCategories(true, false))
        {
            if (personIds.contains(category.getCategoryId()))
            {
                result.add(category);
            }
        }
        result.sort(Comparator.comparing(Category::getDescription, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * The ids of every person category, computed from one pass over the category table rather than by walking
     * {@code getPathToRoot()} per category, which would issue a query per level per person.
     */
    @Transactional(readOnly = true)
    public Set<Integer> getPersonCategoryIds()
    {
        Integer rootId = getPeopleRootId();
        if (rootId == null)
        {
            return new LinkedHashSet<>();
        }

        List<Category> allCategories = _photoOperations.getAllCategories(true, false);
        Map<Integer, List<Category>> childrenByParent = new HashMap<>();
        for (Category category : allCategories)
        {
            childrenByParent.computeIfAbsent(category.getParentCategoryId(), k -> new ArrayList<>()).add(category);
        }

        Set<String> exclusions = getExclusions();
        Set<Integer> result = new LinkedHashSet<>();
        collectDescendants(rootId, childrenByParent, exclusions, result);
        return result;
    }

    private void collectDescendants(Integer parentId, Map<Integer, List<Category>> childrenByParent,
                                    Set<String> exclusions, Set<Integer> accumulator)
    {
        List<Category> children = childrenByParent.get(parentId);
        if (children == null)
        {
            return;
        }
        for (Category child : children)
        {
            String description = child.getDescription();
            if (description != null && exclusions.contains(description.trim().toLowerCase(Locale.ROOT)))
            {
                continue;
            }
            if (accumulator.add(child.getCategoryId()))
            {
                collectDescendants(child.getCategoryId(), childrenByParent, exclusions, accumulator);
            }
        }
    }

    /** Finds an existing person by name, case-insensitively, or null. Used to avoid creating duplicate people. */
    @Transactional(readOnly = true)
    public Category findPersonByName(String name)
    {
        if (name == null || name.isBlank())
        {
            return null;
        }
        String target = name.trim();
        for (Category person : getAllPeople())
        {
            if (target.equalsIgnoreCase(person.getDescription()))
            {
                return person;
            }
        }
        return null;
    }

    /** Creates a person category under the People root, the same way right-click People &gt; Insert does. */
    @Transactional
    public Category createPerson(String name)
    {
        Category peopleRoot = getPeopleRoot();
        if (peopleRoot == null)
        {
            throw new IllegalStateException(describeMissingConfiguration());
        }
        Category person = new Category();
        person.setDescription(name.trim());
        person.setParentCategory(peopleRoot);
        return _photoOperations.saveCategory(person);
    }

    /**
     * Confirms that this face is this person, and tags the photo accordingly.
     * <p>
     * Tags are only ever added, never removed: a photo tagged Alice with no Alice face detected usually means her
     * back was turned or detection missed her, not that the tag is wrong.
     */
    @Transactional
    public void assignFace(PhotoFace face, Category person)
    {
        assignFaces(List.of(face), person);
    }

    /** Confirms a batch of faces as one person, tagging each photo once. */
    @Transactional
    public int assignFaces(Collection<PhotoFace> faces, Category person)
    {
        if (faces.isEmpty())
        {
            return 0;
        }
        Category managedPerson = entityManager.find(Category.class, person.getCategoryId());
        if (managedPerson == null)
        {
            return 0;
        }

        Set<Integer> taggedPhotoIds = new LinkedHashSet<>();
        int count = 0;
        for (PhotoFace face : faces)
        {
            PhotoFace managed = entityManager.find(PhotoFace.class, face.getFaceId());
            if (managed == null)
            {
                continue;
            }
            managed.setPersonCategory(managedPerson);
            managed.setConfirmed(true);
            managed.setClusterId(null);
            count++;

            Photo photo = managed.getPhoto();
            if (photo != null && taggedPhotoIds.add(photo.getPhotoId()))
            {
                // updatePhotoCategories rather than addCategoryToPhoto in a loop: it works on one freshly merged
                // managed instance, which is what keeps a second merge from reverting the join table.
                _photoOperations.updatePhotoCategories(photo, List.of(managedPerson), List.of());
            }
        }

        // A cheap nicety: a person category with no cover photo gets one from the first face confirmed for them.
        if (managedPerson.getDefaultPhoto() == null && !taggedPhotoIds.isEmpty())
        {
            Photo firstPhoto = entityManager.find(Photo.class, taggedPhotoIds.iterator().next());
            if (firstPhoto != null)
            {
                managedPerson.setDefaultPhoto(firstPhoto);
            }
        }
        return count;
    }

    /**
     * Assigns every face in an unknown cluster to a person and tags their photos.
     * <p>
     * Going from zero exemplars to dozens is exactly the moment to look for the rest of them, so the caller should
     * follow this with a propagation pass for that person.
     */
    @Transactional
    public int adoptCluster(int clusterId, Category person)
    {
        return assignFaces(_faceOperations.getAllFacesInCluster(clusterId), person);
    }

    /** Records that this face is not this person, which is how a person gets split back apart. */
    @Transactional
    public void rejectFace(PhotoFace face, Category person)
    {
        _faceOperations.rejectFace(face, person.getCategoryId());
    }

    /**
     * Folds one person category into another: moves the faces, adds the surviving category to every photo the old
     * one was on, then deletes the emptied category.
     * <p>
     * SFace both over-splits and over-merges, especially across a child's age range, so this direction is as
     * necessary as rejection is.
     */
    @Transactional
    public void mergePeople(Category from, Category into)
    {
        if (from == null || into == null || from.getCategoryId().equals(into.getCategoryId()))
        {
            return;
        }
        Category managedFrom = entityManager.find(Category.class, from.getCategoryId());
        Category managedInto = entityManager.find(Category.class, into.getCategoryId());
        if (managedFrom == null || managedInto == null)
        {
            return;
        }
        if (managedFrom.getChildCategories() != null && !managedFrom.getChildCategories().isEmpty())
        {
            throw new IllegalStateException("\"" + managedFrom.getDescription() + "\" has subcategories, so it can't be merged away.");
        }

        _faceOperations.reassignFaces(managedFrom.getCategoryId(), managedInto.getCategoryId());

        // Move the photo tags over through the mapping rather than the join table directly: the join table is
        // owned by Photo, so removing the Category alone would leave orphaned link rows and trip the foreign key.
        List<Photo> taggedPhotos = new ArrayList<>(managedFrom.getPhotos(true));
        for (Photo photo : taggedPhotos)
        {
            _photoOperations.updatePhotoCategories(photo, List.of(managedInto), List.of(managedFrom));
        }

        if (managedInto.getDefaultPhoto() == null && managedFrom.getDefaultPhoto() != null)
        {
            managedInto.setDefaultPhoto(managedFrom.getDefaultPhoto());
        }

        // Anything still pointing at the doomed category has to let go first, or the delete fails.
        entityManager.createNativeQuery("UPDATE category SET default_photo_id = NULL WHERE category_id = :from")
                .setParameter("from", managedFrom.getCategoryId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();

        Category toDelete = entityManager.find(Category.class, from.getCategoryId());
        if (toDelete != null)
        {
            entityManager.remove(toDelete);
        }
    }
}
