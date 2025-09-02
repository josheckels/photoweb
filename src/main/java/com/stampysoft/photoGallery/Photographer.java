package com.stampysoft.photoGallery;

import jakarta.persistence.*;

@Entity @Table(name = "photographer")
public class Photographer
{
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "photographer_id")
    private Integer photographerId;
    @Column
    private String name;
    @Column
    private String copyright;

    public static final Photographer UNKNOWN_PHOTOGRAPHER;
    public static final Photographer VARIOUS_PHOTOGRAPHER;

    static
    {
        UNKNOWN_PHOTOGRAPHER = new Photographer();
        UNKNOWN_PHOTOGRAPHER.setPhotographerId(null);
        UNKNOWN_PHOTOGRAPHER.setName("Unknown");
        UNKNOWN_PHOTOGRAPHER.setCopyright("");

        VARIOUS_PHOTOGRAPHER = new Photographer();
        VARIOUS_PHOTOGRAPHER.setPhotographerId(null);
        VARIOUS_PHOTOGRAPHER.setName("Various");
        VARIOUS_PHOTOGRAPHER.setCopyright("Please see individual photos for copyright information.");
    }

    public Photographer()
    {
    }

    public Integer getPhotographerId()
    {
        return photographerId;
    }

    public void setPhotographerId(Integer id)
    {
        photographerId = id;
    }

    public String getCopyright()
    {
        return copyright;
    }

    public void setCopyright(String copyright)
    {
        this.copyright = copyright;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String toString()
    {
        return name;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof Photographer that)) return false;

        return !(getPhotographerId() != null ? !getPhotographerId().equals(that.getPhotographerId()) : that.getPhotographerId() != null);
    }

    public int hashCode()
    {
        return (photographerId != null ? photographerId.hashCode() : 0);
    }
}
