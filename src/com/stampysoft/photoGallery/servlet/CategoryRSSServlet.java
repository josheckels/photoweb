package com.stampysoft.photoGallery.servlet;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import com.stampysoft.servlet.AbstractServlet;
import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.FeedException;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CategoryRSSServlet extends AbstractRSSServlet
{
    protected SyndFeed getFeed(HttpServletRequest req) throws IOException, FeedException
    {
        boolean includePrivate = AbstractServlet.includePrivate(req);
        SyndFeed feed = new SyndFeedImpl();

        feed.setTitle("Josh Eckels' Homepage RSS Feed");
        feed.setLink("http://www.jeckels.com/");
        feed.setDescription("Newest photo gallery categories");

        List<SyndEntry> entries = new ArrayList<SyndEntry>();

        List<Category> newestCategories = PhotoOperations.getPhotoOperations().getNewestCategories(5, includePrivate);
        for (Category category : newestCategories)
        {
            SyndEntry entry = new SyndEntryImpl();

            entry.setTitle(category.getDescription());
            entry.setLink("http://www.jeckels.com/categoryBrowser?CategoryId=" + category.getCategoryId() + "&fromrss=true");
            entry.setPublishedDate(category.getCreatedOn());

            SyndContent description = new SyndContentImpl();
            description.setType("text/plain");
            description.setValue(category.getDescription());
            entry.setDescription(description);
            entries.add(entry);
        }

        feed.setEntries(entries);

        return feed;
    }
}