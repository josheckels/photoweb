package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.faces.FaceOperations.FacePair;
import com.stampysoft.photoGallery.faces.FaceOperations.FaceRef;
import com.stampysoft.photoGallery.faces.FaceOperations.PersonScore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns embeddings into proposals: seeding from unambiguous photos, propagating out from confirmed faces, and
 * clustering whatever matched nobody.
 * <p>
 * Nothing here ever writes a confirmed match. Every proposal lands with {@code confirmed = false} and a score, and
 * only the review UI promotes it. Re-running is therefore always safe.
 */
public class FaceMatcher
{
    /**
     * The bar for someone who is already tagged on the photo. A positive tag is strong evidence of presence, so
     * this is just the model's own same-identity cutoff.
     */
    public static final float TAGGED_THRESHOLD = FaceEncoder.SAME_IDENTITY_COSINE;

    /**
     * The bar for someone who isn't tagged on the photo. Materially higher, because most people are not in most
     * photos - but not infinite, since a missing tag is weak evidence of absence given incomplete tagging.
     */
    public static final float UNTAGGED_THRESHOLD = 0.50f;

    /** How similar two unknown faces have to be before clustering treats them as the same person. */
    public static final float CLUSTER_THRESHOLD = 0.50f;

    /** Clusters smaller than this are noise rather than a person worth naming. */
    public static final int MINIMUM_CLUSTER_SIZE = 5;

    private static final int PHOTO_BATCH_SIZE = 200;
    private static final int CLUSTER_BATCH_SIZE = 500;
    private static final int NEIGHBOURS_PER_EXEMPLAR = 50;

    private final FaceOperations _faceOperations;
    private final PeopleService _peopleService;

    public FaceMatcher(FaceOperations faceOperations, PeopleService peopleService)
    {
        _faceOperations = faceOperations;
        _peopleService = peopleService;
    }

    /** Lets a long pass report progress and be cancelled, without this class knowing anything about Swing. */
    public interface ProgressListener
    {
        void progress(String task, int done, int total);

        default boolean isCancelled()
        {
            return false;
        }
    }

    public static final ProgressListener NO_PROGRESS = (task, done, total) -> {
    };

    // ------------------------------------------------------------------------------------------------
    // Phase 4 - seed from solo photos
    // ------------------------------------------------------------------------------------------------

