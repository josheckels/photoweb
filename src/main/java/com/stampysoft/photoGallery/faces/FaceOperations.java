package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import com.stampysoft.photoGallery.admin.AdminFrame;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All database access for face tagging, following the same shape as {@link com.stampysoft.photoGallery.PhotoOperations}:
 * one transactional service, queries written out by hand against the EntityManager.
 * <p>
 * Similarity searches are native SQL using pgvector's {@code <=>} cosine operator with the probe vector written
 * into the statement as a literal. That keeps the HNSW index in play and depends only on pgvector itself, rather
 * than on Hibernate's HQL vector function mapping; hibernate-vector is still what reads and writes the embedding
 * column through {@link PhotoFace}.
 */
@Repository
@Transactional
@Service
public class FaceOperations
{
    /** How many nearest confirmed faces a proposal looks at before grouping them by person. */
    private static final int NEIGHBOUR_LIMIT = 100;

    /** How many of a person's own best matches get averaged into their score. */
    private static final int TOP_MATCHES_PER_PERSON = 3;

    /** How many neighbours each face considers when clustering the leftovers. */
    private static final int CLUSTER_NEIGHBOUR_LIMIT = 20;

    @PersistenceContext
    private EntityManager entityManager;

    public static FaceOperations getFaceOperations()
    {
        return AdminFrame.getFrame().getFaceOperations();
    }

