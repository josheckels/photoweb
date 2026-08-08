package com.stampysoft.photoGallery.faces;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.Photo;
import jakarta.persistence.*;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One face detected in one photo, with the embedding that identifies it.
 * <p>
 * A person is a {@link Category} under the People root rather than an entity of its own, so this points straight at
 * {@code category.category_id}. See FACES.md for why.
 */
@Entity
@Table(name = "photo_face")
public class PhotoFace
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "face_id")
    private Long faceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "photo_id", nullable = false)
    private Photo photo;

    /** Bounding box, normalized 0..1 against the original image's dimensions. */
    @Column(name = "x")
    private float x;
    @Column(name = "y")
    private float y;
    @Column(name = "w")
    private float w;
    @Column(name = "h")
    private float h;

    @Column(name = "detect_score")
    private Float detectScore;

    /** L2-normalized SFace feature, so that cosine similarity and inner product agree. */
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = FaceEncoder.EMBEDDING_LENGTH)
    @Column(name = "embedding", nullable = false)
    private float[] embedding;

    /** Which models produced this embedding, so a future model swap only means re-running the backfill. */
    @Column(name = "model_version", nullable = false)
    private String modelVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_category_id")
    private Category personCategory;

    /** False for a proposal from propagation, true only once a human has confirmed it in the review UI. */
    @Column(name = "confirmed", nullable = false)
    private boolean confirmed;

    /** The similarity that produced the proposal, or null for a seeded or hand-assigned face. */
    @Column(name = "match_score")
    private Float matchScore;

    /** Which unknown-face cluster this landed in, for faces that matched nobody. */
    @Column(name = "cluster_id")
    private Integer clusterId;

    /**
     * A human has said this face is nobody: a poster, a photo-in-a-photo, a stranger in the background, or something
     * that isn't a face at all.
     * <p>
     * Distinct from a rejection, which is only ever about one person. Rejecting a face from Alice leaves it in the
     * pool to be proposed as Bob, clustered, and reviewed again; this takes it out of matching altogether. It also
     * survives a re-scan, because it's a human decision like a confirmation is.
     */
    @Column(name = "ignored", nullable = false)
    private boolean ignored;

    public PhotoFace()
    {
    }

    public Long getFaceId()
    {
        return faceId;
    }

    public void setFaceId(Long faceId)
    {
        this.faceId = faceId;
    }

    public Photo getPhoto()
    {
        return photo;
    }

    public void setPhoto(Photo photo)
    {
        this.photo = photo;
    }

    public float getX()
    {
        return x;
    }

    public void setX(float x)
    {
        this.x = x;
    }

    public float getY()
    {
        return y;
    }

    public void setY(float y)
    {
        this.y = y;
    }

    public float getW()
    {
        return w;
    }

    public void setW(float w)
    {
        this.w = w;
    }

    public float getH()
    {
        return h;
    }

    public void setH(float h)
    {
        this.h = h;
    }

    public Float getDetectScore()
    {
        return detectScore;
    }

    public void setDetectScore(Float detectScore)
    {
        this.detectScore = detectScore;
    }

    public float[] getEmbedding()
    {
        return embedding;
    }

    public void setEmbedding(float[] embedding)
    {
        this.embedding = embedding;
    }

    public String getModelVersion()
    {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion)
    {
        this.modelVersion = modelVersion;
    }

    public Category getPersonCategory()
    {
        return personCategory;
    }

    public void setPersonCategory(Category personCategory)
    {
        this.personCategory = personCategory;
    }

    public boolean isConfirmed()
    {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed)
    {
        this.confirmed = confirmed;
    }

    public Float getMatchScore()
    {
        return matchScore;
    }

    public void setMatchScore(Float matchScore)
    {
        this.matchScore = matchScore;
    }

    public Integer getClusterId()
    {
        return clusterId;
    }

    public void setClusterId(Integer clusterId)
    {
        this.clusterId = clusterId;
    }

    public boolean isIgnored()
    {
        return ignored;
    }

    public void setIgnored(boolean ignored)
    {
        this.ignored = ignored;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (!(o instanceof PhotoFace other))
        {
            return false;
        }
        return faceId != null && faceId.equals(other.faceId);
    }

    @Override
    public int hashCode()
    {
        return faceId == null ? 0 : faceId.hashCode();
    }
}