    /**
     * Assigns the faces that need no inference at all: one person tagged, one face detected. A handful of these per
     * person is enough to bootstrap propagation.
     */
    public int seedFromSoloPhotos()
    {
        Set<Integer> personIds = _peopleService.getPersonCategoryIds();
        if (personIds.isEmpty())
        {
            return 0;
        }
        Map<Long, Integer> seeds = _faceOperations.findSoloSeeds(personIds);
        return _faceOperations.assignConfirmed(seeds);
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 5 - constrained propagation
    // ------------------------------------------------------------------------------------------------

    /**
     * Proposes a person for every unassigned face that scores well enough against somebody's confirmed faces.
     *
     * @return how many proposals were written
     */
    public int propagate(ProgressListener listener)
    {
        return propagate(_faceOperations.getPhotoIdsWithUnassignedFaces(), listener);
    }

    /**
     * Proposes matches for the unassigned faces in just these photos. Used on import, where re-scoring the whole
     * library would be absurd, and where proposing straight away is what keeps the review queue short instead of
     * requiring another full backfill later.
     */
    public int propagate(List<Integer> photoIds, ProgressListener listener)
    {
        Set<Integer> personIds = _peopleService.getPersonCategoryIds();
        Set<Integer> matchablePeople = new HashSet<>(_faceOperations.getPeopleWithConfirmedFaces());
        matchablePeople.retainAll(personIds);
        if (matchablePeople.isEmpty())
        {
            return 0;
        }

        if (photoIds.isEmpty())
        {
            return 0;
        }
        Map<Long, Set<Integer>> rejections = _faceOperations.getRejections();

        int proposalCount = 0;
        for (int start = 0; start < photoIds.size(); start += PHOTO_BATCH_SIZE)
        {
            if (listener.isCancelled())
            {
                break;
            }
            listener.progress("Matching faces", start, photoIds.size());

            List<Integer> batch = photoIds.subList(start, Math.min(start + PHOTO_BATCH_SIZE, photoIds.size()));
            Map<Integer, List<PhotoFace>> facesByPhoto = groupByPhoto(_faceOperations.getFacesForPhotos(batch));
            Map<Integer, Set<Integer>> taggedByPhoto = _faceOperations.getTaggedPeopleByPhoto(batch, personIds);

            Map<Long, PersonScore> proposals = new LinkedHashMap<>();
            for (Map.Entry<Integer, List<PhotoFace>> entry : facesByPhoto.entrySet())
            {
                Set<Integer> taggedPeople = taggedByPhoto.getOrDefault(entry.getKey(), Set.of());
                proposals.putAll(proposeForPhoto(entry.getValue(), taggedPeople, matchablePeople, rejections));
            }

            _faceOperations.saveProposals(proposals);
            proposalCount += proposals.size();
        }
        listener.progress("Matching faces", photoIds.size(), photoIds.size());
        return proposalCount;
    }

    /**
     * Runs propagation for one person only, starting from their confirmed faces and working outwards.
     * <p>
     * This is what fires right after an unknown cluster gets a name: going from 0 to 43 exemplars is exactly when
     * "now find the rest of them" pays off, and it produces a follow-up review batch straight away.
     */
    public int propagateForPerson(int personCategoryId, ProgressListener listener)
    {
        if (!_faceOperations.getPeopleWithConfirmedFaces().contains(personCategoryId))
        {
            return 0;
        }

        listener.progress("Looking for more matches", 0, 1);
        List<FaceRef> candidates = _faceOperations.findUnmatchedNeighboursOfPerson(
                personCategoryId, TAGGED_THRESHOLD, NEIGHBOURS_PER_EXEMPLAR);
        if (candidates.isEmpty())
        {
            return 0;
        }

        Set<Integer> photoIds = new HashSet<>();
        for (FaceRef candidate : candidates)
        {
            photoIds.add(candidate.photoId());
        }

        Map<Long, Set<Integer>> rejections = _faceOperations.getRejections();
        Set<Integer> personIds = _peopleService.getPersonCategoryIds();
        Map<Integer, Set<Integer>> taggedByPhoto = _faceOperations.getTaggedPeopleByPhoto(photoIds, personIds);
        Map<Integer, List<PhotoFace>> facesByPhoto = groupByPhoto(_faceOperations.getFacesForPhotos(photoIds));

        Set<Long> candidateFaceIds = new HashSet<>();
        for (FaceRef candidate : candidates)
        {
            candidateFaceIds.add(candidate.faceId());
        }

        Map<Long, PersonScore> proposals = new LinkedHashMap<>();
        int done = 0;
        for (Map.Entry<Integer, List<PhotoFace>> entry : facesByPhoto.entrySet())
        {
            if (listener.isCancelled())
            {
                break;
            }
            listener.progress("Looking for more matches", done++, facesByPhoto.size());

            Set<Integer> taggedPeople = taggedByPhoto.getOrDefault(entry.getKey(), Set.of());
            proposals.putAll(proposeForPhoto(entry.getValue(), taggedPeople, Set.of(personCategoryId), rejections,
                    candidateFaceIds));
        }

        _faceOperations.saveProposals(proposals);
        listener.progress("Looking for more matches", facesByPhoto.size(), facesByPhoto.size());
        return proposals.size();
    }

    private Map<Long, PersonScore> proposeForPhoto(List<PhotoFace> photoFaces, Set<Integer> taggedPeople,
                                                   Set<Integer> matchablePeople, Map<Long, Set<Integer>> rejections)
    {
        return proposeForPhoto(photoFaces, taggedPeople, matchablePeople, rejections, null);
    }

    /**
     * Scores every unassigned face in one photo against every plausible person and resolves the conflicts.
     *
     * @param onlyTheseFaceIds when non-null, restricts scoring to these faces
     */
    private Map<Long, PersonScore> proposeForPhoto(List<PhotoFace> photoFaces, Set<Integer> taggedPeople,
                                                   Set<Integer> matchablePeople, Map<Long, Set<Integer>> rejections,
                                                   Set<Long> onlyTheseFaceIds)
    {
        // Two faces in one photo are essentially never the same person, so anyone already spoken for in this photo
        // is out of the running for the rest of its faces.
        Set<Integer> peopleAlreadyInPhoto = new HashSet<>();
        List<PhotoFace> unassigned = new ArrayList<>();
        for (PhotoFace face : photoFaces)
        {
            // A face someone has marked as nobody is out of the running for everyone, permanently.
            if (face.isIgnored())
            {
                continue;
            }
            if (face.getPersonCategory() == null)
            {
                if (onlyTheseFaceIds == null || onlyTheseFaceIds.contains(face.getFaceId()))
                {
                    unassigned.add(face);
                }
            }
            else
            {
                peopleAlreadyInPhoto.add(face.getPersonCategory().getCategoryId());
            }
        }
        if (unassigned.isEmpty())
        {
            return Map.of();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (PhotoFace face : unassigned)
        {
            Map<Integer, Float> scores = new LinkedHashMap<>();
            if (matchablePeople.size() == 1)
            {
                // A single-person pass asks about that person directly rather than paying for a global search.
                int personId = matchablePeople.iterator().next();
                scores.put(personId, _faceOperations.scoreAgainstPerson(face.getEmbedding(), personId));
            }
            else
            {
                scores.putAll(_faceOperations.scoreAgainstNeighbours(face.getEmbedding()));
                // The global search can miss a person all of whose faces sit outside its cutoff, which matters
                // most for the people actually tagged here, so ask about those directly.
                for (Integer personId : taggedPeople)
                {
                    if (matchablePeople.contains(personId) && !scores.containsKey(personId))
                    {
                        scores.put(personId, _faceOperations.scoreAgainstPerson(face.getEmbedding(), personId));
                    }
                }
            }

            Set<Integer> rejected = rejections.getOrDefault(face.getFaceId(), Set.of());
            for (Map.Entry<Integer, Float> score : scores.entrySet())
            {
                Integer personId = score.getKey();
                if (!matchablePeople.contains(personId) || rejected.contains(personId) || peopleAlreadyInPhoto.contains(personId))
                {
                    continue;
                }
                float threshold = taggedPeople.contains(personId) ? TAGGED_THRESHOLD : UNTAGGED_THRESHOLD;
                if (score.getValue() >= threshold)
                {
                    candidates.add(new Candidate(face.getFaceId(), personId, score.getValue()));
                }
            }
        }

        // Resolve greedily by descending score, each face and each person usable once per photo.
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed());
        Map<Long, PersonScore> proposals = new LinkedHashMap<>();
        Set<Integer> usedPeople = new HashSet<>(peopleAlreadyInPhoto);
        for (Candidate candidate : candidates)
        {
            if (proposals.containsKey(candidate.faceId()) || usedPeople.contains(candidate.personCategoryId()))
            {
                continue;
            }
            proposals.put(candidate.faceId(), new PersonScore(candidate.personCategoryId(), candidate.score()));
            usedPeople.add(candidate.personCategoryId());
        }
        return proposals;
    }

    // ------------------------------------------------------------------------------------------------
    // Phase 6 - cluster the leftovers
    // ------------------------------------------------------------------------------------------------

    /**
     * Groups the faces that matched nobody, which are the people who have no category yet. Two faces from the same
     * photo are never merged into one cluster, which both improves the clustering and acts as a free quality check.
     *
     * @return how many clusters of at least {@value #MINIMUM_CLUSTER_SIZE} faces came out
     */
    public int cluster(ProgressListener listener)
    {
        List<FaceRef> unmatched = _faceOperations.getUnmatchedFaceRefs();
        if (unmatched.isEmpty())
        {
            return 0;
        }

        UnionFind unionFind = new UnionFind();
        for (FaceRef face : unmatched)
        {
            unionFind.add(face.faceId(), face.photoId());
        }

        List<Long> faceIds = new ArrayList<>(unmatched.size());
        for (FaceRef face : unmatched)
        {
            faceIds.add(face.faceId());
        }

        for (int start = 0; start < faceIds.size(); start += CLUSTER_BATCH_SIZE)
        {
            if (listener.isCancelled())
            {
                break;
            }
            listener.progress("Clustering unknown faces", start, faceIds.size());

            List<Long> batch = faceIds.subList(start, Math.min(start + CLUSTER_BATCH_SIZE, faceIds.size()));
            List<FacePair> pairs = _faceOperations.findSimilarUnmatchedPairs(batch, CLUSTER_THRESHOLD);
            pairs.sort(Comparator.comparingDouble(FacePair::similarity).reversed());
            for (FacePair pair : pairs)
            {
                unionFind.union(pair.firstFaceId(), pair.secondFaceId());
            }
        }

        // Number the surviving groups and write them out; singletons stay null, since they're noise not people.
        Map<Long, List<Long>> groups = unionFind.getGroups();
        Map<Long, Integer> clusterIdsByFace = new LinkedHashMap<>();
        int nextClusterId = 1;
        int clustersWorthReviewing = 0;
        for (List<Long> group : groups.values())
        {
            if (group.size() < 2)
            {
                for (Long faceId : group)
                {
                    clusterIdsByFace.put(faceId, null);
                }
                continue;
            }
            int clusterId = nextClusterId++;
            for (Long faceId : group)
            {
                clusterIdsByFace.put(faceId, clusterId);
            }
            if (group.size() >= MINIMUM_CLUSTER_SIZE)
            {
                clustersWorthReviewing++;
            }
        }

        List<Map.Entry<Long, Integer>> entries = new ArrayList<>(clusterIdsByFace.entrySet());
        for (int start = 0; start < entries.size(); start += CLUSTER_BATCH_SIZE)
        {
            listener.progress("Saving clusters", start, entries.size());
            Map<Long, Integer> batch = new LinkedHashMap<>();
            for (Map.Entry<Long, Integer> entry : entries.subList(start, Math.min(start + CLUSTER_BATCH_SIZE, entries.size())))
            {
                batch.put(entry.getKey(), entry.getValue());
            }
            _faceOperations.saveClusterAssignments(batch);
        }

        listener.progress("Clustering unknown faces", faceIds.size(), faceIds.size());
        return clustersWorthReviewing;
    }

    private static Map<Integer, List<PhotoFace>> groupByPhoto(Collection<PhotoFace> faces)
    {
        Map<Integer, List<PhotoFace>> result = new LinkedHashMap<>();
        for (PhotoFace face : faces)
        {
            result.computeIfAbsent(face.getPhoto().getPhotoId(), k -> new ArrayList<>()).add(face);
        }
        return result;
    }

    private record Candidate(long faceId, int personCategoryId, float score)
    {
    }

    /**
     * Union-find that refuses any merge putting two faces from the same photo into one group, because that
     * combination is a guaranteed error rather than a close call.
     */
    private static class UnionFind
    {
        private final Map<Long, Long> _parents = new HashMap<>();
        private final Map<Long, Set<Integer>> _photoIdsByRoot = new HashMap<>();

        void add(long faceId, int photoId)
        {
            _parents.put(faceId, faceId);
            Set<Integer> photoIds = new HashSet<>();
            photoIds.add(photoId);
            _photoIdsByRoot.put(faceId, photoIds);
        }

        long find(long faceId)
        {
            Long parent = _parents.get(faceId);
            if (parent == null || parent == faceId)
            {
                return faceId;
            }
            long root = find(parent);
            _parents.put(faceId, root);
            return root;
        }

        void union(long first, long second)
        {
            if (!_parents.containsKey(first) || !_parents.containsKey(second))
            {
                return;
            }
            long firstRoot = find(first);
            long secondRoot = find(second);
            if (firstRoot == secondRoot)
            {
                return;
            }

            Set<Integer> firstPhotoIds = _photoIdsByRoot.get(firstRoot);
            Set<Integer> secondPhotoIds = _photoIdsByRoot.get(secondRoot);
            for (Integer photoId : secondPhotoIds)
            {
                if (firstPhotoIds.contains(photoId))
                {
                    return;
                }
            }

            _parents.put(secondRoot, firstRoot);
            firstPhotoIds.addAll(secondPhotoIds);
            _photoIdsByRoot.remove(secondRoot);
        }

        Map<Long, List<Long>> getGroups()
        {
            Map<Long, List<Long>> result = new LinkedHashMap<>();
            for (Long faceId : _parents.keySet())
            {
                result.computeIfAbsent(find(faceId), k -> new ArrayList<>()).add(faceId);
            }
            return result;
        }
    }
}
