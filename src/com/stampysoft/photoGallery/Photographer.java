package com.stampysoft.photoGallery;

public class Photographer
{

    private Long _photographerId;
    private String _name;
    private String _copyright;

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

    public Long getPhotographerId()
    {
        return _photographerId;
    }

    public void setPhotographerId(Long id)
    {
        _photographerId = id;
    }

    public String getCopyright()
    {
        return _copyright;
    }

    public void setCopyright(String copyright)
    {
        this._copyright = copyright;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        this._name = name;
    }

    public String toString()
    {
        return _name;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || !(o instanceof Photographer)) return false;

        Photographer that = (Photographer) o;

        return !(getPhotographerId() != null ? !getPhotographerId().equals(that.getPhotographerId()) : that.getPhotographerId() != null);
    }

    public int hashCode()
    {
        return (_photographerId != null ? _photographerId.hashCode() : 0);
    }
}