    private EntityManager getEntityManager()
    {
        return entityManager;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 3 - backfill
    // ------------------------------------------------------------------------------------------------

    /**
     * The photos that still need scanning: never scanned, and not movies. Private photos are included on purpose -
     * the embeddings stay in local Postgres, and read access is gated by the existing includePrivate flag.
     */
    @Transactional(readOnly = true)
    public List<Integer> getUnscannedPhotoIds()
    {
        Query query = getEntityManager().createQuery(
                "select p.photoId from Photo p where p._faceScannedOn is null and p.movie = false order by p.photoId");
        return castList(query.getResultList());
    }

    @Transactional(readOnly = true)
    public long getScannedPhotoCount()
    {
        Query query = getEntityManager().createQuery(
                "select count(p) from Photo p where p._faceScannedOn is not null");
        return ((Number) query.getSingleResult()).longValue();
    }

    @Transactional(readOnly = true)
    public long getFaceCount()
    {
        return ((Number) getEntityManager().createQuery("select count(f) from PhotoFace f").getSingleResult()).longValue();
    }

    @Transactional(readOnly = true)
    public List<Photo> getPhotosByIds(Collection<Integer> photoIds)
    {
        if (photoIds.isEmpty())
        {
            return new ArrayList<>();
        }
        Query query = getEntityManager().createQuery("from Photo where photoId in :ids");
        query.setParameter("ids", photoIds);
        return castList(query.getResultList());
    }

    /**
     * Records the faces found in one photo and stamps {@code face_scanned_on}, even when no faces were found.
     * Stamping regardless is what makes the backfill resumable after a crash: an unstamped photo is one that was
     * never looked at, not one that turned out to be a landscape.
     * <p>
     * Existing unconfirmed faces for the photo are replaced, so a re-scan is idempotent. Confirmed faces are left
     * alone, because a human decision outranks anything detection has to say.
     */
    @Transactional
    public void saveScanResult(int photoId, List<FaceEncoder.DetectedFace> detectedFaces)
    {
        Photo photo = getEntityManager().find(Photo.class, photoId);
        if (photo == null)
        {
            return;
        }

        List<PhotoFace> confirmedFaces = castList(getEntityManager()
                .createQuery("select f from PhotoFace f where f.photo.photoId = :photoId and f.confirmed = true")
                .setParameter("photoId", photoId)
                .getResultList());

        getEntityManager().createQuery("delete from PhotoFace f where f.photo.photoId = :photoId and f.confirmed = false")
                .setParameter("photoId", photoId)
                .executeUpdate();

        for (FaceEncoder.DetectedFace detected : detectedFaces)
        {
            // A confirmed face is a human decision, so a re-scan re-detecting the same region leaves it alone
            // instead of producing a duplicate that needs reviewing all over again.
            if (overlapsConfirmedFace(confirmedFaces, detected))
            {
                continue;
            }
            PhotoFace face = new PhotoFace();
            face.setPhoto(photo);
            face.setX(detected.x());
            face.setY(detected.y());
            face.setW(detected.w());
            face.setH(detected.h());
            face.setDetectScore(detected.detectScore());
            face.setEmbedding(detected.embedding());
            face.setModelVersion(FaceEncoder.MODEL_VERSION);
            getEntityManager().persist(face);
        }

        photo.setFaceScannedOn(new Date());
    }

    /** True if the detection covers essentially the same region as a face that's already been confirmed. */
    private static boolean overlapsConfirmedFace(List<PhotoFace> confirmedFaces, FaceEncoder.DetectedFace detected)
    {
        for (PhotoFace existing : confirmedFaces)
        {
            float overlapWidth = Math.min(existing.getX() + existing.getW(), detected.x() + detected.w()) - Math.max(existing.getX(), detected.x());
            float overlapHeight = Math.min(existing.getY() + existing.getH(), detected.y() + detected.h()) - Math.max(existing.getY(), detected.y());
            if (overlapWidth <= 0 || overlapHeight <= 0)
            {
                continue;
            }
            float intersection = overlapWidth * overlapHeight;
            float union = existing.getW() * existing.getH() + detected.w() * detected.h() - intersection;
            if (union > 0 && intersection / union >= 0.5f)
            {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a whole batch of scan results in one transaction, which is what makes the backfill both fast and
     * resumable: a crash costs at most the batch in flight, and every photo in a committed batch is stamped.
     */
    @Transactional
    public void saveScanResults(Map<Integer, List<FaceEncoder.DetectedFace>> facesByPhotoId)
    {
        for (Map.Entry<Integer, List<FaceEncoder.DetectedFace>> entry : facesByPhotoId.entrySet())
        {
            saveScanResult(entry.getKey(), entry.getValue());
        }
    }

    /** Clears the scan stamp and every unconfirmed face, so that the backfill will revisit these photos. */
    @Transactional
    public int resetScan(boolean keepConfirmed)
    {
        if (!keepConfirmed)
        {
            getEntityManager().createQuery("delete from FacePersonRejection").executeUpdate();
            getEntityManager().createQuery("delete from PhotoFace").executeUpdate();
        }
        else
        {
            getEntityManager().createQuery("delete from PhotoFace f where f.confirmed = false").executeUpdate();
        }
        return getEntityManager().createQuery("update Photo p set p._faceScannedOn = null").executeUpdate();
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 4 - seed from solo photos
    // ------------------------------------------------------------------------------------------------

    /**
     * Finds the unambiguous seeds: photos tagged with exactly one person and containing exactly one detected face.
     * One tag plus one face means that face is that person with no inference required.
     * <p>
     * A photo tagged with exactly N people and containing exactly N faces is constrained but the assignment among
     * them isn't, so those are left for propagation.
     *
     * @return face id to person category id
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> findSoloSeeds(Collection<Integer> personCategoryIds)
    {
        if (personCategoryIds.isEmpty())
        {
            return new LinkedHashMap<>();
        }

        Query query = getEntityManager().createNativeQuery(
                "SELECT f.face_id, solo.category_id " +
                "FROM photo_face f " +
                "JOIN (SELECT photo_id, min(category_id) AS category_id FROM photo_category_link " +
                "      WHERE category_id IN (:personIds) GROUP BY photo_id HAVING count(*) = 1) solo " +
                "  ON solo.photo_id = f.photo_id " +
                "JOIN (SELECT photo_id FROM photo_face GROUP BY photo_id HAVING count(*) = 1) single " +
                "  ON single.photo_id = f.photo_id " +
                "WHERE f.person_category_id IS NULL");
        query.setParameter("personIds", personCategoryIds);

        Map<Long, Integer> result = new LinkedHashMap<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.put(((Number) columns[0]).longValue(), ((Number) columns[1]).intValue());
        }
        return result;
    }

    /** Assigns each face to its person as a confirmed match. Used only for seeds, which need no human review. */
    @Transactional
    public int assignConfirmed(Map<Long, Integer> facesToPeople)
    {
        int count = 0;
        for (Map.Entry<Long, Integer> entry : facesToPeople.entrySet())
        {
            PhotoFace face = getEntityManager().find(PhotoFace.class, entry.getKey());
            Category person = getEntityManager().find(Category.class, entry.getValue());
            if (face == null || person == null)
            {
                continue;
            }
            face.setPersonCategory(person);
            face.setConfirmed(true);
            face.setMatchScore(null);
            face.setClusterId(null);
            count++;
        }
        return count;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 5 - propagation
    // ------------------------------------------------------------------------------------------------

    /** The ids of every photo that has at least one face nobody has been assigned to yet. */
    @Transactional(readOnly = true)
    public List<Integer> getPhotoIdsWithUnassignedFaces()
    {
        Query query = getEntityManager().createQuery(
                "select distinct f.photo.photoId from PhotoFace f where f.personCategory is null order by f.photo.photoId");
        return castList(query.getResultList());
    }

    @Transactional(readOnly = true)
    public List<PhotoFace> getFacesForPhotos(Collection<Integer> photoIds)
    {
        if (photoIds.isEmpty())
        {
            return new ArrayList<>();
        }
        // The person is fetched rather than proxied because callers read it after the transaction has closed.
        Query query = getEntityManager().createQuery(
                "select f from PhotoFace f join fetch f.photo left join fetch f.personCategory where f.photo.photoId in :ids");
        query.setParameter("ids", photoIds);
        return castList(query.getResultList());
    }

    @Transactional(readOnly = true)
    public List<PhotoFace> getFacesForPhoto(int photoId)
    {
        Query query = getEntityManager().createQuery(
                "select f from PhotoFace f join fetch f.photo left join fetch f.personCategory " +
                "where f.photo.photoId = :id order by f.faceId");
        query.setParameter("id", photoId);
        return castList(query.getResultList());
    }

    /**
     * The person categories tagged on each of the given photos. A positive tag is strong evidence the person is
     * present; a missing tag is weak evidence of absence, which is why propagation lowers the bar for tagged
     * candidates rather than excluding untagged ones.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Set<Integer>> getTaggedPeopleByPhoto(Collection<Integer> photoIds, Collection<Integer> personCategoryIds)
    {
        Map<Integer, Set<Integer>> result = new LinkedHashMap<>();
        if (photoIds.isEmpty() || personCategoryIds.isEmpty())
        {
            return result;
        }
        Query query = getEntityManager().createNativeQuery(
                "SELECT photo_id, category_id FROM photo_category_link " +
                "WHERE photo_id IN (:photoIds) AND category_id IN (:personIds)");
        query.setParameter("photoIds", photoIds);
        query.setParameter("personIds", personCategoryIds);
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.computeIfAbsent(((Number) columns[0]).intValue(), k -> new LinkedHashSet<>())
                    .add(((Number) columns[1]).intValue());
        }
        return result;
    }

    /**
     * Scores this face against every person who has confirmed faces nearby, as the mean of the top few cosine
     * similarities against that person's confirmed faces.
     * <p>
     * Deliberately not a single centroid per person: this library spans years, and a child's face at 3 and at 15
     * don't share one. Taking the best few matches lets one person be represented by several ages at once.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Float> scoreAgainstNeighbours(float[] embedding)
    {
        String literal = toVectorLiteral(embedding);
        Query query = getEntityManager().createNativeQuery(
                "SELECT f.person_category_id, 1 - (f.embedding <=> " + literal + ") AS similarity " +
                "FROM photo_face f " +
                "WHERE f.confirmed = true AND f.person_category_id IS NOT NULL " +
                "ORDER BY f.embedding <=> " + literal + " " +
                "LIMIT " + NEIGHBOUR_LIMIT);

        Map<Integer, List<Float>> byPerson = new LinkedHashMap<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            int personId = ((Number) columns[0]).intValue();
            List<Float> similarities = byPerson.computeIfAbsent(personId, k -> new ArrayList<>());
            if (similarities.size() < TOP_MATCHES_PER_PERSON)
            {
                similarities.add(((Number) columns[1]).floatValue());
            }
        }

        Map<Integer, Float> result = new LinkedHashMap<>();
        byPerson.forEach((personId, similarities) -> result.put(personId, mean(similarities)));
        return result;
    }

    /**
     * Scores this face against one specific person, exactly. The neighbour query can miss a person whose faces all
     * sit outside the global top {@value #NEIGHBOUR_LIMIT}, which matters for the people actually tagged on the
     * photo, so those get asked about directly.
     */
    @Transactional(readOnly = true)
    public float scoreAgainstPerson(float[] embedding, int personCategoryId)
    {
        String literal = toVectorLiteral(embedding);
        Query query = getEntityManager().createNativeQuery(
                "SELECT 1 - (f.embedding <=> " + literal + ") AS similarity " +
                "FROM photo_face f " +
                "WHERE f.confirmed = true AND f.person_category_id = :personId " +
                "ORDER BY f.embedding <=> " + literal + " " +
                "LIMIT " + TOP_MATCHES_PER_PERSON);
        query.setParameter("personId", personCategoryId);

        List<Float> similarities = new ArrayList<>();
        for (Object value : query.getResultList())
        {
            similarities.add(((Number) value).floatValue());
        }
        return similarities.isEmpty() ? Float.NEGATIVE_INFINITY : mean(similarities);
    }

    /** The person categories that have at least one confirmed face, and are therefore matchable. */
    @Transactional(readOnly = true)
    public Set<Integer> getPeopleWithConfirmedFaces()
    {
        Query query = getEntityManager().createQuery(
                "select distinct f.personCategory.categoryId from PhotoFace f where f.confirmed = true and f.personCategory is not null");
        return new LinkedHashSet<>(castList(query.getResultList()));
    }

    /** Records a proposal for human review. Nothing here ever sets confirmed - only the review UI does that. */
    @Transactional
    public void saveProposals(Map<Long, PersonScore> proposals)
    {
        for (Map.Entry<Long, PersonScore> entry : proposals.entrySet())
        {
            PhotoFace face = getEntityManager().find(PhotoFace.class, entry.getKey());
            Category person = getEntityManager().find(Category.class, entry.getValue().categoryId());
            if (face == null || person == null || face.isConfirmed())
            {
                continue;
            }
            face.setPersonCategory(person);
            face.setConfirmed(false);
            face.setMatchScore(entry.getValue().score());
            face.setClusterId(null);
        }
    }

    /** Drops every outstanding proposal, so that a re-run starts from confirmed faces only. */
    @Transactional
    public int clearProposals()
    {
        return getEntityManager().createQuery(
                "update PhotoFace f set f.personCategory = null, f.matchScore = null where f.confirmed = false and f.personCategory is not null")
                .executeUpdate();
    }

    /** The (face, person) pairs a human has already said are wrong, which must never be proposed again. */
    @Transactional(readOnly = true)
    public Map<Long, Set<Integer>> getRejections()
    {
        Query query = getEntityManager().createQuery("select r.key.faceId, r.key.categoryId from FacePersonRejection r");
        Map<Long, Set<Integer>> result = new LinkedHashMap<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.computeIfAbsent(((Number) columns[0]).longValue(), k -> new HashSet<>())
                    .add(((Number) columns[1]).intValue());
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 6 - cluster the leftovers
    // ------------------------------------------------------------------------------------------------

    /** Every face that matched nobody, as (faceId, photoId) pairs, in id order. */
    @Transactional(readOnly = true)
    public List<FaceRef> getUnmatchedFaceRefs()
    {
        Query query = getEntityManager().createQuery(
                "select f.faceId, f.photo.photoId from PhotoFace f where f.personCategory is null order by f.faceId");
        List<FaceRef> result = new ArrayList<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.add(new FaceRef(((Number) columns[0]).longValue(), ((Number) columns[1]).intValue()));
        }
        return result;
    }

    /**
     * Finds candidate merges among unmatched faces: for each of the given faces, its nearest unmatched neighbours
     * above the similarity floor. Queried through the HNSW index with a lateral join rather than an N-squared scan.
     */
    @Transactional(readOnly = true)
    public List<FacePair> findSimilarUnmatchedPairs(Collection<Long> faceIds, float minimumSimilarity)
    {
        if (faceIds.isEmpty())
        {
            return new ArrayList<>();
        }
        Query query = getEntityManager().createNativeQuery(
                "SELECT a.face_id, neighbour.face_id, 1 - (a.embedding <=> neighbour.embedding) AS similarity " +
                "FROM photo_face a " +
                "CROSS JOIN LATERAL (" +
                "   SELECT b.face_id, b.embedding FROM photo_face b " +
                "   WHERE b.person_category_id IS NULL AND b.face_id <> a.face_id " +
                "   ORDER BY b.embedding <=> a.embedding LIMIT " + CLUSTER_NEIGHBOUR_LIMIT + ") neighbour " +
                "WHERE a.face_id IN (:faceIds) " +
                "  AND 1 - (a.embedding <=> neighbour.embedding) >= :minimumSimilarity");
        query.setParameter("faceIds", faceIds);
        query.setParameter("minimumSimilarity", minimumSimilarity);

        List<FacePair> result = new ArrayList<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.add(new FacePair(((Number) columns[0]).longValue(), ((Number) columns[1]).longValue(),
                    ((Number) columns[2]).floatValue()));
        }
        return result;
    }

    /**
     * Works outwards from one person's confirmed faces to the unmatched faces nearest them.
     * <p>
     * This is the cheap direction for "now go find the rest of them" after a cluster gets named: it costs one
     * indexed query per exemplar instead of one per unmatched face in the whole library.
     */
    @Transactional(readOnly = true)
    public List<FaceRef> findUnmatchedNeighboursOfPerson(int personCategoryId, float minimumSimilarity, int limitPerExemplar)
    {
        Query query = getEntityManager().createNativeQuery(
                "SELECT DISTINCT neighbour.face_id, neighbour.photo_id " +
                "FROM photo_face a " +
                "CROSS JOIN LATERAL (" +
                "   SELECT b.face_id, b.photo_id, b.embedding FROM photo_face b " +
                "   WHERE b.person_category_id IS NULL " +
                "   ORDER BY b.embedding <=> a.embedding LIMIT " + limitPerExemplar + ") neighbour " +
                "WHERE a.confirmed = true AND a.person_category_id = :personId " +
                "  AND 1 - (neighbour.embedding <=> a.embedding) >= :minimumSimilarity");
        query.setParameter("personId", personCategoryId);
        query.setParameter("minimumSimilarity", minimumSimilarity);

        List<FaceRef> result = new ArrayList<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.add(new FaceRef(((Number) columns[0]).longValue(), ((Number) columns[1]).intValue()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PhotoFace> getFacesByIds(Collection<Long> faceIds)
    {
        if (faceIds.isEmpty())
        {
            return new ArrayList<>();
        }
        Query query = getEntityManager().createQuery("select f from PhotoFace f join fetch f.photo where f.faceId in :ids");
        query.setParameter("ids", faceIds);
        return castList(query.getResultList());
    }

    /** Writes the cluster assignment for a batch of faces. A null cluster id means "not in any cluster". */
    @Transactional
    public void saveClusterAssignments(Map<Long, Integer> clusterIdsByFace)
    {
        for (Map.Entry<Long, Integer> entry : clusterIdsByFace.entrySet())
        {
            PhotoFace face = getEntityManager().find(PhotoFace.class, entry.getKey());
            if (face != null && face.getPersonCategory() == null)
            {
                face.setClusterId(entry.getValue());
            }
        }
    }

    @Transactional
    public int clearClusters()
    {
        return getEntityManager().createQuery("update PhotoFace f set f.clusterId = null where f.clusterId is not null")
                .executeUpdate();
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 7 - review UI queries
    // ------------------------------------------------------------------------------------------------

    /** The people who have proposals waiting, with how many each has. */
    @Transactional(readOnly = true)
    public List<PersonPendingCount> getPendingCountsByPerson()
    {
        Query query = getEntityManager().createQuery(
                "select f.personCategory.categoryId, f.personCategory.description, count(f) from PhotoFace f " +
                "where f.confirmed = false and f.personCategory is not null " +
                "group by f.personCategory.categoryId, f.personCategory.description order by count(f) desc");
        List<PersonPendingCount> result = new ArrayList<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.add(new PersonPendingCount(((Number) columns[0]).intValue(), (String) columns[1],
                    ((Number) columns[2]).intValue()));
        }
        return result;
    }

    /**
     * The proposals for one person, best match first. Sorting by score is what makes the queue quick to work
     * through: confirm the top chunk in one click and only scrutinize the tail.
     */
    @Transactional(readOnly = true)
    public List<PhotoFace> getPendingFacesForPerson(int personCategoryId, int limit)
    {
        Query query = getEntityManager().createQuery(
                "select f from PhotoFace f join fetch f.photo " +
                "where f.confirmed = false and f.personCategory.categoryId = :personId " +
                "order by f.matchScore desc nulls last");
        query.setParameter("personId", personCategoryId);
        query.setMaxResults(limit);
        return castList(query.getResultList());
    }

    /** Clusters worth reviewing, biggest first. Singletons are noise, so the caller sets a floor. */
    @Transactional(readOnly = true)
    public List<ClusterSummary> getClusterSummaries(int minimumSize)
    {
        Query query = getEntityManager().createQuery(
                "select f.clusterId, count(f), min(f.faceId) from PhotoFace f " +
                "where f.clusterId is not null and f.personCategory is null " +
                "group by f.clusterId having count(f) >= :minimumSize order by count(f) desc");
        query.setParameter("minimumSize", (long) minimumSize);
        List<ClusterSummary> result = new ArrayList<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.add(new ClusterSummary(((Number) columns[0]).intValue(), ((Number) columns[1]).intValue(),
                    ((Number) columns[2]).longValue()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<PhotoFace> getFacesInCluster(int clusterId, int limit)
    {
        Query query = getEntityManager().createQuery(
                "select f from PhotoFace f join fetch f.photo where f.clusterId = :clusterId and f.personCategory is null order by f.faceId");
        query.setParameter("clusterId", clusterId);
        query.setMaxResults(limit);
        return castList(query.getResultList());
    }

    @Transactional(readOnly = true)
    public List<PhotoFace> getAllFacesInCluster(int clusterId)
    {
        Query query = getEntityManager().createQuery(
                "select f from PhotoFace f where f.clusterId = :clusterId and f.personCategory is null");
        query.setParameter("clusterId", clusterId);
        return castList(query.getResultList());
    }

    /** Confirmed and pending face counts per person, for the People list. */
    @Transactional(readOnly = true)
    public Map<Integer, int[]> getFaceCountsByPerson()
    {
        Query query = getEntityManager().createQuery(
                "select f.personCategory.categoryId, f.confirmed, count(f) from PhotoFace f " +
                "where f.personCategory is not null group by f.personCategory.categoryId, f.confirmed");
        Map<Integer, int[]> result = new LinkedHashMap<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            int[] counts = result.computeIfAbsent(((Number) columns[0]).intValue(), k -> new int[2]);
            boolean confirmed = Boolean.TRUE.equals(columns[1]);
            counts[confirmed ? 0 : 1] = ((Number) columns[2]).intValue();
        }
        return result;
    }

    /**
     * How much material each person has and how recent it is, for deciding who is worth curating.
     * <p>
     * There is no date on the photo table at all - no date taken, no imported-on - so "recent" comes from the
     * {@code YYYY-MM} that most filenames carry. That is imperfect by design: coverage is 100% for the newest
     * several thousand photos and thins out going back, which is the right way round, since the question this
     * answers is "who turns up in photos I'm still taking". Photos with no parseable date report null rather than
     * guessing. {@code max(photo_id)} comes back too as an import-order tiebreak that is always present.
     *
     * @return person category id to their stats
     */
    @Transactional(readOnly = true)
    public Map<Integer, PersonPhotoStats> getPersonPhotoStats(Collection<Integer> personCategoryIds)
    {
        Map<Integer, PersonPhotoStats> result = new LinkedHashMap<>();
        if (personCategoryIds.isEmpty())
        {
            return result;
        }
        // The regex is wrapped in an outer group on purpose: substring() returns the first capture group when
        // there is one, so the bare (19|20) alternation would yield "20" instead of the whole "2026-07".
        Query query = getEntityManager().createNativeQuery(
                "SELECT l.category_id, count(*) AS tagged_photos, " +
                "       max(substring(p.filename from '((19|20)[0-9]{2}-[0-9]{2})')) AS latest_month, " +
                "       max(p.photo_id) AS latest_photo_id " +
                "FROM photo_category_link l JOIN photo p ON p.photo_id = l.photo_id " +
                "WHERE l.category_id IN (:personIds) " +
                "GROUP BY l.category_id");
        query.setParameter("personIds", personCategoryIds);

        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.put(((Number) columns[0]).intValue(), new PersonPhotoStats(
                    ((Number) columns[1]).intValue(),
                    (String) columns[2],
                    ((Number) columns[3]).intValue()));
        }
        return result;
    }

    /**
     * The detection-recall report: people who are tagged on scanned photos where no face was matched to them.
     * <p>
     * These are not wrong tags - her back was turned, she's in profile, or YuNet missed her. A large number here
     * means detection is the problem, not the tags, which is exactly why tags are never removed.
     */
    @Transactional(readOnly = true)
    public List<PersonPendingCount> getTaggedWithoutFaceReport(Collection<Integer> personCategoryIds)
    {
        if (personCategoryIds.isEmpty())
        {
            return new ArrayList<>();
        }
        Query query = getEntityManager().createNativeQuery(
                "SELECT c.category_id, c.description, count(*) AS missing " +
                "FROM photo_category_link l " +
                "JOIN category c ON c.category_id = l.category_id " +
                "JOIN photo p ON p.photo_id = l.photo_id " +
                "WHERE l.category_id IN (:personIds) AND p.face_scanned_on IS NOT NULL " +
                "  AND NOT EXISTS (SELECT 1 FROM photo_face f " +
                "                  WHERE f.photo_id = l.photo_id AND f.person_category_id = l.category_id) " +
                "GROUP BY c.category_id, c.description ORDER BY 3 DESC");
        query.setParameter("personIds", personCategoryIds);

        List<PersonPendingCount> result = new ArrayList<>();
        for (Object row : query.getResultList())
        {
            Object[] columns = (Object[]) row;
            result.add(new PersonPendingCount(((Number) columns[0]).intValue(), (String) columns[1],
                    ((Number) columns[2]).intValue()));
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------------
    // Confirm / reject / reassign
    // ------------------------------------------------------------------------------------------------

    @Transactional
    public void confirmFaces(Collection<Long> faceIds)
    {
        if (faceIds.isEmpty())
        {
            return;
        }
        getEntityManager().createQuery(
                "update PhotoFace f set f.confirmed = true, f.clusterId = null " +
                "where f.faceId in :ids and f.personCategory is not null")
                .setParameter("ids", faceIds)
                .executeUpdate();
    }

    /**
     * Records that these faces are not the person they were proposed as, and clears the proposal. The rejection is
     * what stops the next propagation round from proposing it all over again.
     */
    @Transactional
    public void rejectFaces(Collection<Long> faceIds)
    {
        for (Long faceId : faceIds)
        {
            PhotoFace face = getEntityManager().find(PhotoFace.class, faceId);
            if (face == null || face.getPersonCategory() == null)
            {
                continue;
            }
            rejectFace(face, face.getPersonCategory().getCategoryId());
        }
    }

    @Transactional
    public void rejectFace(PhotoFace face, int personCategoryId)
    {
        PhotoFace managed = getEntityManager().find(PhotoFace.class, face.getFaceId());
        if (managed == null)
        {
            return;
        }
        FacePersonRejection.Key key = new FacePersonRejection.Key(managed.getFaceId(), personCategoryId);
        if (getEntityManager().find(FacePersonRejection.class, key) == null)
        {
            getEntityManager().persist(new FacePersonRejection(managed.getFaceId(), personCategoryId));
        }
        if (managed.getPersonCategory() != null && managed.getPersonCategory().getCategoryId() == personCategoryId)
        {
            managed.setPersonCategory(null);
            managed.setConfirmed(false);
            managed.setMatchScore(null);
        }
    }

    /** Moves every face and rejection from one person onto another, for merging duplicate person categories. */
    @Transactional
    public int reassignFaces(int fromCategoryId, int toCategoryId)
    {
        Category target = getEntityManager().find(Category.class, toCategoryId);
        if (target == null)
        {
            return 0;
        }
        int moved = getEntityManager().createQuery(
                "update PhotoFace f set f.personCategory = :target where f.personCategory.categoryId = :from")
                .setParameter("target", target)
                .setParameter("from", fromCategoryId)
                .executeUpdate();

        // Carry the rejections across too, otherwise a merge quietly re-opens matches that were already turned down.
        getEntityManager().createNativeQuery(
                "INSERT INTO face_person_rejection (face_id, category_id) " +
                "SELECT face_id, :to FROM face_person_rejection WHERE category_id = :from " +
                "ON CONFLICT DO NOTHING")
                .setParameter("to", toCategoryId)
                .setParameter("from", fromCategoryId)
                .executeUpdate();
        getEntityManager().createQuery("delete from FacePersonRejection r where r.key.categoryId = :from")
                .setParameter("from", fromCategoryId)
                .executeUpdate();
        return moved;
    }

    private static float mean(List<Float> values)
    {
        float total = 0;
        for (Float value : values)
        {
            total += value;
        }
        return total / values.size();
    }

    /**
     * Formats an embedding as a pgvector literal. Safe to inline into SQL: every element is a float we produced
     * ourselves, so there's nothing here a bind parameter would protect against.
     */
    static String toVectorLiteral(float[] embedding)
    {
        StringBuilder sb = new StringBuilder(embedding.length * 12 + 16);
        sb.append("'[");
        for (int i = 0; i < embedding.length; i++)
        {
            if (i > 0)
            {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append("]'::vector");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> castList(List<?> list)
    {
        return (List<T>) list;
    }

    /** A person and a similarity score, as produced by propagation. */
    public record PersonScore(int categoryId, float score)
    {
    }

    /** A face and the photo it came from, which is all clustering needs in memory. */
    public record FaceRef(long faceId, int photoId)
    {
    }

    public record FacePair(long firstFaceId, long secondFaceId, float similarity)
    {
    }

    public record PersonPendingCount(int categoryId, String description, int count)
    {
    }

    /**
     * How many photos a person is tagged on and how recent they are.
     *
     * @param latestMonth the newest {@code YYYY-MM} found in their photos' filenames, or null if none carried one
     */
    public record PersonPhotoStats(int taggedPhotos, String latestMonth, int latestPhotoId)
    {
    }

    public record ClusterSummary(int clusterId, int size, long representativeFaceId)
    {
    }
}
