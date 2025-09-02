package com.stampysoft.photoGallery.controller;

import com.stampysoft.photoGallery.Category;
import com.stampysoft.photoGallery.PhotoOperations;
import com.sun.syndication.feed.synd.*;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@RestController
public class CategoryRssController extends AbstractController {

    private static final String FEED_TYPE = "rss_2.0";
    private static final String MIME_TYPE = "application/xml; charset=UTF-8";
    private static final String COULD_NOT_GENERATE_FEED_ERROR = "Could not generate feed";

    @Autowired
    private PhotoOperations photoOperations;

    @GetMapping(value = {"/rss/category", "/feed", "/categoryRSS"}, produces = MediaType.APPLICATION_XML_VALUE)
    public void getCategoryRssFeed(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = "type", required = false) String feedType) throws IOException {

        boolean privateSetting = includePrivate(null, request);
        
        try {
            SyndFeed feed = new SyndFeedImpl();

            feed.setTitle("Josh Eckels Photo RSS Feed");
            feed.setLink("https://jeckels.com/");
            feed.setDescription("Newest photo categories");

            List<SyndEntry> entries = new ArrayList<>();

            List<Category> newestCategories = photoOperations.getNewestCategories(8, privateSetting);
            for (Category category : newestCategories) {
                SyndEntry entry = new SyndEntryImpl();

                entry.setTitle(category.getDescription());
                entry.setLink("https://jeckels.com/category/" + category.getCategoryId());
                entry.setPublishedDate(category.getCreatedOn());

                SyndContent description = new SyndContentImpl();
                description.setType("text/plain");
                description.setValue(category.getDescription());
                entry.setDescription(description);
                entries.add(entry);
            }

            feed.setEntries(entries);

            // Set feed type (default to rss_2.0 if not specified)
            feed.setFeedType(feedType != null ? feedType : FEED_TYPE);

            // Set content type
            response.setContentType(MIME_TYPE);
            
            // Output feed
            SyndFeedOutput output = new SyndFeedOutput();
            output.output(feed, response.getWriter());
        } catch (FeedException ex) {
            String msg = COULD_NOT_GENERATE_FEED_ERROR;
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        }
    }
}