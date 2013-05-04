/*
 * Copyright (c) 2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.stampysoft.photoGallery;

import java.util.Date;

/**
 * User: jeckels
 * Date: Sep 4, 2008
 */
public class Comment implements Comparable<Comment>
{
    private long _commentId;
    private Photo _photo;
    private String _comment;
    private String _name;
    private String _email;
    private Date _createdOn;
    private String _remoteHost;
    private String _remoteIP;

    public long getCommentId()
    {
        return _commentId;
    }

    public void setCommentId(long commentId)
    {
        _commentId = commentId;
    }

    public Photo getPhoto()
    {
        return _photo;
    }

    public void setPhoto(Photo photo)
    {
        _photo = photo;
    }

//    public long getPhotoId()
//    {
//        return _photoId;
//    }
//
//    public void setPhotoId(long photoId)
//    {
//        _photoId = photoId;
//    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        _name = name;
    }

    public Date getCreatedOn()
    {
        return _createdOn;
    }

    public void setCreatedOn(Date createdOn)
    {
        _createdOn = createdOn;
    }

    public String getEmail()
    {
        return _email;
    }

    public void setEmail(String email)
    {
        _email = email;
    }

    public String toString()
    {
        return "Comment #" + _commentId + " on photo " + _photo.getPhotoId() + " by " + _name + ": " + _comment;
    }

    public int compareTo(Comment o)
    {
        return getCreatedOn().compareTo(o.getCreatedOn()) * -1;
    }

    public void setRemoteHost(String remoteHost)
    {
        _remoteHost = remoteHost;
    }

    public void setRemoteIP(String remoteIP)
    {
        _remoteIP = remoteIP;
    }

    public String getRemoteHost()
    {
        return _remoteHost;
    }

    public String getRemoteIP()
    {
        return _remoteIP;
    }
}