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
package com.stampysoft.photoGallery.servlet;

import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.FeedException;
import com.stampysoft.servlet.AbstractServlet;
import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.photoGallery.Comment;
import com.stampysoft.photoGallery.Photo;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * User: jeckels
 * Date: Sep 24, 2008
 */
public class CommentRSSServlet extends AbstractRSSServlet
{
    protected SyndFeed getFeed(HttpServletRequest req) throws IOException, FeedException
    {
        boolean includePrivate = AbstractServlet.includePrivate(req);
        SyndFeed feed = new SyndFeedImpl();

        feed.setTitle("Josh Eckels Photo Comment RSS Feed");
        feed.setLink("http://www.jeckels.com/");
        feed.setDescription("Newest photo comments");

        List<SyndEntry> entries = new ArrayList<SyndEntry>();

        List<Comment> newestComments = PhotoOperations.getPhotoOperations().getNewestComments(10);
        for (Comment comment : newestComments)
        {
            SyndEntry entry = new SyndEntryImpl();

            Photo photo = comment.getPhoto();
            Set<Category> categories = photo.getCategories(includePrivate);
            Category newestCategory = categories.iterator().next();
            for (Category category : categories)
            {
                if (category.getCreatedOn().compareTo(newestCategory.getCreatedOn()) > 0)
                {
                    newestCategory = category;
                }
            }

            entry.setTitle(comment.getPhoto().getCaption() + " (" + newestCategory.toString() + ")");
            entry.setLink("http://www.jeckels.com/photoDetail?PhotoId=" + photo.getPhotoId() + "&ReferringCategoryId=" + newestCategory.getCategoryId() + "&fromrss=true#comments");
            entry.setAuthor(comment.getName());
            entry.setPublishedDate(comment.getCreatedOn());

            SyndContent description = new SyndContentImpl();
            description.setType("text/plain");
            description.setValue(comment.getName() + ": " + comment.getComment());
            entry.setDescription(description);
            entries.add(entry);
        }

        feed.setEntries(entries);

        return feed;
    }
}