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

import com.stampysoft.servlet.AbstractServlet;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedOutput;
import com.sun.syndication.io.FeedException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

/**
 * User: jeckels
 * Date: Sep 24, 2008
 */
public abstract class AbstractRSSServlet extends AbstractServlet
{
    private static final String FEED_TYPE = "type";
    private static final String MIME_TYPE = "application/xml; charset=UTF-8";
    private static final String COULD_NOT_GENERATE_FEED_ERROR = "Could not generate feed";

    protected void processRequest(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException
    {
        try
        {
            SyndFeed feed = getFeed(req);

            String feedType = req.getParameter(FEED_TYPE);
            feedType = (feedType != null) ? feedType : "rss_2.0";
            feed.setFeedType(feedType);

            res.setContentType(MIME_TYPE);
            SyndFeedOutput output = new SyndFeedOutput();
            output.output(feed, res.getWriter());
        }
        catch (FeedException ex)
        {
            String msg = COULD_NOT_GENERATE_FEED_ERROR;
            log(msg, ex);
            res.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg);
        }
    }

    protected abstract SyndFeed getFeed(HttpServletRequest request) throws IOException, FeedException;
}