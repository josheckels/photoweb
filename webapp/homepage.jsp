<%@page contentType="text/html" %>
<%@ taglib uri="/c" prefix="c" %>
<%@ taglib uri="/fmt" prefix="fmt" %>

<html>
<head>
    <title>Josh Eckels' Homepage</title>
    <link rel="alternate" type="application/rss+xml" title="Josh Eckels' Homepage RSS Feed"
          href="http://www.jeckels.com/categoryRSS">

    <link href="stylesheet.css" type="text/css" rel="stylesheet"/>
</head>
<body>
    <h1 align="center">Josh Eckels' Homepage</h1>

<table>
    <tr>
        <td>
            <h2>Photo Gallery</h2>
            <p>
                It's a delightful mix of people doing embarassing things and some pretty pictures.
                The top-level categories are:
            </p>
            <ul>
                <jsp:useBean id="Subcategories" scope="request" type="java.util.List<com.stampysoft.photoGallery.Category>"/>
                <c:forEach var="subCategory" items="${Subcategories}">
                    <li><a href="categoryBrowser?CategoryId=<c:out value="${subCategory.categoryId}"/>"><c:out
                        value="${subCategory.description}"/></a></li>
                </c:forEach>
            </ul>
            <p>
                See all the categories as a <a href="/categoryList">flat list</a>.
            </p>
            <p>
                The newest categories are:
            </p>
            <ul>
                <jsp:useBean id="NewestCategories" scope="request" type="java.util.List<com.stampysoft.photoGallery.Category>"/>
                <c:forEach var="newCategory" items="${NewestCategories}">
                    <li>
                        <c:forEach var="parentCategory" items="${newCategory.pathToRoot}" varStatus="status">
                            <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${parentCategory.categoryId}"/>"><c:out
                                value="${parentCategory.description}"/></a>:
                        </c:forEach>
                        <a href="categoryBrowser?CategoryId=<c:out default="ROOT" value="${newCategory.categoryId}"/>"><c:out
                            value="${newCategory.description}"/></a>
                        (added <fmt:formatDate value="${newCategory.createdOn}" pattern="EEEE, MMMM d"/>)
                    </li>
                </c:forEach>
            </ul>
            <p>
            Some randomly chosen selections from the wallpaper category are shown below.
            </p>
            <h2>RSS Feed</h2>
            <p>
                Do you find yourself furiously reloading to catch the latest updates? Save
                your fingers and just subscribe to my RSS feed for <a href="/categoryRSS">new categories</a> or <a href="/commentRSS">new comments</a>.
            </p>

            <h2>Wedding</h2>
            <p>
                I married Danelle Wallace in August 2006. For all the details, check out <a href="http://www.joshanddanelle.com/">joshanddanelle.com</a>, or
                look at the <a href="/categoryBrowser?CategoryId=542">pictures from the wedding weekend</a>.
            </p>

            <h2>Work</h2>
            <p>
                I'm working at <a href="http://www.labkey.com/">LabKey Software, LLC</a> on
                the <a href="http://www.labkey.org/">LabKey Server</a>.
            </p>

            <h2>J2ME Ant Tasks</h2>
            <p>
                I've written a <a href="/ant">set of Ant tasks</a> that help automate building Java 2, Micro Edition
                (J2ME) projects.
            </p>

            <h2>Ads, sweet nourishing ads</h2>
            <p>
                And finally, since no web site is complete without at least one advertisement,
                if you're planning on buying something from Amazon, if you go to their site
                through <a href="http://www.amazon.com/exec/obidos/redirect-home/josheckelshom-20/">this link</a>
                and purchase something I'll get some referral money.
            </p>

            <h2>Contact info</h2>
            <p>
                If, for some reason, you would like to get in touch with me, you can e-mail me at
                <a href="mailto:josh@jeckels.com">josh@jeckels.com</a>.
            </p>
        </td>
        <td valign="top">
            <% if (Boolean.TRUE.equals(request.getSession(true).getAttribute("ShowAds")))
            { %>
            <script type="text/javascript"><!--
            google_ad_client = "pub-0439692010885772";
            google_ad_width = 120;
            google_ad_height = 600;
            google_ad_format = "120x600_as";
            google_ad_type = "text";
            google_ad_channel = "";
            google_color_border = "CCCCCC";
            google_color_bg = "FFFFFF";
            google_color_link = "000000";
            google_color_url = "666666";
            google_color_text = "333333";
            //--></script>
            <script type="text/javascript"
                    src="http://pagead2.googlesyndication.com/pagead/show_ads.js">
            </script>
            <% } %>
        </td>
    </tr>
</table>
<jsp:include page="/license.jsp"/>
</body>
</html>
