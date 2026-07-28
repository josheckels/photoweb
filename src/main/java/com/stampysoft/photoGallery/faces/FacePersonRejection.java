package com.stampysoft.photoGallery.faces;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * A face that a human has said is <em>not</em> a given person.
 * <p>
 * This is what stops propagation from re-proposing the same wrong match on every round, and it's the mechanism
 * behind splitting a person: reject the subset of faces that isn't them.
 */
@Entity
@Table(name = "face_person_rejection")
public class FacePersonRejection
{
    @EmbeddedId
    private Key key;

    public FacePersonRejection()
    {
    }

    public FacePersonRejection(long faceId, int categoryId)
    {
        key = new Key(faceId, categoryId);
    }

    public Key getKey()
    {
        return key;
    }

    public void setKey(Key key)
    {
        this.key = key;
    }

    @Embeddable
    public static class Key implements Serializable
    {
        @Column(name = "face_id")
        private Long faceId;

        @Column(name = "category_id")
        private Integer categoryId;

        public Key()
        {
        }

        public Key(Long faceId, Integer categoryId)
        {
            this.faceId = faceId;
            this.categoryId = categoryId;
        }

        public Long getFaceId()
        {
            return faceId;
        }

        public void setFaceId(Long faceId)
        {
            this.faceId = faceId;
        }

        public Integer getCategoryId()
        {
            return categoryId;
        }

        public void setCategoryId(Integer categoryId)
        {
            this.categoryId = categoryId;
        }

        @Override
        public boolean equals(Object o)
        {
            if (!(o instanceof Key other))
            {
                return false;
            }
            return Objects.equals(faceId, other.faceId) && Objects.equals(categoryId, other.categoryId);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(faceId, categoryId);
        }
    }
}
