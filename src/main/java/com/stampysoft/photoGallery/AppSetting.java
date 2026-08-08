package com.stampysoft.photoGallery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A name/value pair that belongs to the installation rather than to a photo - currently just the owner's access
 * token.
 * <p>
 * In the database rather than config.properties because the token is a secret in a tracked file otherwise, and
 * because rotating it should be a button in the admin UI rather than an edit and a restart.
 */
@Entity
@Table(name = "app_setting")
public class AppSetting
{
    /** The owner's all-access token. Absent or blank means owner unlock is switched off. */
    public static final String OWNER_TOKEN = "owner.token";

    @Id
    @Column(name = "setting_name", length = 64)
    private String _name;

    @Column(name = "setting_value")
    private String _value;

    public AppSetting()
    {
    }

    public AppSetting(String name, String value)
    {
        _name = name;
        _value = value;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public String getValue()
    {
        return _value;
    }

    public void setValue(String value)
    {
        _value = value;
    }
}
